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
        if len(polygon) == 4 and area >= image_area * 0.35:
            candidates.append((area, polygon.reshape(4, 2)))
    if not candidates:
        return None
    return max(candidates, key=lambda item: item[0])[1].astype(np.float32)


def clean_image(image: np.ndarray, config: CleaningConfig | None = None) -> CleaningResult:
    config = config or CleaningConfig()
    corners = _page_corners(image)
    ordered = None
    corrected = image
    page_area_ratio = 1.0
    size = config.output_size
    if corners is not None:
        ordered = np.zeros((4, 2), dtype=np.float32)
        sums = corners.sum(axis=1)
        differences = np.diff(corners, axis=1).ravel()
        ordered[0] = corners[np.argmin(sums)]
        ordered[2] = corners[np.argmax(sums)]
        ordered[1] = corners[np.argmin(differences)]
        ordered[3] = corners[np.argmax(differences)]
        destination = np.array(
            [[0, 0], [size - 1, 0], [size - 1, size - 1], [0, size - 1]],
            dtype=np.float32,
        )
        corrected = cv2.warpPerspective(
            image, cv2.getPerspectiveTransform(ordered, destination), (size, size)
        )
        # Remove the detected page edge before looking for drawing ink.
        margin = max(1, size // 40)
        corrected = corrected[margin:-margin, margin:-margin]
        page_area_ratio = cv2.contourArea(ordered) / float(image.shape[0] * image.shape[1])
    quality = evaluate_quality(corrected, page_area_ratio, config.quality)
    if quality.passed:
        gray = cv2.cvtColor(corrected, cv2.COLOR_BGR2GRAY)
        # Lift the paper background while retaining anti-aliased pencil strokes.
        background = cv2.GaussianBlur(gray, (0, 0), 15)
        normalized = cv2.divide(gray, np.maximum(background, 1), scale=255)
        corrected = cv2.cvtColor(cv2.resize(normalized, (size, size)), cv2.COLOR_GRAY2BGR)
    if config.debug_dir:
        debug = Path(config.debug_dir)
        debug.mkdir(parents=True, exist_ok=True)
        cv2.imwrite(
            str(debug / "edges.png"), cv2.Canny(cv2.cvtColor(image, cv2.COLOR_BGR2GRAY), 50, 150)
        )
        cv2.imwrite(str(debug / "cleaned.png"), corrected)
    return CleaningResult(
        "cleaning_v1",
        corrected if quality.passed else None,
        tuple(tuple(int(value) for value in point) for point in ordered)
        if ordered is not None
        else None,
        quality,
    )
