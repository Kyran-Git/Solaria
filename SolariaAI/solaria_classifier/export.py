from __future__ import annotations

import json
from pathlib import Path

import torch

from .data import IMAGENET_MEAN, IMAGENET_STD
from .model import create_backbone


def export_onnx(checkpoint: str | Path, output: str | Path, opset: int = 17) -> None:
    state = torch.load(checkpoint, map_location="cpu", weights_only=False)
    config = state["config"]
    model = create_backbone(config.backbone, len(state["classes"]), pretrained=False)
    model.load_state_dict(state["model"])
    model.eval()
    size = config.augmentation.image_size
    torch.onnx.export(model, torch.randn(1, 3, size, size), output, opset_version=opset,
                      input_names=["images"], output_names=["logits"],
                      dynamic_axes={"images": {0: "batch"}, "logits": {0: "batch"}})
    metadata = {"resize": [size, size], "resize_method": "bilinear", "channels": "RGB (grayscale replicated to 3)",
                "mean": IMAGENET_MEAN, "std": IMAGENET_STD, "labels": state["classes"]}
    Path(str(output) + ".metadata.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")


def check_onnx_parity(checkpoint: str | Path, onnx_path: str | Path, tolerance: float = 1e-4) -> float:
    import onnxruntime as ort
    state = torch.load(checkpoint, map_location="cpu", weights_only=False)
    config = state["config"]
    model = create_backbone(config.backbone, len(state["classes"]), pretrained=False)
    model.load_state_dict(state["model"]); model.eval()
    x = torch.randn(2, 3, config.augmentation.image_size, config.augmentation.image_size)
    with torch.no_grad(): expected = model(x).numpy()
    actual = ort.InferenceSession(str(onnx_path)).run(None, {"images": x.numpy()})[0]
    error = float(abs(expected - actual).max())
    if error > tolerance: raise AssertionError(f"ONNX parity error {error} > {tolerance}")
    return error
