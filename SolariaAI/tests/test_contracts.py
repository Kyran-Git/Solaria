import json
from pathlib import Path

import pytest
from jsonschema import Draft202012Validator

SCHEMA_DIR = Path(__file__).parents[1] / "docs" / "schemas"


def _validator(name: str) -> Draft202012Validator:
    schema = json.loads((SCHEMA_DIR / name).read_text(encoding="utf-8"))
    return Draft202012Validator(schema)


def test_valid_prediction_and_blueprint_examples() -> None:
    prediction = {
        "version": "prediction_v1",
        "label": "comet",
        "confidence": 0.91,
        "accepted": True,
        "reason": None,
        "threshold": 0.6,
        "model_artifact": "runs/demo/model.onnx",
        "metadata_artifact": "runs/demo/model.onnx.metadata.json",
    }
    blueprint = {
        "version": "blueprint_v1",
        "class_label": "comet",
        "parameters": {"tail_length": 0.8, "core_radius": 0.2},
        "fallbacks": {"used": False, "reason": None},
        "scale": 2.0,
        "max_size_blocks": 128,
    }
    _validator("prediction_v1.schema.json").validate(prediction)
    _validator("blueprint_v1.schema.json").validate(blueprint)


@pytest.mark.parametrize(
    ("schema", "invalid"),
    [
        (
            "prediction_v1.schema.json",
            {"version": "prediction_v1", "label": "comet", "confidence": 1.2},
        ),
        (
            "blueprint_v1.schema.json",
            {
                "version": "blueprint_v1",
                "class_label": "comet",
                "parameters": {"tail_length": 2.0},
                "fallbacks": {"used": False, "reason": None},
                "scale": 2.0,
                "max_size_blocks": 128,
            },
        ),
    ],
)
def test_invalid_examples_are_rejected(schema: str, invalid: dict) -> None:
    errors = list(_validator(schema).iter_errors(invalid))
    assert errors
