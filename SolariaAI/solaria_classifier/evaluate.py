from __future__ import annotations

import numpy as np
import torch
from torch.utils.data import DataLoader

from .utils import confusion_matrix


@torch.no_grad()
def evaluate(model: torch.nn.Module, loader: DataLoader, classes: list[str],
             device: torch.device) -> dict[str, object]:
    model.eval()
    targets: list[int] = []
    predictions: list[int] = []
    for images, labels in loader:
        output = model(images.to(device))
        predictions.extend(output.argmax(1).cpu().tolist())
        targets.extend(labels.tolist())
    matrix = confusion_matrix(targets, predictions, len(classes))
    per_class = {name: float(matrix[i, i] / matrix[i].sum()) if matrix[i].sum() else 0.0
                 for i, name in enumerate(classes)}
    return {"accuracy": float(np.trace(matrix) / max(matrix.sum(), 1)),
            "per_class_accuracy": per_class, "confusion_matrix": matrix.tolist()}
