# SolariaMC In-Game Wiki (Oracle Index)

Last updated: 2026-10-15

This mod ships its wiki inside the jar so players can read it in game through
[Oracle Index](https://modrinth.com/mod/oracle-index) 2.0.0. This document
records the layout rules that were verified against the Oracle Index 2.0.0
classes, so the wiki is not "invisible in game" again.

## Two locations, one source of truth

| Location | Role |
|---|---|
| `wiki/` (repository root) | **Source of truth.** Edit pages here. |
| `src/main/resources/assets/oracle_index/books/solariamod/` | Generated copy, produced by the `syncWiki` task. Git-ignored — never edit it by hand. |

`syncWiki` is a Gradle `Sync` task, so it also **deletes** files that were removed
from `wiki/`. `processResources` depends on it, so `build`, `runClient` and
`runServer` all pick up the current wiki.

The book folder name **is** the wiki id and must equal the mod id (`solariamod`)
and the `id` field in `wiki/sinytra-wiki.json`.

## How Oracle Index decides what to show

Verified against `rearth.oracle.docs.DocsIndexer`, `rearth.oracle.docs.V1DocsFormat`
and `rearth.oracle.ui.OracleScreen` in `oracle-index-2.0.0.jar`:

1. Oracle lists every `assets/oracle_index/books/**/*.mdx` resource and takes the
   **second path segment** as the wiki id (`DocsIndexer.extractModid`).
2. It reads `books/<wiki-id>/sinytra-wiki.json`. If `"schema": "1"`, it uses
   `V1DocsFormat`; otherwise the legacy format.
3. `V1DocsFormat` puts **documentation pages under `docs/`** and reference pages
   under `content/`. Only those two folders become navigation tabs.
4. Navigation is built **only** from `_meta.json` files
   (`OracleScreen.buildNavigationEntries`).

## The `_meta.json` rules (the part that breaks easily)

`OracleScreen.parseJson` decides whether an entry is a page or a folder from its
**key alone**:

```java
boolean directory = !id.endsWith(".mdx");
```

Consequences:

- **Page keys must include the `.mdx` suffix.**
  `{"introduction.mdx": "Introduction"}` ✅
  `{"introduction": "Introduction"}` ❌ — Oracle treats it as a *folder*, looks
  for `docs/introduction/_meta.json`, logs
  `No _meta.json found for solariamod at oracle_index:books/solariamod/docs/introduction/_meta.json`
  and **drops the whole branch from the sidebar**.
- **Folder keys must NOT end in `.mdx`** and the folder must exist with its own
  `_meta.json`.
- A value may be a plain string (`"Introduction"`) or an object with a `name`
  field (`{"name": "Introduction", "icon": null}`). Both are read; the object
  form additionally supports icons.
- Every folder needs a `_meta.json`, and every page must be listed in its
  folder's `_meta.json`. An unlisted page is indexed for search but is
  unreachable in the navigation bar.
- `_homepage.mdx` is **not** read by Oracle Index 2.0.0 (the string `_homepage`
  does not appear in the jar). It is kept only for the web (moddedmc.wiki) view.

## Current layout

```
wiki/
├── sinytra-wiki.json      # {"schema":"1","id":"solariamod","modid":"solariamod", ...}
├── _meta.json             # book root
├── _homepage.mdx          # web-only landing page
└── docs/                  # -> Docs tab (V1DocsFormat.getDocsRoot(DOCS) == "/docs")
    ├── _meta.json
    └── introduction.mdx
```

The Docs/Content tab switcher only appears when a wiki has pages in both folders;
a wiki with only `docs/` shows just the Docs tab.

## Optional features

- **Item links:** add `related_items: [solariamod:some_item]` to a page's
  frontmatter to link it to in-game items. For `content/` pages, the page's `id`
  frontmatter field is matched to a registry item automatically.
- **Images:** schema-1 wikis expect images under `assets/<namespace>/` inside the
  book folder (`wiki/assets/solariamod/...`), not the legacy `.assets/`.
- **Unlocking:** a frontmatter `unlock: <type>:<value>` hides a page behind a
  progression condition (obfuscated in the sidebar until unlocked).

## Guard rails

`gradlew.bat wikiStructureTest` (`src/test/java/net/kyrangit/solaria/WikiStructureTests.java`)
encodes the rules above with 26 synthetic assertions. It fails the build when:

- `sinytra-wiki.json` is missing `"schema": "1"` or its `id`/`modid` drift;
- a `_meta.json` key without `.mdx` does not resolve to a real folder;
- a `_meta.json` page key does not resolve to a real file (the original bug);
- a page exists but is not listed in any `_meta.json`;
- the synced book under `src/main/resources/` is missing or differs from `wiki/`.

The task is wired into `gradlew.bat test` and `gradlew.bat build`.

## Refreshing the wiki in game

There is no hot-reload. After editing `wiki/`, run the build (or `syncWiki`) and
use a resource reload in game to refresh the content.

## Known limitation

Pages live under `docs/`. If reference pages are added later, put them under
`wiki/content/` (with its own `_meta.json`), which enables the Content tab
automatically.
