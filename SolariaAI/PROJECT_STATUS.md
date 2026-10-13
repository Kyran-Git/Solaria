# Solaria CNN Classifier — Current Project Status

**Last updated:** 2026-10-14  
**Status:** Classifier framework implemented; Phase 0 confidence handling is
implemented locally; real-data training and Minecraft integration remain future
work.

This file is the living progress record for the Solaria CNN image-classifier
project. It records what is currently available, what has been validated, and
what remains to be done. It is not a replacement for the technical
requirements in [PROJECT_SPECIFICATION.md](PROJECT_SPECIFICATION.md).

## Current completed work

### Project foundation

- Created the standalone `solaria_classifier` Python package.
- Added `pyproject.toml` with package metadata, dependencies, Ruff settings,
  and the `solaria-classifier` command-line entry point.
- Added base and export requirements files.
- Added repository ignore rules for Python caches, virtual environments,
  datasets, and generated classifier artifacts.
- Defined the classifier as independent from the Minecraft mod and Java
  integration.

### Dataset and preprocessing

- Implemented the ImageFolder dataset contract:
  `data/train/<class-name>/` and `data/val/<class-name>/`.
- Added stable class discovery through torchvision's `ImageFolder`.
- Added validation that train and validation class mappings match.
- Added RGB conversion and grayscale-to-three-channel handling.
- Added deterministic validation preprocessing.
- Added ImageNet normalization.
- Added configurable drawing-oriented augmentation:
  - Rotation.
  - Scale jitter.
  - Optional line-weight/sharpness variation.
  - Pixel noise.
- Added warnings for severe class imbalance.
- Added exact duplicate detection using file hashes.
- Added near-duplicate detection using compact image signatures.
- Added cross-split duplicate and near-duplicate checks to identify possible
  validation leakage.

### Model and training

- Added a pretrained torchvision ResNet18 model factory.
- Added EfficientNet-B0 factory support for future backbone comparison.
- Added replacement of the final classification head based on the discovered
  number of classes.
- Added frozen-backbone warmup training.
- Added full-model fine-tuning after warmup.
- Added CPU and CUDA device selection.
- Added fixed seeding for Python, NumPy, PyTorch, and CUDA where available.
- Added deterministic PyTorch and cuDNN settings.
- Added best-checkpoint selection based on validation accuracy.
- Added checkpoint persistence for:
  - Model weights.
  - Optimizer state.
  - Epoch.
  - Class labels.
  - Training configuration.
- Added `resolved_config.json` output for reproducible runs.

### Evaluation

- Added aggregate validation accuracy.
- Added per-class accuracy.
- Added confusion-matrix generation.
- Added JSON metric output.
- Preserved the discovered class order in evaluation outputs.

### ONNX export and validation

- Added ONNX export with a dynamic batch dimension.
- Added stable `images` input and `logits` output names.
- Added the ONNX metadata sidecar:
  `model.onnx.metadata.json`.
- Recorded the following metadata:
  - Input dimensions.
  - Resize method.
  - RGB and grayscale channel behavior.
  - ImageNet mean and standard deviation.
  - Ordered class labels.
- Added PyTorch-versus-ONNX Runtime parity checking.
- Set the current parity tolerance to maximum absolute error `1e-4`.
- Added an integration test covering export and parity.

### CLI and documentation

- Added `train`, `export`, and `parity` CLI commands.
- Added `README.md` with installation and quick-start commands.
- Added `PROJECT_SPECIFICATION.md` with the detailed architecture, contracts,
  workflow, and milestone completion criteria.

### Phase 0 expansion

- Added configurable softmax confidence rejection with an explicit `unknown`
  result for downstream consumers.
- Documented the proposed six-class list and minimum clean/camera dataset
  requirements.
- Real-data training is awaiting human-supplied labeled drawings.

## Current validation status

The implemented framework has been validated with a generated tiny image
dataset:

| Check | Result |
|---|---|
| `python -m pytest -q` | Passed: 4 tests |
| `python -m ruff check .` | Passed |
| `python -m compileall -q solaria_classifier tests` | Passed |
| `git diff --check` | Passed |
| ONNX export test | Passed |
| PyTorch/ONNX Runtime parity | Passed within `1e-4` |

The installed environment is CPU-based. CUDA-specific training behavior is
implemented but has not been exercised in this project environment.

Phase 0 confidence tests use synthetic logits and are a **plumbing check
(synthetic)**, not model-quality evidence. The Phase 0 implementation is
committed locally and awaiting push approval; it is not marked as completed
until pushed.

## Known limitations

- No full training run has been performed with the intended real-world dataset.
- Model accuracy and augmentation settings have not yet been tuned against
  representative hand-drawn images.
- The test dataset is intentionally tiny and does not measure production model
  quality.
- Pillow currently emits a deprecation warning for the image pixel-access path
  used by duplicate inspection.
- PyTorch's current ONNX exporter emits advisory warnings about its
  `dynamic_axes` API; the exported model and parity check currently pass.
- There is no Java or Minecraft inference consumer yet.
- No trained weights, ONNX model binaries, datasets, or generated run
  directories are part of the project source.

## Next planned work

1. Add the intended labeled dataset using the documented train/validation
   directory structure.
2. Run a real training session and review all data-quality warnings.
3. Measure per-class accuracy and inspect the confusion matrix.
4. Tune augmentation and learning-rate settings based on real validation
   behavior.
5. Add a representative real-data smoke test or benchmark.
6. Decide whether ONNX exporter warnings require migration to the newer
   `dynamic_shapes` API.
7. Implement Java/Minecraft ONNX Runtime integration only after the Python
   model contract is stable.

## Update policy

This file must be updated whenever a project change has been:

1. Implemented.
2. Validated with the relevant tests or checks.
3. Committed to Git.
4. Pushed to the shared remote repository.

The update should be included in the same commit as the completed change when
possible, or in the immediately following documentation commit. Every update
must revise the `Last updated` date and, when relevant, the completed-work,
validation-status, known-limitations, or next-planned-work sections.

Do not mark work as completed here merely because it exists in an uncommitted
working tree or has only been tested locally. If a pushed change introduces a
new limitation or changes a project contract, document that change explicitly.
