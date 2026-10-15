# Foundation configuration (M1)

The common entrypoint creates `config/solariamod.json` in the game directory.
Edit it as UTF-8 JSON, then run `/solaria config reload`. Never commit a local
configuration. Missing fields receive defaults; unknown fields, duplicate fields,
nulls, wrong types, malformed JSON, and invalid ranges are rejected. Files over
64 KiB are rejected. This fixed parser safety bound is not a gameplay setting.

| JSON field | Default | Validation / meaning |
|---|---|---|
| `service_url` | `http://127.0.0.1:8000` | HTTP(S) base URL; literal loopback `127.0.0.1` or `[::1]` only; port 1–65535 if supplied; no credentials, path beyond `/`, query, or fragment |
| `connect_timeout_ms` | 3000 | Integer 1–300000 |
| `request_timeout_ms` | 30000 | Integer 1–300000, at least the connection timeout |
| `teacher_permission_level` | 2 | Integer 0–4; 0 intentionally permits everyone, so keep 2 or higher for classroom use |
| `allow_manual_label_on_low_confidence` | true | Boolean; used by future review UI |
| `blocks_per_tick` | 256 | Integer 1–4096; future placement batch size |
| `build_anchor_offset` | `{"x":0,"y":0,"z":5}` | World-axis integer offsets, each -1024–1024; missing axes use defaults |
| `max_size_blocks` | 32768 | Integer 1–1000000; maximum occupied voxel count, not edge length |
| `blocks_per_unit` | 1.0 | Finite number 0.01–64; future generator scale |
| `scan_from_file_enabled` | false | Boolean; future optional client-side file upload |

Ranges are fixed validation guardrails, not substitutes for configurable settings.
No service requests, file uploads, or placement happen in M1. Literal loopback URLs
avoid DNS resolution and accidental remote transmission. The port default is a
configurable foundation choice, not a claim that a service is listening there.

## Commands and failure handling

`/solaria` and `/solaria status` report foundation readiness, configuration errors,
and explicitly state that service health is not checked until M3. All foundation
commands require the current configured teacher permission. The 26.1.2 API is
`source.permissions().hasPermission(new Permission.HasCommandLevel(...))`.
Checks happen at execution, so reloading a permission change takes effect at once.
Denied calls return `PERMISSION_DENIED` with operator guidance.

`/solaria config reload` validates the whole file before publishing an immutable
snapshot. On failure it returns `INPUT_INVALID` and an actionable message, keeps
the previous valid snapshot, and does not overwrite the invalid file. Invalid
startup uses safe defaults and exposes the error through status. The log contains
only `reason_code=INPUT_INVALID`; use the command for detailed diagnostics.

On a dedicated server, commands reload **server** config, never a remote client's
local config. In single-player, client and integrated server share the common
snapshot. Restart a multiplayer client to reload its local configuration in M1;
client-side service/UI lifecycle belongs to M3/M4. No client-supplied config is
trusted by the server.

## Package boundaries

All packages begin with `net.kyrangit.solaria`:

| Source set | Package | Responsibility |
|---|---|---|
| main | root | Common and dedicated-server entrypoints |
| main | `config` | Validated JSON configuration and atomic snapshot replacement |
| main | `logging` | Typed reason codes; logger has no arbitrary-text or throwable API |
| main | `command` | Server-authoritative commands (also integrated server) |
| main (M2) | `blueprint` | Deterministic voxel generation |
| main (M5) | `placement`, `network` | Placement and validated client/server packets |
| client | `client` | Client entrypoint |
| client (M3/M4) | `net`, `screen` | HTTP service client, screens, textures, input |

Future packages are documented rather than filled with empty placeholder classes.
Loom splits the main/client source sets; common code cannot import client classes.
The logging helper currently accepts only a `ReasonCode` enum. There is deliberately
no raw string, URL, file path, image, player name, or exception logging overload.
Session telemetry can be added with constrained types when sessions exist.

## Automated tests

Run `gradlew.bat test` or `gradlew.bat build` from the mod directory. Both invoke
`foundationTest`, a Java 25 executable test runner using shipped Gson and JDK
assertion failures, without JUnit or new dependencies. Gradle's standard `test`
engine has no engine tests; the `foundationTest` result is the meaningful result.
The runner covers defaults, all fields, bounds, strict JSON/types, local URLs,
round trips, file creation, invalid startup, reload rollback/recovery, size limits,
I/O failures, and the constrained logging API. All are **plumbing check (synthetic)**.