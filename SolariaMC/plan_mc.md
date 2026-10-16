# Solaria Minecraft Mod Plan (SolariaMC only)

**For:** the AI agent working in IntelliJ on `SolariaMC` (Fabric mod, Java).
Paths are relative to the repo root (the folder containing `SolariaAI/` and
`SolariaMC/`). If your IDE project root is `SolariaMC/`, the repo root is `..`.

---

## Agent progress ledger (maintained by the agent)

> The teacher's requirements below are **unchanged**. This ledger only records
> what is **done** and what is **not done**. Last updated **2026-10-15**.
> Evidence labels follow §3.1. Local commits: `6268615`, `ee8c636`, `b2a0d98`.
> **Nothing has been pushed.** Verified: `gradlew.bat clean build test` is green
> with 138 synthetic assertions (112 `foundationTest` + 26 `wikiStructureTest`).

### Phase summary

| Phase | State | Short reason |
|---|---|---|
| M0 Audit and toolchain | ✅ done (`synthetic-validated`) | `TOOLCHAIN.md`, `STATUS.md`, root `.gitignore` section, build green. |
| M1 Mod foundation | ✅ done (`synthetic-validated`); in-game `awaiting human action` | Entrypoints, config, privacy log, commands, 112 assertions. |
| M2 Blueprints / voxel generators | 🚧 **in progress** | Data model + palette compiling; generators, doc, previews and tests **not done**. |
| M3 Service client | ⬜ not started | — |
| M4 Scan and review UI | ⬜ not started | — |
| M5 Placement / networking / undo | ⬜ not started | PAUSE point. |
| M6 Teacher-facing polish | ⬜ not started | Optional; first to descope. |
| M7 End-to-end integration | ⬜ not started | PAUSE point. |
| M8 Classroom / evaluation | ⬜ not started | Documents only. |

### M0 details ✅

- [x] Inventory, versions, mappings → `docs/TOOLCHAIN.md` (corrected: MC 26.1.2
      is **unobfuscated with Mojang names**, no Yarn mappings).
- [x] `gradlew.bat build` green; `docs/STATUS.md` with §3.1 labels.
- [x] Root `.gitignore` has `# SolariaMC` section.
- [ ] **Push not done — needs human approval.**

### In-game wiki fix ✅ (`synthetic-validated`; in-game `awaiting human action`)

- [x] Root-caused why nothing appeared in Oracle Index: `wiki/docs/_meta.json`
      used the key `"introduction"` without the `.mdx` suffix. Oracle Index 2.0.0
      (`rearth.oracle.ui.OracleScreen.parseJson`) computes
      `directory = !id.endsWith(".mdx")`, so it treated the page as a folder and
      logged `No _meta.json found for solariamod at
      oracle_index:books/solariamod/docs/introduction/_meta.json`
      (`run/logs/latest.log`, 22:40:18 / 22:40:23).
- [x] Cross-checked the whole layout against the Oracle Index 2.0.0 classes
      (`DocsIndexer`, `V1DocsFormat`, `LegacyDocsFormat`, `OracleScreen`): book
      path, `sinytra-wiki.json` `"schema":"1"`, `docs/` root, `modid`/folder-name
      match, and `_homepage.mdx` (web-only) were all already correct.
- [x] Fixed the key to `"introduction.mdx"`; corrected two false claims in the
      page text (it does not download from moddedmc.wiki, and schema-1 assets live
      in `assets/`, not `.assets/`).
- [x] `syncWiki` changed from `Copy` to Gradle `Sync`, so pages deleted from
      `wiki/` cannot linger in the shipped book. Generated
      `src/main/resources/assets/oracle_index/` is now git-ignored.
- [x] New `wikiStructureTest` (26 synthetic assertions) wired into `test`/`build`;
      verified it fails on the original bad key and passes on the fix.
- [x] Rules recorded in `docs/WIKI.md`; `docs/TOOLCHAIN.md` and `docs/STATUS.md`
      updated.
