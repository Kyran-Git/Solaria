# SolariaMC Status

Last updated: 2026-10-15

## Status labels

| Label | Meaning |
|---|---|
| `synthetic-validated` | Validated with a mock service, hand-written fixtures, or headless tests. Never counts as classroom usability or learning impact. |
| `real-validated` | Validated against the real AI model and/or in a running Minecraft client with human observation. |
| `awaiting human action` | The agent cannot observe or verify this; a human must run the manual checklist and report back. |
| `committed locally, awaiting push approval` | Code is committed to the local repository but has not been pushed. The human must approve any push. |

## Phase status

| Phase | Status | Notes |
|---|---|---|
| **M0: Audit and toolchain** | `synthetic-validated` | Build green on Java 25 + Fabric Loom 1.17.21 + MC 26.1.2. Audit claims corrected; prior commit claim was premature. |
| **M1: Mod foundation** | `synthetic-validated`; `awaiting human action` for in-game checks | Entrypoints, config, privacy-safe logging, permission-gated commands, 112 synthetic assertions. |
| **M2: Blueprints and voxel generators** | not started | PAUSE point — human reviews previews. |
| **M3: Service client** | not started | Needs contract fixtures (hand-written until AI agent publishes them). |
| **M4: Scan and review UI** | not started | — |
| **M5: Placement, networking, undo** | not started | PAUSE point — human runs manual checklist. |
| **M6: Teacher-facing polish** | not started | Optional; first to descope. |
| **M7: End-to-end integration** | not started | PAUSE point — human runs end-to-end flow. |
| **M8: Classroom and evaluation materials** | not started | Documents only. |

## M1 validation evidence (2026-10-15)

- IDE build: successful, no reported build errors.
- `gradlew.bat clean build test`: successful with Java 25; `foundationTest` ran
  112 assertions. Standard `test` engine reports `NO-SOURCE` because tests use a
  Java main runner, not a JUnit engine. This is **plumbing check (synthetic)** only.
- IDE inspections: no errors; minor style suggestions (single-use helper
  parameters, redundant local/main modifiers, inverted predicate, test URL warning).
- `git diff --check`: passed; Git emitted only LF/CRLF normalization warnings.
- JAR inspection: common/server/client foundation classes and wiki assets present.
- `gradlew.bat runServer --args="nogui"`: Fabric loaded Solaria on the dedicated
  server path without a client-class loading failure. Minecraft created its
  settings/EULA files, then exited because EULA was not accepted. The Gradle task
  succeeded, but this is **not** evidence of a running server or working commands.
- Client rendering, in-world commands, operator/non-operator checks, and full
  server startup: `awaiting human action`; see `MOD_MANUAL_TEST.md`.
- No new dependencies or fixed toolchain constraints changed. Wiki sync retained.
  Existing Oracle Index bundles ML libraries; see `TOOLCHAIN.md` caveat. Solaria's
  new code performs no network requests and contains no classification code.

## In-game wiki fix (2026-10-15)

The Solaria wiki did not appear in Oracle Index. Root cause found and fixed.

- **Cause:** `wiki/docs/_meta.json` listed the page as `"introduction"` without the
  `.mdx` suffix. Oracle Index 2.0.0 (`rearth.oracle.ui.OracleScreen.parseJson`)
  computes `directory = !id.endsWith(".mdx")`, so it treated the page as a folder,
  looked for `books/solariamod/docs/introduction/_meta.json`, logged
  `No _meta.json found for solariamod at oracle_index:books/solariamod/docs/introduction/_meta.json`
  and dropped the branch from the sidebar. The exact warning is visible in
  `run/logs/latest.log` at 22:40:18 and 22:40:23.
- **Fix:** key changed to `"introduction.mdx"`. Layout (`sinytra-wiki.json` with
  `"schema": "1"`, `docs/` root, `_meta.json` per folder, book folder named
  `solariamod`) was already correct and matches the Oracle Index 2.0.0 code.
- **Hardening:** `syncWiki` is now a Gradle `Sync` task (was `Copy`), so pages
  deleted from `wiki/` no longer linger in the shipped book. The generated
  `src/main/resources/assets/oracle_index/` folder is git-ignored; `wiki/` is the
  single source of truth.
- **Regression guard:** new `wikiStructureTest` task
  (`src/test/java/net/kyrangit/solaria/WikiStructureTests.java`) with 26 synthetic
  assertions. Verified to fail with the original `"introduction"` key and to pass
  with the corrected key. Wired into `test` and `build`.
- **Evidence:** `gradlew.bat clean build test` green — 112 `foundationTest` +
  26 `wikiStructureTest` assertions; `docs/_meta.json` inside
  `build/libs/solariamod-1.0.0.jar` contains `"introduction.mdx"`.
- Rules documented in `docs/WIKI.md`.
- In-game confirmation that the wiki now renders: `awaiting human action`.

## Commit bookkeeping

At resumption, commit `6268615` contained the root ignore/wiki-sync changes, but
the M0 documents were still untracked. The old M0 status incorrectly claimed a
local commit. M1 and the corrected audit documents are now in local commit
`ee8c636` (`Implement M1 mod foundation and validate configuration`):
**committed locally, awaiting push approval**. Nothing was pushed. Pre-existing
untracked wiki/resources, plan copy, and AI/shared files were not included.

## Contract observations (carried forward; not revalidated in M1)

The published schemas in `SolariaAI/docs/schemas/` appear to describe **record**
formats (what gets stored in run records), while Appendix A §A.4 describes
**request** formats (what the mod sends to the service). The field sets differ:

- `review_v1.schema.json` has `predicted_label`, `override`, `reviewer`,
  `notes`, `version`; Appendix A's request body has `action`, `final_label`,
  `reviewer_role`, `reviewed_at_ms` (no `version`, `predicted_label`, etc.).
- `build_result_v1.schema.json` uses statuses `completed`/`failed`/`not_attempted`;
  Appendix A uses `built`/`failed`/`not_built`, plus `t3_ms`, `blocks_placed`,
  `reason_code`.

This is **not blocking** for M0–M2. It will be tracked when M3 begins. If the
divergence is confirmed, an entry will be added to `docs/CONTRACT_REQUESTS.md`.

## Descope notes

Nothing descoped yet. If time runs short, cut order per the plan:
1. Phase M6 (facts and in-world labels)
2. `scan file` and key binding (keep the command)
3. Preview polish beyond slices
