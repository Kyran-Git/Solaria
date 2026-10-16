# Oracle Index: Ingame Wiki Setup for Mods

This document teaches an AI agent how to lay out a mod's wiki so it is loaded by the **Oracle Index** mod and viewable ingame.

## Purpose

Oracle Index is a Minecraft mod that displays mod wikis ingame. Developers who want their wiki accessible through it must place the wiki content in a specific resource path inside their mod project.

## Setup Steps

1. **Create the wiki content layout as normal**, following the instructions of moddedmc.wiki (directories of content pages organized by topic).
2. **Add `_meta.json` files** with entries for **all** content. Oracle Index reads the `_meta.json` files to build the ingame navigation bar. Content without a `_meta.json` entry will not appear in navigation.
3. **Copy the entire wiki into the mod's resources folder.** This can be automated with a Gradle sync task (the original page links to an "auto gradle sync" guide). The required destination path is:

   ```
   resources/assets/oracle_index/books/<your-mod-id>/
   ```

4. **Oracle Index loads the mod's resources automatically.** No extra registration code is needed once the files are at the correct path.

## Required Path

```
src/main/resources/assets/oracle_index/books/<your-mod-id>/
```

- `<your-mod-id>` must be replaced with the mod's actual mod ID (for example, `oritech`).
- The whole wiki lives inside this folder.

## CRITICAL: `_meta.json` keys must end in `.mdx` for pages

Verified against Oracle Index 2.0.0 (`rearth.oracle.ui.OracleScreen#parseJson`):

```java
boolean directory = !id.endsWith(".mdx");
```

Oracle decides whether a key is a **page** or a **folder** from the key alone.

- A page key **must** include the extension: `{"introduction.mdx": "Introduction"}` ✅
- A key without it is treated as a **folder**: `{"introduction": "Introduction"}` ❌
  Oracle then looks for `docs/introduction/_meta.json`, logs
  `No _meta.json found for <modid> at oracle_index:books/<modid>/docs/introduction/_meta.json`
  and **removes that entire branch from the sidebar** — the page is invisible in game.
- Folder keys must **not** end in `.mdx`, and the folder must exist with its own `_meta.json`.
- Values may be a plain string, or an object with a `name` (and optional `icon`) field.
- Every page must be listed in its folder's `_meta.json`, or it is unreachable in navigation.

Also note: `_homepage.mdx` is **not** read by Oracle Index 2.0.0 (the string
`_homepage` does not occur in the jar). It is only meaningful for the web view.

### Which folders become tabs

Oracle only builds navigation for the two roots defined by the wiki format:

- `docs/` → the **Docs** tab
- `content/` → the **Content** tab

The tab switcher appears only when a wiki has pages in both folders.

### Assets

For schema-1 wikis (`"schema": "1"` in `sinytra-wiki.json`), images live in
`assets/<namespace>/` inside the book folder. The legacy `.assets/` and
`.assets/item/<namespace>/` folders are only for legacy wikis and are unnecessary,
because a registry item ID renders using its in-game model.

## Example Directory Tree (mod ID: `oritech`)

```
resources
└── main
    └── assets
        ├── emi
        ├── minecraft
        └── oracle_index
            └── books
                └── oritech              <- <your-mod-id>
                    ├── .assets
                    ├── .translated
                    ├── arcane
                    ├── getting_started
                    ├── interaction
                    ├── logistics
                    ├── processing
                    ├── resources
                    └── ... (more folders may follow)
```

### Notes on the example

- ⚠️ **This example is a *legacy-format* wiki, not a schema-1 wiki.** The absence of
  `sinytra-wiki.json` with `"schema": "1"`, plus the `.assets`/`.translated` folders and
  the flat topic folders at the book root, are all legacy traits. Oracle falls back to
  `LegacyDocsFormat` in that case, where the docs root is the book root (`""`) and
  reference pages live in `/.content`.
- A **schema-1** wiki (what Solaria uses) instead has `sinytra-wiki.json` with
  `"schema": "1"`, puts guides under `docs/` and reference pages under `content/`,
  and stores images in `assets/<namespace>/`. Do not mix the two.
- `emi` and `minecraft` are unrelated sibling asset folders that happen to exist in this project. Only `oracle_index` matters for the wiki.
- `.assets` and `.translated` are legacy dot-prefixed folders at the root of the book. They appear to hold shared assets (images, etc.) and translated versions of the wiki, respectively. This is inferred from the folder names, not stated on the original page.
- The other folders (`arcane`, `getting_started`, `interaction`, `logistics`, `processing`, `resources`) are content sections. Each one is a topic category of the wiki, and each should be covered by a `_meta.json` entry.
- The listing in the source screenshot was cut off, so the full set of folders is unknown.

## Rules for an Agent Setting This Up

- Place the wiki under `assets/oracle_index/books/<mod-id>/`, never elsewhere.
- Give the book folder exactly the mod id, and keep `sinytra-wiki.json`'s `id` and
  `modid` in agreement with it.
- For schema-1 wikis, set `"schema": "1"` and put pages under `docs/` (guides) and
  `content/` (block/item reference). Those two folders are the only navigation roots.
- Ensure every content file and folder has a corresponding entry in a `_meta.json`.
- **List page keys with their `.mdx` extension and folder keys without it** — Oracle
  infers page-vs-folder from the key alone, and a wrong key silently deletes the branch.
- Keep the wiki structure identical to the moddedmc.wiki layout; only the destination path changes.
- Prefer automating the copy with a Gradle `Sync` task so the in-repo wiki and the resources folder stay in sync and deleted pages disappear.
- Do not hardcode a mod ID from the example. Use the project's real mod ID.

## Quick Checklist

- [ ] Wiki follows the moddedmc.wiki layout
- [ ] `sinytra-wiki.json` present, `"schema": "1"`, `id`/`modid` match the book folder name
- [ ] `_meta.json` exists in **every** folder, with entries for all content
- [ ] Page keys end in `.mdx`; folder keys do not and point at real folders
- [ ] Every page is listed in a `_meta.json` (else it is unreachable in the sidebar)
- [ ] Wiki copied to `resources/assets/oracle_index/books/<mod-id>/`
- [ ] Gradle `Sync` automation set up (optional but recommended)
- [ ] Mod ID in the path matches the actual mod ID