- [ ] In-game confirmation that the wiki renders: `awaiting human action`.

### M1 details ✅ (`synthetic-validated`)

- [x] Entrypoints: common, client (`src/client/java`), server. Loom split
      environment source sets. Packages: `config`, `command`, `logging`.
- [x] JSON config with all listed fields and safe defaults; strict parser,
      bounded, duplicate-rejecting, never echoes config values in errors, atomic
      reload, 64 KiB cap.
- [x] `PrivacyLog` accepts only `ReasonCode` enum; test enforces this.
      (`ReasonCode` currently has only `INPUT_INVALID`, `PERMISSION_DENIED`.)
- [x] `/solaria`, `/solaria status`, `/solaria config reload`; permission-gated
      via 26.1.2 `PermissionSet / Permission.HasCommandLevel`.
- [x] 112 dependency-free assertions; custom `foundationTest` task.
- [ ] In-game: mod loading, commands, permissions, rendering → `awaiting human
      action`. EULA not accepted; server stops at EULA gate.

### M2 details 🚧 in progress

Written and compiling (untracked in `blueprint/`):

- [x] `AstronomyClass` — 10 wire ids.
- [x] `VoxelGrid` — dense x→y→z grid, `snapshot()` for deterministic comparison.
- [x] `MaterialPalette` — 1-based indices, `FORBIDDEN_BLOCKS` set, builder
      validation against gravity/fluid/harmful blocks.
- [x] `Palette.create()` — 19 safe unique blocks (verified via `javap`).
- [x] `Blueprint` record.
- [x] `compileJava` passes with all five files.

**Not done yet (phase incomplete):**

- [ ] Deterministic generators for all 10 classes.
- [ ] Size cap / scale enforcement (`max_size_blocks`, `blocks_per_unit`).
- [ ] `docs/BLUEPRINTS.md`.
- [ ] Preview Gradle task (PNG slices + isometric → `build/previews/`).
- [ ] Tests: determinism, cap/scale, all classes covered, no forbidden blocks.
- [ ] **Deviation:** §4 M2.2 asks for palette as a *data file*; currently
      hardcoded in `Palette.java`. Needs human decision.
- [ ] **PAUSE not reached** — no previews exist for human review.

### Deviations and blockers (carried forward)

1. **Schema vs. Appendix A divergence.** Published schemas look like record
   formats; §A.4 gives request formats with different fields. Not blocking
   M0–M2; to be resolved before M3.
2. **Oracle Index pulls ML libraries** (DJL, ONNX Runtime). M1 adds none, but
   a strict ban requires a human decision about the existing wiki dependency.
3. **This plan file is untracked** — it is a copy of the teacher's original
   (byte-identical). The ledger is added here only, **not** to the original.

---

## 0. Scope boundary (read first)

**You own:** everything inside `SolariaMC/`.

**You must not touch the AI project.** Specifically:

- Do not edit, run, or install anything in `SolariaAI/`. You may **read**
  `SolariaAI/docs/schemas/`, `SolariaAI/docs/API_V1.md`, `SolariaAI/docs/CONTRACT_STATUS.md`,
  and `SolariaAI/contracts/fixtures/` once they exist, and copy fixtures into
  `SolariaMC` with a script. Nothing else in `SolariaAI` is your concern.
- No ML, OpenCV, ONNX, or image-cleaning code in the mod. The mod is a client
  of the local service described in Appendix A.
- Do not edit root files (`README.md`, `.gitattributes`, `.idea/`, `docs/`)
  except: (a) add Java/Gradle-related lines to the root `.gitignore` under a
  clearly labelled `# SolariaMC` comment, and (b) append to
  `docs/CONTRACT_REQUESTS.md` under your own heading (section 3.4).

The AI agent publishes the contract, schemas, fixtures, and a mock service
(`solaria-classifier serve --mock`). Until that exists, build against the
contract in Appendix A and a mock HTTP server inside your own tests.

