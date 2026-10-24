package net.kyrangit.solaria;

import net.kyrangit.solaria.blueprint.AstronomyClass;
import net.kyrangit.solaria.blueprint.Blueprint;
import net.kyrangit.solaria.blueprint.BlueprintException;
import net.kyrangit.solaria.blueprint.BlueprintGenerator;
import net.kyrangit.solaria.blueprint.MaterialPalette;
import net.kyrangit.solaria.blueprint.Palette;
import net.kyrangit.solaria.logging.ReasonCode;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Dependency-free Phase 2 unit tests covering:
 * <ul>
 *   <li>the ten-class contract (ordered ids),</li>
 *   <li>determinism: same seed + same (x,y,z) → same result via {@code voxelAt},</li>
 *   <li>size cap enforcement: bounding-box edge-length cap ({@code maxExtentBlocks}),
 *       no auto-shrink, {@link ReasonCode#CAP_EXCEEDED} on oversized requests,</li>
 *   <li>69-block palette safety: no forbidden blocks,</li>
 *   <li>every class emits non-air voxels and uses only indices in range.</li>
 * </ul>
 * Plumbing check (synthetic), not classroom evidence.
 */
public final class BlueprintTests {
	// Fourth revision (v2) wire ids — all lowercase.
	private static final List<String> EXPECTED_IDS = List.of(
		"sun", "earth", "moon", "mars", "jupiter",
		"venus", "saturn", "uranus", "neptune", "mercury"
	);
	private static int assertions;

	public static void main(String[] args) {
		testTenClassContract();
		testPaletteSafety();
		testDeterminism();
		testCapEnforced();
		testCapExceededWhenImpossible();
		testInvalidInputs();
		testAllClassesEmitVoxels();
		testColourCoverage();
		System.out.println("Blueprint plumbing check (synthetic): " + assertions + " assertions passed.");
	}

	// ------------------------------------------------------------------ Contract

	private static void testTenClassContract() {
		AstronomyClass[] values = AstronomyClass.values();
		check(values.length == 10, "exactly ten classes");
		for (int i = 0; i < values.length; i++) {
			check(values[i].id().equals(EXPECTED_IDS.get(i)),
				"class " + i + " id matches: expected '" + EXPECTED_IDS.get(i) + "' got '" + values[i].id() + "'");
		}
		Set<String> ids = new LinkedHashSet<>();
		for (AstronomyClass value : values) {
			check(ids.add(value.id()), "ids are unique: " + value.id());
		}
		for (String id : EXPECTED_IDS) {
			check(AstronomyClass.fromId(id) != null, "fromId resolves: " + id);
		}
		check(AstronomyClass.fromId("Venus") == null, "fromId is case-sensitive: title-case 'Venus' is rejected");
		check(AstronomyClass.fromId("Earth") == null, "fromId is case-sensitive: title-case 'Earth' is rejected");
		check(AstronomyClass.fromId("asteroid") == null, "fromId rejects unknown id 'asteroid'");
		check(AstronomyClass.fromId("mercury") != null, "fromId resolves v2 id 'mercury'");
	}

	// ------------------------------------------------------------------ Palette

	private static void testPaletteSafety() {
		MaterialPalette palette = Palette.create();
		check(palette.size() == 69, "palette has 69 entries");
		for (String blockId : palette.allBlockIds()) {
			check(!MaterialPalette.FORBIDDEN_BLOCKS.contains(blockId),
				"palette entry is not forbidden: " + blockId);
		}
		// Verify stable frozen indices 1-19
		check(palette.blockId(1).equals(Palette.GLOWSTONE),           "idx 1 = glowstone");
		check(palette.blockId(10).equals(Palette.STONE),              "idx 10 = stone");
		check(palette.blockId(19).equals(Palette.QUARTZ_BLOCK),       "idx 19 = quartz_block");
		// Spot-check a few v2 entries
		check(palette.blockId(20).equals(Palette.ORANGE_TERRACOTTA),  "idx 20 = orange_terracotta");
		check(palette.blockId(31).equals(Palette.CYAN_CONCRETE),      "idx 31 = cyan_concrete");
		check(palette.blockId(69).equals(Palette.REDSTONE_BLOCK),     "idx 69 = redstone_block");
		// Builder rejects forbidden blocks
		try {
			MaterialPalette.builder("minecraft:stone").add("minecraft:sand").build();
			throw new AssertionError("builder accepted a gravity block");
		} catch (IllegalArgumentException expected) {
			check(expected.getMessage().contains("sand"), "builder rejects sand with a clear message");
		}
	}

	// ------------------------------------------------------------------ Determinism

	private static void testDeterminism() {
		// Generate each class twice at a small scale and compare via hasSameVoxels.
		for (AstronomyClass cls : AstronomyClass.values()) {
			Blueprint a = BlueprintGenerator.generate(cls, 0.05);
			Blueprint b = BlueprintGenerator.generate(cls, 0.05);
			check(a.hasSameVoxels(b), "deterministic voxelAt for " + cls.id());
			// Spot-check a handful of specific positions directly
			int half = a.diameter() / 2;
			check(a.voxelAt(0, 0, 0) == b.voxelAt(0, 0, 0),       "centre voxel repeatable: " + cls.id());
			// Use diameter*2 so we are clear of both body and any ring
			int farOut = a.diameter();
			check(a.voxelAt(farOut, farOut, farOut) == 0,          "far corner = air: " + cls.id());
		}
	}

	// ------------------------------------------------------------------ Cap enforcement

	private static void testCapEnforced() {
		// At scale 0.001 the smallest class (mercury, base 301) gives diameter 1 → 3 (min clamp).
		// At scale 1.0 sun (base 1001) gives extent 1001, well under 1536.
		// Ring planets: Saturn at scale 1.0 gives extent ≈ ceil(861*1.9)=1636 > 1536 → CAP_EXCEEDED.
		// Saturn at scale 0.9 gives base diameter 775, extent ≈ ceil(775*1.9)=1473 ≤ 1536 → ok.
		Blueprint saturnOk = BlueprintGenerator.generate(AstronomyClass.SATURN, 0.9);
		check(saturnOk != null, "saturn at 0.9 scale accepted");
		check(saturnOk.diameter() > 0, "saturn diameter positive");

		try {
			BlueprintGenerator.generate(AstronomyClass.SATURN, 1.0);
			throw new AssertionError("saturn at scale 1.0 should exceed cap");
		} catch (BlueprintException e) {
			check(e.reasonCode() == ReasonCode.CAP_EXCEEDED, "CAP_EXCEEDED for saturn at 1.0");
			check(e.getMessage().contains("saturn"), "error message names the class");
		}

		// Sun at scale 1.0 → extent 1001 ≤ 1536 → accepted.
		Blueprint sunOk = BlueprintGenerator.generate(AstronomyClass.SUN, 1.0);
		check(sunOk != null && sunOk.diameter() == 1001, "sun at 1.0 accepted with correct diameter");

		// Uranus at scale 1.0 → extent ≈ ceil(721*1.6)=1154 ≤ 1536 → accepted.
		Blueprint uranusOk = BlueprintGenerator.generate(AstronomyClass.URANUS, 1.0);
		check(uranusOk != null, "uranus at 1.0 scale accepted");
	}

	private static void testCapExceededWhenImpossible() {
		// Very large scale: Sun at 2.0 → diameter 2003, extent 2003 > 1536 → CAP_EXCEEDED.
		try {
			BlueprintGenerator.generate(AstronomyClass.SUN, 2.0);
			throw new AssertionError("expected CAP_EXCEEDED for sun at scale 2.0");
		} catch (BlueprintException expected) {
			check(expected.reasonCode() == ReasonCode.CAP_EXCEEDED, "CAP_EXCEEDED for sun at 2.0");
			check(expected.getMessage().contains("sun"), "error message names the class");
		}
	}

	// ------------------------------------------------------------------ Invalid inputs

	private static void testInvalidInputs() {
		assertInvalid(() -> BlueprintGenerator.generate(null, 1.0), "null class");
		assertInvalid(() -> BlueprintGenerator.generate(AstronomyClass.EARTH, 0.0), "zero scale");
		assertInvalid(() -> BlueprintGenerator.generate(AstronomyClass.EARTH, -1.0), "negative scale");
		assertInvalid(() -> BlueprintGenerator.generate(AstronomyClass.EARTH, Double.NaN), "NaN scale");
		assertInvalid(() -> BlueprintGenerator.generate(AstronomyClass.EARTH, Double.POSITIVE_INFINITY), "infinite scale");
	}

	private static void assertInvalid(Runnable action, String label) {
		try {
			action.run();
			throw new AssertionError("expected INPUT_INVALID for " + label);
		} catch (BlueprintException expected) {
			check(expected.reasonCode() == ReasonCode.INPUT_INVALID, "INPUT_INVALID for " + label);
			check(!expected.getMessage().isBlank(), "non-blank error for " + label);
		}
	}

	// ------------------------------------------------------------------ Voxel content

	private static void testAllClassesEmitVoxels() {
		for (AstronomyClass cls : AstronomyClass.values()) {
			Blueprint bp = BlueprintGenerator.generate(cls, 0.05);
			int half = bp.diameter() / 2;
			MaterialPalette palette = bp.palette();
			Set<Integer> seen = new HashSet<>();
			// Sample a moderate grid across the bounding box (faster than full scan)
			int extent = (int) Math.ceil(half * 2.1); // include ring for Saturn/Uranus
			int step = Math.max(1, extent / 15);
			for (int x = -extent; x <= extent; x += step) {
				for (int y = -extent; y <= extent; y += step) {
					for (int z = -extent; z <= extent; z += step) {
						int idx = bp.voxelAt(x, y, z);
						if (idx != 0) seen.add(idx);
					}
				}
			}
			check(!seen.isEmpty(), "non-air voxels emitted: " + cls.id());
			for (int idx : seen) {
				check(idx >= 1 && idx <= palette.size(),
					"palette index in range [1," + palette.size() + "] for " + cls.id() + ": " + idx);
				String blockId = palette.blockId(idx);
				check(!MaterialPalette.FORBIDDEN_BLOCKS.contains(blockId),
					"used block is safe for " + cls.id() + ": " + blockId);
			}
			// The centre of every body must be non-air (solid sphere)
			check(bp.voxelAt(0, 0, 0) != 0, "centre is non-air: " + cls.id());
			// Outside the bounding box must be air
			check(bp.voxelAt(half + 100, 0, 0) == 0, "far outside = air: " + cls.id());
		}
	}

	// ------------------------------------------------------------------ Colour coverage

	private static void testColourCoverage() {
		// v2 colour list: yellow, orange, blue, green, white, grey, red, tan, brown, cyan.
		// The 69-block palette covers all of these with dedicated entries.
		MaterialPalette palette = Palette.create();
		String[] required = {
			Palette.YELLOW_CONCRETE, Palette.ORANGE_CONCRETE, Palette.BLUE_CONCRETE,
			Palette.GREEN_CONCRETE, Palette.WHITE_CONCRETE, Palette.LIGHT_GRAY_CONCRETE,
			Palette.GRAY_CONCRETE, Palette.RED_CONCRETE, Palette.BROWN_CONCRETE,
			Palette.LIGHT_BLUE_CONCRETE, Palette.RED_TERRACOTTA,
			// v2 additions
			Palette.CYAN_CONCRETE, Palette.LAPIS_BLOCK, Palette.TERRACOTTA,
			Palette.ORANGE_TERRACOTTA, Palette.SMOOTH_SANDSTONE
		};
		for (String blockId : required) {
			check(palette.allBlockIds().contains(blockId),
				"palette supplies colour swatch: " + blockId);
		}
	}

	// ------------------------------------------------------------------ util

	private static void check(boolean condition, String message) {
		assertions++;
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
