# SolariaAI: Docker and Teacher Guide

This guide explains container operation, teacher review, and SolariaAI's responsibilities. Commands use **Windows PowerShell**, with the checkout at **`D:/Solaria`**.

> **Current limits:** the default container uses MOCK predictions, not real drawing recognition. Minecraft capture/relay and teacher-decision integration code exists, but live camera/LAN behaviour and real-model accuracy still require validation. Generation, travel, and returning students to their original location are unfinished. A saved decision or `ready` acknowledgement is **not** proof of generation or travel.

## 1. Choose the correct container

| Configuration | Purpose |
| --- | --- |
| `D:/Solaria/SolariaAI/compose.yaml` | Long-running inference API and teacher console on port 8000 |
| `D:/Solaria/SolariaAI/compose.tests.yaml` | Automated synthetic validation; exits when finished and exposes no browser port |

Neither container automatically trains a model.

## 2. Prepare Docker

1. Start Docker Desktop in **Linux-container mode**.
2. Ensure Docker Compose is available and internet access permits dependency downloads during the first build.
3. Keep host port **8000** free. Stop an older native service if necessary.
4. For this setup, run the service and trusted Minecraft host on the same Windows machine. Do not expose port 8000 to students or the LAN.

```powershell
docker info --format '{{.OSType}}'
docker compose version
```

The first command should report `linux`. No host Python installation or GPU is required for the service.

## 3. Build and start the service

Run from any PowerShell working directory:

```powershell
docker compose -f D:/Solaria/SolariaAI/compose.yaml up -d --build --wait
docker compose -f D:/Solaria/SolariaAI/compose.yaml ps
```

`--build` packages the current code and web assets, `-d` leaves the container running, and `--wait` waits for readiness. Confirm a healthy service.

The default mode is **MOCK**: predictions are synthetic and normally return Earth, not genuine recognition. Run one service instance; reviews are in-process and not shared across replicas.

### Retrieve the private token

Current Compose generates a token inside the container at `/var/lib/solaria/startup-token.txt`. A named Docker credentials volume retains it across ordinary container replacement.

Read it into this PowerShell session:

```powershell
$env:SOLARIA_STARTUP_TOKEN = (docker compose -f D:/Solaria/SolariaAI/compose.yaml exec -T ai-service cat /var/lib/solaria/startup-token.txt).Trim()
```

Display it privately when ready to paste into the console:

```powershell
$env:SOLARIA_STARTUP_TOKEN
```

**Treat it as a password.** Do not share it with students, put it in URLs, commit it, or reveal it during screen sharing. Docker administrators can access it. Teacher and trusted host share this credential; there are no separate named reviewer accounts.

Do not generate an unrelated host token. Setting a PowerShell environment variable alone does not replace the service's stored credential; retrieve the credential the service actually uses.

Optional authenticated health check:

```powershell
Invoke-RestMethod -Uri 'http://127.0.0.1:8000/v1/health' -Headers @{ Authorization = "Bearer $env:SOLARIA_STARTUP_TOKEN" }
```

## 4. Connect the teacher console

1. Open **http://127.0.0.1:8000/** on the teacher's machine.
2. Paste the token into **Startup bearer token**, without `Bearer`.
3. Click **Connect**.
4. Check **Service overview**: mode, model, confidence threshold, queue, and classes.
5. Expect a synthetic-prediction warning in MOCK mode.

The feed refreshes every **3 seconds** while connected. **Refresh** requests an immediate update. An empty feed is normal until scans arrive: the page cannot capture, upload, create sample scans, or train a model.

The HTML page is public, but classroom APIs require authentication. Opening `/v1/health` directly in a browser tab sends no bearer header and may return `UNAUTHORIZED`.

## 5. Coordinate with Minecraft

Intended flow:

**Student camera in Minecraft → trusted host → SolariaAI → teacher review → host polling and student agreement.**

- Students use the in-game scanner, not the teacher web page.
- Only the trusted host sends authenticated API requests. Never give students the token.
- The host supplies pseudonymous participant IDs and binds scans to active sessions.
- The host HTTP client reads `SOLARIA_STARTUP_TOKEN` from its process environment. Launch through a process inheriting the retrieved token; an already-running game cannot acquire later environment changes.
- Verify the mod configuration and launcher separately. Starting Docker does not configure Minecraft or prove live camera/LAN integration works.

Another machine's or container's `127.0.0.1` is not this Windows host. Remote/dedicated-server networking requires separate review.

## 6. Review, approve, correct, or reject