## 1. Fixed decisions (do not revisit without asking)

| Topic | Decision |
|---|---|
| Minecraft target | **Java Edition with Fabric** (not Forge, not Education Edition). Defined by `SolariaMC/src/main/resources/fabric.mod.json`: `fabricloader >=0.19.3`, `minecraft ~26.1.2`, `java >=25`, `fabric-api *`. Do not change these constraints without asking. |
| Product | A student's pencil drawing of an astronomy concept is scanned, classified (by the service), **reviewed by the teacher**, and built as a 3D structure in-game. |
| Classes (10) | `sun`, `earth`, `moon`, `mars`, `jupiter`, `rocket`, `satellite`, `solar_system`, `lunar_eclipse`, `solar_eclipse`. The live list always comes from `GET /v1/health`; the mod holds one blueprint per class above. |
| Teacher review/override | **Required.** Every accepted prediction is shown to the teacher, who can confirm, override the label, or reject before anything is built. |
| Structures | Each class has a fixed, pre-authored blueprint (a deterministic voxel grid). **No parameter extraction** from the drawing. The mod owns all blueprint, voxel, palette, and placement logic. |
| Integration | Local HTTP+JSON service (Appendix A). The mod never contains ML code. |
| Report | The thesis is human-owned. You never edit it; you write `SolariaMC/docs/REPORT_SYNC_MC.md` (Phase M8). |

## 2. Repository ownership

```text
<repo root>/
  SolariaMC/     <- you
  SolariaAI/     <- other agent; read-only contract files only
  docs/          <- shared; append-only CONTRACT_REQUESTS.md only
  README.md  .gitignore  .gitattributes  .idea/   <- see section 0
```

Never commit: scan images, per-run records, personal data, local config with
machine-specific paths, `build/`, `.gradle/`, `run/`.

## 3. Agent rules

### 3.1 Evidence labelling

- Anything validated with a mock service, hand-written fixtures, or headless
  tests is **plumbing check (synthetic)** in code, tests, and docs. It never
  counts as classroom usability or learning impact.
- Use these status labels in `SolariaMC/docs/STATUS.md`: `synthetic-validated`,
  `real-validated`, `awaiting human action`,
  `committed locally, awaiting push approval`. In-game behavior you cannot
  observe yourself is `awaiting human action` until the human runs the manual
  checklist.
- Never claim you saw something render or play in a running client unless a
  tool result actually showed it.

### 3.2 Engineering rules

- Every threshold, cap, scale, batch size, timeout, URL, and permission level is
  configuration with safe defaults.
- Every failure maps to a **stable reason code** (Appendix A.5) with a
  player-readable, actionable message. Never reuse or rename a code.
- Generation is deterministic: same blueprint and settings give byte-identical
  voxel data (fixed axis order x, y, z; sorted serialization; no unseeded
  randomness).
- Oversize structures are scaled down or rejected, never silently truncated.
- Prefer the JDK and what Minecraft/Fabric already ship (`java.net.http`, Gson,
  `com.sun.net.httpserver` for test servers) over new dependencies. Ask before
  adding any library.
- Client-only code (screens, textures, key bindings, HTTP calls) stays in the
  client source set; the server never trusts client data (section M5).
- Do not assume API or mappings names from older Minecraft versions. Verify
  against the actual 26.1.2 setup in the Gradle project and current Fabric
  documentation.

### 3.3 Per-phase done criteria

`gradlew.bat build` (including unit tests) passing, plus `git diff --check`.
Run headless server or game tests where the toolchain supports them. Commit each
validated phase locally in small commits (follow any commit-trailer convention
already in the history). **Never push** without explicit human approval.

### 3.4 Coordination with the AI agent

- Appendix A is shared. Neither agent changes it unilaterally.
- To request a change, append an entry to `docs/CONTRACT_REQUESTS.md` under the
  heading `## From SolariaMC` (date, what, why, version impact). Do not edit the
  other agent's entries. The human arbitrates.
