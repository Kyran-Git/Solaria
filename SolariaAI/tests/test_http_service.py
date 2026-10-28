import base64
import io
import json
import subprocess
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from contextlib import contextmanager
from http.client import HTTPConnection
from pathlib import Path

import jsonschema
import pytest
from PIL import Image, ImageDraw

from solaria_classifier.classes import CLASS_IDS
from solaria_classifier.http_service import ScanRuntime, SolariaHTTPServer
from solaria_classifier.inference import MockClassifier
from solaria_classifier.scan_processor import ServiceError


def test_configurable_upload_limits():
    with running_service(max_body_bytes=65536, max_image_pixels=65536) as server:
        status, payload = request(server, "POST", "/v1/classify", drawing_bytes())
        assert status == 422 and payload["reason_code"] == "INPUT_INVALID"
        status, payload = request(server, "POST", "/v1/classify", b"x" * 65537)
        assert status == 413 and payload["reason_code"] == "INPUT_TOO_LARGE"
    for setting in ({"max_body_bytes": 0}, {"max_image_pixels": 16_000_001}):
        with pytest.raises(ValueError):
            ScanRuntime(MockClassifier(), "test-token", **setting)



def drawing_bytes(format="PNG"):
    image = Image.new("RGB", (640, 480), "white")
    ImageDraw.Draw(image).ellipse((200, 120, 440, 360), outline="black", width=8)
    output = io.BytesIO()
    image.save(output, format=format)
    return output.getvalue()


@contextmanager
def running_service(**kwargs):
    runtime = ScanRuntime(MockClassifier(), "test-private-token", **kwargs)
    server = SolariaHTTPServer(runtime, 0)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        yield server
    finally:
        server.shutdown()
        server.server_close()
        runtime.close()
        thread.join(timeout=5)


def request(server, method="GET", path="/v1/health", body=None, **headers):
    connection = HTTPConnection("127.0.0.1", server.server_port, timeout=20)
    defaults = {
        "Authorization": "Bearer test-private-token",
        "X-Request-ID": "test-1",
        "Content-Type": "image/png",
    }
    defaults.update(headers)
    try:
        connection.request(method, path, body=body, headers=defaults)
        response = connection.getresponse()
        payload = json.loads(response.read())
        schema = json.loads(
            (Path(__file__).parents[1] / "docs/schemas/runtime_v1.schema.json").read_text(
                encoding="utf-8"
            )
        )
        jsonschema.Draft202012Validator(schema).validate(payload)
        return response.status, payload
    finally:
        connection.close()


@pytest.mark.parametrize("format", ["PNG", "JPEG"])
def test_live_health_and_classify_all_planets(format, tmp_path, monkeypatch):
    monkeypatch.chdir(tmp_path)
    with running_service() as server:
        assert server.server_address[0] == "127.0.0.1"
        status, health = request(server)
        assert status == 200
        assert health["classes"] == list(CLASS_IDS)
        assert health["mock"] is True and health["threshold"] == 0.6
        assert health["camera_available"] is False
        for label in health["classes"]:
            status, result = request(
                server,
                "POST",
                "/v1/classify",
                drawing_bytes(format),
                **{
                    "X-Solaria-Label": label,
                    "Content-Type": "image/jpeg" if format == "JPEG" else "image/png",
                },
            )
            assert status == 200
            assert result["label"] == label and result["accepted"] is True
            assert result["request_id"] == "test-1" and result["mock"] is True
            assert result["quality"]["status"] == "pass"
            assert len(result["top3"]) == 3
            thumbnail = Image.open(io.BytesIO(base64.b64decode(result["thumbnail"]["base64"])))
            assert thumbnail.format == "PNG" and max(thumbnail.size) <= 128
    assert list(tmp_path.iterdir()) == []


@pytest.mark.parametrize(
    ("headers", "body", "expected", "code"),
    [
        ({"Authorization": "Bearer wrong"}, b"x", 401, "UNAUTHORIZED"),
        ({"X-Request-ID": ""}, b"x", 400, "INPUT_INVALID"),
        ({"Content-Type": "text/plain"}, b"x", 415, "INPUT_INVALID"),
        ({}, b"not an image", 422, "INPUT_INVALID"),
        ({"Content-Length": str(2 * 1024 * 1024 + 1)}, b"x", 413, "INPUT_TOO_LARGE"),
        ({"X-Solaria-Label": "asteroid"}, b"x", 422, "LABEL_NOT_IN_CLASSES"),
        ({"X-Solaria-Scenario": "invented"}, b"x", 400, "INPUT_INVALID"),
    ],
)
def test_http_errors_are_structured(headers, body, expected, code):
    with running_service() as server:
        status, payload = request(server, "POST", "/v1/classify", body, **headers)
        assert status == expected and payload["reason_code"] == code
        assert payload["api_version"] == "api_v1"
        assert "test-private-token" not in json.dumps(payload)


@pytest.mark.parametrize(
    ("scenario", "code"),
    [
        ("quality_blur", "too_blurry"),
        ("quality_no_page", "no_drawing_found"),
        ("low_confidence", "CONF_BELOW_THRESHOLD"),
    ],
)
def test_rejections_never_allow_build(scenario, code):
    with running_service() as server:
        status, result = request(
            server, "POST", "/v1/classify", drawing_bytes(), **{"X-Solaria-Scenario": scenario}
        )
        assert status == 200
        assert result["label"] == "unknown" and result["accepted"] is False
        assert result["reason_code"] == code


