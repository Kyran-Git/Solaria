"""Authenticated HTTP runtime; native loopback default, opt-in Docker interface bind."""

from __future__ import annotations

import hmac
import json
import math
import os
import re
import secrets
import signal
import threading
import time
from concurrent.futures import ThreadPoolExecutor, TimeoutError
from dataclasses import asdict
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from solaria_vision.quality import QualityConfig

from . import __version__
from .inference import MockClassifier, OnnxClassifier
from .reviews import ReviewStore
from .scan_processor import SCENARIOS, ServiceError, process_scan

MAX_BODY_BYTES = 2 * 1024 * 1024
WEB_ASSETS = {
    "/": ("index.html", "text/html; charset=utf-8"),
    "/assets/dashboard.css": ("dashboard.css", "text/css; charset=utf-8"),
    "/assets/dashboard.js": ("dashboard.js", "text/javascript; charset=utf-8"),
}


class ScanRuntime:
    def __init__(
        self,
        classifier,
        token: str,
        *,
        threshold: float = 0.6,
        workers: int = 2,
        queue_size: int = 30,
        timeout: float = 15.0,
        scenario: str = "success",
    ) -> None:
        if not token or len(token) > 256 or not token.isascii() or not token.isprintable():
            raise ValueError(
                "Use a nonempty printable ASCII startup token of at most 256 characters."
            )
        if (
            not 0 <= threshold <= 1
            or not 1 <= workers <= 32
            or not 0 <= queue_size <= 256
            or not math.isfinite(timeout)
            or not 0 < timeout <= 300
            or scenario not in SCENARIOS
        ):
            raise ValueError("Invalid threshold, queue, worker, timeout or scenario setting.")
        self.classifier, self.token = classifier, token
        self.threshold, self.timeout, self.scenario = threshold, timeout, scenario
        self.workers, self.queue_size = workers, queue_size
        self.executor = ThreadPoolExecutor(max_workers=workers, thread_name_prefix="solaria-scan")
        self.slots = threading.BoundedSemaphore(workers + queue_size)
        self.lock = threading.Lock()
        self.pending = self.active = self.timed_out = 0
        self.closed = False
        self.reviews = ReviewStore(classifier.classes)

    def health(self) -> dict:
        with self.lock:
            queue = {
                "pending": self.pending,
                "in_flight": self.active,
                "capacity": self.queue_size,
                "workers": self.workers,
                "timed_out": self.timed_out,
            }
        return {
            "api_version": "api_v1",
            "status": "ok",
            "version": __version__,
            "service_version": __version__,
            "model": self.classifier.model,
            "model_loaded": not self.classifier.mock,
            "mock": self.classifier.mock,
            "classes": list(self.classifier.classes),
            "threshold": self.threshold,
            "thresholds": {"confidence": self.threshold, "quality": asdict(QualityConfig())},
            "camera_available": False,
            "queue": queue,
        }

    def _work(self, body, request_id, scenario, label, deadline):
        with self.lock:
            self.pending -= 1
            self.active += 1
        try:
            return process_scan(
                self.classifier, body, request_id, self.threshold, scenario, label, deadline
            )
        finally:
            with self.lock:
                self.active -= 1

    def classify(self, body: bytes, request_id: str, scenario: str | None, label: str | None):
        if self.closed or not self.slots.acquire(blocking=False):
            raise ServiceError(503, "QUEUE_FULL", "Service is busy; try again in a moment.")
        deadline = time.monotonic() + self.timeout
        with self.lock:
            self.pending += 1
        try:
            future = self.executor.submit(
                self._work, body, request_id, scenario or self.scenario, label, deadline
            )
        except RuntimeError:
            with self.lock:
                self.pending -= 1
            self.slots.release()
            raise ServiceError(503, "SERVICE_UNAVAILABLE", "Service is stopping.") from None

        def finished(task):
            if task.cancelled():
                with self.lock:
                    self.pending -= 1
            self.slots.release()

        future.add_done_callback(finished)
        try:
            return future.result(timeout=max(0.0, deadline - time.monotonic()))
        except TimeoutError:
            future.cancel()
            with self.lock:
                self.timed_out += 1
            raise ServiceError(504, "SCAN_TIMEOUT", "Scan timed out; please retry.") from None
        except ServiceError as exc:
            if exc.code == "SCAN_TIMEOUT":
                with self.lock:
                    self.timed_out += 1
            raise

    def close(self) -> None:
        self.closed = True
        self.executor.shutdown(wait=True, cancel_futures=True)


