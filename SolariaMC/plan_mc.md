# Solaria Minecraft Mod Plan (SolariaMC only)

**For:** the AI agent working in IntelliJ on `SolariaMC` (Fabric mod, Java).
Paths are relative to the repo root (the folder containing `SolariaAI/` and
`SolariaMC/`). If your IDE project root is `SolariaMC/`, the repo root is `..`.

---

## Revision log (human-approved)

### 2026-10-20: student-confirm flow, Java Edition requirements

Everything marked **[REV 2026-10-20]** below changed in this revision. The
thesis (`FYP_Report_V7.docx`) was already updated to match, so code, docs and
thesis now describe the same system.

1. **Who builds.** [REV] The **student** sees the prediction and a build prompt
   when the confidence passes the threshold, and accepts it to start the build.
   The **teacher can override** the label if the structure shows the wrong
   astronomy concept (the structure is undone and rebuilt with the corrected
   label). This replaces the earlier rule that a teacher had to confirm every
   accepted prediction before anything was built.
2. **Permissions.** [REV] `teacher_permission_level` is split into
   `build_permission_level` (default 0, so students can build) and
   `override_permission_level` (default 2, teacher). See M1, M5.
3. **Contract impact.** [REV] The shared contract needs two additions
   (`reviewer_role` values and a post-build override). They are logged under
   "Proposed contract amendments" at the end of this file. **Do not implement
   them until the human has arbitrated.** Until then, follow the interim
   behaviour in M4 and M5.
4. **Deployment topology.** [REV] The service only listens on `127.0.0.1` in
   v1, so the machine that scans must also run the service. See section 0.1
   and the human decision list in section 7.
5. **Hardware baseline.** [REV] Use the official Minecraft: Java Edition
   requirements (Mojang Studios, 2026) as the baseline and measure on the
   weakest classroom machine. See M7.
6. **Ethics gate.** [REV] Nothing involving participants (user test, pre/post
   items given to students) happens before the human confirms ethics approval.
   See M8.

### 2026-10-20 (second revision): new class set

The ten classes changed. **[REV]** The new list was:

1. `sun`
2. `earth`
3. `moon`
4. `mars`
5. `jupiter`
6. `venus`
7. `saturn`
8. `neptune`
9. `uranus`
10. `pluto`

- **Removed at this stage:** `removed vehicle class`, `satellite`, `solar_system`,
  `lunar_eclipse`, and `solar_eclipse`. Delete their generators and tests if any
  exist. Do not keep them as hidden extras.
- **Wire ids are lowercase** (the list was written with capitals, but ids are
  dataset folder names and model labels and must match everywhere). The thesis
  shows display names (Venus, Saturn, and so on).
- This second revision was later superseded by the third revision below.

### 2026-10-21 (third revision): class set with Mercury, no Pluto

**[REV]** The final ten classes are:

1. `sun`
2. `earth`
3. `moon`
4. `mars`
5. `jupiter`
6. `Venus`
7. `Saturn`
8. `Neptune`
9. `Uranus`
10. `mercury`

- **Added:** `mercury`. It must behave like the other nine bodies.
- **Removed:** `pluto`. Delete its generator and palette references if any
  exist. Do not keep it as a hidden extra, and do not describe any remaining
  rocky body as a dwarf planet.
- The live list still comes from `GET /v1/health`; the contract text does not
  change. The AI project's dataset folders, model metadata, fixtures, mock
  labels, and docs must carry the same ten ids. The mod holds one blueprint
  per class above.
- `removed vehicle class` no longer exists **anywhere** in the project: not in code,
  fixtures, docs, or revision logs as a live choice. Only this revision file
  may name it as a removed item for history.

---

## Agent progress ledger (maintained by the agent)

> This ledger records what is **done** and what is **not done**. Last updated
> **2026-10-15**, with revision notes added **2026-10-20** by the human.
> Evidence labels follow section 3.1. Local commits: `6268615`, `ee8c636`,
> `b2a0d98`. **Nothing has been pushed.** Verified: `gradlew.bat clean build test`
> is green with 138 synthetic assertions (112 `foundationTest` + 26
> `wikiStructureTest`).

### Phase summary

| Phase | State | Short reason |
|---|---|---|
| M0 Audit and toolchain | âœ… done (`synthetic-validated`) | `TOOLCHAIN.md`, `STATUS.md`, root `.gitignore` section, build green. |
| M1 Mod foundation | âœ… done (`synthetic-validated`), in-game `awaiting human action` | Entrypoints, config, privacy log, commands, 112 assertions. **[REV] Config needs the permission split (see M1 follow-up).** |
| M2 Blueprints / voxel generators | ðŸš§ **in progress** | Data model + palette compiling. Generators, doc, previews and tests **not done**. |
| M3 Service client | â¬œ not started | |
| M4 Scan and build prompt UI | â¬œ not started | [REV] Was "Scan and review UI". |
| M5 Placement / networking / undo / override | â¬œ not started | PAUSE point. |
| M6 Teacher-facing polish | â¬œ not started | Optional, first to descope. |
| M7 End-to-end integration | â¬œ not started | PAUSE point. |
| M8 Classroom / evaluation | â¬œ not started | Documents only, ethics-gated. |

