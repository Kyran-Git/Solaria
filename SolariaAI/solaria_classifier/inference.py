"""Classroom inference: NumPy/Pillow/ONNX Runtime only, never PyTorch."""

from __future__ import annotations

import json
from pathlib import Path

import numpy as np
from PIL import Image

from .classes import CLASS_IDS


class MockClassifier:
    mock = True
    model = "mock"
    classes = CLASS_IDS

    def probabilities(self, image: Image.Image, label: str | None = None) -> np.ndarray:
        # Explicitly synthetic: no claim to recognise the drawing.
        result = np.full(len(self.classes), 0.08 / (len(self.classes) - 1))
        result[self.classes.index(label or "earth")] = 0.92
        return result


class OnnxClassifier:
    mock = False

    def __init__(self, model: str | Path, metadata: str | Path | None = None) -> None:
        import onnxruntime as ort

        model = Path(model)
        sidecar = Path(metadata) if metadata else Path(str(model) + ".metadata.json")
        self.metadata = json.loads(sidecar.read_text(encoding="utf-8"))
        self.classes = self.metadata["labels"]
        if len(self.classes) != len(CLASS_IDS) or set(self.classes) != set(CLASS_IDS):
            raise ValueError("Metadata must contain exactly the ten Solaria class ids.")
        resize = self.metadata["resize"]
        if len(resize) != 2 or any(type(n) is not int or not 1 <= n <= 2048 for n in resize):
            raise ValueError("Invalid metadata resize.")
        if self.metadata.get("resize_method") != "bilinear":
            raise ValueError("Only bilinear preprocessing is supported.")
        self.mean = np.asarray(self.metadata["mean"], dtype=np.float32)
        self.std = np.asarray(self.metadata["std"], dtype=np.float32)
        if (
            self.mean.shape != (3,)
            or self.std.shape != (3,)
            or not np.isfinite(self.mean).all()
            or not np.isfinite(self.std).all()
            or (self.std <= 0).any()
        ):
            raise ValueError("Invalid metadata normalisation.")
        self.session = ort.InferenceSession(str(model), providers=["CPUExecutionProvider"])
        inputs = self.session.get_inputs()
        if len(inputs) != 1 or inputs[0].type != "tensor(float)":
            raise ValueError("Expected one float image input.")
        shape = inputs[0].shape
        height, width = self.metadata["resize"]
        if len(shape) != 4 or any(
            isinstance(actual, int) and actual != expected
            for actual, expected in zip(shape[1:], (3, height, width))
        ):
            raise ValueError("Model input dimensions do not match metadata.")
        if self.metadata.get("channels") != "RGB (grayscale replicated to 3)":
            raise ValueError("Unsupported metadata channel preprocessing.")
        self.input_name = inputs[0].name
        self.model = model.name
        # Fail at startup, not during a lesson, if output dimensions or inference are broken.
        self.probabilities(Image.new("RGB", (width, height)))

    def probabilities(self, image: Image.Image, label: str | None = None) -> np.ndarray:
        height, width = self.metadata["resize"]
        rgb = image.convert("RGB").resize((width, height), Image.Resampling.BILINEAR)
        pixels = np.asarray(rgb, dtype=np.float32) / 255.0
        tensor = ((pixels - self.mean) / self.std).transpose(2, 0, 1)[None]
        logits = self.session.run(None, {self.input_name: tensor})[0]
        if logits.shape != (1, len(self.classes)) or not np.isfinite(logits).all():
            raise ValueError("Model output does not match metadata classes.")
        shifted = logits[0].astype(np.float64) - float(logits.max())
        probabilities = np.exp(shifted)
        return probabilities / probabilities.sum()
