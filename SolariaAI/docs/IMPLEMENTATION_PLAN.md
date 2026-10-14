# Solaria Expansion Implementation Plan

This file is superseded by the AI-only plan in the repo root/session plan and by
`SolariaAI/PROJECT_STATUS.md`, `SolariaAI/PROJECT_SPECIFICATION.md`, and
`SolariaAI/docs/PIPELINE_SPECIFICATION.md`.

The current project scope is intentionally limited to `SolariaAI`: classification,
cleaning, local service logic, schema contracts, and evaluation tooling. The mod
side is out of scope for this agent and is owned by a separate Fabric project.

The implementation therefore proceeds in the following AI-only order:

1. Phase 0 — audit and reconcile the Python project, update the status/docs,
   and baseline the checks.
2. Phase 1 — classifier alignment with the ten-class list, metrics, split tool,
and data-collection guidance.
3. Phase 2 — publish the shared `api_v1` contract, service schemas, mock service,
   and contract fixtures.
4. Phase 3 — capture and cleaning with quality gates and synthetic coverage.
5. Phase 4 — real prediction pipeline and session timing.
6. Phase 5 — evaluation tooling and `REPORT_SYNC_AI.md`.

This plan no longer includes blueprint generation, voxel generation,
parameter extraction, or Minecraft placement. Those are handled by the mod's
separate plan and are not part of `SolariaAI`.