### M0 details âœ…

- [x] Inventory, versions, mappings â†’ `docs/TOOLCHAIN.md` (corrected: MC 26.1.2
      is **unobfuscated with Mojang names**, no Yarn mappings).
- [x] `gradlew.bat build` green. `docs/STATUS.md` with section 3.1 labels.
- [x] Root `.gitignore` has `# SolariaMC` section.
- [ ] **Push not done, needs human approval.**

### In-game wiki fix âœ… (`synthetic-validated`, in-game `awaiting human action`)

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
- [x] Fixed the key to `"introduction.mdx"`. Corrected two false claims in the
      page text (it does not download from moddedmc.wiki, and schema-1 assets live
      in `assets/`, not `.assets/`).
- [x] `syncWiki` changed from `Copy` to Gradle `Sync`, so pages deleted from
      `wiki/` cannot linger in the shipped book. Generated
      `src/main/resources/assets/oracle_index/` is now git-ignored.
- [x] New `wikiStructureTest` (26 synthetic assertions) wired into `test`/`build`.
      Verified it fails on the original bad key and passes on the fix.
- [x] Rules recorded in `docs/WIKI.md`. `docs/TOOLCHAIN.md` and `docs/STATUS.md`
      updated.
- [ ] In-game confirmation that the wiki renders: `awaiting human action`.

### M1 details âœ… (`synthetic-validated`)

- [x] Entrypoints: common, client (`src/client/java`), server. Loom split
      environment source sets. Packages: `config`, `command`, `logging`.
- [x] JSON config with all listed fields and safe defaults. Strict parser,
      bounded, duplicate-rejecting, never echoes config values in errors, atomic
      reload, 64 KiB cap.
- [x] `PrivacyLog` accepts only `ReasonCode` enum, and a test enforces this.
      (`ReasonCode` currently has only `INPUT_INVALID`, `PERMISSION_DENIED`.)
- [x] `/solaria`, `/solaria status`, `/solaria config reload`, permission-gated
      via 26.1.2 `PermissionSet / Permission.HasCommandLevel`.
- [x] 112 dependency-free assertions, custom `foundationTest` task.
- [ ] In-game: mod loading, commands, permissions, rendering â†’ `awaiting human
      action`. EULA not accepted, so the server stops at the EULA gate.
- [ ] **[REV] Follow-up:** replace `teacher_permission_level` with
      `build_permission_level` (default 0) and `override_permission_level`
      (default 2), update the strict parser and its tests, and keep the old key
      rejected with a clear message (not silently ignored). Small change, do it
      before M5.

### M2 details ðŸš§ in progress

Written and compiling (untracked in `blueprint/`):

- [x] `AstronomyClass` with 10 wire ids. **[REV]** These are the old ids. Replace them with the new class set (revision log) and update the tests that enumerate them.
- [x] `VoxelGrid`, a dense xâ†’yâ†’z grid, with `snapshot()` for deterministic
      comparison.
- [x] `MaterialPalette` with 1-based indices, `FORBIDDEN_BLOCKS` set, and builder
      validation against gravity/fluid/harmful blocks.
- [x] `Palette.create()` with 19 safe unique blocks (verified via `javap`).
- [x] `Blueprint` record.
- [x] `compileJava` passes with all five files.

**Not done yet (phase incomplete):**

- [ ] Deterministic generators for all 10 classes.
- [ ] Size cap / scale enforcement (`max_size_blocks`, `blocks_per_unit`).
- [ ] `docs/BLUEPRINTS.md`.
- [ ] Preview Gradle task (PNG slices only, see M2.4) â†’ `build/previews/`.
- [ ] Tests: determinism, cap/scale, all classes covered, no forbidden blocks.
- [ ] **Deviation:** section 4 M2.2 asks for the palette as a *data file*, but it
      is currently hardcoded in `Palette.java`. **Recommendation:** make it a
      small JSON data file, since teachers may want to adjust materials. Needs a
      human decision.
- [ ] **PAUSE not reached.** No previews exist for human review.

### Deviations and blockers (carried forward)

1. **Schema vs. Appendix A divergence.** Published schemas look like record
   formats, while section A.4 gives request formats with different fields. Not
   blocking M0 to M2. To be resolved **before M3** (human).
2. **Oracle Index pulls ML libraries** (DJL, ONNX Runtime). M1 adds none, but the
   rule "no ML in the mod" conflicts with the existing wiki dependency.
   **Recommendation:** drop the in-game wiki or make it optional, since it is off
   the critical path. Needs a human decision.
3. **This plan file is untracked.** It started as a byte-identical copy of the
   teacher's original. **[REV]** It now differs from that original by the
   2026-10-20 revision, and every changed passage is marked **[REV 2026-10-20]**.
   The teacher's original file is not edited.
4. **[REV] Role model in the contract.** `review_v1` only knows
   `reviewer_role: "teacher"`. See "Proposed contract amendments".

---

## 0. Scope boundary (read first)

**You own:** everything inside `SolariaMC/`.

