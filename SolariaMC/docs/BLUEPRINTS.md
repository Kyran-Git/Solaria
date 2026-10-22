# Blueprints (M2, revised)

This file documents the Solaria blueprint pipeline: the voxel generators that turn an
`AstronomyClass` id into a deterministic, palette-safe sphere that the mod can build in
the world.

> **Revision note.** The first M2 draft used 11 to 19 block planets, stored as dense
> grids and placed in the overworld. That is far too small for a student to explore.
> This revision switches to full-size spheres that students fly around in creative mode,
> built in one dedicated space dimension. Walkable landing dimensions are future work.

> No live world yet. M2 is still pure data: it compiles, it runs under `./gradlew test`,
> and it writes human-reviewable preview PNGs under `build/previews/`. World placement
> and the dimension itself are M3.

## Scope decision

- **Flight only.** Students are in creative mode and fly. Normal flight is about
  10.92 blocks per second and sprint-flying is about 21.6.
- **One space dimension.** Every planet is built in a single dedicated dimension
  (`solaria:space`), not in the overworld and not in one dimension per planet.
- **Landing dimensions are future work.** Walking on a planet surface with its own
  terrain and gravity is out of scope for this milestone.

Why not the overworld: it is only 384 blocks tall (y -64 to 319), and the planets below
are up to 1,001 blocks across. A custom dimension can be much taller. Its top build
height (`min_y + height - 1`) can be at most 2031.

## Planet sizes

Sizes are compressed, not true scale. The order matches the real order of the bodies.
Every diameter is odd so there is always a centre block. Lap times are one trip around
the equator.

| Class   | Base diameter | Lap, normal flight | Lap, sprint-flying | Crust voxels (3 thick) |
|---------|--------------:|-------------------:|-------------------:|-----------------------:|
| pluto   | 301           | ~1.4 min           | ~0.7 min           | 0.84M |
| moon    | 351           | ~1.7 min           | ~0.9 min           | 1.14M |
| mars    | 421           | ~2.0 min           | ~1.0 min           | 1.65M |
| venus   | 561           | ~2.7 min           | ~1.4 min           | 2.93M |
| earth   | 571           | ~2.7 min           | ~1.4 min           | 3.04M |
| neptune | 701           | ~3.4 min           | ~1.7 min           | 4.59M |
| uranus  | 721           | ~3.5 min           | ~1.8 min           | 4.86M + ring |
| saturn  | 861           | ~4.1 min           | ~2.1 min           | 6.94M + ring |
| jupiter | 951           | ~4.6 min           | ~2.3 min           | 8.47M |
| sun     | 1001          | ~4.8 min           | ~2.4 min           | 9.39M |

A lap is only one pass. A student who loops around and stops to look at features will
spend longer than that on each body.

Crust voxel counts use `0.5236 * (d^3 - (d - 6)^3)` for a 3-block crust. The ring
estimate assumes a ring spanning 1.2 to 2 planet radii and one block thick, which adds
roughly 1.5M voxels for Saturn and 1.0M for Uranus.

## Space dimension

| Setting | Proposed value | Reason |
|---|---|---|
| Dimension id | `solaria:space` | One shared build site for all ten classes. |
| `min_y` | `-768` | Room for the largest vertical extent below the centre. |
| `height` | `1536` | Top build height is 767, well under the limit of 2031. |
| World content | empty (void) | No terrain to clear, no ground, no footprint limit. |
| Planet anchors | one fixed anchor per class, at least 4,000 blocks apart | Planets and rings never overlap. |

The vertical range comes from the Sun (radius 500) and from Uranus, whose near-vertical
ring reaches about 720 blocks above and below the centre if its outer radius is twice the
planet radius. Keep `min_y` and `height` multiples of 16.

The dimension ships inside the mod as datapack JSON (a dimension type plus a dimension
file). Sky, time of day, and ambient light are visual choices for M3. The requirement is
only that a student can see every planet clearly.

## `blueprint_v2` payload

Planets are no longer stored as dense grids. A blueprint is now a small descriptor, and
voxels are computed on demand from their position. This is what makes million-voxel
planets practical, because the mod can generate chunks as the student flies near them.

| Field            | Type               | Meaning |
|------------------|--------------------|---------|
| `astronomyClass` | `AstronomyClass`   | One of the ten classes. Identifies the generator and its seed. |
| `palette`        | `MaterialPalette`  | The single shared palette (`Palette.create()`). |
| `blocksPerUnit`  | `double`           | The scale applied to the base diameter. |
| `diameter`       | `int`              | Primary-body diameter in blocks, after scaling. Always odd. |
| `ring`           | `RingSpec` or none | Ring inner radius, outer radius, thickness, and tilt, for saturn and uranus. |

