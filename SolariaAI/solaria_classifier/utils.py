from __future__ import annotations

import json
import random
from pathlib import Path

import numpy as np
import torch


def seed_everything(seed: int) -> None:
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    torch.cuda.manual_seed_all(seed)
    torch.use_deterministic_algorithms(True, warn_only=True)
    if torch.backends.cudnn.is_available():
        torch.backends.cudnn.deterministic = True
        torch.backends.cudnn.benchmark = False


def save_checkpoint(path: Path, model: torch.nn.Module, optimizer: torch.optim.Optimizer,
                    epoch: int, classes: list[str], config: object) -> None:
    torch.save({"model": model.state_dict(), "optimizer": optimizer.state_dict(),
                "epoch": epoch, "classes": classes, "config": config}, path)


def confusion_matrix(targets: list[int], predictions: list[int], n: int) -> np.ndarray:
    result = np.zeros((n, n), dtype=np.int64)
    for target, prediction in zip(targets, predictions):
        result[target, prediction] += 1
    return result


def write_json(path: Path, value: object) -> None:
    path.write_text(json.dumps(value, indent=2, default=str), encoding="utf-8")
