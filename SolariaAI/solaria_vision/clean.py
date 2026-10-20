from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path

import cv2
import numpy as np

from .quality import QualityConfig, QualityResult, evaluate_quality


@dataclass(frozen=True)
class CleaningConfig:
    output_size: int = 512
    quality: QualityConfig = field(default_factory=QualityConfig)
    debug_dir: str | None = None


@dataclass(frozen=True)
class CleaningResult:
    version: str
    image: np.ndarray | None
    page_corners: tuple[tuple[int, int], ...] | None
    quality: QualityResult


def _page_corners(image: np.ndarray) -> np.ndarray | None:
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    edges = cv2.Canny(gray, 50, 150)
    contours, _ = cv2.findContours(edges, cv2.RETR_LIST, cv2.CHAIN_APPROX_SIMPLE)
    height, width = gray.shape
    image_area = float(height * width)
    candidates = []
    for contour in contours:
        perimeter = cv2.arcLength(contour, True)
        polygon = cv2.approxPolyDP(contour, 0.03 * perimeter, True)
        area = cv2.contourArea(polygon)
        if len(polygon) == 4 and area >= image_area * 0.05:
            candidates.append((area, polygon.reshape(4, 2)))
    if not candidates:
        return None
    return max(candidates, key=lambda item: item[0])[1].astype(np.float32)


def clean_image(image: np.ndarray, config: CleaningConfig | None = None) -> CleaningResult:
    config = config or CleaningConfig()
    corners = _page_corners(image)
    if corners is None:
        quality = QualityResult(
            False,
            "page_not_found",
            "Page not found; place the whole page in view and retake the image.",
        )
        return CleaningResult("cleaning_v1", None, None, quality)

    ordered = np.zeros((4, 2), dtype=np.float32)
    sums = corners.sum(axis=1)
    differences = np.diff(corners, axis=1).ravel()
    ordered[0] = corners[np.argmin(sums)]
    ordered[2] = corners[np.argmax(sums)]
    ordered[1] = corners[np.argmin(differences)]
    ordered[3] = corners[np.argmax(differences)]
    size = config.output_size
    destination = np.array(
        [[0, 0], [size - 1, 0], [size - 1, size - 1], [0, size - 1]],
        dtype=np.float32,
    )
    corrected = cv2.warpPerspective(image, cv2.getPerspectiveTransform(ordered, destination), (size, size))
    page_area_ratio = cv2.contourArea(ordered) / float(image.shape[0] * image.shape[1])
    quality = evaluate_quality(corrected, page_area_ratio, config.quality)
    if config.debug_dir:
        debug = Path(config.debug_dir)
        debug.mkdir(parents=True, exist_ok=True)
        cv2.imwrite(str(debug / "edges.png"), cv2.Canny(cv2.cvtColor(image, cv2.COLOR_BGR2GRAY), 50, 150))
        cv2.imwrite(str(debug / "cleaned.png"), corrected)
    return CleaningResult(
        "cleaning_v1",
        corrected if quality.passed else None,
        tuple(tuple(int(value) for value in point) for point in ordered),
        quality,
    )
