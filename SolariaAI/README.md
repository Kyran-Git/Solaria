# Solaria CNN image classifier

Standalone PyTorch/torchvision training for folder-organized sketch or image datasets.
Minecraft/Java integration is intentionally out of scope.

## Dataset

Put images in `data/train/<class-name>/` and `data/val/<class-name>/`. Classes are
sorted by `ImageFolder` and the mapping is checked between splits and saved in the
resolved configuration and checkpoint. Exact duplicates and severe class imbalance
produce warnings.

## Install and run

```powershell
python -m pip install -e ".[dev,export]"
solaria-classifier train data --output-dir runs/demo --epochs 10
solaria-classifier export runs/demo/best.pt runs/demo/model.onnx
solaria-classifier parity runs/demo/best.pt runs/demo/model.onnx
```

The default ResNet18 uses ImageNet pretrained weights, a frozen-backbone warmup,
then full fine-tuning. `--backbone efficientnet_b0` is already supported by the
backbone factory. Training is deterministic for the configured seed. Checkpoints,
`resolved_config.json`, and per-class accuracy/confusion matrix metrics are saved.

Augmentations include rotation, scale jitter, optional line-weight/sharpness
variation, and pixel noise. ONNX export has a dynamic batch dimension and writes
`model.onnx.metadata.json` documenting resize, RGB/grayscale handling, and
ImageNet normalization.

## Tests

```powershell
pytest -q
ruff check .
```
