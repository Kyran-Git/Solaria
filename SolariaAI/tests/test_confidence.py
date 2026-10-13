import pytest
import torch

from solaria_classifier.confidence import classify_logits


def test_confident_prediction_is_accepted() -> None:
    result = classify_logits(torch.tensor([4.0, 0.0]), ["alpha", "beta"], threshold=0.8)
    assert result.label == "alpha"
    assert result.accepted is True
    assert result.confidence > 0.8
    assert result.reason is None


def test_ambiguous_prediction_is_unknown() -> None:
    result = classify_logits(torch.tensor([0.1, 0.0]), ["alpha", "beta"], threshold=0.6)
    assert result.label == "unknown"
    assert result.accepted is False
    assert result.reason is not None


@pytest.mark.parametrize(
    ("logits", "classes", "threshold"),
    [
        (torch.tensor([[1.0, 0.0]]), ["alpha", "beta"], 0.5),
        (torch.tensor([1.0]), ["alpha", "beta"], 0.5),
        (torch.tensor([1.0, 0.0]), ["alpha", "beta"], -0.1),
    ],
)
def test_confidence_input_validation(
    logits: torch.Tensor, classes: list[str], threshold: float
) -> None:
    with pytest.raises(ValueError):
        classify_logits(logits, classes, threshold)
