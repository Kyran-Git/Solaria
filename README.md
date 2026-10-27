# Solaria

Solaria is a Minecraft learning tool for secondary school students. A student
draws an astronomy concept on paper, scans it through an in-game camera item,
and a machine-learning service cleans and classifies the drawing. If the
student accepts the result, they travel to a dimension for that concept and fly
around a full-size sphere of it. A teacher oversees the process and can
override the classification.

This directory holds two repositories that must stay in step:

| Folder | What it is | Where it runs |
|---|---|---|
| `SolariaAI/` | Python package: training, cleaning, quality checks, classification, and a local HTTP service. Separate program and separate tests. | The teacher's machine, bound to `127.0.0.1`. |
| `SolariaMC/` | Fabric mod for Minecraft Java Edition 26.1.2: camera item, GUI, networking, dimensions, generation, and teacher tools. | The teacher's machine (hosts a LAN world) and every student machine (joins as a client). |

## Current plan (Solaria v2)

The authoritative plan is the "Solaria end-to-end build prompt" (see
`SOLARIA_AGENT_PROMPT.md`). The important fixed
decisions it carries:

- **Platform.** Minecraft Java Edition 26.1.2 with Fabric, Java 25, LAN hosted
  by the teacher.
- **Ten classes.** `sun`, `earth`, `moon`, `mars`, `jupiter`, `venus`,
  `saturn`, `uranus`, `neptune`, `mercury`. All ids are lowercase and must
  match the model metadata byte for byte.
- **One dimension per class.** Ids are `solaria:<class>`. Each dimension holds
  one full-size sphere of that class centred at the origin.
- **Student mode.** Creative, flight only. No walkable terrain in this version.
- **AI boundary.** The mod never loads a model and never contains AI code. The
  AI service runs as a separate process on the teacher's machine, over
  loopback HTTP with a startup token.
- **Confidence.** Default threshold is `0.60`. Below the threshold the result
  is `unknown` and nothing is generated automatically.
- **Teacher authority.** A teacher decision always beats the model.
- **Privacy.** Everything runs locally. No cloud, no uploads, no student
  usernames in logs. Participant codes only.

## Phases (both repos share the numbering)

| # | Phase | Owner |
|---|---|---|
| 0 | Audit, reconciliation report, and webcam capture spike | Both repos |
| 1 | AI service: cleaning, quality checks, classify (mock first), bounded queue, packaging for the teacher machine | SolariaAI |
| 2 | Blueprint v2: `voxelAt`, new sizes, 69-block palette, determinism tests | SolariaMC |
| 3 | Ten per-class dimensions, travel and return, Sun-sized load numbers for 1, 10, and 30 students | SolariaMC |
| 4 | Scan item and GUI, webcam capture, chunked upload, mock classifier end to end | SolariaMC |
| 5 | Teacher permission, approval modes, review queue, override, reject, audit log | SolariaMC |
| 6 | Integration, docs, classroom QA checklist, real-model path ready | Both repos |

Each phase ends with a short report, the checks defined in the plan, and a
pause for human approval before the next phase begins.

