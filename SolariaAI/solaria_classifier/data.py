from __future__ import annotations

import hashlib
import warnings
from collections import Counter
from dataclasses import dataclass
from pathlib import Path

import torch
from torch.utils.data import DataLoader
from torchvision import datasets, transforms

IMAGENET_MEAN = (0.485, 0.456, 0.406)
IMAGENET_STD = (0.229, 0.224, 0.225)


@dataclass
class DatasetBundle:
    train: datasets.ImageFolder
    val: datasets.ImageFolder
    classes: list[str]
    class_to_idx: dict[str, int]


def _transform(train: bool, size: int, grayscale: bool, rotation: float,
               scale_jitter: tuple[float, float], noise_std: float,
               line_thickness: float) -> transforms.Compose:
    ops: list[object] = [transforms.Resize((size, size))]
    if train:
        ops += [transforms.RandomAffine(degrees=rotation, scale=scale_jitter)]
        if line_thickness:
            # Sharpness variation approximates scanned/sketched line-weight changes.
            ops += [transforms.RandomAdjustSharpness(sharpness_factor=max(0.1, 1.0 - line_thickness),
                                                      p=min(1.0, abs(line_thickness)))]
    ops += [transforms.Grayscale(num_output_channels=3)] if grayscale else [transforms.Lambda(lambda x: x.convert("RGB"))]
    ops += [transforms.ToTensor()]
    if train and noise_std:
        ops += [transforms.Lambda(lambda x: (x + torch.randn_like(x) * noise_std).clamp(0, 1))]
    ops += [transforms.Normalize(IMAGENET_MEAN, IMAGENET_STD)]
    return transforms.Compose(ops)


def _duplicate_warnings(samples: list[tuple[str, int]], scope: str) -> None:
    hashes = Counter(hashlib.sha256(Path(p).read_bytes()).digest() for p, _ in samples)
    if any(n > 1 for n in hashes.values()):
        warnings.warn(f"{scope} contains exact duplicate images; inspect train/validation leakage.",
                      UserWarning)
    # A small perceptual signature catches copies saved with different compression.
    signatures: list[int] = []
    for path, _ in samples:
        try:
            from PIL import Image
            pixels = Image.open(path).convert("L").resize((8, 8))
            mean = sum(pixels.getdata()) / 64
            signatures.append(sum((value >= mean) << i for i, value in enumerate(pixels.getdata())))
        except (OSError, ValueError):
            continue
    if any((a ^ b).bit_count() <= 2 for i, a in enumerate(signatures) for b in signatures[i + 1:]):
        warnings.warn(f"{scope} contains near-duplicate images; inspect split leakage.", UserWarning)
    counts = Counter(label for _, label in samples)
    if counts and min(counts.values()) * 3 < max(counts.values()):
        warnings.warn(f"Severe class imbalance detected in {scope}: {dict(counts)}", UserWarning)


def build_datasets(data_dir: str | Path, augmentation: object, grayscale: bool = False) -> DatasetBundle:
    root = Path(data_dir)
    kwargs = {
        "size": augmentation.image_size,
        "grayscale": grayscale,
        "rotation": augmentation.rotation,
        "scale_jitter": augmentation.scale_jitter,
        "noise_std": augmentation.noise_std,
        "line_thickness": augmentation.line_thickness,
    }
    train = datasets.ImageFolder(root / "train", transform=_transform(True, **kwargs))
    val = datasets.ImageFolder(root / "val", transform=_transform(False, **kwargs))
    if train.class_to_idx != val.class_to_idx:
        raise ValueError("Train and validation class mappings differ.")
    _duplicate_warnings(train.samples, "Training split")
    _duplicate_warnings(val.samples, "Validation split")
    _duplicate_warnings(train.samples + val.samples, "Combined dataset")
    return DatasetBundle(train, val, train.classes, train.class_to_idx)


def loaders(bundle: DatasetBundle, batch_size: int, workers: int) -> tuple[DataLoader, DataLoader]:
    return (DataLoader(bundle.train, batch_size=batch_size, shuffle=True, num_workers=workers),
            DataLoader(bundle.val, batch_size=batch_size, shuffle=False, num_workers=workers))
