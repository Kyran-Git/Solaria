"""Synthetic-only integration probe for a running MOCK service; no files saved."""

from __future__ import annotations

import argparse
import io
import json
import os
import time
from concurrent.futures import ThreadPoolExecutor
from http.client import HTTPConnection

from PIL import Image, ImageDraw

from .classes import CLASS_IDS


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=8000)
    args = parser.parse_args()
    token = os.environ.get("SOLARIA_STARTUP_TOKEN")
    if not token:
        parser.error("Set SOLARIA_STARTUP_TOKEN to the running service's token.")

    def call(path, body=None, **extra):
        connection = HTTPConnection("127.0.0.1", args.port, timeout=30)
        headers = {
            "Authorization": f"Bearer {token}",
            "X-Request-ID": "smoke",
            "Content-Type": "image/png",
            **extra,
        }
        try:
            connection.request("GET" if body is None else "POST", path, body=body, headers=headers)
            response = connection.getresponse()
            result = json.loads(response.read())
            if response.status != 200:
                raise RuntimeError(f"HTTP {response.status}: {result.get('reason_code')}")
            return result
        finally:
            connection.close()

    health = call("/v1/health")
    if not health["mock"] or set(health["classes"]) != set(CLASS_IDS):
        raise RuntimeError("Smoke test requires a MOCK service with the ten Solaria classes.")
    image = Image.new("RGB", (640, 480), "white")
    ImageDraw.Draw(image).ellipse((200, 120, 440, 360), outline="black", width=8)
    buffer = io.BytesIO()
    image.save(buffer, format="PNG")
    body = buffer.getvalue()
    for label in health["classes"]:
        result = call("/v1/classify", body, **{"X-Solaria-Label": label})
        if result["label"] != label or not result["accepted"]:
            raise RuntimeError(f"Class round-trip failed: {label}")
    rejected = call("/v1/classify", body, **{"X-Solaria-Scenario": "low_confidence"})
    if rejected["accepted"] or rejected["label"] != "unknown":
        raise RuntimeError("Low-confidence scan was incorrectly accepted.")
    # 30 concurrent scans; if the service is still processing earlier requests
    # it returns 503, so retry with a short delay until all 30 succeed.
    # The classroom design requires all scans to eventually succeed or time out,
    # never to be dropped silently by the TCP layer.
    with ThreadPoolExecutor(max_workers=30) as pool:

        def burst(i):
            for attempt in range(4):
                try:
                    return call("/v1/classify", body, **{"X-Request-ID": f"smoke-{i}"})
                except Exception:
                    if attempt == 3:
                        raise
                    time.sleep(0.5 * (attempt + 1))

        results = list(pool.map(burst, range(30)))
    if len({r["request_id"] for r in results}) != 30 or not all(r["mock"] for r in results):
        raise RuntimeError("Burst request ids or mock flags did not round-trip.")
    print(
        "synthetic-validated: health, ten class round-trips, low confidence, 30 concurrent scans."
    )


if __name__ == "__main__":
    main()
