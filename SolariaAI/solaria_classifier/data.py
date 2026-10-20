from __future__ import annotations

import csv
import hashlib
import random
import warnings
from collections import Counter
from dataclasses import dataclass
from pathlib import Path

import torch
from PIL import Image, ImageOps
from torch.utils.data import DataLoader
from torchvision import datasets, transforms

from .classes import CLASS_IDS, is_valid_class

IMAGENET_MEAN = (0.485, 0.456, 0.406)
IMAGENET_STD = (0.229, 0.224, 0.225)


@dataclass
class DatasetBundle:
    train: datasets.ImageFolder
    val: datasets.ImageFolder
    classes: list[str]
    class_to_idx: dict[str, int]
    test: datasets.ImageFolder | None = None


def check_class_set(classes: list[str], scope: str) -> None:
    """Fail when a split holds labels outside the required ten-class set.

    A stray folder such as ``Mercury`` or a leftover ``rocket`` directory would
    otherwise be discovered silently by ``ImageFolder`` and would change the
    model's output width and label order. Rejecting it here keeps the dataset,
    the checkpoint, and the mod's blueprint list in step.
    """
    unknown = sorted(label for label in classes if not is_valid_class(label))
    if unknown:
        expected = ", ".join(CLASS_IDS)
        raise ValueError(
            f"{scope} contains unsupported class folders {unknown}. "
            f"The required ten-class set is: {expected}."
        )


def _transform(train: bool, size: int, grayscale: bool, rotation: float,
               scale_jitter: tuple[float, float], noise_std: float,
               line_thickness: float, brightness_jitter: float = 0.0,
               horizontal_flip: bool = False) -> transforms.Compose:
    ops: list[object] = [transforms.Resize((size, size))]
    if train:
        ops += [transforms.RandomAffine(degrees=rotation, scale=scale_jitter)]
        if horizontal_flip:
            ops += [transforms.RandomHorizontalFlip(p=0.5)]
        if brightness_jitter:
            ops += [transforms.ColorJitter(brightness=brightness_jitter)]
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


def _prepare_split_assignment(participants: list[str], seed: int) -> dict[str, str]:
    rng = random.Random(seed)
    ordered = participants[:]
    rng.shuffle(ordered)
    total = len(ordered)
    if total == 0:
        return {}
    train_end = max(1, round(total * 0.70))
    val_end = train_end + max(1, round(total * 0.15))
    split_by_participant: dict[str, str] = {}
    for idx, participant in enumerate(ordered):
        if idx < train_end:
            split_by_participant[participant] = "train"
        elif idx < val_end:
            split_by_participant[participant] = "val"
        else:
            split_by_participant[participant] = "test"
    return split_by_participant


def prepare_dataset_from_manifest(
    manifest_path: str | Path,
    output_dir: str | Path,
    *,
    data_origin: str = "synthetic",
    seed: int = 42,
) -> dict[str, object]:
    manifest = Path(manifest_path)
    rows = list(csv.DictReader(manifest.read_text(encoding="utf-8").splitlines()))
    required = {"file", "class", "participant_code"}
    if not required.issubset(rows[0].keys()) if rows else False:
        raise ValueError("Manifest CSV must include file,class,participant_code columns.")
    if not rows:
        raise ValueError("Manifest CSV is empty.")

    root = manifest.parent
    participants = sorted({row["participant_code"] for row in rows})
    split_by_participant = _prepare_split_assignment(participants, seed)
    class_names = sorted({row["class"] for row in rows})
    check_class_set(class_names, "Manifest")
    counts = {split: 0 for split in ("train", "val", "test")}
    split_files: dict[str, list[str]] = {split: [] for split in counts}
    for row in rows:
        target_split = split_by_participant[row["participant_code"]]
        src = (root / row["file"]).resolve()
        if not src.exists():
            raise FileNotFoundError(f"Manifest file not found: {src}")
        dest_dir = Path(output_dir) / target_split / row["class"]
        dest_dir.mkdir(parents=True, exist_ok=True)
        dest = dest_dir / Path(row["file"]).name
        with Image.open(src) as image:
            image = ImageOps.exif_transpose(image)
            if image.mode != "RGB":
                image = image.convert("RGB")
            image.save(dest, format="PNG")
        split_files[target_split].append(str(dest))
        counts[target_split] += 1
    manifest_path_out = Path(output_dir) / "dataset_manifest.json"
    manifest_payload = {
        "data_origin": data_origin,
        "seed": seed,
        "split_ratios": {"train": 0.7, "val": 0.15, "test": 0.15},
        "classes": class_names,
        "participants": participants,
        "split_by_participant": split_by_participant,
        "counts": counts,
    }
    manifest_path_out.write_text(__import__("json").dumps(manifest_payload, indent=2), encoding="utf-8")
    return manifest_payload


def build_datasets(data_dir: str | Path, augmentation: object, grayscale: bool = False) -> DatasetBundle:
    root = Path(data_dir)
    kwargs = {
        "size": augmentation.image_size,
        "grayscale": grayscale,
        "rotation": augmentation.rotation,
        "scale_jitter": augmentation.scale_jitter,
        "noise_std": augmentation.noise_std,
        "line_thickness": augmentation.line_thickness,
        "brightness_jitter": getattr(augmentation, "brightness_jitter", 0.0),
        "horizontal_flip": getattr(augmentation, "horizontal_flip", False),
    }
    train = datasets.ImageFolder(root / "train", transform=_transform(True, **kwargs))
    val = datasets.ImageFolder(root / "val", transform=_transform(False, **kwargs))
    check_class_set(train.classes, "Training split")
    check_class_set(val.classes, "Validation split")
    if train.class_to_idx != val.class_to_idx:
        raise ValueError("Train and validation class mappings differ.")
    test_bundle = None
    test_path = root / "test"
    if test_path.exists():
        test = datasets.ImageFolder(test_path, transform=_transform(False, **kwargs))
        check_class_set(test.classes, "Test split")
        if test.class_to_idx != train.class_to_idx:
            raise ValueError("Test split class mapping differs from train and validation.")
        test_bundle = test
    _duplicate_warnings(train.samples, "Training split")
    _duplicate_warnings(val.samples, "Validation split")
    if test_bundle is not None:
        _duplicate_warnings(test_bundle.samples, "Test split")
    combined = train.samples + val.samples + (test_bundle.samples if test_bundle else [])
    _duplicate_warnings(combined, "Combined dataset")
    return DatasetBundle(train, val, train.classes, train.class_to_idx, test_bundle)


def loaders(bundle: DatasetBundle, batch_size: int, workers: int) -> tuple[DataLoader, DataLoader]:
    val_loader = DataLoader(bundle.val, batch_size=batch_size, shuffle=False, num_workers=workers)
    train_loader = DataLoader(bundle.train, batch_size=batch_size, shuffle=True, num_workers=workers)
    return train_loader, val_loader
