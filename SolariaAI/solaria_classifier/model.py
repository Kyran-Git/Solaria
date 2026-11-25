from __future__ import annotations

from torch import nn
from torchvision import models


def create_backbone(name: str, num_classes: int, pretrained: bool = True) -> nn.Module:
    """Factory keeps backbone replacement (for example mobilenet_v3_small) localized."""
    name = name.lower()
    if name in {"mobilenet_v3_small", "mobilenet-v3-small"}:
        weights = models.MobileNet_V3_Small_Weights.DEFAULT if pretrained else None
        model = models.mobilenet_v3_small(weights=weights)
        model.classifier[-1] = nn.Linear(model.classifier[-1].in_features, num_classes)
        return model
    if name in {"mobilenet_v3_large", "mobilenet-v3-large"}:
        weights = models.MobileNet_V3_Large_Weights.DEFAULT if pretrained else None
        model = models.mobilenet_v3_large(weights=weights)
        model.classifier[-1] = nn.Linear(model.classifier[-1].in_features, num_classes)
        return model
    if name == "resnet18":
        weights = models.ResNet18_Weights.DEFAULT if pretrained else None
        model = models.resnet18(weights=weights)
        model.fc = nn.Linear(model.fc.in_features, num_classes)
        return model
    if name in {"efficientnet_b0", "efficientnet-b0"}:
        weights = models.EfficientNet_B0_Weights.DEFAULT if pretrained else None
        model = models.efficientnet_b0(weights=weights)
        model.classifier[1] = nn.Linear(model.classifier[1].in_features, num_classes)
        return model
    raise ValueError(
        f"Unsupported backbone {name!r}; expected mobilenet_v3_small, "
        "mobilenet_v3_large, resnet18, or efficientnet_b0"
    )


def set_backbone_frozen(model: nn.Module, frozen: bool) -> None:
    for parameter in model.parameters():
        parameter.requires_grad = not frozen
    if frozen:
        head = getattr(model, "fc", None)
        if head is None:
            head = getattr(model, "classifier", None)
        if isinstance(head, nn.Module):
            for parameter in head.parameters():
                parameter.requires_grad = True