1. Select a row under **Student scan results**.
2. Confirm the pseudonymous student/request identity before acting.
3. Inspect the thumbnail, original AI label/confidence, quality result/reason, and top predictions.
4. Compare **Effective classification** with the original AI prediction.
5. Read the override history and Minecraft acknowledgement.

Confidence is model certainty, not teaching correctness. Consider the student's intent and your own judgement.

### Approve or correct a quality-passing scan

1. Choose the intended **Correct classification**.
2. Set **Teacher decision** to **Approve / correct**.
3. Optionally add a note of up to 500 characters. Avoid personal information.
4. Click **Save teacher decision**.
5. Verify the effective classification and new history entry.
6. Wait for Minecraft acknowledgement; saving alone does not change the game.

Approval may retain the same label. Corrections preserve the original AI prediction and add a revision/history entry. A low-confidence result can be corrected if image quality passed.

### Reject or request a rescan

For a quality-passing but unsuitable submission, select **Reject — request rescan**, add a reason, and save. The form still requires a supported class selection; rejection does not approve that label.

If image quality **failed**, overrides are blocked, including rejection writes through this form. Ask the student to rescan using the quality hint. Do not bypass failed quality checks.

### Understand acknowledgement

| Status | Meaning |
| --- | --- |
| Not acknowledged | No host acknowledgement for this teacher revision has arrived |
| `waiting_student` | Host recorded the decision; student agreement is needed |
| `ready` | Host reports both approvals recorded; **not generation or travel confirmation** |
| `rejected` | Host acknowledged rejection |

A new teacher revision clears the previous service acknowledgement. In the game-side review flow, a changed label or rejection clears student agreement; approval after rejection requires agreement again.

If another console changed the record, select its row again, inspect the latest decision, and retry only if appropriate. `REVIEW_CONFLICT` prevents stale writes. Each record allows at most **20 overrides**; request a new scan after that limit.

## 7. End the lesson safely

1. Finish pending reviews and explain unresolved/rescan decisions to students.
2. Click **Disconnect**. This clears the page's token and classroom display, not server records.
3. Close the tab. Refreshing also clears the page token; it is not stored in browser storage.
4. Stop/remove the service when finished:

```powershell
docker compose -f D:/Solaria/SolariaAI/compose.yaml down
Remove-Item Env:SOLARIA_STARTUP_TOKEN -ErrorAction SilentlyContinue
```

**Retention rules:** the newest **200 scans**, thumbnails, decisions, and acknowledgements are memory-only. Records expire after **2 hours**, enforced on access. Restart, recreation, or shutdown discards all reviews. This is not a permanent audit system or dataset collector.

The credentials volume persists after ordinary `down`: the token survives, **reviews do not**. Do not add `--volumes` unless deliberately resetting pairing and prepared to update the trusted host's token.

## 8. Update, inspect, and troubleshoot

After source changes, rebuild using section 3 and reload the browser. Assets are baked into the image, not bind-mounted. Rebuilding/recreating loses reviews even though the token persists.

```powershell
docker compose -f D:/Solaria/SolariaAI/compose.yaml logs --tail 100 ai-service
docker compose -f D:/Solaria/SolariaAI/compose.yaml ps
```

| Problem | Action |
| --- | --- |
| Docker engine/pipe unavailable | Start Docker Desktop and wait for its Linux engine |
| Port 8000 already in use | Identify and stop the conflicting service; do not expose a LAN binding as a workaround |
| `UNAUTHORIZED` | Retrieve the current stored token again and reconnect; check that the host inherited the same value |
| Empty feed | Check actual game submission and host connectivity; the page never fabricates scans |
| Original AI label differs from effective label | Expected after a teacher correction; inspect history |
| `QUALITY_REJECTED` | Improve the capture according to its hint and rescan |
| `REVIEW_CONFLICT` | Reselect the row and review the newest revision |
| Record disappears / `NOT_FOUND` | It may have expired, been evicted by capacity, or been lost on restart; request a new scan |
| No Minecraft acknowledgement | Check host/session connectivity and polling; do not interpret a saved decision as game application |
| Healthy but MOCK | Expected with base Compose; a healthy process does not mean a real model is loaded |

An unhealthy status alone does not trigger Docker's restart policy. Health checks are read-only and do not create scans or prove Minecraft delivery.

## 9. Use an exported real model when available

This is an administrator/model-maintainer task, not a teacher-console action. No approved real model is included by default.

1. Prepare a validated export folder containing exactly named artifacts `model.onnx` and `model.onnx.metadata.json`.
2. Ensure metadata describes the supported classes and matching preprocessing.
3. Stop the base configuration before changing mode; this discards pending reviews.
4. Set the actual export-folder path and start with both Compose files:

