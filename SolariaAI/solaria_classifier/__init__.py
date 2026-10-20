"""Standalone, reproducible image classification toolkit."""

from .service import (
    ApiV1Envelope,
    BuildResultRecord,
    PredictionRecord,
    ReviewRecord,
    mock_service_response,
)

__version__ = "0.1.0"

__all__ = [
    "ApiV1Envelope",
    "BuildResultRecord",
    "PredictionRecord",
    "ReviewRecord",
    "mock_service_response",
]
