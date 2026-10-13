# Solaria Expansion Implementation Plan

The expansion proceeds in strict order from the classifier foundation through
capture/cleaning, prediction, blueprint generation, parameter extraction, and
end-to-end scanning. Existing classifier contracts remain unchanged. Synthetic
fixtures validate plumbing only and must be labeled **plumbing check
(synthetic)**; real quality, camera, Minecraft, and user-study claims require
human evidence.

## Phases

1. **Phase 0 — classifier foundation:** configurable confidence/unknown
   handling, six-class and real-data requirements, and awaiting-human status.
2. **Phase 1 — contracts and documentation:** pipeline specification,
   versioned prediction/blueprint schemas and tests, and this plan.
3. **Phase 2 — capture and cleaning:** optional vision dependencies,
   `cleaning_v1`, file/camera capture, actionable configurable quality gates,
   debug intermediates, synthetic tests, and a 20-capture human protocol.
4. **Phase 3 — prediction pipeline:** metadata-driven `prediction_v1`,
   shared cleaning, optional camera augmentation, and separate clean/camera
   evaluation reporting.
5. **Phase 4 — blueprint builder:** after asking for Minecraft version and
   edition, select a maintained compatible licensed schematic library; add
   validated blueprints, deterministic capped voxel generators, visualization,
   export, examples, and import guidance.
6. **Phase 5 — parameter extraction:** normalized per-class extractors,
   confidence/fallback metadata, synthetic tolerance tests, and real-drawing
   validation guidance.
7. **Phase 6 — integration:** `solaria scan`, clean rejection behavior,
   ignored per-run records, and a five-participant user-test protocol.

## Done criteria and controls

Each phase must pass focused tests plus `pytest -q`, `ruff check .`,
compileall, and `git diff --check` before the next phase. Every threshold,
tolerance, quality gate, extractor limit, scale, and build cap is configurable.
Failures return stable reason codes and actionable messages. Oversize structures
are scaled down or rejected, never silently truncated.

There are mandatory review pauses after Phases 2 and 4. Stop and ask if an
existing classifier contract would need to change, human-only evidence has no
meaningful substitute, Minecraft version/licensing/maintenance cannot be
established, requirements conflict, a criterion cannot be met, or a dependency
is heavy, unmaintained, or unavailable on Python 3.10+.

Each validated phase is committed locally with the Copilot co-author trailer
and is marked as committed locally, awaiting push approval until the human
explicitly approves a push. `PROJECT_STATUS.md` distinguishes synthetic
validation, real validation, and awaiting human action.

## Final human actions

Provide real labeled drawings and varied camera captures; train and review the
real model; supply extraction-validation drawings; import and inspect a
schematic in Minecraft; run the five-or-more-participant user test; and approve
any push to the shared remote.