- Ignore unknown response fields so additive changes do not break you.

### 3.5 Mandatory review pauses

Stop and wait for the human after **Phase M2**, **Phase M5**, and **Phase M7**,
with a short summary of what to inspect.

### 3.6 Stop and ask when

- the Minecraft 26.1.2 / Java 25 / Fabric toolchain cannot be made to build, or
  a needed Fabric API is missing or unmaintained for this version;
- the contract in Appendix A cannot be implemented as written or is
  contradicted by the published schemas;
- a feature needs a new dependency or would change `fabric.mod.json`
  constraints;
- a human-only input blocks the phase (continue with mocks where allowed and
  mark the output `awaiting human action`);
- requirements conflict, or a done criterion cannot be met.

### 3.7 Privacy and ethics (students are minors)

- The cleaned drawing is held in memory only to show the review screen and is
  dropped when the screen closes or the session ends. Never write drawings to
  disk or to logs.
- Logs contain session ids, labels, confidences, reason codes, and timings only.
  No names, no image data.
- The mod talks only to the configured local service (default
  `127.0.0.1`). No other network calls.

### 3.8 Environment notes

The human works on Windows (PowerShell). Use `gradlew.bat`, quote paths, and
avoid bash-only syntax in documented commands. Verify `java -version` is 25 or
newer. An unattended agent cannot interact with a GUI client: use unit tests,
headless server runs, and game tests where possible, and leave in-game checks
to the human via the manual checklist.

## 4. Phases

M0-M2 and the mock-based part of M3 do not need the AI agent's code. From M3
onward prefer the published mock service and fixtures once they exist.

### Phase M0: Audit and toolchain ✅

1. Inventory `SolariaMC`: is it a fresh Fabric template or does it contain
   code? What are the Loom, mappings, and Fabric API versions in use?
2. Confirm `gradlew.bat build` passes on the pinned versions in IntelliJ with a
   Java 25 JDK. If not, stop and report the exact error.
3. Write `SolariaMC/docs/TOOLCHAIN.md`: versions, Gradle tasks that work
   (`build`, `test`, `runClient`, `runServer`), mapping approach, and anything
   surprising about 26.1.2.
4. Create `SolariaMC/docs/STATUS.md` with the status labels from 3.1.
5. Add Java/Gradle ignore lines (section 0).

Done: build green, docs written, commit.

### Phase M1: Mod foundation ✅

1. Mod entrypoints (common, client, server) with a clear package layout:
   `config`, `net` (service client), `screen`, `blueprint`, `placement`,
   `command`, `network` (client/server packets).
2. JSON config file (service URL, timeouts, `teacher_permission_level` default
   2, `allow_manual_label_on_low_confidence` default true, blocks per tick,
   build anchor offset, `max_size_blocks`, blocks-per-unit scale, scan-from-file
   enabled default false). Safe defaults, validation, clear errors on bad values.
3. Logging helper that enforces the privacy rules in 3.7.
4. `/solaria` command root with `status` and `config reload`.
5. Unit tests for config parsing and validation.

Done: criteria in 3.3.

### Phase M2: Blueprints and voxel generators, then PAUSE 🚧

1. **`blueprint_v1`** (owned by this project; document it in
   `SolariaMC/docs/BLUEPRINTS.md`): class, material palette references, scale,
   size cap. Independent of any save-file format.
2. **Palette as data:** abstract materials mapped to block ids in a data file,
   with a fallback block. The palette must avoid gravity blocks (sand, gravel,
   concrete powder), fluids, blocks that need support, and anything harmful or
   disruptive (explosives, fire, lava).
