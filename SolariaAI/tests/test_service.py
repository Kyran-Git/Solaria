import pytest

from solaria_classifier.service import (
    ApiV1Envelope,
    BuildResultRecord,
    PredictionRecord,
    ReviewRecord,
    mock_service_response,
)


def test_prediction_record_matches_api_contract() -> None:
    prediction = PredictionRecord(label="sun", confidence=0.91, accepted=True, threshold=0.6)
    assert prediction.version == "prediction_v1"
    assert prediction.label == "sun"
    assert 0.0 <= prediction.confidence <= 1.0


def test_review_and_build_records_are_valid() -> None:
    review = ReviewRecord(predicted_label="moon", final_label="moon", action="confirm")
    build = BuildResultRecord(status="completed")

    assert review.version == "review_v1"
    assert build.version == "build_result_v1"
    assert build.status == "completed"


def test_mock_service_returns_api_v1_payload() -> None:
    payload = mock_service_response(label="jupiter", confidence=0.83, review_action="override")

    assert payload["version"] == "api_v1"
    assert payload["prediction"]["label"] == "jupiter"
    assert payload["review"]["action"] == "override"
    assert payload["build_result"]["status"] == "completed"


@pytest.mark.parametrize(
    ("factory", "kwargs"),
    [
        (PredictionRecord, {"label": "unknown", "confidence": 0.4}),
        (ReviewRecord, {"predicted_label": "unknown", "action": "confirm"}),
        (BuildResultRecord, {"status": "queued"}),
    ],
)
def test_invalid_service_values_rejected(factory, kwargs) -> None:
    with pytest.raises(ValueError):
        factory(**kwargs)


def test_api_envelope_to_dict_includes_optional_sections() -> None:
    envelope = ApiV1Envelope(
        request_id="abc-123",
        prediction=PredictionRecord(label="saturn", confidence=0.88),
        review=ReviewRecord(predicted_label="saturn", final_label="saturn", action="confirm"),
        build_result=BuildResultRecord(status="completed"),
    )

    payload = envelope.to_dict()
    assert payload["version"] == "api_v1"
    assert payload["request_id"] == "abc-123"
    assert payload["review"]["version"] == "review_v1"
    assert payload["build_result"]["version"] == "build_result_v1"
