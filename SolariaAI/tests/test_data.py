from __future__ import annotations

import csv
import json
import random
import shutil
import warnings
from pathlib import Path

import numpy as np
import pytest
from PIL import Image

torch = pytest.importorskip("torch")

from solaria_classifier import train as train_module
from solaria_classifier.config import SPLIT_RATIOS, AugmentationConfig, TrainConfig
from solaria_classifier.data import (
    DatasetBundle,
    _prepare_split_assignment,
    build_datasets,
    prepare_dataset_from_manifest,
    test_loader,
)
from solaria_classifier.evaluate import evaluate_test
from solaria_classifier.model import create_backbone


def _patterned_image(seed: int, size: int = 32) -> Image.Image:
    """Create a deterministic high-entropy 8x8-block pattern with a distinct perceptual hash."""
    rng = random.Random(seed)
    grid = np.zeros((size, size, 3), dtype=np.uint8)
    step = size // 8
    for gy in range(8):
        for gx in range(8):
            val = 240 if rng.random() > 0.5 else 15
            grid[gy * step : (gy + 1) * step, gx * step : (gx + 1) * step] = (
                val,
                (val + (seed * 17)) % 256,
                (val + (seed * 31)) % 256,
            )
    return Image.fromarray(grid, mode="RGB")


def _write_clean_three_split_dataset(
    root: Path,
    *,
    splits: tuple[str, ...] = ("train", "val", "test"),
    classes: tuple[str, ...] = ("earth", "mars"),
    per_class: int = 1,
) -> dict[str, list[Path]]:
    created: dict[str, list[Path]] = {split: [] for split in splits}
    seed = 100
    for split in splits:
        for label in classes:
            directory = root / split / label
            directory.mkdir(parents=True, exist_ok=True)
            for idx in range(per_class):
                seed += 1
                path = directory / f"{split}_{label}_{idx}.png"
                _patterned_image(seed).save(path)
                created[split].append(path)
    return created


def _write_manifest(
    raw_dir: Path,
    rows: list[tuple[str, str, str]],
) -> Path:
    manifest = raw_dir / "manifest.csv"
    with manifest.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(["file", "class", "participant_code"])
        writer.writerows(rows)
    return manifest


def test_split_ratios_constant() -> None:
    assert SPLIT_RATIOS == {"train": 0.7, "val": 0.2, "test": 0.1}


def test_prepare_dataset_70_20_10_participant_disjoint_and_manifest(tmp_path: Path) -> None:
    raw_dir = tmp_path / "raw"
    raw_dir.mkdir()
    classes = ("earth", "mars", "moon", "sun")
    rows: list[tuple[str, str, str]] = []
    img_seed = 1000
    expected_by_participant: dict[str, list[str]] = {}
    for p_idx in range(20):
        participant = f"P{p_idx:02d}"
        drawing_count = 2 + (p_idx % 4)
        expected_by_participant[participant] = []
        for d_idx in range(drawing_count):
            img_seed += 1
            cls = classes[(p_idx + d_idx) % len(classes)]
            filename = f"{participant}_{d_idx}_{cls}.png"
            _patterned_image(img_seed).save(raw_dir / filename)
            rows.append((filename, cls, participant))
            expected_by_participant[participant].append(filename)

    manifest = _write_manifest(raw_dir, rows)
    out_dir = tmp_path / "prepared"
    with warnings.catch_warnings(record=True) as caught:
        warnings.simplefilter("always")
        payload = prepare_dataset_from_manifest(manifest, out_dir, seed=42)

    leakage_warnings = [str(w.message) for w in caught if "leakage" in str(w.message).lower()]
    assert leakage_warnings == []

    assert payload["split_ratios"] == {"train": 0.7, "val": 0.2, "test": 0.1}
    manifest_json = json.loads((out_dir / "dataset_manifest.json").read_text(encoding="utf-8"))
    assert manifest_json["split_ratios"] == {"train": 0.7, "val": 0.2, "test": 0.1}

    split_by_participant: dict[str, str] = payload["split_by_participant"]  # type: ignore[assignment]
    train_p = {p for p, s in split_by_participant.items() if s == "train"}
    val_p = {p for p, s in split_by_participant.items() if s == "val"}
    test_p = {p for p, s in split_by_participant.items() if s == "test"}

    assert (len(train_p), len(val_p), len(test_p)) == (14, 4, 2)
    assert train_p.isdisjoint(val_p)
    assert train_p.isdisjoint(test_p)
    assert val_p.isdisjoint(test_p)

    # Every file for a participant must land strictly in that participant's split.
    for participant, filenames in expected_by_participant.items():
        assigned = split_by_participant[participant]
        for filename in filenames:
            matches = list((out_dir / assigned).rglob(filename))
            assert len(matches) == 1
            for other in ("train", "val", "test"):
                if other != assigned:
                    assert list((out_dir / other).rglob(filename)) == []