The key operation is `voxelAt(x, y, z)`, which takes coordinates relative to the planet
centre and returns a palette index (`0` is air). A helper can still copy a small region
into a `VoxelGrid` for previews and tests.

The compact constructor rejects non-finite or non-positive scales and non-positive
diameters, so a `Blueprint` always describes a buildable structure.

`Blueprint.hasSameVoxels(Blueprint)` stays as the determinism oracle. It now compares
class, diameter, ring, and a hash of a fixed set of sampled regions instead of a full
byte snapshot.

Palette growth and the new payload change the on-disk contract, so this is `blueprint_v2`.
No world data exists yet, so no migration is needed.

## Determinism rules

The generators in `BlueprintGenerator` and `BlueprintShapes` must give the same voxel at
the same position on every machine. Rules the code follows:

1. **No `java.util.Random`.** All randomness comes from `VoxelNoise` (SplitMix32-finalised
   integer hashes and trilinear value noise).
2. **`voxelAt` is a pure function.** The result depends only on the class seed, the
   diameter, ring settings, and the local `x, y, z`. It keeps no state and reads nothing
   else. This replaces the old fixed iteration order rule, because chunks can now be
   generated in any order and must still agree.
3. **No iteration-order dependence on hash maps.** `EnumMap` is used for the per-class
   seeds and base diameters, both populated in a `static` block.
4. **Per-class 32-bit seed.** Seeds are literal constants in `BlueprintGenerator`.
   Changing a seed changes every voxel for that class, which is why the tests compare
   two consecutive generations.

## Scale and size limits

One config value drives scale (`SolariaConfig`):

- `blocksPerUnit` (default `1.0`): multiplier on the base diameter. Real builds use `1.0`.
  Tests use small values such as `0.05` so they run in milliseconds.

`BlueprintGenerator.generate(cls, blocksPerUnit)` does this:

1. Compute an odd diameter from the class's base diameter times `blocksPerUnit`.
2. Work out the full bounding box, including any ring.
3. If the box is taller than the dimension (`maxExtentBlocks`, default `1536`), raise
   `BlueprintException(ReasonCode.CAP_EXCEEDED)`.

The old behaviour of halving the scale until the voxel count fit under `maxSizeBlocks` is
removed. The size is the whole point of this revision, so a planet is either built at the
requested size or rejected, never silently shrunk. `maxSizeBlocks` is no longer used.

Invalid inputs (null class, non-finite scale) still raise
`BlueprintException(ReasonCode.INPUT_INVALID)`.

## Palette

The 69-block hardcoded palette lives in `Palette.create()`. It is validated by
`MaterialPalette.Builder` so no gravity blocks, fluids, fires, torches, or
support-dependent blocks can enter it. Index `0` is implicit air and indices `1..69` are
the swatches below. The blocks are chosen for Minecraft 26.1.2.

