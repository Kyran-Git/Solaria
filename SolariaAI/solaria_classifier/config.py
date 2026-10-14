from __future__ import annotations

import json
from dataclasses import asdict, dataclass, field
from pathlib import Path


@dataclass
class AugmentationConfig:
    image_size: int = 224
    rotation: float = 12.0
    scale_jitter: tuple[float, float] = (0.85, 1.15)
    brightness_jitter: float = 0.0
    horizontal_flip: bool = False
    line_thickness: float = 0.0
    noise_std: float = 0.02


@dataclass
class TrainConfig:
    data_dir: str
    output_dir: str = "runs"
    backbone: str = "resnet18"
    epochs: int = 10
    batch_size: int = 32
    learning_rate: float = 3e-4
    fine_tune_learning_rate: float = 1e-5
    warmup_epochs: int = 2
    num_workers: int = 0
    seed: int = 42
    grayscale: bool = False
    augmentation: AugmentationConfig = field(default_factory=AugmentationConfig)

    def save_resolved(self, path: Path, classes: list[str]) -> None:
        value = asdict(self)
        value["classes"] = classes
        path.write_text(json.dumps(value, indent=2), encoding="utf-8")
