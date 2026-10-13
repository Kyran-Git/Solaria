from __future__ import annotations

from pathlib import Path

import torch
from torch import nn

from .config import TrainConfig
from .data import build_datasets, loaders
from .evaluate import evaluate
from .model import create_backbone, set_backbone_frozen
from .utils import save_checkpoint, seed_everything, write_json


def train(config: TrainConfig) -> Path:
    seed_everything(config.seed)
    output = Path(config.output_dir)
    output.mkdir(parents=True, exist_ok=True)
    bundle = build_datasets(config.data_dir, config.augmentation, config.grayscale)
    train_loader, val_loader = loaders(bundle, config.batch_size, config.num_workers)
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model = create_backbone(config.backbone, len(bundle.classes)).to(device)
    set_backbone_frozen(model, True)
    optimizer = torch.optim.AdamW(filter(lambda p: p.requires_grad, model.parameters()), lr=config.learning_rate)
    criterion = nn.CrossEntropyLoss()
    best = 0.0
    config.save_resolved(output / "resolved_config.json", bundle.classes)
    for epoch in range(config.epochs):
        if epoch == config.warmup_epochs:
            set_backbone_frozen(model, False)
            optimizer = torch.optim.AdamW(model.parameters(), lr=config.fine_tune_learning_rate)
        model.train()
        for images, labels in train_loader:
            optimizer.zero_grad(set_to_none=True)
            criterion(model(images.to(device)), labels.to(device)).backward()
            optimizer.step()
        metrics = evaluate(model, val_loader, bundle.classes, device)
        write_json(output / "metrics.json", metrics)
        if float(metrics["accuracy"]) >= best:
            best = float(metrics["accuracy"])
            save_checkpoint(output / "best.pt", model, optimizer, epoch, bundle.classes, config)
    return output / "best.pt"