```powershell
docker compose -f D:/Solaria/SolariaAI/compose.yaml down
$env:SOLARIA_MODEL_DIR = 'D:/Solaria/SolariaAI/runs/lesson'
docker compose -f D:/Solaria/SolariaAI/compose.yaml -f D:/Solaria/SolariaAI/compose.model.yaml up -d --build --wait
```

The example folder must already contain your real export. It is mounted read-only, not copied into the image. Missing/invalid artifacts fail startup instead of falling back silently to mock.

Retrieve the stored token and reconnect as before. Verify authenticated health reports **`mock: false`** and **`model_loaded: true`**. Real mode still requires evaluated drawing accuracy and camera-quality thresholds; loading a model is not sufficient evidence of classroom readiness.

Stop real mode using the same two configuration files:

```powershell
docker compose -f D:/Solaria/SolariaAI/compose.yaml -f D:/Solaria/SolariaAI/compose.model.yaml down
```

To return to mock, start the base configuration from section 3.

## 10. Run automated validation separately

After changes, build and run the disposable test container:

```powershell
docker compose -f D:/Solaria/SolariaAI/compose.tests.yaml build
docker compose -f D:/Solaria/SolariaAI/compose.tests.yaml run --rm ai-tests
$LASTEXITCODE
```

Exit code `0` means the runner succeeded. It checks dependencies, schemas, lint, the full Python suite, ONNX export/parity, and a live synthetic service workflow. It exercises review/override behaviour without real student data. It publishes no host ports and requires no manual teacher token.

This is **synthetic validation**, not proof of real-drawing accuracy, browser interaction, camera/LAN behaviour, or scan-to-planet success. Do not run synthetic smoke commands against a real model or during a real classroom session; smoke creates temporary synthetic reviews.

## 11. Other SolariaAI responsibilities

| Responsibility | What SolariaAI does | Teacher / maintainer responsibility |
| --- | --- | --- |
| Image preparation | Page/line-work cleaning and quality checks, with actionable failure hints | Help students improve framing, lighting, focus, and contrast; validate camera thresholds |
| Classification | ONNX inference, confidence, top predictions, acceptance and mock status | Review mistakes; do not mistake synthetic results for real recognition |
| Runtime contract | Authenticated health/classify/review/override/ack APIs and class metadata | Keep the host paired and supported class mapping consistent |
| Capacity control | Bounded processing queue, workers, request limits, and timeouts | Monitor availability; stagger retries rather than repeatedly flooding the service |
| Teacher decisions | Revision-safe approve/correct/reject history preserving the original prediction | Review the right participant, explain decisions, handle conflicts, and request rescans |
| Privacy | No raw-frame persistence; bounded volatile reviews/thumbnails; credential-only persistence | Protect the token and screen; avoid personal details in IDs/notes; obtain appropriate dataset consent separately |
| Offline model work | Dataset loading, training, evaluation, ONNX export and parity tooling | Curate labels/splits, evaluate real drawings, calibrate, and deploy approved artifacts |

Supported runtime classes currently include `sun`, `earth`, `moon`, `mars`, `jupiter`, `venus`, `saturn`, `uranus`, `neptune`, and `mercury`. Use the service's health response as the runtime source of truth; do not assume a fixed numeric class order.

### Offline training stays separate

- Source drawings may be organised in **`D:/Solaria/SolariaAI/TrainingDataset/<class>/`**.
- The documented training inputs are **`D:/Solaria/SolariaAI/data/train/<class>/`** and **`D:/Solaria/SolariaAI/data/val/<class>/`**. Prepare reviewed splits explicitly; the runtime container does not populate or convert these directories.
- Split by participant where appropriate, inspect duplicates/leakage and class balance, and assess held-out real drawings.
- Runtime reviews and corrections are **not** automatically saved as training samples or used for online learning. Dataset collection requires a separate approved process.
- The service image excludes training datasets, PyTorch, and training/export tooling. Deploy exported artifacts only after offline validation.

Minecraft owns camera capture, game networking, session/identity validation, student agreement, and any future generation/placement/travel. SolariaAI does not create dimensions or teleport students.

## 12. Quick teacher checklist

**Before:** Docker running → service healthy → correct mode verified → private token retrieved → console connected → host integration checked.

**During:** verify participant → inspect quality and predictions → approve/correct/reject → check acknowledgement → obtain rescan/student agreement when needed.

**After:** resolve pending cases → disconnect → close the console → shut down deliberately, knowing reviews are lost but pairing credentials remain.

**Repository note:** the existing `D:/Solaria/.gitignore` excludes Markdown files. This guide exists locally but will not appear in normal Git changes unless the repository's documentation tracking policy is changed.
