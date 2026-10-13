# Solaria CNN Image Classifier Project Specification

## 1. Project definition

Solaria's first machine-learning milestone is a standalone image-classification
framework. It takes labeled images, trains a convolutional neural network (CNN)
to map each image to one known class, evaluates the model, saves a reproducible
checkpoint, and exports the trained model to ONNX.

The framework is being built as the machine-learning component that will
eventually support a Minecraft mod. The current milestone deliberately stops at
the Python training and model-export boundary. It does not contain Minecraft
mod code, Java code, networking, game-state logic, camera capture, or in-game
prediction display.

The future Minecraft integration will consume the ONNX model and its metadata
sidecar. Keeping that boundary explicit makes the classifier independently
testable and prevents the initial project from being coupled to the mod.

## 2. Scope

### Included in this milestone

- A Python package named `solaria_classifier`.
- PyTorch and torchvision model training.
- ImageFolder-style train/validation datasets.
- Stable class-label discovery and train/validation mapping checks.
- RGB and grayscale image handling.
- Deterministic validation preprocessing.
- Configurable drawing/sketch-oriented training augmentation.
- Transfer learning from pretrained ResNet18 weights.
- A replaceable backbone factory with EfficientNet-B0 support.
- Frozen-backbone warmup followed by full fine-tuning.
- Fixed-seed, repeatable training configuration.
- Checkpoint and resolved-configuration persistence.
- Aggregate accuracy, per-class accuracy, and confusion-matrix metrics.
- Data-quality warnings for class imbalance, exact duplicates, and near-duplicates.
- ONNX export with dynamic batch support.
- An ONNX metadata sidecar that defines the inference preprocessing contract.
- PyTorch-versus-ONNX Runtime parity validation.
- A command-line interface and focused automated tests.

### Explicitly excluded for now

- Minecraft or Minecraft mod source code.
- Java inference or ONNX Runtime Java setup.
- Communication between Python and Minecraft.
- Image acquisition from the game, screen capture, or camera integration.
- Real-time inference services.
- Dataset labeling tools or a web interface.
- Automatic class discovery during inference.
- Object detection, segmentation, or multi-label classification.
- Production model hosting and deployment infrastructure.

Those features may use this project later, but they must not change the
classifier's core dataset, checkpoint, and ONNX contracts without a deliberate
versioned design change.

## 3. Repository layout

```text
SolariaAI/
  solaria_classifier/
    config.py       Configuration dataclasses and resolved-config serialization
    data.py         Dataset loading, transforms, and data-quality checks
    model.py        Backbone factory and freeze/unfreeze operations
    train.py        Training orchestration and checkpoint creation
    evaluate.py     Accuracy and confusion-matrix calculations
    export.py       ONNX export and parity checking
    utils.py        Seeding, checkpoint, JSON, and metric helpers
    cli.py          `solaria-classifier` command-line entry point
  tests/
    test_classifier.py
  pyproject.toml
  requirements-base.txt
  requirements.txt
  README.md
  PROJECT_SPECIFICATION.md
  PROJECT_STATUS.md
```

Generated datasets, checkpoints, ONNX files, and run directories are local
artifacts. They must not be committed to the repository.

## 4. Technology and dependency contract

- Python 3.10 or newer.
- PyTorch for tensor operations, optimization, checkpointing, and inference.
- torchvision for pretrained CNN backbones and ImageFolder loading.
- Pillow for image decoding and conversion.
- NumPy for array and numerical operations.
- PyYAML for future configuration-file support.
- ONNX, ONNX Runtime, and `onnxscript` for model export and verification.
- pytest for automated tests.
- Ruff for linting.

Install the project in editable mode with the development and export
dependencies:

```powershell
python -m pip install -e ".[dev,export]"
```

The package exposes this command:

```powershell
solaria-classifier <command> ...
```

## 5. Dataset contract

The required dataset layout is:

```text
data/
  train/
    class_a/
      image-001.png
      image-002.png
    class_b/
      image-003.png
  val/
    class_a/
      image-101.png
    class_b/
      image-102.png
```

Each immediate directory below `train` and `val` is one class. The class names
are the labels returned by the model. The implementation uses torchvision's
`ImageFolder` behavior, which sorts class names and assigns stable integer
indices.

The following conditions are required:

1. Both `train` and `val` must exist.
2. Each split must contain the same class names.
3. The `class_to_idx` mapping must be identical in both splits.
4. Every file must be a readable supported image.
5. Validation images must not be copies of training images.

