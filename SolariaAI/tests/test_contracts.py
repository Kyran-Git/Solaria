import json
from pathlib import Path

import pytest
from jsonschema import Draft202012Validator

SCHEMA_DIR = Path(__file__).parents[1] / "docs" / "schemas"


def _validator(name: str) -> Draft202012Validator:
    schema = json.loads((SCHEMA_DIR / name).read_text(encoding="utf-8"))
    return Draft202012Validator(schema)


def test_valid_prediction_review_and_api_examples() -> None:
    prediction = {
        "version": "prediction_v1",
        "label": "earth",
        "confidence": 0.91,
        "accepted": True,
        "reason": None,
        "threshold": 0.6,
        "model_artifact": "runs/demo/model.onnx",
        "metadata_artifact": "runs/demo/model.onnx.metadata.json",
    }
    review = {
        "version": "review_v1",
        "predicted_label": "earth",
        "final_label": "earth",
        "action": "confirm",
        "override": False,
        "reviewer": "mock_service",
        "notes": None,
    }
    build = {
        "version": "build_result_v1",
        "status": "completed",
        "message": "Build completed successfully.",
        "artifact": "runs/demo/build-report.json",
    }
    payload = {
        "version": "api_v1",
        "request_id": "req-001",
        "status": "ok",
        "prediction": prediction,
        "review": review,
        "build_result": build,
    }
    _validator("prediction_v1.schema.json").validate(prediction)
    _validator("review_v1.schema.json").validate(review)
    _validator("build_result_v1.schema.json").validate(build)
    _validator("api_v1.schema.json").validate(payload)


@pytest.mark.parametrize(
    ("schema", "invalid"),
    [
        (
            "prediction_v1.schema.json",
            {"version": "prediction_v1", "label": "earth", "confidence": 1.2},
        ),
        (
            "review_v1.schema.json",
            {
                "version": "review_v1",
                "predicted_label": "earth",
                "final_label": "earth",
                "action": "confirm",
                "override": "no",
                "reviewer": "mock_service",
                "notes": None,
            },
        ),
        (
            "build_result_v1.schema.json",
            {
                "version": "build_result_v1",
                "status": "queued",
                "message": "Build completed successfully.",
            },
        ),
    ],
)
def test_invalid_examples_are_rejected(schema: str, invalid: dict) -> None:
    errors = list(_validator(schema).iter_errors(invalid))
    assert errors