3. **Deterministic generators** for all ten classes, enforcing the size cap and
   scale (scale down or reject with `CAP_EXCEEDED`). Proposed designs, to refine
   for educational accuracy:
   - `sun`, `earth`, `moon`, `mars`, `jupiter`: spheres with class-appropriate
     materials and surface detail (for example banding on Jupiter, craters on
     the Moon);
   - `rocket`: body, nose cone, fins;
   - `satellite`: body, solar-panel wings, antenna dish;
   - `solar_system`: central sun and the planets in the correct order at a
     compressed, clearly stated scale;
   - `lunar_eclipse`: sun, Earth, Moon aligned with the Moon in Earth's shadow;
   - `solar_eclipse`: sun, Moon, Earth aligned with the Moon between them.
4. **Preview tool:** a Gradle task or test that renders each blueprint to PNG
   slices and a simple isometric image under `SolariaMC/build/previews/`, so the
   human can review structures without launching Minecraft.
5. Tests: byte-identical output for the same input, cap and scale behavior,
   every class has a blueprint, palette contains no forbidden blocks
   (**plumbing check (synthetic)**).

**PAUSE.** The human reviews the previews for scientific correctness and
classroom suitability, and approves or adjusts the designs.

### Phase M3: Service client

1. Typed models for the Appendix A responses, parsed with Gson; unknown fields
   ignored.
2. Async client (`java.net.http.HttpClient`) with timeouts and cancellation;
   never blocks the render or server tick thread. Methods: health, scan (upload
   bytes or camera), get session, get image, review, build result.
3. Error mapping: connection failure becomes `SERVICE_UNAVAILABLE`; service
   error bodies keep their `reason_code`; unknown codes are shown generically.
4. Startup/`/solaria status` health check: compares `classes` from the service
   with the mod's blueprints; warn on mismatch (`BLUEPRINT_MISSING` for any
   class without a blueprint). Never hardcode the class list in logic.
5. Tests against an embedded JDK `HttpServer` serving the AI project's fixtures
   (copy them with a script from `SolariaAI/contracts/fixtures/`; until they
   exist, hand-write fixtures from Appendix A and mark them
   **plumbing check (synthetic)**). Cover every scenario in A.6 and every error
   path.
6. When the AI agent's mock service is available, add a manual integration note
   to `SolariaMC/docs/MOD_MANUAL_TEST.md` for running against
   `solaria-classifier serve --mock`.

Done: criteria in 3.3.

### Phase M4: Scan and review UI

1. `/solaria scan` and a configurable key binding start a scan through the
   client. Optional (config, off by default): `/solaria scan file <path>` reads
   an image on the client and uploads it, for scanner software that saves files.
2. **Review screen** (client): the cleaned drawing, the predicted class,
   a confidence bar, a class selector built from the service's `classes` for
   override (a cycling or list control; vanilla has no dropdown, so choose
   something that works for ten items), and Confirm / Override / Reject / Retry
   buttons. Verify texture and screen APIs against the real 26.1.2 setup.
3. States, each with an actionable message:
   - `quality_rejected`: show which gate failed and what to fix, with Retry;
   - `low_confidence`: tell the student to retry or refine the drawing; if
     `allow_manual_label_on_low_confidence` is on, let the teacher pick a label
     manually (sent as `override`);
   - `awaiting_review`: confirm, override, or reject;
   - service errors: `SERVICE_UNAVAILABLE`, `CAMERA_UNAVAILABLE`,
     `MODEL_UNAVAILABLE`.
4. Review actions are sent to the service as `review_v1`. Only a confirmed or
   overridden label proceeds to a build request (section M5).
5. Screen logic is separated from rendering so state transitions can be unit
   tested without a client.
6. Tests for the state machine across every session status
   (**plumbing check (synthetic)**).

### Phase M5: Placement, networking, undo, then PAUSE

1. **Client to server packet** carrying the session id and final label. The
   server is authoritative and validates everything:
   - sender permission level is at least `teacher_permission_level`
     (`PERMISSION_DENIED`);
   - the label is one of the known blueprints (`BLUEPRINT_MISSING`);
   - rate limit (one build in progress per player; cooldown configurable);
   - size cap re-checked on the server (`CAP_EXCEEDED`).
