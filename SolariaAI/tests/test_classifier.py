import json
from pathlib import Path

import pytest
from PIL import Image

torch = pytest.importorskip("torch")

from solaria_classifier.classes import CLASS_IDS
from solaria_classifier.config import AugmentationConfig, TrainConfig
from solaria_classifier.data import IMAGENET_MEAN, IMAGENET_STD, build_datasets, check_class_set
from solaria_classifier.export import check_onnx_parity, export_onnx
from solaria_classifier.inference import OnnxClassifier
from solaria_classifier.model import create_backbone, set_backbone_frozen
from solaria_classifier.utils import confusion_matrix, save_checkpoint, seed_everything


def _write_dataset(root: Path) -> None:
    for split in ("train", "val", "test"):
        for label, color in (("earth", (255, 0, 0)), ("mars", (0, 255, 0))):
            directory = root / split / label
            directory.mkdir(parents=True)
            Image.new("RGB", (24, 24), color).save(directory / "one.png")


def test_stable_mapping_and_tensor_shape(tmp_path: Path) -> None:
    _write_dataset(tmp_path)
    bundle = build_datasets(tmp_path, AugmentationConfig(image_size=16))
    assert bundle.class_to_idx == {"earth": 0, "mars": 1}
    assert bundle.test is not None
    image, label = bundle.train[0]
    assert image.shape == (3, 16, 16)
    assert label == 0


def test_required_class_set_has_ten_unique_ids() -> None:
    assert len(CLASS_IDS) == 10
    assert len(set(CLASS_IDS)) == 10
    assert CLASS_IDS == tuple(label.lower() for label in CLASS_IDS)


def test_retired_classes_are_rejected() -> None:
    for retired in ("satellite", "solar_system", "lunar_eclipse", "solar_eclipse", "asteroid"):
        with pytest.raises(ValueError, match="unsupported class folders"):
            check_class_set([retired], "Test split")


def test_mercury_is_a_valid_class() -> None:
    check_class_set(["mercury"], "Test split")


def test_dataset_with_retired_class_folder_fails(tmp_path: Path) -> None:
    _write_dataset(tmp_path)
    retired = tmp_path / "train" / "asteroid"
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


def test_default_backbone_is_mobilenet_v3_small(monkeypatch: pytest.MonkeyPatch) -> None:
    from solaria_classifier import cli, train

    assert TrainConfig(data_dir=".").backbone == "mobilenet_v3_small"

    captured: list[TrainConfig] = []
    monkeypatch.setattr(train, "train", captured.append)
    monkeypatch.setattr("sys.argv", ["solaria-classifier", "train", "some-dir"])
    cli.main()
    assert len(captured) == 1
    assert captured[0].backbone == "mobilenet_v3_small"


@pytest.mark.parametrize(
    ("name", "attr"),
    [
        ("mobilenet_v3_small", "classifier"),
        ("mobilenet-v3-small", "classifier"),
        ("mobilenet_v3_large", "classifier"),
        ("mobilenet-v3-large", "classifier"),
        ("resnet18", "fc"),
        ("efficientnet_b0", "classifier"),
        ("efficientnet-b0", "classifier"),
    ],
)
def test_backbone_head(name: str, attr: str) -> None:
    model = create_backbone(name, 10, pretrained=False)
    head = getattr(model, attr)
    final_layer = head[-1] if isinstance(head, torch.nn.Sequential) else head
    assert isinstance(final_layer, torch.nn.Linear)
    assert final_layer.out_features == 10


def test_unknown_backbone_lists_supported_options() -> None:
    with pytest.raises(ValueError, match="mobilenet_v3_small.*mobilenet_v3_large.*resnet18"):
        create_backbone("vgg16", 10, pretrained=False)