| Idx | Block id                              | Used for |
|-----|---------------------------------------|----------|
| 1   | `minecraft:glowstone`                 | sun core |
| 2   | `minecraft:gold_block`                | sun surface, venus / saturn accent |
| 3   | `minecraft:yellow_concrete`           | sun corona, venus clouds |
| 4   | `minecraft:blue_concrete`             | earth ocean, neptune body |
| 5   | `minecraft:green_concrete`            | earth land |
| 6   | `minecraft:snow_block`                | earth polar ice |
| 7   | `minecraft:iron_block`                | earth / mars core |
| 8   | `minecraft:light_gray_concrete`       | moon surface, saturn ring edge |
| 9   | `minecraft:gray_concrete`             | moon mare, uranus shading |
| 10  | `minecraft:stone`                     | moon core, fallback |
| 11  | `minecraft:red_terracotta`            | mars surface |
| 12  | `minecraft:white_concrete`            | polar caps, clouds, saturn band, pluto heart |
| 13  | `minecraft:orange_concrete`           | sun / jupiter / saturn bands |
| 14  | `minecraft:brown_concrete`            | jupiter dark band |
| 15  | `minecraft:red_concrete`              | great red spot, neptune storm |
| 16  | `minecraft:diamond_block`             | gas-giant core, uranus body |
| 17  | `minecraft:black_concrete`            | reserved shadow / backdrop |
| 18  | `minecraft:light_blue_concrete`       | uranus body, neptune streak |
| 19  | `minecraft:quartz_block`              | saturn ring, uranus ring |
| 20  | `minecraft:orange_terracotta`         | mars dust, venus and jupiter warm bands |
| 21  | `minecraft:yellow_terracotta`         | saturn body, venus haze, jupiter pale zones |
| 22  | `minecraft:terracotta`                | mars plains, jupiter and saturn tan bands, earth desert |
| 23  | `minecraft:brown_terracotta`          | mars dark regions, jupiter belts, pluto dark terrain |
| 24  | `minecraft:white_terracotta`          | jupiter cream zones, saturn pale bands, pluto cream |
| 25  | `minecraft:smooth_sandstone`          | venus body, saturn pale gold |
| 26  | `minecraft:end_stone`                 | venus cream clouds |
| 27  | `minecraft:andesite`                  | moon highlands |
| 28  | `minecraft:tuff`                      | moon crater shading |
| 29  | `minecraft:smooth_stone`              | moon bright highlands |
| 30  | `minecraft:calcite`                   | saturn ring gaps, moon bright craters |
| 31  | `minecraft:cyan_concrete`             | uranus body, neptune accent |
| 32  | `minecraft:packed_ice`                | uranus pale atmosphere |
| 33  | `minecraft:lapis_block`               | neptune deep blue |
| 34  | `minecraft:blue_terracotta`           | neptune dark spot, earth deep shadow |
| 35  | `minecraft:green_terracotta`          | earth forest and land variation |
| 36  | `minecraft:moss_block`                | earth vegetation |
| 37  | `minecraft:lime_concrete`             | earth grassland, bright vegetation |
| 38  | `minecraft:lime_terracotta`           | earth dry plains, olive hills |
| 39  | `minecraft:prismarine`                | earth shallow ocean, uranus teal tint |
| 40  | `minecraft:dark_prismarine`           | earth deep ocean, neptune deep shadow |
| 41  | `minecraft:blue_ice`                  | uranus pale ice-blue, neptune streak, earth glacier |
| 42  | `minecraft:light_blue_terracotta`     | neptune haze, uranus muted shading |
| 43  | `minecraft:cyan_terracotta`           | uranus shading, earth muted coast |
| 44  | `minecraft:oxidized_copper`           | uranus teal-green tint, earth lagoon |
| 45  | `minecraft:red_sandstone`             | mars rock |
| 46  | `minecraft:smooth_red_sandstone`      | mars smooth plains |
| 47  | `minecraft:granite`                   | mars rusty highlands |
| 48  | `minecraft:packed_mud`                | mars dust, jupiter tan zones, pluto tan terrain |
| 49  | `minecraft:mud_bricks`                | mars and jupiter tan-brown bands |
| 50  | `minecraft:netherrack`                | mars dark rock, canyon shadow |
| 51  | `minecraft:red_nether_bricks`         | mars deep canyon |
| 52  | `minecraft:raw_iron_block`            | mars rust dust, jupiter peach bands |
| 53  | `minecraft:raw_copper_block`          | mars orange-brown, jupiter warm belts |
| 54  | `minecraft:raw_gold_block`            | venus golden haze, saturn dull gold |
| 55  | `minecraft:honeycomb_block`           | venus amber haze, sun corona |
| 56  | `minecraft:ochre_froglight`           | sun pale corona, venus bright cloud tops |
| 57  | `minecraft:shroomlight`               | sun granulation |
| 58  | `minecraft:sea_lantern`               | reserved (was solar eclipse corona) |
| 59  | `minecraft:end_stone_bricks`          | saturn cream bands, venus clouds, pluto pale patches |
| 60  | `minecraft:smooth_quartz`             | saturn ring bright |
| 61  | `minecraft:diorite`                   | moon highlands |
| 62  | `minecraft:light_gray_terracotta`     | moon dusty regolith, pluto dusty terrain |
| 63  | `minecraft:gray_terracotta`           | saturn ring gap |
| 64  | `minecraft:deepslate`                 | reserved (was mercury dark crater) |
| 65  | `minecraft:smooth_basalt`             | moon dark mare |
| 66  | `minecraft:blackstone`                | reserved shadow / backdrop alternative |
| 67  | `minecraft:coal_block`                | reserved (was solar eclipse disc) |
| 68  | `minecraft:copper_block`              | reserved (was satellite body) |
| 69  | `minecraft:redstone_block`            | reserved (was rocket nose) |

Notes on the palette:

- **Stable indices.** Entries `1..19` keep their original ids and numbers. New entries are
  appended, so existing numbers never shift.
- **Cyan is no longer approximated.** `cyan_concrete` (31) gives uranus a real cyan, so the
  old "cyan proxy" label on index 18 is gone.
- **Tan is no longer approximated.** Plain `terracotta` (22) is the dedicated tan swatch, so
  the old "colour gap" section no longer applies.
- **Reserved entries.** Indices 58, 64, 66, 67, 68, and 69 were added for classes that are no
  longer in the set (mercury, rocket, satellite, and the two eclipses). Because no world data
  exists yet, they can simply be dropped when `blueprint_v2` is cut. If kept, mark them
  reserved so no generator uses them by accident.
