"""Synthetic inference/teacher-review probe for MOCK mode; creates volatile test reviews."""

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

    def call(path, body=None, *, expected_status=200, **extra):
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
            if response.status != expected_status:
                raise RuntimeError(f"HTTP {response.status}: {result.get('reason_code')}")
            return result
        finally:
            connection.close()

    health = call("/v1/health")
    if not health["mock"] or set(health["classes"]) != set(CLASS_IDS):
        raise RuntimeError("Smoke test requires a MOCK service with the ten Solaria classes.")
    # The public page is a teacher console, never a browser scan client.
    connection = HTTPConnection("127.0.0.1", args.port, timeout=10)
    try:
        connection.request("GET", "/")
        response = connection.getresponse()
        page = response.read()
        if (
            response.status != 200
            or b'id="review-feed"' not in page
            or b'id="override-form"' not in page
            or b'type="file"' in page
            or token.encode() in page
        ):
            raise RuntimeError("Teacher console missing, exposes a token, or still offers upload.")
    finally:
        connection.close()
    if (
        call("/v1/reviews", expected_status=401, Authorization="").get("reason_code")
        != "UNAUTHORIZED"
    ):
        raise RuntimeError("Review feed did not enforce authentication.")
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
    review_scan = call(
        "/v1/classify",
        body,
        **{
            "X-Request-ID": "smoke-teacher-review",
            "X-Solaria-Student-ID": "synthetic-smoke-student",
            "X-Solaria-Label": "earth",
        },
    )
    scan_id = review_scan["scan_id"]
    feed = call("/v1/reviews")
    record = next((r for r in feed["reviews"] if r["scan_id"] == scan_id), None)
    if record is None or record["student_id"] != "synthetic-smoke-student":
        raise RuntimeError("Submitted scan did not appear in the teacher feed.")
    original = record["prediction"]
    payload = json.dumps(
        {"label": "mars", "revision": record["revision"], "note": "Synthetic smoke correction"}
    ).encode()
    path = f"/v1/reviews/{scan_id}"
    reviewed = call(path + "/override", payload, **{"Content-Type": "application/json"})["review"]
    if (
        reviewed["prediction"] != original
        or reviewed["final_label"] != "mars"
        or not reviewed["accepted"]
        or reviewed["decider"] != "teacher"
        or reviewed["revision"] != 1
        or len(reviewed["overrides"]) != 1
        or call(path)["review"] != reviewed
    ):
        raise RuntimeError("Teacher override or host decision lookup failed.")
    conflict = call(
        path + "/override", payload, expected_status=409, **{"Content-Type": "application/json"}
    )
    if conflict.get("reason_code") != "REVIEW_CONFLICT":
        raise RuntimeError("Stale override did not produce a revision conflict.")
    def acknowledge(revision, status, *, expected_status=200, **identity):
        payload = {
            "revision": revision,
            "request_id": "smoke-teacher-review",
            "student_id": "synthetic-smoke-student",
            "status": status,
            **identity,
        }
        return call(
            path + "/ack",
            json.dumps(payload).encode(),
            expected_status=expected_status,
            **{"Content-Type": "application/json"},
        )

    def application(revision, status):
        acknowledged = acknowledge(revision, status)["review"]
        expected = {"revision": revision, "status": status}
        if (
            acknowledged["application"] != expected
            or call(path)["review"]["application"] != expected
        ):
            raise RuntimeError("Host acknowledgement did not round-trip through review polling.")

    acknowledge(1, "ready", expected_status=409, student_id="other-student")
    acknowledge(1, "ready", expected_status=409, request_id="other-request")
    application(1, "waiting_student")
    application(1, "ready")
    application(1, "ready")  # A retry is idempotent, not evidence of travel.
    acknowledge(1, "waiting_student", expected_status=409)

    # A new rejection invalidates prior acknowledgements, including delayed host writes.
    rejected = call(
        path + "/override",
        json.dumps({"label": "mars", "revision": 1, "note": "Rescan", "accepted": False}),
        **{"Content-Type": "application/json"},
    )["review"]
    if rejected["accepted"] or rejected["application"] is not None or rejected["revision"] != 2:
        raise RuntimeError("Teacher rejection did not clear the previous acknowledgement.")
    acknowledge(1, "ready", expected_status=409)
    acknowledge(2, "ready", expected_status=409)
    application(2, "rejected")

    approved = call(
        path + "/override",
        json.dumps({"label": "mars", "revision": 2, "note": "Approved", "accepted": True}),
        **{"Content-Type": "application/json"},
    )["review"]
    if (
        not approved["accepted"]
        or approved["revision"] != 3
        or approved["application"] is not None
        or approved["prediction"] != original
    ):
        raise RuntimeError("Teacher reapproval did not start a clean application revision.")
    acknowledge(2, "rejected", expected_status=409)
    application(3, "waiting_student")
    application(3, "ready")
    # These synthetic host writes test the API, not Minecraft consent, builds or travel.

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
        "synthetic-validated: health, teacher console, authenticated review feed, "
        "teacher correction/rejection/reapproval, preserved AI prediction, host polling, "
        "identity-bound acknowledgements, stale revision conflicts, acknowledgement resets, "
        "ten class round-trips, low confidence, 30 concurrent scans."
    )


if __name__ == "__main__":
    main()
