"""In-memory decoding, quality, cleaning and prediction for one scan."""

from __future__ import annotations

import base64
import io
import time
import warnings

import cv2
import numpy as np
from PIL import Image, UnidentifiedImageError

from solaria_vision.clean import clean_image
from solaria_vision.quality import failure

SCENARIOS = {
    "success",
    "low_confidence",
    "quality_blur",
    "quality_no_page",
    "model_unavailable",
    "slow",
}


class ServiceError(Exception):
    def __init__(self, status: int, code: str, message: str) -> None:
        super().__init__(message)
        self.status, self.code, self.message = status, code, message


def process_scan(
    classifier,
    body: bytes,
    request_id: str,
    threshold: float,
    scenario: str,
    label: str | None,
    deadline: float,
) -> dict:
    started = time.monotonic()
    if started >= deadline:
        raise ServiceError(504, "SCAN_TIMEOUT", "Scan timed out; please retry.")
    try:
        with warnings.catch_warnings():
            warnings.simplefilter("error", Image.DecompressionBombWarning)
            with Image.open(io.BytesIO(body)) as image:
                if image.format not in {"PNG", "JPEG"} or image.width * image.height > 4_000_000:
                    raise ValueError("Unsupported image or dimensions.")
                image.load()
                rgb = image.convert("RGB")
    except (
        UnidentifiedImageError,
        OSError,
        ValueError,
        Image.DecompressionBombWarning,
        Image.DecompressionBombError,
    ) as exc:
        raise ServiceError(
            422, "INPUT_INVALID", "Supply a PNG or JPEG of at most 4 megapixels."
        ) from exc
    if classifier.mock and scenario == "model_unavailable":
        raise ServiceError(503, "MODEL_UNAVAILABLE", "Ask your teacher to start the model service.")
    if classifier.mock and scenario == "slow":
        time.sleep(max(0.0, min(0.25, deadline - time.monotonic())))
        raise ServiceError(504, "SCAN_TIMEOUT", "Scan timed out; please retry.")
    result = clean_image(cv2.cvtColor(np.asarray(rgb), cv2.COLOR_RGB2BGR))
    quality = result.quality
    if classifier.mock and scenario in {"quality_blur", "quality_no_page"}:
        quality = failure("too_blurry" if scenario == "quality_blur" else "no_drawing_found")
    response = {
        "api_version": "api_v1",
        "request_id": request_id,
        "mock": classifier.mock,
        "quality": {
            "status": "pass" if quality.passed else "fail",
            "reason_code": quality.reason_code,
            "hint": quality.message,
        },
        "label": "unknown",
        "confidence": 0.0,
        "accepted": False,
        "threshold": threshold,
        "reason_code": quality.reason_code,
        "top3": [],
        "thumbnail": None,
    }
    if quality.passed:
        cleaned = Image.fromarray(cv2.cvtColor(result.image, cv2.COLOR_BGR2RGB))
        probabilities = classifier.probabilities(cleaned, label)
        if classifier.mock and scenario == "low_confidence":
            probabilities = np.full(len(classifier.classes), 1.0 / len(classifier.classes))
        indices = np.argsort(-probabilities, kind="stable")[:3]
        confidence = float(probabilities[indices[0]])
        accepted = confidence >= threshold
        response.update(
            label=classifier.classes[indices[0]] if accepted else "unknown",
            confidence=confidence,
            accepted=accepted,
            reason_code=None if accepted else "CONF_BELOW_THRESHOLD",
            top3=[
                {"label": classifier.classes[i], "confidence": float(probabilities[i])}
                for i in indices
            ],
        )
        cleaned.thumbnail((128, 128))
        output = io.BytesIO()
        cleaned.save(output, format="PNG")
        response["thumbnail"] = {
            "content_type": "image/png",
            "base64": base64.b64encode(output.getvalue()).decode("ascii"),
        }
    if time.monotonic() >= deadline:
        raise ServiceError(504, "SCAN_TIMEOUT", "Scan timed out; please retry.")
    response["elapsed_ms"] = round((time.monotonic() - started) * 1000, 3)
    return response
