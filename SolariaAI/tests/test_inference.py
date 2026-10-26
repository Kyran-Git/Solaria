import json

import numpy as np
import pytest
from PIL import Image

from solaria_classifier.classes import CLASS_IDS
from solaria_classifier.inference import OnnxClassifier


def write_model(tmp_path, labels=None):
    onnx = pytest.importorskip("onnx")
    from onnx import TensorProto, helper, numpy_helper

    model_path = tmp_path / "synthetic.onnx"
    # Mean RGB values become three of the ten logits. Test-only, not a trained CNN.
    weights = np.zeros((3, 10), dtype=np.float32)
    weights[:, :3] = np.eye(3)
    graph = helper.make_graph(
        [
            helper.make_node("ReduceMean", ["images"], ["means"], axes=[2, 3], keepdims=0),
            helper.make_node("MatMul", ["means", "weights"], ["logits"]),
        ],
        "synthetic-test",
        [helper.make_tensor_value_info("images", TensorProto.FLOAT, [1, 3, 8, 8])],
        [helper.make_tensor_value_info("logits", TensorProto.FLOAT, [1, 10])],
        [numpy_helper.from_array(weights, "weights")],
    )
    model = helper.make_model(graph, opset_imports=[helper.make_opsetid("", 17)], ir_version=9)
    onnx.save(model, model_path)
    metadata = {
        "labels": labels or list(reversed(CLASS_IDS)),
        "resize": [8, 8],
        "resize_method": "bilinear",
        "channels": "RGB (grayscale replicated to 3)",
        "mean": [0.1, 0.2, 0.3],
        "std": [0.5, 0.5, 0.5],
    }
    sidecar = tmp_path / "synthetic.onnx.metadata.json"
    sidecar.write_text(json.dumps(metadata), encoding="utf-8")
    return model_path, sidecar


def test_onnx_uses_metadata_order_and_normalisation(tmp_path):
    model, _ = write_model(tmp_path)
    classifier = OnnxClassifier(model)
    assert classifier.classes == list(reversed(CLASS_IDS))
    image = Image.new("RGB", (16, 12), (255, 0, 0))
    logits = np.array([1.8, -0.4, -0.6] + [0.0] * 7)
    expected = np.exp(logits - logits.max())
    expected /= expected.sum()
    np.testing.assert_allclose(classifier.probabilities(image), expected, atol=1e-6)
    assert classifier.mock is False
    assert classifier.probabilities(image.convert("L")).shape == (10,)


@pytest.mark.parametrize(
    "labels", [list(CLASS_IDS[:-1]), ["earth"] * 10, list(CLASS_IDS[:-1]) + ["asteroid"]]
)
def test_metadata_class_mismatch_fails_at_startup(tmp_path, labels):
    model, _ = write_model(tmp_path, labels)
    with pytest.raises(ValueError, match="ten Solaria"):
        OnnxClassifier(model)


@pytest.mark.parametrize(
    ("key", "value"),
    [("std", [0, 1, 1]), ("resize", [16, 16]), ("resize_method", "nearest"), ("channels", "BGR")],
)
def test_invalid_preprocessing_metadata_fails_at_startup(tmp_path, key, value):
    model, sidecar = write_model(tmp_path)
    metadata = json.loads(sidecar.read_text(encoding="utf-8"))
    metadata[key] = value
    sidecar.write_text(json.dumps(metadata), encoding="utf-8")
    with pytest.raises(ValueError):
        OnnxClassifier(model)