**You must not touch the AI project.** Specifically:

- Do not edit, run, or install anything in `SolariaAI/`. You may **read**
  `SolariaAI/docs/schemas/`, `SolariaAI/docs/API_V1.md`,
  `SolariaAI/docs/CONTRACT_STATUS.md`, and `SolariaAI/contracts/fixtures/` once
  they exist, and copy fixtures into `SolariaMC` with a script. Nothing else in
  `SolariaAI` is your concern.
- No ML, OpenCV, ONNX, or image-cleaning code in the mod. The mod is a client of
  the local service described in Appendix A.
- Do not edit root files (`README.md`, `.gitattributes`, `.idea/`, `docs/`)
  except (a) add Java/Gradle-related lines to the root `.gitignore` under a
  clearly labelled `# SolariaMC` comment, and (b) append to
  `docs/CONTRACT_REQUESTS.md` under your own heading (section 3.4).

The AI agent publishes the contract, schemas, fixtures, and a mock service
(`solaria-classifier serve --mock`). Until that exists, build against the
contract in Appendix A and a mock HTTP server inside your own tests.

### 0.1 Deployment topology (v1) [REV 2026-10-20]

The service listens on `127.0.0.1` only and LAN use is out of scope for v1
(A.1). Therefore **the machine that scans a drawing runs the Minecraft client
and the classifier service together.** Until the human chooses otherwise, assume
this single-machine setup:

