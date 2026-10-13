# Solaria Pipeline Specification

## Purpose and versioning

This document defines the contracts between the standalone classifier and the
future camera-to-Minecraft pipeline. A contract change increments its version
and requires updated schemas and tests. The classifier's existing dataset,
preprocessing, checkpoint, ONNX, metadata, and saved class-order contracts
remain unchanged.

All synthetic measurements in tests and reports must be labeled **plumbing
check (synthetic)**. They are not evidence of real model quality or camera
performance.

## Stages

```text
input image -> capture -> cleaning_v1 -> prediction_v1
            -> parameter extraction -> blueprint_v1 -> deterministic voxel grid
            -> schematic export
```

Capture may be a local file or an optional camera. Cleaning is shared by
dataset preparation and live inference. Each stage either returns its versioned
result or an explicit failure with a reason code and actionable message.

## Cleaning contract: `cleaning_v1`

The cleaning result records the source, corrected image path or bytes, page
quadrilateral, perspective-correction dimensions, and quality-gate results.
The implementation must preserve debug intermediates when requested. Quality
gates are configurable and cover page detection, blur, darkness, brightness,
and minimum page area. A failed gate never proceeds to prediction.

## Prediction contract: `prediction_v1`

The JSON shape is defined by `schemas/prediction_v1.schema.json`. It records
the saved model class label, confidence, acceptance decision, rejection reason,
model artifact, metadata artifact, and effective confidence threshold. Models
must load preprocessing and class order from their metadata or checkpoint; no
new stage may duplicate those constants.

## Blueprint and voxel-grid contracts

`blueprint_v1` records a normalized class, normalized extracted parameters,
confidence/fallback metadata, material choices, scale settings, and size-cap
settings. The blueprint schema is intentionally independent of Minecraft file
format. A deterministic generator turns a valid blueprint into a voxel grid
with fixed axis order and sorted serialization. Every generator enforces the
configured scale and maximum dimensions; it must scale down or reject an
oversize request, never truncate it silently.

The schematic library and export details are deferred until the target
Minecraft version and edition are supplied by the human. Compatibility,
maintenance, and licensing must be recorded before Phase 4 implementation.

## Configurable decisions

Thresholds and limits are configuration values, not hidden constants:

- cleaning quality thresholds and debug output;
- prediction confidence threshold and optional explicit unknown class;
- camera-condition augmentation (off by default);
- extractor defaults, confidence limits, and fallback behavior;
- blocks-per-unit scale and `max_size_blocks`;
- schematic output path and per-run artifact retention.

Invalid input, quality rejection, confidence rejection, and cap rejection must
be visible to the caller with a stable reason code and actionable message.

## Validation policy

Synthetic images, hand-written blueprints, mocked cameras, and file fixtures
validate plumbing, determinism, contract shape, and failure behavior only.
They must be labeled **plumbing check (synthetic)**. Real camera accuracy
requires at least 20 varied human-supplied captures; extraction eyeball
validation requires at least 10 real drawings per class; Minecraft usability
requires a human import and walkability/flyability check.