def test_30_concurrent_http_requests_finish():
    with running_service() as server:
        body = drawing_bytes()
        with ThreadPoolExecutor(max_workers=30) as executor:
            results = list(
                executor.map(
                    lambda i: request(
                        server, "POST", "/v1/classify", body, **{"X-Request-ID": f"burst-{i}"}
                    ),
                    range(30),
                )
            )
        assert all(status == 200 for status, _ in results)
        assert len({result["request_id"] for _, result in results}) == 30
        assert server.runtime.health()["queue"]["pending"] == 0


def test_runtime_queue_full_and_timeout_recovery(monkeypatch):
    import solaria_classifier.http_service as http

    entered, release = threading.Event(), threading.Event()

    def blocked(*args, **kwargs):
        entered.set()
        release.wait(2)
        return {}

    monkeypatch.setattr(http, "process_scan", blocked)
    runtime = ScanRuntime(MockClassifier(), "token", workers=1, queue_size=0, timeout=0.05)
    try:
        with ThreadPoolExecutor(max_workers=1) as executor:
            first = executor.submit(runtime.classify, b"", "first", None, None)
            assert entered.wait(2)
            with pytest.raises(ServiceError) as error:
                runtime.classify(b"", "overflow", None, None)
            assert error.value.code == "QUEUE_FULL"
            with pytest.raises(ServiceError) as error:
                first.result()
            assert error.value.code == "SCAN_TIMEOUT"
            assert runtime.health()["queue"]["in_flight"] == 1
            release.set()
        for _ in range(100):
            if runtime.health()["queue"]["in_flight"] == 0:
                break
            time.sleep(0.01)
        assert runtime.health()["queue"]["timed_out"] == 1
    finally:
        release.set()
        runtime.close()


def test_service_import_does_not_load_training_framework():
    result = subprocess.run(
        [
            sys.executable,
            "-c",
            (
                "import solaria_classifier.http_service; import sys; "
                "assert 'torch' not in sys.modules; assert 'torchvision' not in sys.modules"
            ),
        ],
        check=False,
        capture_output=True,
        text=True,
        timeout=30,
    )
    assert result.returncode == 0, result.stderr


@pytest.mark.parametrize(
    ("scenario", "status", "code"),
    [("model_unavailable", 503, "MODEL_UNAVAILABLE"), ("slow", 504, "SCAN_TIMEOUT")],
)
def test_failure_scenarios_over_http(scenario, status, code):
    with running_service() as server:
        actual, result = request(
            server, "POST", "/v1/classify", drawing_bytes(), **{"X-Solaria-Scenario": scenario}
        )
        assert actual == status and result["reason_code"] == code


def test_blank_frame_is_rejected_even_in_mock_mode():
    buffer = io.BytesIO()
    Image.new("RGB", (640, 480), "white").save(buffer, format="PNG")
    with running_service() as server:
        status, result = request(server, "POST", "/v1/classify", buffer.getvalue())
        assert status == 200
        assert result["quality"]["status"] == "fail"
        assert not result["accepted"] and result["thumbnail"] is None


def test_real_mode_cannot_be_forced_by_mock_headers():
    with running_service() as server:
        server.runtime.classifier.mock = False
        status, result = request(
            server, "POST", "/v1/classify", drawing_bytes(), **{"X-Solaria-Label": "mars"}
        )
        assert status == 400 and result["reason_code"] == "INPUT_INVALID"


def test_unknown_routes_do_not_pretend_sessions_exist():
    with running_service() as server:
        status, result = request(server, "POST", "/v1/scan", drawing_bytes())
        assert status == 404 and result["reason_code"] == "NOT_FOUND"


def test_docker_interface_bind_still_requires_authentication():
    runtime = ScanRuntime(MockClassifier(), "test-private-token")
    try:
        with SolariaHTTPServer(runtime, 0, "0.0.0.0") as server:
            thread = threading.Thread(target=server.serve_forever, daemon=True)
            thread.start()
            try:
                assert server.server_address[0] == "0.0.0.0"
                assert request(server)[0] == 200
                status, payload = request(server, Authorization="Bearer wrong-token")
                assert status == 401 and payload["reason_code"] == "UNAUTHORIZED"
            finally:
                server.shutdown()
                thread.join(timeout=5)
    finally:
        runtime.close()


def test_docker_bind_refuses_generated_token(monkeypatch):
    from argparse import Namespace

    from solaria_classifier.http_service import serve

    monkeypatch.delenv("SOLARIA_STARTUP_TOKEN", raising=False)
    with pytest.raises(ValueError, match="SOLARIA_STARTUP_TOKEN is required"):
        serve(Namespace(host="0.0.0.0"))


def test_arbitrary_bind_address_is_rejected():
    runtime = ScanRuntime(MockClassifier(), "test-private-token")
    try:
        with pytest.raises(ValueError, match="explicit Docker"):
            SolariaHTTPServer(runtime, 0, "192.0.2.1")
    finally:
        runtime.close()