2. **Placement engine** (server): anchor relative to the player's position and
   facing plus the configured offset; resolve the footprint; reject if it
   leaves the world height range or touches unloaded chunks
   (`PLACEMENT_BLOCKED`); apply a configurable obstruction policy (default:
   reject when more than a set fraction of the footprint is non-air). Place in
   batches per tick. Choose block-update flags that avoid neighbor-update
   storms, and verify them for 26.1.2.
3. **Undo:** `/solaria undo` restores the previous block states of the last
   build, with a capped history and memory bound. Also a way to clear in-flight
   builds.
4. **Feedback:** server to client messages for progress, success, and each
   failure code.
5. **Single-player note:** with default settings a single-player world without
   cheats has permission level 0 and cannot build. Document this and the
   `teacher_permission_level` setting in `SolariaMC/docs/INSTALL.md`.
6. Report `build_result_v1` to the service (`built`, `failed`, or `not_built`,
   `t3_ms`, `blocks_placed`, reason code).
7. Tests: placement planning, batching, undo restore, permission and validation
   rejections (**plumbing check (synthetic)**). Add game tests or a headless
   `runServer` check if the toolchain supports them.
8. `SolariaMC/docs/MOD_MANUAL_TEST.md`: checklist for a human to verify the
   review screen, permissions, each of the ten builds, obstruction rejection,
   undo, and each failure message in a running client.

**PAUSE.** The human runs the manual checklist in Minecraft and reports back.

### Phase M6: Teacher-facing polish (optional, descope-first)

1. Optional teacher-editable data file with one short fact per class, sent to
   chat after a successful build. Content is authored by the teacher; do not
   invent scientific claims beyond obvious basics, and mark the file as a draft
   for the teacher to review.
2. In-world labels (for example signs) for the compressed-scale structures so
   the scale is stated, not implied.

### Phase M7: End-to-end integration, then PAUSE

1. Document the startup order (`SolariaAI/docs` has the service commands:
   `solaria-classifier serve ...`; link to it, do not copy it) and the mod's
   side in `SolariaMC/docs/INSTALL.md`: Java 25, Fabric loader, Fabric API, the
   mod jar, config.
2. End-to-end against the mock service for every scenario (human-run), then
   against the real model once the AI agent reports Phase 4 complete.
3. Timing: record `t3_ms` and report it; the full `t3 - t1` (capture to
   build-complete) is computed from the session. Review time is human time and
   is reported separately.
4. Failure matrix test: service down, camera missing, model missing, quality
   reject, low confidence, label rejected, oversize, blocked placement, no
   permission. Each ends in a clear message and a recoverable state.
5. Verify the minimum hardware assumption: Java 25 and a modern Minecraft may
   need more than the thesis §3.8 table says. Add a section to
   `MOD_MANUAL_TEST.md` for the human to run the mod on the weakest classroom
   machine and record frame rate and memory. Do not assert specs you did not
   measure.

**PAUSE.** The human runs the end-to-end flow with a real drawing and reports
timing and any failures.

### Phase M8: Classroom and evaluation materials, report sync

You build documents and drafts; the human runs the studies.

1. `SolariaMC/docs/TEACHER_GUIDE.md`: one-page quick start (launch order, scan,
   review, override, build, undo, troubleshooting by reason code).
2. `SolariaMC/docs/USER_TEST.md`: protocol for five or more participants (task
   script, observation sheet, short usability questionnaire for teacher and
   students). State plainly that this measures usability, not learning.
3. **Draft pre/post assessment items:** a question bank aligned to the ten
   concepts and the misconceptions in the thesis (seasons, moon phases,
   eclipses), laid out for Google Forms, marked as a draft for the teacher and
   supervisor to review. Do not claim validity or reliability.
4. `SolariaMC/docs/REPORT_SYNC_MC.md`: the thesis edits needed for Minecraft
   content (section 5). Do not edit the thesis.
