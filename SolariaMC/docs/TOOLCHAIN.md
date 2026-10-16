# SolariaMC Toolchain

Last updated: 2026-10-15 (M0 audit corrected during M1 validation)

## Versions

| Component | Version | Source |
|---|---|---|
| Minecraft | `26.1.2` | `gradle.properties` |
| Fabric Loader | `0.19.3` (declared min), `0.19.5` (resolved at runtime) | `gradle.properties`, `run/logs/latest.log` |
| Fabric Loom | `1.17-SNAPSHOT` (resolves to `1.17.21`) | `gradle.properties`, Gradle configure output |
| Fabric API | `0.155.2+26.1.2` | `gradle.properties` |
| Gradle | `9.8.0` | `gradle/wrapper/gradle-wrapper.properties` |
| Java | `25` (toolchain-enforced; JDK at `C:\Program Files\Java\jdk-25`) | `build.gradle`, `javaENV.md` |
| Java compile release | `25` | `build.gradle` (`options.release = 25`) |
| Oracle Index | `2.0.0` (recommended, not required) | `gradle.properties`, `fabric.mod.json` |
| Architectury API | `20.1.17` (required by Oracle Index) | `gradle.properties` |

## Mappings

Minecraft 26.1.2 ships unobfuscated, with **Mojang-style names**. There is no
explicit `mappings` block in `build.gradle`, which uses the non-remapping
`net.fabricmc.fabric-loom` plugin.

Evidence: the existing code imports `net.minecraft.resources.Identifier` (Mojang
name), not `net.minecraft.util.Identifier` (Yarn name). The runtime log says
`Mappings not present!`, which is expected for this unobfuscated version. The
previous claim about intermediary runtime remapping was not applicable here.

All new code **must** use Mojang mapping names. Do not copy class/method names
from Yarn-based tutorials or older Fabric documentation without verifying them
against the actual 26.1.2 decompiled sources.

## Gradle tasks

| Task | Status | Notes |
|---|---|---|
| `gradlew.bat build` | verified | Java 25 build passes, including 112 synthetic assertions via `foundationTest` and 26 wiki-structure assertions via `wikiStructureTest`. |
| `gradlew.bat test` | wired through `foundationTest` + `wikiStructureTest` | Standard test engine is `NO-SOURCE`; dependency-free Java tests execute before it. See M1 status for run evidence. |
| `gradlew.bat runClient` | awaiting human action | Task exists; no GUI observation claimed. Earlier documentation overstated verification. |
| `gradlew.bat runServer --args="nogui"` | partial smoke check | Fabric loaded the mod and created config; Minecraft exited at the EULA gate. No running world or command behavior verified. |
| `gradlew.bat runDatagen` | unverified | Task exists; not executed in this continuation. |
| `syncWiki` | ✅ works | `Sync` task: mirrors `wiki/` into `src/main/resources/assets/oracle_index/books/solariamod/` and deletes pages removed from `wiki/`. Runs before `processResources`. The generated folder is git-ignored; `wiki/` is the source of truth. |
| `gradlew.bat wikiStructureTest` | ✅ works | 26 synthetic assertions over the Oracle Index book (see `docs/WIKI.md`). Fails the build when a page is missing from a `_meta.json`, listed without its `.mdx` suffix, or not synced. |

## Build output

The mod jar is produced at `build/libs/solariamod-<version>.jar`. Current
version: `1.0.0`.

## Notable 26.1.2 specifics

1. **Java 25 required.** Minecraft 26.1.2 will not start on older JDKs. The
   Gradle toolchain ensures compilation and runtime use JDK 25 even if Gradle
   itself runs on another JDK (the machine's `JAVA_HOME` points to Temurin 21).

2. **Fabric Loader ≥0.19.3.** The `fabric.mod.json` declares `>=0.19.3`; at
   runtime Fabric resolved `0.19.5`. This is fine — the constraint is a minimum.

3. **No Yarn mappings.** Minecraft 26.1.2 is unobfuscated and uses Mojang names.
   API names from older Fabric tutorials (which used
   Yarn) may differ. Always verify against the actual decompiled sources in the
   IDE.

4. **Oracle Index and Architectury.** These are included for the in-game wiki
   viewer. Oracle Index is declared as `recommends` (optional at runtime) in
   `fabric.mod.json`. Architectury is a transitive requirement of Oracle Index.

5. **`syncWiki` task.** A custom Gradle `Sync` task mirrors the `wiki/`
   directory into the resource tree so Oracle Index can find the pages at runtime.
   Because it is a `Sync` (not `Copy`), pages deleted from `wiki/` also disappear
   from the generated book, so the in-game wiki cannot serve stale pages. The
   generated `src/main/resources/assets/oracle_index/` folder is git-ignored;
   `wiki/` is the single source of truth. See `docs/WIKI.md` for the layout rules.

## Entrypoints (current)

| Entrypoint | Class | Purpose |
|---|---|---|
| `main` | `net.kyrangit.solaria.SolariaMod` | Config initialization and command registration |
| `client` | `net.kyrangit.solaria.client.SolariaClient` | Client-only boundary in `src/client/java` |
| `server` | `net.kyrangit.solaria.SolariaServer` | Dedicated-server boundary; integrated servers use common initialization |
| `fabric-datagen` | `net.kyrangit.solaria.SolariaModDataGenerator` | Data generation (currently empty) |

Loom uses split environment source sets. HTTP/UI code belongs exclusively in
the client source set; no service calls or UI are implemented in M1.

## Existing dependency caveat

The server smoke launch reports Oracle Index 2.0.0 nested dependencies including
DJL and ONNX Runtime. These are pre-existing wiki-mod transitive dependencies,
not new M1 dependencies and not used by Solaria's classification code (none
exists in the mod). A strict ban on ML libraries anywhere in the runtime would
require human review of the existing Oracle Index choice. Its behavior and
network/privacy properties have not been audited here.

## Mixins (current)

`ExampleMixin` injects a no-op at the head of `MinecraftServer.loadLevel()`.
This is template scaffolding and will be replaced or removed in later phases.