@pytest.mark.parametrize(
    "name",
    ["mobilenet_v3_small", "mobilenet_v3_large", "resnet18", "efficientnet_b0"],
)
def test_set_backbone_frozen_and_unfrozen(name: str) -> None:
    model = create_backbone(name, 10, pretrained=False)
    set_backbone_frozen(model, True)
    if hasattr(model, "features"):
        assert all(not p.requires_grad for p in model.features.parameters())
        assert all(p.requires_grad for p in model.classifier.parameters())
    else:
        assert all(p.requires_grad for p in model.fc.parameters())
        assert all(
            not p.requires_grad
            for param_name, p in model.named_parameters()
            if not param_name.startswith("fc.")
        )

    set_backbone_frozen(model, False)
    assert all(p.requires_grad for p in model.parameters())


@pytest.mark.parametrize(
    "backbone",
    ["mobilenet_v3_small", "mobilenet_v3_large", "resnet18", "efficientnet_b0"],
)
def test_onnx_export_matches_pytorch(tmp_path: Path, backbone: str) -> None:
    pytest.importorskip("onnx")
    ort = pytest.importorskip("onnxruntime")
    import torch.optim

    classes = list(CLASS_IDS)
    config = TrainConfig(data_dir=str(tmp_path), output_dir=str(tmp_path), backbone=backbone)
    model = create_backbone(backbone, len(classes), pretrained=False)
    optimizer = torch.optim.AdamW(model.parameters(), lr=1e-3)
    checkpoint = tmp_path / f"{backbone}.pt"
    save_checkpoint(checkpoint, model, optimizer, 0, classes, config)
    exported = tmp_path / f"{backbone}.onnx"
    export_onnx(checkpoint, exported)

    assert exported.is_file()
    assert not Path(f"{exported}.data").exists()

    metadata = json.loads(Path(f"{exported}.metadata.json").read_text(encoding="utf-8"))
    assert metadata == {
        "backbone": backbone,
        "resize": [224, 224],
        "resize_method": "bilinear",
        "channels": "RGB (grayscale replicated to 3)",
        "mean": list(IMAGENET_MEAN),
        "std": list(IMAGENET_STD),
        "labels": classes,
    }

    session = ort.InferenceSession(str(exported), providers=["CPUExecutionProvider"])
    inp, out = session.get_inputs()[0], session.get_outputs()[0]
    assert inp.name == "images"
    assert out.name == "logits"
    assert not isinstance(inp.shape[0], int)
    assert not isinstance(out.shape[0], int)
    for batch_size in (1, 2, 4):
        sample = torch.randn(batch_size, 3, 224, 224).numpy()
        logits = session.run(None, {"images": sample})[0]
        assert logits.shape == (batch_size, len(classes))

    assert check_onnx_parity(checkpoint, exported) <= 1e-4

    if backbone.startswith("mobilenet"):
        classifier = OnnxClassifier(exported)
        assert classifier.metadata["backbone"] == backbone
        assert classifier.classes == classes
        probs = classifier.probabilities(Image.new("RGB", (224, 224), (128, 64, 32)))
        assert probs.shape == (len(classes),)
        assert abs(float(probs.sum()) - 1.0) < 1e-6


def test_mobilenet_onnx_classifier_preserves_permuted_class_order(tmp_path: Path) -> None:
    pytest.importorskip("onnx")
    pytest.importorskip("onnxruntime")
    import torch.optim

    permuted = list(reversed(CLASS_IDS))
    config = TrainConfig(
        data_dir=str(tmp_path),
        output_dir=str(tmp_path),
        backbone="mobilenet_v3_small",
    )
    model = create_backbone("mobilenet_v3_small", len(permuted), pretrained=False)
    optimizer = torch.optim.AdamW(model.parameters(), lr=1e-3)
    checkpoint = tmp_path / "permuted.pt"
    save_checkpoint(checkpoint, model, optimizer, 0, permuted, config)
    exported = tmp_path / "permuted.onnx"
    export_onnx(checkpoint, exported)

    assert exported.is_file()
    assert not Path(f"{exported}.data").exists()

    classifier = OnnxClassifier(exported)
    assert classifier.metadata["backbone"] == "mobilenet_v3_small"
    assert classifier.classes == permuted
    probs = classifier.probabilities(Image.new("RGB", (224, 224), (20, 40, 60)))
    assert probs.shape == (len(permuted),)
    assert abs(float(probs.sum()) - 1.0) < 1e-6