- **Safety rules unchanged.** No sand, red sand, gravel, or concrete powder. All entries are
  full opaque cubes that stay in place when nothing supports them. Confirm that the
  `MaterialPalette.Builder` accepts the light-emitting entries (glowstone, shroomlight,
  ochre froglight, sea lantern) and `redstone_block`.
- **Matching.** Do nearest-colour matching in Lab colour space rather than plain RGB. It
  separates the many terracotta shades much better.

### Per-class palette recipes

Use only these blocks for each class so nearest-colour matching stays clean.

| Class   | Main blocks (idx) | Accent blocks (idx) |
|---------|-------------------|---------------------|
| sun     | 1, 2, 3, 13 | 57, 56, 55 |
| earth   | 4, 39, 5, 37 | 40, 36, 35, 38, 22, 12, 6 |
| moon    | 8, 9, 10, 29 | 27, 61, 28, 62, 30, 65 |
| mars    | 11, 45, 22, 20 | 46, 47, 48, 50, 51, 23, 12 |
| jupiter | 24, 22, 20, 13 | 23, 14, 15, 48, 52, 53 |
| venus   | 25, 56, 55, 21 | 59, 26, 54, 20 |
| saturn  | 21, 25, 54, 59 | 24, 22, 2 (body), 19, 60, 30, 8 (rings), 63 (ring gap) |
| uranus  | 41, 32, 16, 18 | 31, 44, 43, 9, 19 (ring) |
| neptune | 4, 33, 34, 42 | 31, 40, 41, 15 (storm), 12 (clouds) |
| pluto   | 48, 62, 24 | 23, 59, 12 (heart-shaped plain) |

## Per-class notes

| Class   | Base diameter | Features |
|---------|--------------:|----------|
| sun     | 1001          | gold shell, glow and granulation, concentric corona |
| earth   | 571           | ice caps, land and ocean noise, upper clouds |
| moon    | 351           | maria noise, craters |
| mars    | 421           | polar caps, dark patches on red terracotta |
| jupiter | 951           | horizontal bands, equator-pinned Red Spot |
| venus   | 561           | pale yellow swirled cloud layer |
| saturn  | 861 + ring    | banded body, flat equatorial `quartz_block` ring |
| neptune | 701           | latitudinal streaks, dark storm spot |
| uranus  | 721 + ring    | light-blue body, near-vertical ring |
| pluto   | 301           | proposed: tan and brown terrain with a pale heart-shaped plain (not yet implemented) |

Two things need rework because the planets are now about 30 times wider:

- **Feature scales.** Noise frequency, crater count and radius, band height, and Red Spot
  size were all tuned for 11 to 19 block planets. Express each as a fraction of the diameter
  so a planet looks right at any size.
- **Cores.** The old glow, iron, and diamond cores sit inside a hollow 3-block crust and
  would never be seen. Either drop them or use them to colour inner crust layers. A
  cutaway that shows the layers is a good later feature.

The ringed planets extend well past the planet body, so their bounding box is much larger
than the diameter. Ring voxels are counted in the voxel estimates above.

## Tasks

| Gradle task         | What it does |
|---------------------|--------------|
| `blueprintTest`     | Runs `net.kyrangit.solaria.BlueprintTests`. The assertion count will change with this revision. Tests use a small `blocksPerUnit` so they stay fast. |
| `blueprintPreview`  | Writes a small set of PNG slices for every class (equator, a few latitudes, and the poles) under `build/previews/<class>/`, sampled through `voxelAt`. |
| `test`              | Depends on `foundationTest`, `wikiStructureTest`, and `blueprintTest`. |

The preview task is not part of `test`, so CI stays cheap. It no longer writes every slice,
because a 1,001-wide planet would produce about a thousand images per class.

## Future work

- **Landing dimensions.** One walkable dimension per rocky body (moon, mars, earth, venus,
  pluto) with its own terrain. Per-planet gravity would be set per planet on arrival.
  Minecraft has a gravity attribute for entities, so check that it behaves as expected in
  26.1.2.
- **Cutaway interior.** A removed wedge that reveals the core, mantle, and crust.
- **Gas giants and the Sun** stay flight-only, since they have no surface to land on.

## Assumptions to verify before coding

1. **`voxelAt` is feasible.** The noise is hash-based, so each voxel should be computable
   from its position alone. Confirm that no generator depends on a previously generated
   voxel, such as craters placed by scanning the grid.
2. **Ring size.** The ring radii used above (1.2 to 2 planet radii) are an estimate. Check
   them against `BlueprintShapes`, since they set the vertical range of the dimension.
3. **Client load.** A 1,001-wide planet is a lot of chunks to light and render. Test the
   largest planet on a normal student machine before locking the sizes.
4. **Dimension type fields.** Confirm the `min_y` and `height` values load cleanly in
   26.1.2 and that both are accepted as multiples of 16.