- One **scan station** (the teacher's machine or a shared classroom machine)
  runs the game client, the service, and the camera or scanner.
- Students may join the same world from other machines as ordinary players and
  explore the built structures. They do not need the service.
- Students scan one at a time at the scan station and accept the build prompt
  there. Students on other machines do not scan in v1.

If the human wants every student machine to scan, that needs a contract change
(LAN service) and a heavier hardware requirement. Do not design for it unless
told (section 7).

## 1. Fixed decisions (do not revisit without asking)

| Topic | Decision |
|---|---|
| Minecraft target | **Java Edition with Fabric** (not Forge, not Education Edition, which cannot run Fabric mods). Defined by `SolariaMC/src/main/resources/fabric.mod.json`: `fabricloader >=0.19.3`, `minecraft ~26.1.2`, `java >=25`, `fabric-api *`. Do not change these constraints without asking. **[REV]** Reason for Fabric over Forge, as used in the thesis: it is generally lighter and faster, and easier to develop for. |
| Product | **[REV]** A student's pencil drawing of an astronomy concept is scanned, classified (by the service), and **shown to the student with a build prompt when the confidence passes the threshold**. The student accepts the prompt and the structure is built in-game. The **teacher can override** the label if the structure shows the wrong concept. |
| Classes (10) | **[REV]** `earth`, `moon`, `mercury`, `venus`, `mars`, `jupiter`, `saturn`, `uranus`, `neptune`, `sun` (lowercase wire ids). The live list always comes from `GET /v1/health`. The mod holds one blueprint per class above. |
| Student accept and teacher override | **[REV] Required.** Nothing is built without the student accepting the prompt. Below the confidence threshold the student is told to retry and nothing is built. The teacher (override permission) can change the label before the build, or after the build via undo and rebuild. A teacher may also reject a prediction. |
| Structures | Each class has a fixed, pre-authored blueprint (a deterministic voxel grid). **No parameter extraction** from the drawing. The mod owns all blueprint, voxel, palette, and placement logic. |
| Integration | Local HTTP+JSON service (Appendix A). The mod never contains ML code. |
| Report | The thesis is human-owned. You never edit it. You write `SolariaMC/docs/REPORT_SYNC_MC.md` (Phase M8). **[REV]** The current thesis file is `FYP_Report_V7.docx`, already updated for Java Edition, Fabric, and the student-accept flow. |

## 2. Repository ownership

```text
<repo root>/
  SolariaMC/     <- you
  SolariaAI/     <- other agent. Read-only contract files only
  docs/          <- shared. Append-only CONTRACT_REQUESTS.md only
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
- Never claim you saw something render or play in a running client unless a tool
  result actually showed it.

### 3.2 Engineering rules

- Every threshold, cap, scale, batch size, timeout, URL, and permission level is
  configuration with safe defaults.
- Every failure maps to a **stable reason code** (Appendix A.5) with a
  player-readable, actionable message. Never reuse or rename a code.
- Generation is deterministic: same blueprint and settings give byte-identical
  voxel data (fixed axis order x, y, z, sorted serialization, no unseeded
  randomness).
- Oversize structures are scaled down or rejected, never silently truncated.
- Prefer the JDK and what Minecraft/Fabric already ship (`java.net.http`, Gson,
  `com.sun.net.httpserver` for test servers) over new dependencies. Ask before
  adding any library.
- Client-only code (screens, textures, key bindings, HTTP calls) stays in the
  client source set. The server never trusts client data (section M5).
- Do not assume API or mappings names from older Minecraft versions. Verify
  against the actual 26.1.2 setup in the Gradle project and current Fabric
  documentation.
- **[REV]** Put every player-readable message in a language file (not inline
  strings), so Malay text can be added later without code changes. English is
  the only required language.

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
- **[REV]** The two amendments under "Proposed contract amendments" at the end
  of this file are already approved in principle by the human as proposals. Log
  them in `docs/CONTRACT_REQUESTS.md` before M3. They are not in force until the
  human arbitrates and the AI agent's plan carries the same change.

### 3.5 Mandatory review pauses

Stop and wait for the human after **Phase M2**, **Phase M5**, and **Phase M7**,
with a short summary of what to inspect.

### 3.6 Stop and ask when

- the Minecraft 26.1.2 / Java 25 / Fabric toolchain cannot be made to build, or a
  needed Fabric API is missing or unmaintained for this version,
- the contract in Appendix A cannot be implemented as written or is contradicted
  by the published schemas,
- a feature needs a new dependency or would change `fabric.mod.json`
  constraints,
- a human-only input blocks the phase (continue with mocks where allowed and
  mark the output `awaiting human action`),
- requirements conflict, or a done criterion cannot be met.

### 3.7 Privacy and ethics (students are minors)

- The cleaned drawing is held in memory only to show the prompt screen and is
  dropped when the screen closes or the session ends. Never write drawings to
  disk or to logs.
- Logs contain session ids, labels, confidences, reason codes, and timings only.
  No names, no image data.
- The mod talks only to the configured local service (default `127.0.0.1`). No
  other network calls.
- **[REV]** Because students now press the build button themselves, never log or
  display a player name next to a label or confidence. Use the session id.

### 3.8 Environment notes

The human works on Windows (PowerShell). Use `gradlew.bat`, quote paths, and
avoid bash-only syntax in documented commands. Verify `java -version` is 25 or
newer. An unattended agent cannot interact with a GUI client. Use unit tests,
headless server runs, and game tests where possible, and leave in-game checks to
the human via the manual checklist.

## 4. Phases

M0 to M2 and the mock-based part of M3 do not need the AI agent's code. From M3
onward prefer the published mock service and fixtures once they exist.

### Phase M0: Audit and toolchain âœ…

1. Inventory `SolariaMC`: is it a fresh Fabric template or does it contain code?
   What are the Loom, mappings, and Fabric API versions in use?
2. Confirm `gradlew.bat build` passes on the pinned versions in IntelliJ with a
   Java 25 JDK. If not, stop and report the exact error.
3. Write `SolariaMC/docs/TOOLCHAIN.md`: versions, Gradle tasks that work
   (`build`, `test`, `runClient`, `runServer`), mapping approach, and anything
   surprising about 26.1.2.
4. Create `SolariaMC/docs/STATUS.md` with the status labels from 3.1.
5. Add Java/Gradle ignore lines (section 0).

Done: build green, docs written, commit.

### Phase M1: Mod foundation âœ…

1. Mod entrypoints (common, client, server) with a clear package layout:
   `config`, `net` (service client), `screen`, `blueprint`, `placement`,
   `command`, `network` (client/server packets).
2. JSON config file (service URL, timeouts, **[REV]** `build_permission_level`
   default 0, `override_permission_level` default 2,
   `allow_manual_label_on_low_confidence` default true, blocks per tick, build
   anchor offset, `max_size_blocks`, blocks-per-unit scale, scan-from-file
   enabled default false, **[REV]** per-player build cooldown, and
   **[REV]** `max_concurrent_builds` for the whole world). Safe defaults,
   validation, clear errors on bad values.
3. Logging helper that enforces the privacy rules in 3.7.
4. `/solaria` command root with `status` and `config reload`.
5. Unit tests for config parsing and validation.

Done: criteria in 3.3. **[REV]** The permission split is a follow-up (see the M1
ledger).

### Phase M2: Blueprints and voxel generators, then PAUSE ðŸš§

1. **`blueprint_v1`** (owned by this project, documented in
   `SolariaMC/docs/BLUEPRINTS.md`): class, material palette references, scale,
   size cap. Independent of any save-file format.
2. **Palette as data:** abstract materials mapped to block ids in a data file,
   with a fallback block. The palette must avoid gravity blocks (sand, gravel,
   concrete powder), fluids, blocks that need support, and anything harmful or
   disruptive (explosives, fire, lava).
3. **Deterministic generators** for all ten classes, enforcing the size cap and
   scale (scale down or reject with `CAP_EXCEEDED`). **[REV]** Every class is a
   single body. Proposed designs, to refine for educational accuracy:
   - `sun`: sphere in warm yellow and orange blocks with a lighter, uneven
     surface,
   - `earth`: sphere with ocean, continent patches, and a few white cloud
     patches,
   - `moon`: grey sphere with craters (shallow pits),
   - `mercury`: small dark-grey rocky sphere with prominent craters,
   - `mars`: red-orange sphere with darker patches and a white polar cap,
   - `jupiter`: sphere with horizontal bands and a Great Red Spot,
   - `venus`: pale yellow sphere with soft swirling cloud patterns and no
     surface features,
   - `saturn`: banded pale gold sphere with a flat ring around the equator,
   - `neptune`: deep blue sphere with a dark storm spot and light streaks,
   - `uranus`: pale cyan sphere with a thin ring tilted close to vertical (the
     planet spins on its side).
   Rules for all ten:
   - Each blueprint has its own diameter chosen so the shape is recognisable
     and fits `max_size_blocks`. **Sizes are not proportional to real sizes**,
     so say this in `BLUEPRINTS.md` and to the player.
   - Rings count toward the footprint and the size cap.
   - Ring blocks must be ordinary full blocks that need no support, with no
     gravity or fluid blocks.
   - Check that the 19-block palette covers the colours above (yellow, orange,
     blue, green, white, grey, red, tan, brown, cyan). If it does not, propose
     extra safe blocks to the human and wait for approval before adding them.
4. **Preview tool:** a Gradle task or test that renders each blueprint to PNG
   slices under `SolariaMC/build/previews/`, so the human can review structures
   without launching Minecraft. **[REV]** Slices are enough. The isometric image
   is optional and goes last, since it costs time and the human can judge from
   slices.
5. Tests: byte-identical output for the same input, cap and scale behavior,
   every class has a blueprint, palette contains no forbidden blocks
   (**plumbing check (synthetic)**).

**PAUSE.** The human reviews the previews for scientific correctness and
classroom suitability, and approves or adjusts the designs.

### Phase M3: Service client

1. Typed models for the Appendix A responses, parsed with Gson. Unknown fields
   ignored.
2. Async client (`java.net.http.HttpClient`) with timeouts and cancellation. It
   never blocks the render or server tick thread. Methods: health, scan (upload
   bytes or camera), get session, get image, review, build result.
3. Error mapping: connection failure becomes `SERVICE_UNAVAILABLE`. Service error
   bodies keep their `reason_code`. Unknown codes are shown generically.
4. Startup/`/solaria status` health check: compares `classes` from the service
   with the mod's blueprints and warns on mismatch (`BLUEPRINT_MISSING` for any
   class without a blueprint). Never hardcode the class list in logic.
5. Tests against an embedded JDK `HttpServer` serving the AI project's fixtures
   (copy them with a script from `SolariaAI/contracts/fixtures/`. Until they
   exist, hand-write fixtures from Appendix A and mark them **plumbing check
   (synthetic)**). Cover every scenario in A.6 and every error path.
6. When the AI agent's mock service is available, add a manual integration note
   to `SolariaMC/docs/MOD_MANUAL_TEST.md` for running against
   `solaria-classifier serve --mock`.
7. **[REV]** The review call takes a `reviewer_role` argument (`student` or
   `teacher`). Until the contract amendment is arbitrated, the client sends
   `"teacher"` for override and reject, and for confirm sends whatever the
   published schema accepts. If the schema rejects `"student"`, stop and ask
   (section 3.6), do not work around it.

Done: criteria in 3.3.

### Phase M4: Scan and build prompt UI [REV]

1. `/solaria scan` and a configurable key binding start a scan through the
   client. Optional (config, off by default): `/solaria scan file <path>` reads
   an image on the client and uploads it, for scanner software that saves files.
2. **Build prompt screen** (client): the cleaned drawing, the predicted class, a
   confidence bar **with a marker for the threshold**, and **Build** and **Retry**
   buttons for the student. **Only for players at or above
   `override_permission_level`**, the screen also shows a class selector (built
   from the service's `classes`) and a Reject button. Vanilla has no dropdown, so
   use a cycling or list control that works for ten items. Verify texture and
   screen APIs against the real 26.1.2 setup.
3. States, each with an actionable message:
   - `quality_rejected`: show which gate failed and what to fix, with Retry.
   - `low_confidence`: tell the student to retry or refine the drawing. If
     `allow_manual_label_on_low_confidence` is on, a teacher can pick a label
     manually (sent as `override`). A student cannot.
   - `awaiting_review`: the student can **Build**, **Retry**, and a teacher can
     also override or reject.
   - service errors: `SERVICE_UNAVAILABLE`, `CAMERA_UNAVAILABLE`,
     `MODEL_UNAVAILABLE`.
4. Review actions are sent to the service as `review_v1`. Build means `confirm`
   (role `student`, once the amendment is in force). A teacher's change of label
   is `override`. A teacher's rejection is `reject`. Only a confirmed or
   overridden label proceeds to a build request (section M5).
5. **Teacher override after a build:** a command `/solaria override <class>`
   (permission `override_permission_level`) undoes the last build and builds the
   corrected class. Interim rule until the contract amendment is arbitrated: do
   the undo and rebuild locally, log the event with reason codes only, and do
   **not** send a second `review_v1` or `build_result_v1` for a completed
   session. Mark this clearly in `STATUS.md` as `awaiting human action` for the
   contract decision.
6. Screen logic is separated from rendering so state transitions can be unit
   tested without a client.
7. Tests for the state machine across every session status and both roles
   (**plumbing check (synthetic)**). Include: student cannot see the selector,
   student cannot override, low confidence never shows Build.

### Phase M5: Placement, networking, undo, override, then PAUSE

1. **Client to server packet** carrying the session id, the final label, and
   the action (`confirm` or `override`). The server is authoritative and
   validates everything it can:
   - sender permission level is at least `build_permission_level`
     (`PERMISSION_DENIED`). **[REV]** An `override` action additionally needs
     `override_permission_level`.
   - the label is one of the known blueprints (`BLUEPRINT_MISSING`),
   - rate limit: one build in progress per player, a configurable per-player
     cooldown, and a configurable cap on concurrent builds in the world,
   - size cap re-checked on the server (`CAP_EXCEEDED`).
   - **[REV] Known limit, document it:** the server cannot see the service, so it
     cannot check that a student's label matches the prediction. A modified
     client could build any of the ten blueprints. All ten are safe by design
     (palette rules, size cap, obstruction policy), so the risk is a harmless
     wrong build. State this plainly in `INSTALL.md` as the classroom trust
     model. Do not claim the server enforces that students only build what the
     classifier predicted.
2. **Placement engine** (server): anchor relative to the player's position and
   facing plus the configured offset. Resolve the footprint. Reject if it leaves
   the world height range or touches unloaded chunks (`PLACEMENT_BLOCKED`).
   Apply a configurable obstruction policy (default: reject when more than a set
   fraction of the footprint is non-air). Place in batches per tick. Choose
   block-update flags that avoid neighbor-update storms, and verify them for
   26.1.2.
3. **Undo and override:** `/solaria undo` restores the previous block states of
   the player's own last build, with a capped history and memory bound.
   **[REV]** A player at `override_permission_level` can also undo another
   player's last build, and `/solaria override <class>` undoes the last build
   and builds the corrected class in one action, as one history entry. Also a way
   to clear in-flight builds.
4. **Feedback:** server to client messages for progress, success, and each
   failure code. **[REV]** The success message states that the model is not to
   scale with the other bodies.
5. **Single-player note:** with default settings a single-player world without
   cheats gives the player permission level 0. **[REV]** With
   `build_permission_level` at 0 the student can build, but `/solaria override`
   and the label selector need level 2, so the teacher needs cheats enabled or
   operator status. Document this and both settings in
   `SolariaMC/docs/INSTALL.md`.
6. Report `build_result_v1` to the service (`built`, `failed`, or `not_built`,
   `t3_ms`, `blocks_placed`, reason code).
7. Tests: placement planning, batching, undo restore, override-as-one-history-
   entry, permission and validation rejections for both thresholds, rate limit
   and concurrency cap (**plumbing check (synthetic)**). Add game tests or a
   headless `runServer` check if the toolchain supports them.
8. `SolariaMC/docs/MOD_MANUAL_TEST.md`: checklist for a human to verify the
   build prompt screen, a student build, a teacher override before and after a
   build, permissions at both levels, each of the ten builds, obstruction
   rejection, undo, and each failure message in a running client.

**PAUSE.** The human runs the manual checklist in Minecraft and reports back.

### Phase M6: Teacher-facing polish (optional, descope-first)

1. Optional teacher-editable data file with one short fact per class, sent to
   chat after a successful build. Content is authored by the teacher. Do not
   invent scientific claims beyond obvious basics, and mark the file as a draft
   for the teacher to review.
2. In-world labels (for example signs) that show the name of the body and the
   words "not to scale", so the statement is in the world and not only in chat.

### Phase M7: End-to-end integration, then PAUSE

1. Document the startup order (`SolariaAI/docs` has the service commands:
   `solaria-classifier serve ...`. Link to it, do not copy it) and the mod's
   side in `SolariaMC/docs/INSTALL.md`: Java 25, Fabric loader, Fabric API, the
   mod jar, config. **[REV]** Describe the single scan station setup from
   section 0.1.
2. End-to-end against the mock service for every scenario (human-run), then
   against the real model once the AI agent reports Phase 4 complete.
3. Timing: record `t3_ms` and report it. The full `t3 - t1` (capture to
   build-complete) is computed from the session. **[REV]** The student's decision
   time (reading the prediction and pressing Build) is human time. Record it
   separately, using the review timestamp, and report machine time as the total
   minus decision time. The thesis applies its 5 second target to machine time
   only.
4. Failure matrix test: service down, camera missing, model missing, quality
   reject, low confidence, student retries, **[REV]** student without build
   permission, **[REV]** override without override permission, **[REV]** teacher
   override after a build (undo plus rebuild), label rejected, oversize, blocked
   placement. Each ends in a clear message and a recoverable state.
5. **[REV] Hardware baseline.** Use the official Minecraft: Java Edition
   requirements published by Mojang Studios in July 2026 as the starting point:
   64-bit OS, a 4-core CPU (for example Core i3-10100, Ryzen 3 3100, or Apple
   M1), 8 GB RAM with a discrete GPU or 12 GB with integrated graphics, and a
   Vulkan 1.3 GPU with at least 2 GB VRAM, targeting 1080p at 30 FPS on the Fast
   preset. These are the vendor's figures, not measurements. Add a section to
   `MOD_MANUAL_TEST.md` for the human to run the mod and the service together on
   the weakest classroom machine and record frame rate, memory use, and the
   service's share of CPU and RAM, and storage used. Do not assert specs you did
   not measure. The thesis lists storage as "to be measured" and expects this
   result.

**PAUSE.** The human runs the end-to-end flow with a real drawing and reports
timing and any failures.

### Phase M8: Classroom and evaluation materials, report sync

You build documents and drafts. The human runs the studies.

**[REV] Ethics gate:** do not write participant-facing instructions as ready to
use until the human confirms ethics approval (the ERC1 to ERC3 submission).
Label every participant-facing draft "not approved for use with participants".

1. `SolariaMC/docs/TEACHER_GUIDE.md`: one-page quick start (launch order, scan,
   the student build prompt, teacher override and undo, troubleshooting by reason
   code).
2. `SolariaMC/docs/USER_TEST.md`: protocol for five or more participants (task
   script, observation sheet, short usability questionnaire for teacher and
   students). State plainly that this measures usability, not learning.
3. **Draft pre/post assessment items:** a question bank aligned to the ten
   bodies in the class list. **[REV]** Eclipses, the removed vehicle class, and
   the generic satellite category are no longer classes, so do not write items
   on them. Use misconceptions from the thesis only where they concern these ten
   bodies. Lay the items out for Google
   Forms, marked as a draft for the teacher and supervisor to review. Do not
   claim validity or reliability.
4. `SolariaMC/docs/REPORT_SYNC_MC.md`: a consistency check of the code against
   the thesis (section 5). Do not edit the thesis.
5. Package: `gradlew.bat build` produces the jar. Document its location and the
   mod version in `INSTALL.md`. Ask the human which licence to declare before
   writing one into the metadata.

## 5. Report sync items you own (for `REPORT_SYNC_MC.md`) [REV]

The thesis `FYP_Report_V7.docx` already says the following. Your job is to check
that the code makes each statement true and to list anything that is not.

- Platform: Minecraft: Java Edition 26.1.2 with Fabric Loader 0.19.3 or newer,
  Fabric API, and Java 25. Education Edition does not run Fabric mods.
- Integration: the mod is a client of a local Python service over `api_v1`
  (local HTTP and JSON), and the mod contains no ML code.
- Structures: pre-authored blueprints, one per class, no parameter extraction.
  **[REV]** Every class is a single body. Sizes are chosen for recognisability,
  so bodies are not to scale with one another, and the player is told this.
- Flow: the student accepts a build prompt above the confidence threshold, the
  teacher can override, and a structure that is too large is scaled down or
  rejected. The last build can be undone.
- Timing: `t3` is "Completion of Structure Placement", the 5 second target
  applies to machine time only, and student decision time is reported
  separately.
- Hardware: Table 3.8 uses the official Java Edition figures, with storage "to
  be measured". Report what you measure, do not edit the table.
- The user test measures usability, and the learning-impact study is separate.
  Its analysis lives in the AI project.

Also list, for the human, anything in the thesis the code cannot yet support
(for example the claim that the service runs on the same machine as the game
that scans), and the screenshots that must come from a real Java Edition run
(Figure 2.6, Figure 3.6.5, and the storyboard mockups).

## 6. Schedule and descoping

The human is in project month 9 of 12. Target ordering, not guarantees. The
human has asked to start the AI side first, so begin this plan when told.

| Phase | Needs from the AI agent | Notes |
|---|---|---|
| M0-M2 | Nothing | Can start immediately when told |
| M3-M4 | Contract (Appendix A), mock service when available | Hand-written fixtures until then. **[REV]** Log the contract amendments first |
| M5 | Nothing new | Human in-game check needed |
| M7 | Real model (AI Phase 4) | Falls back to the mock otherwise |
| M8 | Run records (AI Phase 5) for analysis | Documents only, ethics-gated |

If time runs short, cut in this order and say so in `STATUS.md`:
1. Phase M6 (facts and in-world labels),
2. `scan file` and key binding (keep the command),
3. preview polish beyond slices,
4. **[REV]** the in-game wiki (it also removes the ML library dependency).

Never cut: the build prompt screen with student accept and teacher override,
server-side validation and permission checks, size caps, undo, the ten
blueprints, the failure messages, and the manual test checklist.

## 7. Human actions (you cannot do these)

- Running the client and the manual checklist, and judging structures in-game.
- Approving blueprint previews for scientific and classroom suitability.
- Testing on the weakest classroom machine.
- Running the user test and the pre/post study, and reviewing the draft
  assessment items with the supervisor. **[REV]** Only after ethics approval.
- Choosing the mod licence.
- Editing the thesis.
- Approving any push to the shared remote.
- **[REV] Arbitrating the two proposed contract amendments**, and making the
  matching change in the AI agent's plan so Appendix A stays identical in both.
- **[REV] Choosing the deployment topology.** Default is one scan station
  (section 0.1). The alternatives are every student machine running the service,
  or a LAN service (needs a contract change and bigger hardware).
- **[REV] Deciding the open items from the ledger:** palette as a data file
  (recommended yes), and the in-game wiki (recommended drop or make optional).
- **[REV] Getting the supervisor's agreement** to the platform and flow change,
  since Objective 2 and three figures in the thesis changed.
- **[REV] Telling the AI agent about the new class set.** In `SolariaAI`, the
  class list appears in the project specification (the required ten-class list),
  the README, the status file, the dataset folder names, the model metadata, the
  fixtures, and the mock service labels. All must use the ten ids in the
  revision log. The mod cannot work until the model's `classes` match its
  blueprints.
- **[REV] Deciding how students draw.** Seven of the ten classes are round
  bodies, and pencil drawings of Venus, Mars, Neptune, Uranus, Earth and Mercury
  can look almost the same. Decide whether the worksheet asks for one
  distinguishing feature per planet (for example rings, bands, craters, a
  tilted ring), or allows colour. This decides how well the classifier can work,
  and it is a data-collection decision, not a mod decision.

---

## Appendix A: Shared contract `api_v1` (identical in both plans)

**Authority:** once published, the schemas in `SolariaAI/docs/schemas/` and
`SolariaAI/docs/API_V1.md` are authoritative. Until then this appendix is the
contract. If the two ever differ, do not guess. Log it in
`docs/CONTRACT_REQUESTS.md` and ask the human.

**[REV]** This appendix is unchanged. The proposed amendments are listed after
it and are not in force.

### A.1 Transport

- HTTP + JSON on `http://127.0.0.1:<port>` (host and port configurable, default
  port 8765). Local machine only in v1. LAN use is out of scope.
