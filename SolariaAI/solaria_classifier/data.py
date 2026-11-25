from __future__ import annotations

import csv
import hashlib
import json
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
from .config import SPLIT_RATIOS

IMAGENET_MEAN = (0.485, 0.456, 0.406)
IMAGENET_STD = (0.229, 0.224, 0.225)
_SPLIT_PAIRS: tuple[tuple[str, str], ...] = (("train", "val"), ("train", "test"), ("val", "test"))


@dataclass
class DatasetBundle:
    train: datasets.ImageFolder
    val: datasets.ImageFolder
    classes: list[str]
    class_to_idx: dict[str, int]
    test: datasets.ImageFolder | None = None


def check_class_set(classes: list[str], scope: str) -> None:
    """Fail when a split holds labels outside the required ten-class set.

    A stray folder such as ``asteroid`` would otherwise be discovered silently by
    ``ImageFolder`` and would change the model's output width and label order.
    Rejecting it here keeps the dataset, the checkpoint, and the mod's
    blueprint list in step.
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


def _perceptual_signature(path: str | Path) -> int | None:
    try:
        with Image.open(path) as image:
            pixels = image.convert("L").resize((8, 8)).tobytes()
        mean = sum(pixels) / 64
        return sum((value >= mean) << i for i, value in enumerate(pixels))
    except (OSError, ValueError):
        return None


def _split_fingerprints(paths: list[str]) -> tuple[list[bytes], list[int]]:
    hashes = [hashlib.sha256(Path(path).read_bytes()).digest() for path in paths]
    signatures = [sig for path in paths if (sig := _perceptual_signature(path)) is not None]
    return hashes, signatures


def _within_split_warnings(
    samples: list[tuple[str, int]],
    scope: str,
    hashes: list[bytes],
    signatures: list[int],
) -> None:
    if any(n > 1 for n in Counter(hashes).values()):
        warnings.warn(f"{scope} contains exact duplicate images.", UserWarning)
    if any(
        (a ^ b).bit_count() <= 2
        for i, a in enumerate(signatures)
        for b in signatures[i + 1 :]
    ):
        warnings.warn(f"{scope} contains near-duplicate images.", UserWarning)
    counts = Counter(label for _, label in samples)
    if counts and min(counts.values()) * 3 < max(counts.values()):
        warnings.warn(f"Severe class imbalance detected in {scope}: {dict(counts)}", UserWarning)


def _cross_split_leakage_warnings(
    split_paths: dict[str, list[str]],
    fingerprints: dict[str, tuple[list[bytes], list[int]]] | None = None,
) -> None:
    computed = (
        fingerprints
        if fingerprints is not None
        else {split: _split_fingerprints(paths) for split, paths in split_paths.items() if paths}
    )
    for left, right in _SPLIT_PAIRS:
        if left not in computed or right not in computed:
            continue
        left_hashes, left_sigs = computed[left]
        right_hashes, right_sigs = computed[right]
        if set(left_hashes) & set(right_hashes):
            warnings.warn(
                f"Cross-split exact duplicate leakage detected between {left} and {right} splits.",
                UserWarning,
            )
        if any((a ^ b).bit_count() <= 2 for a in left_sigs for b in right_sigs):
            warnings.warn(
                f"Cross-split near-duplicate leakage detected between {left} and {right} splits.",
                UserWarning,
            )


def _prepare_split_assignment(participants: list[str], seed: int) -> dict[str, str]:
    rng = random.Random(seed)
    ordered = participants[:]
    rng.shuffle(ordered)
    total = len(ordered)
    if total == 0:
        return {}
    if total < 3:
        raise ValueError(
            "Need at least 3 distinct participants for a non-empty 70/20/10 "
            f"train/val/test split; got {total}."
        )
    test_count = max(1, round(total * SPLIT_RATIOS["test"]))
    val_count = max(1, round(total * SPLIT_RATIOS["val"]))
    if val_count + test_count >= total:
        val_count = max(1, total - 2)
        test_count = max(1, total - 1 - val_count)
    train_end = total - val_count - test_count
    val_end = train_end + val_count
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
    if not rows:
        raise ValueError("Manifest CSV is empty.")
    required = {"file", "class", "participant_code"}
    if not required.issubset(rows[0].keys()):
        raise ValueError("Manifest CSV must include file,class,participant_code columns.")

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
    _cross_split_leakage_warnings(split_files)
    manifest_path_out = Path(output_dir) / "dataset_manifest.json"
    manifest_payload = {
        "data_origin": data_origin,
        "seed": seed,
        "split_ratios": dict(SPLIT_RATIOS),
        "classes": class_names,
        "participants": participants,
        "split_by_participant": split_by_participant,
        "counts": counts,
    }
    manifest_path_out.write_text(json.dumps(manifest_payload, indent=2), encoding="utf-8")
    return manifest_payload


def build_datasets(
    data_dir: str | Path,
    augmentation: object,
    grayscale: bool = False,
    *,
    require_test: bool = True,
) -> DatasetBundle:
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
    if test_path.is_dir():
        test = datasets.ImageFolder(test_path, transform=_transform(False, **kwargs))
        check_class_set(test.classes, "Test split")
        if test.class_to_idx != train.class_to_idx:
            raise ValueError("Test split class mapping differs from train and validation.")
        test_bundle = test
    elif require_test:
        raise FileNotFoundError(f"Required test split directory not found: {test_path}")

    split_samples: dict[str, tuple[str, list[tuple[str, int]]]] = {
        "train": ("Training split", train.samples),
        "val": ("Validation split", val.samples),
    }
    if test_bundle is not None:
        split_samples["test"] = ("Test split", test_bundle.samples)

    split_paths = {
        split: [path for path, _ in samples]
        for split, (_, samples) in split_samples.items()
    }
    fingerprints = {
        split: _split_fingerprints(paths)
        for split, paths in split_paths.items()
    }
    for split, (scope, samples) in split_samples.items():
        hashes, signatures = fingerprints[split]
        _within_split_warnings(samples, scope, hashes, signatures)
    _cross_split_leakage_warnings(split_paths, fingerprints)
    return DatasetBundle(train, val, train.classes, train.class_to_idx, test_bundle)


def loaders(bundle: DatasetBundle, batch_size: int, workers: int) -> tuple[DataLoader, DataLoader]:
    val_loader = DataLoader(bundle.val, batch_size=batch_size, shuffle=False, num_workers=workers)
    train_loader = DataLoader(bundle.train, batch_size=batch_size, shuffle=True, num_workers=workers)
    return train_loader, val_loader


def test_loader(bundle: DatasetBundle, batch_size: int, workers: int) -> DataLoader:
    if bundle.test is None:
        raise ValueError("Test split is required for final evaluation, but bundle.test is None.")
    return DataLoader(bundle.test, batch_size=batch_size, shuffle=False, num_workers=workers)


test_loader.__test__ = False
