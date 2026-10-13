from __future__ import annotations

from dataclasses import dataclass

import torch


@dataclass(frozen=True)
class ConfidenceDecision:
    label: str
    confidence: float
    accepted: bool
    reason: str | None = None


def classify_logits(
    logits: torch.Tensor,
    classes: list[str],
    threshold: float = 0.6,
    unknown_label: str = "unknown",
) -> ConfidenceDecision:
    """Choose a saved class only when its softmax confidence clears a threshold."""
    if logits.ndim != 1:
        raise ValueError("Expected one-dimensional logits for a single image.")
    if len(logits) != len(classes):
        raise ValueError("The number of logits must match the saved class list.")
    if not 0.0 <= threshold <= 1.0:
        raise ValueError("Confidence threshold must be between 0 and 1.")
    if not classes:
        raise ValueError("At least one class is required.")

    probabilities = torch.softmax(logits.detach(), dim=0)
    index = int(torch.argmax(probabilities).item())
    confidence = float(probabilities[index].item())
    if confidence < threshold:
        return ConfidenceDecision(
            label=unknown_label,
            confidence=confidence,
            accepted=False,
            reason=f"Top class confidence {confidence:.3f} is below threshold {threshold:.3f}.",
        )
    return ConfidenceDecision(label=classes[index], confidence=confidence, accepted=True)
