"""Ordered, synthetic-only checks used by the Docker test image."""

from __future__ import annotations

import argparse
import importlib
import json
import os
import secrets
import signal
import subprocess
import sys
import time
from contextlib import contextmanager
from http.client import HTTPConnection
from pathlib import Path


def run_step(title: str, *arguments: str, env: dict | None = None) -> None:
    print(f"\n=== {title} ===", flush=True)
    subprocess.run([sys.executable, *arguments], check=True, env=env)


def validate_environment() -> None:
    # Fail rather than silently skipping optional inference/export tests.
    for module in (
        "torch",
        "torchvision",
        "onnx",
        "onnxruntime",
        "onnxscript",
        "cv2",
        "pytest",
        "ruff",
        "jsonschema",
    ):
        importlib.import_module(module)
    from jsonschema import Draft202012Validator

    for path in sorted(Path("docs/schemas").glob("*.schema.json")):
        Draft202012Validator.check_schema(json.loads(path.read_text(encoding="utf-8")))
    if not Path("docs/schemas/runtime_v1.schema.json").is_file():
        raise RuntimeError("Run from the SolariaAI directory; runtime schema is missing.")
    print("Test dependencies and JSON schemas are available.", flush=True)


def wait_for_service(process, port: int, token: str, timeout: float) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError(f"Mock service exited before readiness (code {process.returncode}).")
        connection = HTTPConnection("127.0.0.1", port, timeout=1)
        try:
            connection.request("GET", "/v1/health", headers={"Authorization": f"Bearer {token}"})
            response = connection.getresponse()
            health = json.loads(response.read())
            if response.status == 200 and health.get("mock") is True:
                return
        except (OSError, ValueError):
            pass
        finally:
            connection.close()
        time.sleep(0.1)
    raise RuntimeError(f"Mock service did not become ready within {timeout:g} seconds.")


@contextmanager
def mock_service(port: int, timeout: float):
    env = {**os.environ, "SOLARIA_STARTUP_TOKEN": secrets.token_urlsafe(32)}
    process = subprocess.Popen(
        [sys.executable, "-m", "solaria_classifier", "serve", "--mock", "--port", str(port)],
        env=env,
    )
    try:
        wait_for_service(process, port, env["SOLARIA_STARTUP_TOKEN"], timeout)
        yield env
    finally:
        process.terminate()
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=5)
        print("Mock service stopped.", flush=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=8000)
    parser.add_argument("--startup-timeout", type=float, default=30)
    args = parser.parse_args()
    if not 1 <= args.port <= 65535 or not 0 < args.startup_timeout <= 300:
        parser.error("Use a port in 1..65535 and a startup timeout in (0, 300].")

    def terminate(signum, frame):
        raise SystemExit(128 + signum)

    signal.signal(signal.SIGTERM, terminate)
    try:
        print("\n=== 1/6: Dependencies and schemas ===", flush=True)
        validate_environment()
        run_step("2/6: Installed dependency consistency", "-m", "pip", "check")
        run_step("3/6: Ruff lint", "-m", "ruff", "check", ".")
        run_step(
            "4/6: Full pytest suite (including ONNX export/parity)",
            "-m",
            "pytest",
            "-q",
            "--tb=short",
            "-p",
            "no:cacheprovider",
        )
        print("\n=== 5/6: Start authenticated loopback mock service ===", flush=True)
        with mock_service(args.port, args.startup_timeout) as env:
            run_step(
                "6/6: Live smoke (teacher decisions, revision-safe host acks, 30 concurrent scans)",
                "-m",
                "solaria_classifier.smoke",
                "--port",
                str(args.port),
                env=env,
            )
        print("\nPASS: all ordered checks completed (synthetic validation only).", flush=True)
        return 0
    except (subprocess.CalledProcessError, OSError, RuntimeError) as exc:
        print(f"\nFAIL: {exc}", file=sys.stderr, flush=True)
        return 1
    except KeyboardInterrupt:
        return 130


if __name__ == "__main__":
    raise SystemExit(main())
