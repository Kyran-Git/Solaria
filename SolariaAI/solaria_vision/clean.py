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


def _normalize_lighting(image: np.ndarray) -> np.ndarray:
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    height, width = gray.shape
    scale = min(1.0, 256.0 / max(min(height, width), 1))
    small = (
        cv2.resize(
            gray,
            (max(1, round(width * scale)), max(1, round(height * scale))),
            interpolation=cv2.INTER_AREA,
        )
        if scale < 1.0
        else gray
    )
    small_min = min(small.shape)
    kernel_size = max(15, round(small_min * 0.14) | 1)
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (kernel_size, kernel_size))
    closed = cv2.morphologyEx(small, cv2.MORPH_CLOSE, kernel)
    bg_local = cv2.GaussianBlur(closed.astype(np.float32), (0, 0), max(5.0, small_min * 0.10))
    # Paper illumination is an upper envelope of luminance; guarding against large dark
    # regions with an edge-preserving median prevents bright halos on adjacent paper.
    bg_local = np.maximum(bg_local, cv2.medianBlur(small, 3).astype(np.float32))
    paper_ref = float(np.percentile(bg_local, 95))
    bg_safe = np.maximum(bg_local, max(paper_ref * 0.65, 1.0))
    if scale < 1.0:
        bg_safe = cv2.resize(bg_safe, (width, height), interpolation=cv2.INTER_LINEAR)
    bg_eff = 0.75 * bg_safe + 0.25 * max(paper_ref, 1.0)
    target = 222.0 if paper_ref <= 222.0 else min(paper_ref, 230.0)
    gain = np.clip(target / np.maximum(bg_eff, 1.0), 0.88, 2.1)
    max_channel = np.maximum(image.max(axis=2).astype(np.float32), 1.0)
    pixel_gain = np.minimum(gain, 255.0 / max_channel)
    return np.clip(np.round(image.astype(np.float32) * pixel_gain[..., None]), 0, 255).astype(
        np.uint8
    )


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
        normalized = _normalize_lighting(corrected)
        interpolation = cv2.INTER_AREA if min(normalized.shape[:2]) > size else cv2.INTER_LINEAR
        corrected = cv2.resize(normalized, (size, size), interpolation=interpolation)
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