@pytest.mark.parametrize("participant_count", [1, 2])
def test_prepare_dataset_rejects_fewer_than_three_participants(
    tmp_path: Path, participant_count: int
) -> None:
    raw_dir = tmp_path / "raw"
    raw_dir.mkdir()
    rows: list[tuple[str, str, str]] = []
    for idx in range(participant_count):
        filename = f"p{idx}.png"
        _patterned_image(500 + idx).save(raw_dir / filename)
        rows.append((filename, "earth", f"P{idx}"))
    manifest = _write_manifest(raw_dir, rows)

    with pytest.raises(ValueError, match="at least 3 distinct participants"):
        prepare_dataset_from_manifest(manifest, tmp_path / "out", seed=42)


@pytest.mark.parametrize("total", [3, 4, 5, 6, 10, 20])
def test_split_assignment_guarantees_all_three_splits_non_empty(total: int) -> None:
    participants = [f"P{i:02d}" for i in range(total)]
    assignment = _prepare_split_assignment(participants, seed=7)
    assert set(assignment.keys()) == set(participants)
    split_counts = {split: sum(v == split for v in assignment.values()) for split in ("train", "val", "test")}
    assert all(count >= 1 for count in split_counts.values())
    assert sum(split_counts.values()) == total


def test_missing_test_split_fails_require_test_and_final_evaluation(tmp_path: Path) -> None:
    _write_clean_three_split_dataset(tmp_path, splits=("train", "val"))
    aug = AugmentationConfig(image_size=16)

    with pytest.raises(FileNotFoundError, match="Required test split directory not found"):
        build_datasets(tmp_path, aug)

    bundle = build_datasets(tmp_path, aug, require_test=False)
    assert bundle.test is None

    with pytest.raises(ValueError, match="Test split is required"):
        test_loader(bundle, batch_size=2, workers=0)

    model = create_backbone("mobilenet_v3_small", len(bundle.classes), pretrained=False)
    with pytest.raises(ValueError, match="Test split is required"):
        evaluate_test(model, bundle, batch_size=2, workers=0, device=torch.device("cpu"))


def test_evaluate_test_runs_on_held_out_test_split(tmp_path: Path) -> None:
    _write_clean_three_split_dataset(tmp_path, splits=("train", "val", "test"), per_class=2)
    bundle = build_datasets(tmp_path, AugmentationConfig(image_size=32))
    assert bundle.test is not None
    loader = test_loader(bundle, batch_size=2, workers=0)
    assert len(loader.dataset) == 4

    model = create_backbone("mobilenet_v3_small", len(bundle.classes), pretrained=False)
    metrics = evaluate_test(model, bundle, batch_size=2, workers=0, device=torch.device("cpu"))
    assert 0.0 <= float(metrics["accuracy"]) <= 1.0
    assert sum(sum(row) for row in metrics["confusion_matrix"]) == 4


