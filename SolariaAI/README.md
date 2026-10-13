# Solaria CNN image classifier

Standalone PyTorch/torchvision training for folder-organized sketch or image datasets.
Minecraft/Java integration is intentionally out of scope.

See [PROJECT_SPECIFICATION.md](PROJECT_SPECIFICATION.md) for the complete project
scope, architecture, data contract, training workflow, export contract, and
completion criteria.
See [PROJECT_STATUS.md](PROJECT_STATUS.md) for the current implementation and
validation status.

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

## Proposed real-data classes

The downstream pipeline proposes these six labels:
`spiral_galaxy`, `solar_system`, `black_hole`, `ringed_planet`, `comet`, and
`supernova`. Real training requires at least 100 clean labeled drawings and
20 varied camera-captured labeled drawings per class, split by drawing rather
than near-identical capture. See `PROJECT_SPECIFICATION.md` for the complete
capture and layout requirements. Real-data training is awaiting human-supplied
drawings.

Downstream consumers can use `solaria_classifier.confidence.classify_logits`
with a configurable threshold (the proposed default is `0.60`) to reject
ambiguous predictions as `unknown`. This does not add an `unknown` model class;
an explicit `unknown` folder is an optional future dataset choice.

## Tests

```powershell
pytest -q
ruff check .
```