- All timestamps are epoch milliseconds from the same machine clock.
- Clients ignore unknown response fields. Breaking changes get a new version
  (`/v2`, new schema names).
- Error body (any non-2xx):
  `{"api_version":"api_v1","reason_code":"...","message":"..."}`.

### A.2 Endpoints

- `GET /v1/health` returns `api_version`, `service_version`, `mock` (bool),
  `model_loaded` (bool), `classes` (the ordered class list from the model
  metadata, **never hardcoded by clients**), `thresholds` (`confidence`, and the
  quality-gate settings in effect), `camera_available`.
- `POST /v1/scan` creates a session.
  - Upload: request body is the raw image bytes with `Content-Type: image/png` or
    `image/jpeg`.
  - Camera: `Content-Type: application/json`, body `{"source":"camera"}`.
  - Returns 200 with a session even when cleaning or confidence rejects the image
    (the rejection is in the session). Non-2xx only for malformed requests or
    server faults.
- `GET /v1/sessions/{id}` returns the session.
- `GET /v1/sessions/{id}/image` returns the cleaned drawing as `image/png`
  (404 `SESSION_NOT_FOUND` / 410 `SESSION_EXPIRED` otherwise).
- `POST /v1/sessions/{id}/review` accepts `review_v1` and returns the updated
  session.