class SolariaHTTPServer(ThreadingHTTPServer):
    daemon_threads = True
    # Large enough for 30-student classrooms plus headroom; the OS backlog
    # is set separately and capped by the kernel, but this value is passed to
    # socket.listen() and must be ≥ the expected burst connection count.
    request_queue_size = 64

    def __init__(self, runtime: ScanRuntime, port: int = 8000, host: str = "127.0.0.1"):
        if host not in {"127.0.0.1", "0.0.0.0"}:
            raise ValueError("Only loopback or the explicit Docker interface bind is supported.")
        self.runtime = runtime
        super().__init__((host, port), ScanHandler)
        self.connections = threading.BoundedSemaphore(runtime.workers + runtime.queue_size + 8)

    def process_request(self, request, client_address):
        # Bound HTTP threads as well as processing jobs, including slow uploads.
        if not self.connections.acquire(blocking=False):
            self.shutdown_request(request)
            return
        try:
            super().process_request(request, client_address)
        except Exception:
            self.connections.release()
            raise

    def process_request_thread(self, request, client_address):
        try:
            super().process_request_thread(request, client_address)
        finally:
            self.connections.release()

    def handle_error(self, request, client_address):
        pass  # Never print addresses, request bytes or exception details.


class ScanHandler(BaseHTTPRequestHandler):
    server_version = "Solaria"

    def setup(self):
        super().setup()
        self.connection.settimeout(5.0)

    def log_message(self, format, *args):
        pass

    def _json(self, status: int, payload: dict):
        data = json.dumps(payload, allow_nan=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        if status == 503:
            self.send_header("Retry-After", "1")
        self.end_headers()
        self.wfile.write(data)

    def _dispatch(self):
        runtime = self.server.runtime
        authorization = self.headers.get("Authorization", "")
        if not hmac.compare_digest(authorization.encode(), ("Bearer " + runtime.token).encode()):
            raise ServiceError(401, "UNAUTHORIZED", "A valid startup bearer token is required.")
        if self.command == "GET" and self.path == "/v1/health":
            return runtime.health()
        if self.command == "GET" and self.path == "/v1/reviews":
            return runtime.reviews.feed()
        review_path = re.fullmatch(r"/v1/reviews/([a-f0-9]{32})(/override)?", self.path)
        if review_path:
            scan_id, action = review_path.groups()
            if self.command == "GET" and action is None:
                return {"api_version": "api_v1", "review": runtime.reviews.get(scan_id)}
            if self.command == "POST" and action == "/override":
                if self.headers.get_content_type() != "application/json":
                    raise ServiceError(415, "INPUT_INVALID", "Use Content-Type application/json.")
                body = self._body(4096)
                try:
                    payload = json.loads(body)
                except (ValueError, UnicodeError):
                    raise ServiceError(400, "INPUT_INVALID", "Supply valid JSON.") from None
                return {
                    "api_version": "api_v1",
                    "review": runtime.reviews.override(scan_id, payload),
                }
        if self.command != "POST" or self.path != "/v1/classify":
            raise ServiceError(404, "NOT_FOUND", "Unknown runtime route.")
        request_id = self.headers.get("X-Request-ID", "")
        if not re.fullmatch(r"[A-Za-z0-9_-]{1,64}", request_id):
            raise ServiceError(
                400, "INPUT_INVALID", "Supply X-Request-ID (1-64 letters, digits, _ or -)."
            )
        if self.headers.get_content_type() not in {"image/png", "image/jpeg"}:
            raise ServiceError(415, "INPUT_INVALID", "Use Content-Type image/png or image/jpeg.")
        scenario = self.headers.get("X-Solaria-Scenario")
        label = self.headers.get("X-Solaria-Label")
        if (scenario or label) and not runtime.classifier.mock:
            raise ServiceError(
                400, "INPUT_INVALID", "Scenario headers are only allowed in mock mode."
            )
        if scenario is not None and scenario not in SCENARIOS:
            raise ServiceError(400, "INPUT_INVALID", "Unknown mock scenario.")
        if label is not None and label not in runtime.classifier.classes:
            raise ServiceError(422, "LABEL_NOT_IN_CLASSES", "Use a class advertised by health.")
        student_id = self.headers.get("X-Solaria-Student-ID")
        if student_id is not None and not re.fullmatch(r"[A-Za-z0-9_-]{1,64}", student_id):
            raise ServiceError(
                400, "INPUT_INVALID", "Use a pseudonymous student id (1-64 ASCII id characters)."
            )
        body = self._body(MAX_BODY_BYTES)
        result = runtime.classify(body, request_id, scenario, label)
        result["scan_id"] = runtime.reviews.add(result, student_id)
        return result

    def _body(self, limit):
        if self.headers.get("Transfer-Encoding"):
            raise ServiceError(400, "INPUT_INVALID", "Chunked HTTP bodies are not supported.")
        lengths = self.headers.get_all("Content-Length", [])
        if len(lengths) != 1 or not lengths[0].isdigit():
            raise ServiceError(411, "INPUT_INVALID", "Supply one Content-Length header.")
        length = int(lengths[0])
        if not 0 < length <= limit:
            raise ServiceError(413, "INPUT_TOO_LARGE", "Request body exceeds the route limit.")
        body = self.rfile.read(length)
        if len(body) != length:
            raise ServiceError(400, "INPUT_INVALID", "Incomplete request body.")
        return body

    def _dashboard(self):
        # Only these public, fixed assets bypass API authentication. Never serve
        # arbitrary filesystem paths or inject the private runtime token.
        filename, content_type = WEB_ASSETS[self.path]
        data = (Path(__file__).with_name("web") / filename).read_bytes()
        self.send_response(200)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("X-Frame-Options", "DENY")
        self.send_header("Referrer-Policy", "no-referrer")
        self.send_header(
            "Content-Security-Policy",
            "default-src 'none'; script-src 'self'; style-src 'self'; "
            "img-src 'self' blob: data:; connect-src 'self'; "
            "base-uri 'none'; form-action 'none'; frame-ancestors 'none'",
        )
        self.end_headers()
        self.wfile.write(data)

    def _handle(self):
        try:
            if self.command == "GET" and self.path in WEB_ASSETS:
                self._dashboard()
                return
            self._json(200, self._dispatch())
        except ServiceError as exc:
            payload = {"api_version": "api_v1", "reason_code": exc.code, "message": exc.message}
            if exc.status == 503:
                payload["retry_after_ms"] = 1000
            self._json(exc.status, payload)
        except (BrokenPipeError, ConnectionResetError, TimeoutError):
            pass
        except Exception:  # noqa: BLE001 -- sanitise failures at the HTTP privacy boundary
            self._json(
                500,
                {
                    "api_version": "api_v1",
                    "reason_code": "INTERNAL_ERROR",
                    "message": "Scan failed; ask your teacher to retry.",
                },
            )

    do_GET = _handle
    do_POST = _handle


def serve(args) -> None:
    host = args.host
    configured_token = os.environ.get("SOLARIA_STARTUP_TOKEN")
    if host != "127.0.0.1" and not configured_token:
        raise ValueError("SOLARIA_STARTUP_TOKEN is required for a non-loopback Docker bind.")
    token = configured_token or secrets.token_urlsafe(32)
    classifier = MockClassifier() if args.mock else OnnxClassifier(args.model, args.metadata)
    runtime = ScanRuntime(
        classifier,
        token,
        threshold=args.threshold,
        workers=args.workers,
        queue_size=args.queue_size,
        timeout=args.timeout,
        scenario=args.scenario,
    )

    def stop(signum, frame):
        # Unwind the server context and drain running work on Docker stop.
        raise KeyboardInterrupt

    previous_sigterm = signal.signal(signal.SIGTERM, stop)
    try:
        with SolariaHTTPServer(runtime, args.port, host) as server:
            print(
                f"SolariaAI {'MOCK (synthetic)' if classifier.mock else 'ONNX'} "
                f"listening on {host}:{server.server_port}",
                flush=True,
            )
            if not configured_token:
                print(f"Startup token (copy into the host mod; keep private): {token}", flush=True)
            try:
                server.serve_forever()
            except KeyboardInterrupt:
                pass
    finally:
        signal.signal(signal.SIGTERM, previous_sigterm)
        runtime.close()