5. Package: `gradlew.bat build` produces the jar; document its location and the
   mod version in `INSTALL.md`. Ask the human which licence to declare before
   writing one into the metadata.

## 5. Report sync items you own (for `REPORT_SYNC_MC.md`)

Adjusted to the real state of the code:

- Minecraft Education references (§2.6, system architecture figure,
  storyboard, flowchart text, §3.8) become Java Edition with Fabric `26.1.2` and
  Java 25; Education Edition does not run Fabric mods.
- §3.8 minimum hardware: unverified for 26.1.2 / Java 25 until measured on the
  weakest classroom machine.
- Integration: the mod is a client of a local Python service over `api_v1`, not
  an in-process bridge.
- Structures are pre-authored blueprints, one per class; no parameter
  extraction. Scale is compressed where stated, so the storyboard's "1:1 scale"
  wording must be corrected.
- Teacher review/override is implemented as specified (permission level, review
  screen); explain the single-player permission caveat.
- Undo, placement caps, and failure behavior as implemented.
- The user test measures usability; the learning-impact study is separate and
  its analysis lives in the AI project.

## 6. Schedule and descoping

The human is in project month 9 of 12. Target ordering, not guarantees. The
human has asked to start the AI side first, so begin this plan when told.

| Phase | Needs from the AI agent | Notes |
|---|---|---|
| M0-M2 | Nothing | Can start immediately when told |
| M3-M4 | Contract (Appendix A); mock service when available | Hand-written fixtures until then |
| M5 | Nothing new | Human in-game check needed |
| M7 | Real model (AI Phase 4) | Falls back to the mock otherwise |
| M8 | Run records (AI Phase 5) for analysis | Documents only |

If time runs short, cut in this order and say so in `STATUS.md`:
1. Phase M6 (facts and in-world labels);
2. `scan file` and key binding (keep the command);
3. preview polish beyond slices.

Never cut: the review screen with override, server-side validation and permission
checks, size caps, undo, the ten blueprints, the failure messages, and the
manual test checklist.

## 7. Human actions (you cannot do these)

- Running the client and the manual checklist; judging structures in-game.
- Approving blueprint previews for scientific and classroom suitability.
- Testing on the weakest classroom machine.
- Running the user test and the pre/post study; reviewing the draft assessment
  items with the supervisor.
- Choosing the mod licence.
- Editing the thesis.
- Approving any push to the shared remote.

---

## Appendix A: Shared contract `api_v1` (identical in both plans)

**Authority:** once published, the schemas in `SolariaAI/docs/schemas/` and
`SolariaAI/docs/API_V1.md` are authoritative. Until then this appendix is the
contract. If the two ever differ, do not guess: log it in
`docs/CONTRACT_REQUESTS.md` and ask the human.

### A.1 Transport

- HTTP + JSON on `http://127.0.0.1:<port>` (host and port configurable; default
  port 8765). Local machine only in v1; LAN use is out of scope.
- All timestamps are epoch milliseconds from the same machine clock.
- Clients ignore unknown response fields. Breaking changes get a new version
  (`/v2`, new schema names).
- Error body (any non-2xx): `{"api_version":"api_v1","reason_code":"...","message":"..."}`.

### A.2 Endpoints

- `GET /v1/health` returns `api_version`, `service_version`, `mock` (bool),
  `model_loaded` (bool), `classes` (the ordered class list from the model
  metadata; **never hardcoded by clients**), `thresholds`
  (`confidence`, and the quality-gate settings in effect), `camera_available`.
- `POST /v1/scan` creates a session.
  - Upload: request body is the raw image bytes with
    `Content-Type: image/png` or `image/jpeg`.
  - Camera: `Content-Type: application/json`, body `{"source":"camera"}`.
  - Returns 200 with a session even when cleaning or confidence rejects the
    image (the rejection is in the session). Non-2xx only for malformed
    requests or server faults.
