from __future__ import annotations

from dataclasses import dataclass

import cv2
import numpy as np

QUALITY_HINTS = {
    "no_drawing_found": "Place a drawing on a light page in view.",
    "too_blurry": "Hold the camera steady and focus on the drawing.",
    "too_dark": "Add light and retake the image.",
    "too_bright": "Reduce glare so the drawing is visible.",
    "low_contrast": "Use darker lines against a light page.",
    "drawing_too_small": "Move closer so the drawing fills more of the frame.",
    "drawing_off_centre": "Move the drawing towards the centre of the frame.",
}


@dataclass(frozen=True)
class QualityConfig:
    min_page_area_ratio: float = 0.2
    min_laplacian_variance: float = 50.0
    min_mean_brightness: float = 35.0
    max_mean_brightness: float = 254.5
    min_contrast: float = 20.0
    min_drawing_area_ratio: float = 0.02
    max_centre_offset: float = 0.3


@dataclass(frozen=True)
class QualityResult:
    passed: bool
    reason_code: str | None
    message: str


def failure(code: str) -> QualityResult:
    return QualityResult(False, code, QUALITY_HINTS[code])


def drawing_mask(gray: np.ndarray) -> np.ndarray:
    # A relative threshold also handles lightly shaded paper; ignore sensor noise.
    background = float(np.percentile(gray, 90))
    return (gray < background - 15).astype(np.uint8)


def evaluate_quality(
    image: np.ndarray,
    page_area_ratio: float = 1.0,
    config: QualityConfig | None = None,
) -> QualityResult:
    config = config or QualityConfig()
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    brightness = float(gray.mean())
    if brightness < config.min_mean_brightness:
        return failure("too_dark")
    if brightness > config.max_mean_brightness:
        return failure("too_bright")
    mask = drawing_mask(gray)
    ys, xs = np.nonzero(mask)
    if not len(xs):
        return failure("no_drawing_found")
    if float(gray.max()) - float(gray.min()) < config.min_contrast:
        return failure("low_contrast")
    height, width = gray.shape
    area = (int(xs.max()) - int(xs.min()) + 1) * (int(ys.max()) - int(ys.min()) + 1)
    if (
        page_area_ratio < config.min_page_area_ratio
        or area / gray.size < config.min_drawing_area_ratio
    ):
        return failure("drawing_too_small")
    cx, cy = (float(xs.min()) + float(xs.max())) / 2, (float(ys.min()) + float(ys.max())) / 2
    if max(abs(cx / width - 0.5), abs(cy / height - 0.5)) > config.max_centre_offset:
        return failure("drawing_off_centre")
    if float(cv2.Laplacian(gray, cv2.CV_64F).var()) < config.min_laplacian_variance:
        return failure("too_blurry")
    return QualityResult(True, None, "Quality checks passed.")