- `POST /v1/sessions/{id}/build_result` accepts `build_result_v1`, completes the
  session, and writes the run record.

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

- `prediction` is `null` when cleaning failed. Otherwise it is the
  `prediction_v1` object (label, confidence, accepted, rejection reason, model
  and metadata artifacts, effective threshold).
- `status`: `quality_rejected` (a gate failed), `low_confidence` (prediction not
  accepted), `awaiting_review` (accepted, waiting for a confirm, override or
  reject), `reviewed` (a `review_v1` was recorded), `completed` (a
  `build_result_v1` was recorded).
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

- `confirm`: `final_label` must equal the predicted label. Allowed only when
  `status` is `awaiting_review`.
- `override`: `final_label` must be in `classes`. Allowed for `awaiting_review`
  and for `low_confidence` (a manual label when the mod allows it).
- `reject`: no `final_label`. The session ends with no build.
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

### A.5 Reason codes (stable, never reuse or rename)

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

---

## Proposed contract amendments [REV 2026-10-20] (NOT in force)

Log these in `docs/CONTRACT_REQUESTS.md` under `## From SolariaMC`. The human
arbitrates. If approved, the AI agent's plan and schemas must carry the same
change so Appendix A stays identical in both plans. Both are additive.

**Amendment 1: reviewer roles.** `reviewer_role` accepts `"student"` and
`"teacher"` (today only `"teacher"`).
- `confirm` is normally sent with role `student`. A teacher may also confirm.
- `override` and `reject` require role `teacher`.
- Why: students now press Build, and the run record should show who confirmed.
- The service does not authenticate roles. The role is recorded as reported by
  the mod, in line with the classroom trust model in M5.
- Version impact: additive field values, so no new API version.

**Amendment 2: teacher override after a build.** Allow `override` with role
`teacher` when `status` is `reviewed` or `completed`, and allow a second
`build_result_v1` for the corrected build.
- Run record additions: `override_after_build` (bool), the first and the
  corrected `final_label`, and both `t3_ms` values.
- Why: the teacher corrects a wrong concept after seeing the structure. Today
  the contract only allows override before the build, so a post-build correction
  cannot be recorded.
- Alternative the human may prefer: no contract change, and the mod treats a
  post-build override as a local event that is not sent to the service. The
  cost is that the run record does not show corrections made after a build, and
  that matters for the thesis override-rate analysis.
- Version impact: additive, so no new API version.

Until the human decides, the mod follows the interim behaviour in M4 item 5.
