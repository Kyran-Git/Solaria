from __future__ import annotations

from dataclasses import dataclass

import cv2
import numpy as np


@dataclass(frozen=True)
class QualityConfig:
    min_page_area_ratio: float = 0.2
    min_laplacian_variance: float = 50.0
    min_mean_brightness: float = 35.0
    max_mean_brightness: float = 230.0


@dataclass(frozen=True)
class QualityResult:
    passed: bool
    reason_code: str | None
    message: str


def evaluate_quality(
    image: np.ndarray,
    page_area_ratio: float,
    config: QualityConfig | None = None,
) -> QualityResult:
    config = config or QualityConfig()
    if page_area_ratio < config.min_page_area_ratio:
        return QualityResult(
            False,
            "page_region_too_small",
            "Page region is too small; move the camera closer and retake.",
        )
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    brightness = float(gray.mean())
    if brightness < config.min_mean_brightness:
        return QualityResult(
            False, "image_too_dark", "Image is too dark; add light and retake the image."
        )
    if brightness > config.max_mean_brightness:
        return QualityResult(
            False, "image_too_bright", "Image is too bright; reduce glare and retake the image."
        )
    blur_score = float(cv2.Laplacian(gray, cv2.CV_64F).var())
    if blur_score < config.min_laplacian_variance:
        return QualityResult(
            False, "image_too_blurry", "Image is too blurry; hold the camera steady and retake."
        )
    return QualityResult(True, None, "Quality checks passed.")