Dataset inspection warns, rather than silently changing data, when it detects:

- Severe differences in the number of samples per class.
- Exact duplicates using file-content hashes.
- Near-duplicates using compact grayscale perceptual signatures.
- Duplicate or near-duplicate content across the training and validation
  splits, which can make validation accuracy misleading.

Warnings require a human review of the dataset. The framework must not
automatically delete or relabel images.

## 6. Image preprocessing

The default model input is a three-channel RGB tensor of shape:

```text
(3, 224, 224)
```

The preprocessing contract is:

1. Decode the image with Pillow.
2. Convert grayscale input to RGB by replicating the grayscale channel three
   times.
3. Resize to the configured square input size using bilinear interpolation.
4. Convert pixels to a tensor.
5. Normalize channels with ImageNet statistics:

```text
mean = (0.485, 0.456, 0.406)
std  = (0.229, 0.224, 0.225)
```

Training may apply augmentation before the tensor conversion. Validation,
checkpoint inference, and ONNX inference must use the deterministic path.
Preprocessing changes are model-contract changes and must be reflected in the
ONNX metadata sidecar and tests.

## 7. Training augmentation

The default augmentation configuration is intentionally suited to hand-drawn
or sketch-like images:

| Setting | Default |
|---|---:|
| Input size | `224` |
| Rotation range | `12` degrees |
| Scale jitter | `0.85` to `1.15` |
| Line-thickness variation | disabled (`0.0`) |
| Pixel noise standard deviation | `0.02` |

Rotation and scale jitter improve tolerance to small drawing-placement and
orientation differences. Optional line-weight/sharpness variation and pixel
noise improve tolerance to differences in stroke rendering, scanning, or
compression.

Augmentation must remain configurable. It must not be applied to validation
images, because validation must measure the model against a stable,
reproducible input transformation.

## 8. Model architecture

The initial model is a torchvision pretrained ResNet18:

1. Load ImageNet-pretrained ResNet18 weights during normal training.
2. Replace its final classification layer.
3. Set the output size to the number of classes discovered in `train`.
4. Keep the backbone construction behind `create_backbone`.

EfficientNet-B0 is already available through the same factory and must preserve
the same training, checkpoint, and export interfaces. Adding another backbone
must not require changing dataset loading, evaluation, CLI contracts, or ONNX
metadata.

The classifier uses two training stages:

1. **Warmup:** freeze the backbone and train only the replacement classifier
   head for `warmup_epochs` (default: 2).
2. **Fine-tuning:** unfreeze the full model and continue training with the
   fine-tuning learning rate.

Default training values are:

| Setting | Default |
|---|---:|
| Epochs | `10` |
| Batch size | `32` |
| Warmup epochs | `2` |
| Head learning rate | `3e-4` |
| Fine-tuning learning rate | `1e-5` |
| Seed | `42` |
| Data-loader workers | `0` |

These are starting points, not guarantees of accuracy. Real dataset size,
class difficulty, and drawing style determine the final settings.

## 9. Reproducibility and run artifacts

Every run must seed Python, NumPy, PyTorch, and CUDA random generators when
available. Deterministic PyTorch and cuDNN settings must be enabled where
supported.

Each training run writes at least:

```text
runs/<run-name>/
  best.pt
  metrics.json
  resolved_config.json
```

`resolved_config.json` records the effective training configuration and the
discovered class list. `best.pt` records the model state, optimizer state,
epoch, class labels, and configuration needed to reconstruct the model.

The best checkpoint is selected by validation accuracy. A run must not claim
that it is reproducible if its seed, class mapping, input size, or model
configuration was not saved.

## 10. Evaluation requirements

Validation must report:

- Overall accuracy.
- Correct and total counts for every class.
- Per-class accuracy.
- A confusion matrix whose rows represent true labels and columns represent
  predicted labels.

Metrics must be written in machine-readable JSON. The class order used in the
confusion matrix must be the saved class order, not an independently sorted
order discovered later.

Aggregate accuracy alone is insufficient for judging a model. In particular,
per-class accuracy is required to reveal a model that performs well only on
large or easy classes.

## 11. Command-line workflow

Train a model:

```powershell
solaria-classifier train data --output-dir runs/demo --epochs 10
```

Select a different supported backbone:

```powershell
solaria-classifier train data --output-dir runs/efficient --backbone efficientnet_b0
```

Export the best checkpoint to ONNX:

```powershell
solaria-classifier export runs/demo/best.pt runs/demo/model.onnx
```