def test_train_never_accesses_test_split(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    data_dir = tmp_path / "data"
    out_dir = tmp_path / "runs"
    _write_clean_three_split_dataset(data_dir, splits=("train", "val", "test"), per_class=2)

    class _ForbiddenTestSplit:
        def __len__(self) -> int:
            raise AssertionError("train() must not inspect bundle.test length")

        def __getitem__(self, index: int):
            raise AssertionError("train() must not read samples from bundle.test")

    real_build = train_module.build_datasets

    def _guarded_build(*args, **kwargs) -> DatasetBundle:
        bundle = real_build(*args, **kwargs)
        assert bundle.test is not None
        bundle.test = _ForbiddenTestSplit()  # type: ignore[assignment]
        return bundle

    monkeypatch.setattr(train_module, "build_datasets", _guarded_build)
    monkeypatch.setattr(
        train_module,
        "create_backbone",
        lambda name, num_classes: create_backbone(name, num_classes, pretrained=False),
    )

    config = TrainConfig(
        data_dir=str(data_dir),
        output_dir=str(out_dir),
        backbone="mobilenet_v3_small",
        epochs=1,
        warmup_epochs=0,
        batch_size=2,
        augmentation=AugmentationConfig(image_size=32),
    )
    checkpoint = train_module.train(config)
    assert checkpoint.is_file()


@pytest.mark.parametrize(("left", "right"), [("train", "val"), ("train", "test"), ("val", "test")])
def test_exact_duplicate_leakage_warns_for_each_split_pair(
    tmp_path: Path, left: str, right: str
) -> None:
    created = _write_clean_three_split_dataset(tmp_path, splits=("train", "val", "test"), per_class=2)
    src = created[left][0]
    dst = tmp_path / right / src.parent.name / "leaked_exact.png"
    shutil.copyfile(src, dst)

    with warnings.catch_warnings(record=True) as caught:
        warnings.simplefilter("always")
        build_datasets(tmp_path, AugmentationConfig(image_size=16))

    messages = [str(w.message) for w in caught]
    assert any(
        "exact duplicate leakage" in msg and f"between {left} and {right} splits" in msg
        for msg in messages
    ), messages
    for other_left, other_right in (("train", "val"), ("train", "test"), ("val", "test")):
        if (other_left, other_right) != (left, right):
            assert not any(
                f"between {other_left} and {other_right} splits" in msg for msg in messages
            ), messages


@pytest.mark.parametrize(("left", "right"), [("train", "val"), ("train", "test"), ("val", "test")])
def test_near_duplicate_leakage_warns_for_each_split_pair(
    tmp_path: Path, left: str, right: str
) -> None:
    created = _write_clean_three_split_dataset(tmp_path, splits=("train", "val", "test"), per_class=2)
    src = created[left][0]
    # Re-save with a tiny single-pixel tweak so SHA-256 differs while the 8x8 perceptual hash matches.
    with Image.open(src) as image:
        arr = np.array(image)
    arr[0, 0, 0] = (int(arr[0, 0, 0]) + 1) % 256
    dst = tmp_path / right / src.parent.name / "leaked_near.png"
    Image.fromarray(arr, mode="RGB").save(dst)

    with warnings.catch_warnings(record=True) as caught:
        warnings.simplefilter("always")
        build_datasets(tmp_path, AugmentationConfig(image_size=16))

    messages = [str(w.message) for w in caught]
    assert any(
        "near-duplicate leakage" in msg and f"between {left} and {right} splits" in msg
        for msg in messages
    ), messages
    assert not any(
        "exact duplicate leakage" in msg and f"between {left} and {right} splits" in msg
        for msg in messages
    ), messages
    for other_left, other_right in (("train", "val"), ("train", "test"), ("val", "test")):
        if (other_left, other_right) != (left, right):
            assert not any(
                f"between {other_left} and {other_right} splits" in msg for msg in messages
            ), messages


def test_within_split_duplicate_and_class_imbalance_warnings_preserved(tmp_path: Path) -> None:
    created = _write_clean_three_split_dataset(tmp_path, splits=("train", "val", "test"), per_class=1)
    # Create an exact duplicate and severe class imbalance (5 vs 1) strictly inside train.
    train_earth = created["train"][0]
    shutil.copyfile(train_earth, tmp_path / "train" / "earth" / "dup_exact.png")
    for idx in range(3):
        _patterned_image(900 + idx).save(tmp_path / "train" / "earth" / f"extra_{idx}.png")

    with warnings.catch_warnings(record=True) as caught:
        warnings.simplefilter("always")
        build_datasets(tmp_path, AugmentationConfig(image_size=16))

    messages = [str(w.message) for w in caught]
    assert any("Training split contains exact duplicate images." in msg for msg in messages), messages
    assert any("Training split contains near-duplicate images." in msg for msg in messages), messages
    assert any("Severe class imbalance detected in Training split" in msg for msg in messages), messages
    assert not any("Cross-split" in msg for msg in messages), messages


def test_prepare_dataset_warns_on_cross_split_duplicate_images(tmp_path: Path) -> None:
    raw_dir = tmp_path / "raw"
    raw_dir.mkdir()
    participants = ["P00", "P01", "P02"]
    assignment = _prepare_split_assignment(participants, seed=42)
    by_split = {split: participant for participant, split in assignment.items()}

    shared = _patterned_image(777)
    shared.save(raw_dir / "p_train.png")
    shared.save(raw_dir / "p_test.png")
    _patterned_image(888).save(raw_dir / "p_val.png")
    manifest = _write_manifest(
        raw_dir,
        [
            ("p_train.png", "earth", by_split["train"]),
            ("p_val.png", "earth", by_split["val"]),
            ("p_test.png", "earth", by_split["test"]),
        ],
    )

    with warnings.catch_warnings(record=True) as caught:
        warnings.simplefilter("always")
        prepare_dataset_from_manifest(manifest, tmp_path / "out", seed=42)

    messages = [str(w.message) for w in caught]
    assert any(
        "exact duplicate leakage" in msg and "between train and test splits" in msg
        for msg in messages
    ), messages
