from __future__ import annotations

from pathlib import Path

import cv2
import numpy as np


def capture_file(path: str | Path) -> np.ndarray:
    image = cv2.imread(str(path), cv2.IMREAD_COLOR)
    if image is None:
        raise ValueError(f"Could not read image file: {path}")
    return image


def capture_camera(camera_index: int = 0) -> np.ndarray:
    camera = cv2.VideoCapture(camera_index)
    try:
        if not camera.isOpened():
            raise RuntimeError(
                f"Could not open camera {camera_index}; connect a camera or use --input."
            )
        ok, frame = camera.read()
        if not ok or frame is None:
            raise RuntimeError("Camera returned no frame; hold still and try again.")
        return np.asarray(frame)
    finally:
        camera.release()
