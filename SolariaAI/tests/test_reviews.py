import json
from concurrent.futures import ThreadPoolExecutor

import pytest
from test_http_service import drawing_bytes, request, running_service

from solaria_classifier.classes import CLASS_IDS
from solaria_classifier.reviews import ReviewStore
from solaria_classifier.scan_processor import ServiceError


def submit(server, **headers):
    status, result = request(server, "POST", "/v1/classify", drawing_bytes(), **headers)
    assert status == 200
    return result


def override(server, scan_id, payload):
    return request(
        server,
        "POST",
        f"/v1/reviews/{scan_id}/override",
        json.dumps(payload),
        **{"Content-Type": "application/json"},
    )


def test_classroom_scan_override_and_host_poll(tmp_path, monkeypatch):
    monkeypatch.chdir(tmp_path)
    with running_service() as server:
        assert request(server, path="/v1/reviews")[1]["reviews"] == []
        prediction = submit(server, **{"X-Solaria-Student-ID": "student-7"})
        scan_id = prediction.pop("scan_id")
        status, feed = request(server, path="/v1/reviews")
        assert status == 200
        record = feed["reviews"][0]
        assert record["student_id"] == "student-7"
        assert record["prediction"] == prediction
        status, response = override(
            server, scan_id, {"label": "mars", "revision": 0, "note": "Checked drawing"}
        )
        assert status == 200
        reviewed = response["review"]
        assert reviewed["prediction"] == prediction
        assert reviewed["final_label"] == "mars"
        assert reviewed["accepted"] is True and reviewed["decider"] == "teacher"
        assert reviewed["revision"] == 1
        assert reviewed["overrides"][0]["note"] == "Checked drawing"
        assert request(server, path=f"/v1/reviews/{scan_id}")[1]["review"] == reviewed
        assert override(server, scan_id, {"label": "moon", "revision": 0, "note": ""})[0] == 409
        assert request(server, path=f"/v1/reviews/{scan_id}")[1]["review"] == reviewed
    assert list(tmp_path.iterdir()) == []


def test_low_confidence_can_be_overridden_but_quality_failure_cannot():
    with running_service() as server:
        low = submit(server, **{"X-Solaria-Scenario": "low_confidence"})
        assert low["label"] == "unknown"
        assert (
            override(server, low["scan_id"], {"label": "earth", "revision": 0, "note": ""})[0]
            == 200
        )
        failed = submit(server, **{"X-Solaria-Scenario": "quality_blur"})
        status, error = override(
            server, failed["scan_id"], {"label": "earth", "revision": 0, "note": ""}
        )
        assert status == 409 and error["reason_code"] == "QUALITY_REJECTED"


@pytest.mark.parametrize(
    "payload",
    [
        [],
        {},
        {"label": "mars", "revision": True, "note": ""},
        {"label": "mars", "revision": -1, "note": ""},
        {"label": "mars", "revision": 0, "note": "x" * 501},
        {"label": "mars", "revision": 0, "note": "bad\nnote"},
        {"label": "mars", "revision": 0, "note": "", "accepted": 1},
        {"label": ["mars"], "revision": 0, "note": ""},
        {"label": "asteroid", "revision": 0, "note": ""},
    ],
)
def test_invalid_override_does_not_change_record(payload):
    with running_service() as server:
        result = submit(server)
        status, _ = override(server, result["scan_id"], payload)
        assert status in {400, 422}
        assert (
            request(server, path=f"/v1/reviews/{result['scan_id']}")[1]["review"]["revision"] == 0
        )


def test_review_body_limits_and_missing_records():
    with running_service() as server:
        scan_id = submit(server)["scan_id"]
        path = f"/v1/reviews/{scan_id}/override"
        for body, headers, expected in [
            (b"{", {"Content-Type": "application/json"}, 400),
            (b"{}", {"Content-Type": "text/plain"}, 415),
            (b"x" * 4097, {"Content-Type": "application/json"}, 413),
        ]:
            assert request(server, "POST", path, body, **headers)[0] == expected
        assert request(server, path="/v1/reviews/" + "a" * 32)[0] == 404
        assert override(server, "a" * 32, {"label": "mars", "revision": 0, "note": ""})[0] == 404
        assert (
            request(
                server,
                "POST",
                "/v1/classify",
                drawing_bytes(),
                **{"X-Solaria-Student-ID": "bad id"},
            )[0]
            == 400
        )
        a, b = submit(server), submit(server)
        assert a["scan_id"] != b["scan_id"]  # request ids are correlation, not record identity


def test_store_bounds_expiry_copy_isolation_and_concurrent_override(monkeypatch):
    clock = [100.0]
    monkeypatch.setattr("solaria_classifier.reviews.time.monotonic", lambda: clock[0])
    store = ReviewStore(CLASS_IDS, capacity=2, ttl=10)
    prediction = {"label": "earth", "accepted": True, "quality": {"status": "pass"}}
    first = store.add(prediction)
    second = store.add(prediction)
    third = store.add(prediction)
    assert [r["scan_id"] for r in store.feed()["reviews"]] == [third, second]
    with pytest.raises(ServiceError):
        store.get(first)
    prediction["label"] = "mars"
    copy = store.get(third)
    copy["prediction"]["label"] = "moon"
    assert store.get(third)["prediction"]["label"] == "earth"

    def attempt(label):
        try:
            store.override(third, {"label": label, "revision": 0, "note": ""})
            return 200
        except ServiceError as error:
            return error.status

    with ThreadPoolExecutor(max_workers=2) as pool:
        assert sorted(pool.map(attempt, ["mars", "moon"])) == [200, 409]
    for revision in range(1, 20):
        store.override(third, {"label": "mars", "revision": revision, "note": ""})
    with pytest.raises(ServiceError):
        store.override(third, {"label": "mars", "revision": 20, "note": ""})
    clock[0] += 10
    assert store.feed()["reviews"] == []
    with pytest.raises(ServiceError):
        store.get(third)


def test_revision_safe_host_acknowledgement_and_rejection():
    with running_service() as server:
        prediction = submit(server, **{"X-Solaria-Student-ID": "host-participant"})
        scan_id = prediction["scan_id"]
        path = f"/v1/reviews/{scan_id}/ack"

        def ack(revision, status, **identity):
            payload = {"revision": revision, "status": status,
                       "request_id": prediction["request_id"], "student_id": "host-participant"}
            payload.update(identity)
            return request(server, "POST", path, json.dumps(payload),
                           **{"Content-Type": "application/json"})

        assert ack(0, "ready")[0] == 409
        assert override(server, scan_id, {"label": "mars", "revision": 0, "note": ""})[0] == 200
        assert ack(1, "ready", student_id="other")[0] == 409
        assert ack(1, "ready", request_id="other")[0] == 409
        assert ack(1, "waiting_student")[0] == 200
        assert ack(1, "ready")[0] == 200
        assert ack(1, "ready")[0] == 200  # idempotent retry
        assert ack(1, "waiting_student")[0] == 409
        status, decision = override(server, scan_id, {
            "label": "moon", "revision": 1, "note": "Rescan", "accepted": False
        })
        assert status == 200
        assert decision["review"]["application"] is None
        assert not decision["review"]["accepted"]
        assert ack(1, "ready")[0] == 409  # old host result cannot acknowledge a newer decision
        assert ack(2, "ready")[0] == 409
        assert ack(2, "rejected")[1]["review"]["application"] == {
            "revision": 2, "status": "rejected"
        }
        assert ack(True, "ready")[0] == 400
        assert ack(2, "teleported")[0] == 400
