# Solaria AI Pipeline Specification

## Purpose and versioning

This document defines the contracts for the Python-only AI project. The shared
contract with the Minecraft client is `api_v1`; the Python project owns the
cleaning, prediction, session, review, and evaluation flows. Any breaking change
must increment the version and update the schemas and tests.

All synthetic measurements in tests and reports must be labeled **plumbing
check (synthetic)**. They are not evidence of real model quality or camera
performance.

## Stages

```text
paper drawing -> capture -> cleaning_v1 -> prediction_v1
               -> (confidence gate) -> review_v1 -> build_result_v1
```

Capture may be a local file or an optional camera. Cleaning is shared by
dataset preparation and live inference. Each stage either returns its versioned
result or an explicit failure with a reason code and actionable message.

## Cleaning contract: `cleaning_v1`

The cleaning result records the source, corrected image information, page
quadrilateral, perspective-correction metadata, and quality-gate results. The
implementation must preserve debug intermediates when requested. Quality gates
are configurable and cover page detection, blur, darkness, brightness, and
minimum page area. A failed gate never proceeds to prediction.

## Prediction contract: `prediction_v1`

The JSON shape is defined by `schemas/prediction_v1.schema.json`. It records the
model label, confidence, acceptance decision, rejection reason, model artifact,
metadata artifact, and effective confidence threshold. Models must load
preprocessing and class order from their metadata or checkpoint; no new stage may
duplicate those constants.

## Review and build-result contracts

The service records teacher decisions in `review_v1`, including predicted label,
final label, action (`confirm` / `override` / `reject`), the override flag, and
review metadata. A later `build_result_v1` records whether the build completed,
failed, or was not attempted.

These records stay in the Python service and are consumed by the mod as a client
of the service. Blueprint generation and voxel placement are outside this project
and are owned by the mod's separate plan.

## Configurable decisions

Thresholds and limits are configuration values, not hidden constants:

- cleaning quality thresholds and debug output;
- prediction confidence threshold and unknown rejection behavior;
- service host, port, and TTL settings;
- camera availability and single-frame capture settings;
- run-record retention and image retention flags.

Invalid input, quality rejection, confidence rejection, review rejection, and
service failures must be visible to the caller with a stable reason code and an
actionable message.

## Validation policy

Synthetic images, mocked cameras, and file fixtures validate plumbing,
determinism, contract shape, and failure behavior only. They must be labeled
**plumbing check (synthetic)**. Real camera accuracy requires human-supplied
captures; the training and evaluation workflow remains subject to human data
collection and model review.
