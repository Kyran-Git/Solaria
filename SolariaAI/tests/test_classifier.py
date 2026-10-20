from pathlib import Path

import pytest
from PIL import Image

torch = pytest.importorskip("torch")

from solaria_classifier.classes import CLASS_IDS
from solaria_classifier.config import AugmentationConfig
from solaria_classifier.data import build_datasets, check_class_set
from solaria_classifier.export import check_onnx_parity, export_onnx
from solaria_classifier.model import create_backbone
from solaria_classifier.utils import confusion_matrix, seed_everything


def _write_dataset(root: Path) -> None:
    for split in ("train", "val"):
        for label, color in (("earth", (255, 0, 0)), ("mars", (0, 255, 0))):
            directory = root / split / label
            directory.mkdir(parents=True)
            Image.new("RGB", (24, 24), color).save(directory / "one.png")


def test_stable_mapping_and_tensor_shape(tmp_path: Path) -> None:
    _write_dataset(tmp_path)
    bundle = build_datasets(tmp_path, AugmentationConfig(image_size=16))
    assert bundle.class_to_idx == {"earth": 0, "mars": 1}
    image, label = bundle.train[0]
    assert image.shape == (3, 16, 16)
    assert label == 0


def test_required_class_set_has_ten_unique_ids() -> None:
    assert len(CLASS_IDS) == 10
    assert len(set(CLASS_IDS)) == 10
    assert CLASS_IDS == tuple(label.lower() for label in CLASS_IDS)


def test_retired_classes_are_rejected() -> None:
    for retired in ("rocket", "satellite", "solar_system", "lunar_eclipse", "solar_eclipse"):
        with pytest.raises(ValueError, match="unsupported class folders"):
            check_class_set([retired], "Test split")


def test_mercury_is_not_a_class() -> None:
    with pytest.raises(ValueError, match="unsupported class folders"):
        check_class_set(["mercury"], "Test split")


def test_dataset_with_retired_class_folder_fails(tmp_path: Path) -> None:
    _write_dataset(tmp_path)
    retired = tmp_path / "train" / "rocket"
    retired.mkdir()
    Image.new("RGB", (24, 24), (0, 0, 255)).save(retired / "one.png")

    with pytest.raises(ValueError, match="unsupported class folders"):
        build_datasets(tmp_path, AugmentationConfig(image_size=16))


def test_confusion_matrix_and_seed() -> None:
    assert confusion_matrix([0, 1, 1], [0, 0, 1], 2).tolist() == [[1, 0], [1, 1]]
    seed_everything(7)
    first = torch.rand(2)
    seed_everything(7)
    assert torch.equal(first, torch.rand(2))


def test_backbone_head() -> None:
    model = create_backbone("resnet18", 3, pretrained=False)
    assert model.fc.out_features == 3


def test_onnx_export_matches_pytorch(tmp_path: Path) -> None:
    pytest.importorskip("onnx")
    pytest.importorskip("onnxruntime")
    import torch.optim

    from solaria_classifier.config import TrainConfig
    from solaria_classifier.utils import save_checkpoint

    config = TrainConfig(data_dir=str(tmp_path), output_dir=str(tmp_path), backbone="resnet18")
    model = create_backbone("resnet18", 2, pretrained=False)
    optimizer = torch.optim.AdamW(model.parameters(), lr=1e-3)
    checkpoint = tmp_path / "model.pt"
    save_checkpoint(checkpoint, model, optimizer, 0, ["earth", "mars"], config)
    exported = tmp_path / "model.onnx"
    export_onnx(checkpoint, exported)
    assert check_onnx_parity(checkpoint, exported) <= 1e-4
