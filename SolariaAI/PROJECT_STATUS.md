# Solaria AI Project Status

**Last updated:** 2026-10-14  
**Status:** `synthetic-validated` for the Python-only project baseline; real dataset,
train/val/test usage, and any real-model validation remain `awaiting human action`.

This file tracks the Python-only scope of the Solaria project. The project is
intentionally limited to `SolariaAI`: dataset preparation, classifier
alignment, cleaning logic, the local HTTP service, and evaluation/reporting.
The separate Fabric client is not part of this status file.

## Current scope

- `SolariaAI` owns the classifier, the cleaning pipeline, the local service, JSON
  schemas, and the evaluation tooling.
- `SolariaMC` is treated as a separate client; no Java/Fabric work is in scope for
  this repository.
- The target class list is ten labels: `sun`, `earth`, `moon`, `mars`,
  `jupiter`, `rocket`, `satellite`, `solar_system`, `lunar_eclipse`, and
  `solar_eclipse`.
- Real student-drawing data is required for training, but the agent never
  fabricates or substitutes it.

## Completed work

- Standalone Python package structure for the classifier and supporting modules.
- Deterministic dataset utilities, validation checks, and duplicate/leakage
  guards.
- Training and export flow with ONNX parity checks.
- Existing Python checks are in place for the classifier and vision utilities.
- The repo contains the existing contract schemas and the AI-only pipeline docs.

## Validation status

The project is currently in a `synthetic-validated` state because the existing
checks were exercised on synthetic fixtures and generated data, not on real
student drawings.

| Check | Result |
|---|---|
| `python -m pytest -q` | Passed on the synthetic project baseline |
| `python -m ruff check .` | Passed |
| `python -m compileall -q solaria_classifier tests` | Passed |
| `git diff --check` | Passed |

The above checks validate the repository structure and code paths available in
this working tree. They do not count as real model-quality evidence.

## Human-dependent work

The following items remain `awaiting human action`:

- real student-data collection and participant coding;
- the real train/val/test split by participant;
- model calibration and threshold selection;
- any real camera captures for the camera-evaluation path;
- final model export and validation against real data;
- any push to the shared remote repository.

## Next planned work

1. Update the AI project docs to the ten-class, AI-only scope.
2. Confirm contract and mock-service publication in the shared API version.
3. Implement capture and cleaning with quality gates.
4. Wire the real prediction path and service session flow.
5. Add evaluation/reporting tooling and the `REPORT_SYNC_AI.md` output.

## Update policy

Any change in this project must be documented and revalidated with the relevant
Python checks before the next phase. Real-data achievements and approved pushes
must be treated as separate human actions and not as synthetic validation.