- `GET /v1/sessions/{id}` returns the session.
- `GET /v1/sessions/{id}/image` returns the cleaned drawing as `image/png`
  (404 `SESSION_NOT_FOUND` / 410 `SESSION_EXPIRED` otherwise).
- `POST /v1/sessions/{id}/review` accepts `review_v1` and returns the updated
  session.
- `POST /v1/sessions/{id}/build_result` accepts `build_result_v1`, completes
  the session, and writes the run record.

### A.3 Session JSON

```json
{
  "api_version": "api_v1",
  "session_id": "string",
  "status": "quality_rejected | low_confidence | awaiting_review | reviewed | completed",
  "t1_ms": 0,
  "t2_ms": 0,
  "cleaning": {
    "passed": true,
    "reason_code": null,
    "message": null,
    "gates": [{"name": "blur", "passed": true, "value": 0.0, "threshold": 0.0}]
  },
  "prediction": null,
  "image_url": "/v1/sessions/{id}/image",
  "review": null,
  "build_result": null
}
```

- `prediction` is `null` when cleaning failed; otherwise it is the
  `prediction_v1` object (label, confidence, accepted, rejection reason, model
  and metadata artifacts, effective threshold).
- `status`: `quality_rejected` (a gate failed), `low_confidence` (prediction not
  accepted), `awaiting_review` (accepted, waiting for the teacher), `reviewed`
  (a `review_v1` was recorded), `completed` (a `build_result_v1` was recorded).
- `t2_ms` is absent if cleaning failed before classification.

### A.4 Review and build result

`review_v1` request body:

```json
{
  "action": "confirm | override | reject",
  "final_label": "moon",
  "reviewer_role": "teacher",
  "reviewed_at_ms": 0
}
```

- `confirm`: `final_label` must equal the predicted label; allowed only when
  `status` is `awaiting_review`.
- `override`: `final_label` must be in `classes`; allowed for `awaiting_review`
  and for `low_confidence` (a manual label when the mod allows it).
- `reject`: no `final_label`; the session ends with no build.
- Errors: `LABEL_NOT_IN_CLASSES` (422), `REVIEW_NOT_ALLOWED` (409).

`build_result_v1` request body:

```json
{
  "status": "built | failed | not_built",
  "t3_ms": 0,
  "blocks_placed": 0,
  "reason_code": null
}
```

`reason_code` is set when `status` is `failed`. The run record stores predicted
label, final label, override flag, reason codes, and `t1`/`t2`/`t3`.

### A.5 Reason codes (stable; never reuse or rename)

| Code | Raised by |
|---|---|
| `INPUT_INVALID` | service |
| `QUALITY_NO_PAGE`, `QUALITY_BLUR`, `QUALITY_DARK`, `QUALITY_BRIGHT`, `QUALITY_SMALL_PAGE` | service |
| `CONF_BELOW_THRESHOLD` | service |
| `CAMERA_UNAVAILABLE`, `MODEL_UNAVAILABLE` | service |
| `SESSION_NOT_FOUND`, `SESSION_EXPIRED` | service |
| `REVIEW_NOT_ALLOWED`, `LABEL_NOT_IN_CLASSES` | service |
| `REVIEW_REJECTED` | teacher action (recorded) |
| `SERVICE_UNAVAILABLE` | mod (service unreachable) |
| `BLUEPRINT_MISSING`, `CAP_EXCEEDED`, `PLACEMENT_BLOCKED`, `PERMISSION_DENIED` | mod |

### A.6 Mock service

`solaria-classifier serve --mock [--scenario <name>]` implements the whole API
without a model or camera. Scenarios: `success`, `low_confidence`,
`quality_blur`, `quality_no_page`, `camera_unavailable`, `model_unavailable`,
`slow`. A request header `X-Solaria-Scenario` overrides the scenario for that
request and `X-Solaria-Label` fixes the label for `success`. All mock output is
**plumbing check (synthetic)**. Example responses are in
`SolariaAI/contracts/fixtures/`.
