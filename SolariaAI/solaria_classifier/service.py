from __future__ import annotations

from dataclasses import asdict, dataclass
from typing import Any

from .classes import CLASS_IDS, is_valid_class

#: The ten supported class ids, re-exported here for contract consumers.
VALID_CLASSES: list[str] = list(CLASS_IDS)


@dataclass(frozen=True)
class PredictionRecord:
    version: str = "prediction_v1"
    label: str = "sun"
    confidence: float = 0.92
    accepted: bool = True
    reason: str | None = None
    threshold: float = 0.6
    model_artifact: str = "runs/demo/model.onnx"
    metadata_artifact: str = "runs/demo/model.onnx.metadata.json"

    def __post_init__(self) -> None:
        if not is_valid_class(self.label):
            raise ValueError(f"Unsupported class label: {self.label}")
        if not 0.0 <= self.confidence <= 1.0:
            raise ValueError("Confidence must be in the range [0, 1].")
        if not 0.0 <= self.threshold <= 1.0:
            raise ValueError("Threshold must be in the range [0, 1].")


@dataclass(frozen=True)
class ReviewRecord:
    version: str = "review_v1"
    predicted_label: str = "sun"
    final_label: str | None = "sun"
    action: str = "confirm"
    override: bool = False
    reviewer: str = "mock_service"
    notes: str | None = None

    def __post_init__(self) -> None:
        if not is_valid_class(self.predicted_label):
            raise ValueError(f"Unsupported predicted label: {self.predicted_label}")
        if self.final_label is not None and not is_valid_class(self.final_label):
            raise ValueError(f"Unsupported final label: {self.final_label}")
        if self.action not in {"confirm", "override", "reject"}:
            raise ValueError("Review action must be confirm, override, or reject.")


@dataclass(frozen=True)
class BuildResultRecord:
    version: str = "build_result_v1"
    status: str = "completed"
    message: str | None = "Build completed successfully."
    artifact: str | None = None

    def __post_init__(self) -> None:
        if self.status not in {"completed", "failed", "not_attempted"}:
            raise ValueError("Build status must be completed, failed, or not_attempted.")


@dataclass(frozen=True)
class ApiV1Envelope:
    version: str = "api_v1"
    request_id: str = "mock-request"
    status: str = "ok"
    prediction: PredictionRecord = PredictionRecord()
    review: ReviewRecord | None = None
    build_result: BuildResultRecord | None = None

    def __post_init__(self) -> None:
        if self.status not in {"ok", "rejected", "failed"}:
            raise ValueError("Status must be ok, rejected, or failed.")

    def to_dict(self) -> dict[str, Any]:
        payload = asdict(self)
        if self.review is not None:
            payload["review"] = asdict(self.review)
        if self.build_result is not None:
            payload["build_result"] = asdict(self.build_result)
        return payload


def mock_service_response(
    *,
    request_id: str = "mock-request",
    label: str = "sun",
    confidence: float = 0.92,
    accepted: bool = True,
    threshold: float = 0.6,
    status: str = "ok",
    review_action: str = "confirm",
    build_status: str = "completed",
) -> dict[str, Any]:
    prediction = PredictionRecord(
        label=label,
        confidence=confidence,
        accepted=accepted,
        threshold=threshold,
    )
    review = ReviewRecord(
        predicted_label=label,
        final_label=label if accepted else None,
        action=review_action,
        override=review_action == "override",
    )
    build_result = BuildResultRecord(status=build_status)
    return ApiV1Envelope(
        request_id=request_id,
        status=status,
        prediction=prediction,
        review=review,
        build_result=build_result,
    ).to_dict()
