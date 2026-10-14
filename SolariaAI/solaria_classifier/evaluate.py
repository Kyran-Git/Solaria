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
    confidences: list[float] = []
    for images, labels in loader:
        output = model(images.to(device))
        probabilities = torch.softmax(output, dim=1)
        pred = probabilities.argmax(1)
        predictions.extend(pred.cpu().tolist())
        targets.extend(labels.tolist())
        confidences.extend(probabilities.max(dim=1).values.detach().cpu().tolist())
    matrix = confusion_matrix(targets, predictions, len(classes))
    per_class_accuracy = {name: float(matrix[i, i] / matrix[i].sum()) if matrix[i].sum() else 0.0
                          for i, name in enumerate(classes)}
    per_class_precision: dict[str, float] = {}
    per_class_recall: dict[str, float] = {}
    per_class_f1: dict[str, float] = {}
    for i, name in enumerate(classes):
        tp = int(matrix[i, i])
        fp = int(matrix[:, i].sum() - tp)
        fn = int(matrix[i, :].sum() - tp)
        precision = tp / (tp + fp) if (tp + fp) else 0.0
        recall = tp / (tp + fn) if (tp + fn) else 0.0
        f1 = (2.0 * precision * recall / (precision + recall)) if (precision + recall) else 0.0
        per_class_precision[name] = precision
        per_class_recall[name] = recall
        per_class_f1[name] = f1
    macro_f1 = float(sum(per_class_f1.values()) / max(len(per_class_f1), 1))
    coverage_vs_accuracy: list[dict[str, float | int]] = []
    for threshold in (0.5, 0.6, 0.7, 0.8, 0.9):
        accepted = [float(conf) >= threshold for conf in confidences]
        if not any(accepted):
            coverage_vs_accuracy.append({
                "threshold": threshold,
                "accepted_fraction": 0.0,
                "accepted_accuracy": 0.0,
            })
            continue
        accepted_idx = [idx for idx, ok in enumerate(accepted) if ok]
        accepted_correct = sum(1 for idx in accepted_idx if predictions[idx] == targets[idx])
        coverage_vs_accuracy.append({
            "threshold": threshold,
            "accepted_fraction": float(len(accepted_idx) / len(confidences)),
            "accepted_accuracy": float(accepted_correct / len(accepted_idx)),
        })
    return {
        "accuracy": float(np.trace(matrix) / max(matrix.sum(), 1)),
        "per_class_accuracy": per_class_accuracy,
        "per_class_precision": per_class_precision,
        "per_class_recall": per_class_recall,
        "per_class_f1": per_class_f1,
        "macro_f1": macro_f1,
        "coverage_vs_accuracy": coverage_vs_accuracy,
        "confusion_matrix": matrix.tolist(),
    }