Validate PyTorch and ONNX Runtime output parity:

```powershell
solaria-classifier parity runs/demo/best.pt runs/demo/model.onnx
```

The parity command compares both models on the same generated input tensor and
requires the maximum absolute output error to be no greater than `1e-4`.

## 12. ONNX export contract

The exported model must:

- Accept input named `images`.
- Produce output named `logits`.
- Accept a dynamic batch dimension.
- Preserve the selected input size and number of output classes.
- Reconstruct the exact trained model from the checkpoint.

Export creates a sidecar next to the ONNX file:

```text
model.onnx.metadata.json
```

The sidecar must explicitly contain:

- Input resize dimensions.
- Resize method.
- RGB and grayscale-to-three-channel behavior.
- ImageNet mean and standard deviation.
- Ordered class labels.

The sidecar is the preprocessing contract for future Java/Minecraft
inference. A future integration must consume this metadata instead of
duplicating preprocessing constants in Java code.

Before an exported model is accepted, ONNX Runtime must produce logits within
the configured tolerance of PyTorch logits for the same input tensor. Export
success by itself is not sufficient.

## 13. Development and validation process

Changes must follow this sequence:

1. Update the relevant configuration or implementation.
2. Add or update a focused test for observable behavior.
3. Run the test suite:

   ```powershell
   pytest -q
   ```

4. Run linting:

   ```powershell
   ruff check .
   ```

5. Compile-check the package:

   ```powershell
   python -m compileall -q solaria_classifier tests
   ```

6. Check patch whitespace:

   ```powershell
   git diff --check
   ```

The test suite must cover class mapping and tensor shape, reproducible seeding,
classification-head replacement, confusion-matrix behavior, and ONNX export
parity. A tiny generated dataset is appropriate for framework tests; it is not
a substitute for a real-data training smoke test.

## 14. Completion criteria for this milestone

The CNN framework is complete when:

1. A correctly structured dataset can be trained from the command line.
2. The train and validation class mappings are checked and saved.
3. A best checkpoint and resolved configuration are produced.
4. Aggregate and per-class evaluation metrics plus a confusion matrix are
   available.
5. Dataset-quality warnings identify likely leakage or severe imbalance.
6. A supported checkpoint exports to ONNX with dynamic batch support.
7. The preprocessing and class metadata sidecar is written beside the model.
8. ONNX Runtime parity passes at the documented tolerance.
9. Focused tests, linting, compilation, and whitespace checks pass.
10. No Minecraft or Java integration is required to satisfy this milestone.

## 15. Future integration boundary

When Minecraft integration begins, it should be implemented as a separate
consumer of the artifacts produced here:

```text
image -> metadata-defined preprocessing -> ONNX Runtime -> logits
       -> saved class-label mapping -> predicted class
```

The mod-side implementation must use the exported model's ordered labels and
preprocessing metadata. It must not infer a different class order, normalize
images with different constants, or silently resize images using a different
method. Any intentional contract change requires updated metadata, parity
tests, and a corresponding versioned documentation change.

## 16. Confidence and unknown handling

The classifier may be consumed by a downstream pipeline that must reject
ambiguous predictions instead of turning them into structures. The shared
confidence helper computes softmax probabilities from one model output,
selects the saved class with the highest probability, and accepts it only when
the configurable threshold is met. Below the threshold it returns the
literal label `unknown`, `accepted: false`, the measured confidence, and an
actionable rejection reason.

The default proposed downstream threshold is `0.60`; callers must pass their
effective threshold explicitly or record it in their configuration. This
helper does not change training, preprocessing, checkpoint, ONNX, metadata, or
class-order contracts. A future dataset may instead include an explicit
`unknown` class directory, but that is a dataset/model decision and is not
implicitly added by this helper.

The proposed six-class dataset labels are:

1. `spiral_galaxy`
2. `solar_system`
3. `black_hole`
4. `ringed_planet`
5. `comet`
6. `supernova`

For a real-data training run, provide at least 100 clean labeled drawings and
at least 20 varied camera-captured labeled drawings per class, with the
validation split held out by drawing rather than by near-identical captures.
Clean examples should be legible, centered, and supplied as PNG or JPEG files;
camera examples should include varied lighting, viewing angle, distance,
background, rotation, and mild occlusion while retaining the intended class.
Use the existing `data/train/<class-name>/` and `data/val/<class-name>/`
layout, keep both splits mapped to the same six labels, and review all
duplicate, near-duplicate, and class-imbalance warnings before training.
