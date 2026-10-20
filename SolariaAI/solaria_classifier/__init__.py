"""Standalone, reproducible image classification toolkit."""

from .classes import CLASS_IDS, DISPLAY_NAMES, display_name, is_valid_class
from .service import (
    VALID_CLASSES,
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
    "CLASS_IDS",
    "CLASS_LABELS",
    "DISPLAY_NAMES",
    "PredictionRecord",
    "ReviewRecord",
    "VALID_CLASSES",
    "display_name",
    "is_valid_class",
    "mock_service_response",
]
