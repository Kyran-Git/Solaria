package net.kyrangit.solaria;

import net.kyrangit.solaria.blueprint.AstronomyClass;
import net.kyrangit.solaria.blueprint.Blueprint;
import net.kyrangit.solaria.blueprint.BlueprintException;
import net.kyrangit.solaria.blueprint.BlueprintGenerator;
import net.kyrangit.solaria.blueprint.MaterialPalette;
import net.kyrangit.solaria.blueprint.Palette;
import net.kyrangit.solaria.blueprint.VoxelGrid;
import net.kyrangit.solaria.config.SolariaConfig;
import net.kyrangit.solaria.logging.ReasonCode;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Dependency-free M2 unit tests covering:
 * <ul>
 *   <li>the ten-class contract (ordered ids),</li>
 *   <li>determinism (byte-identical voxel snapshots across runs),</li>
 *   <li>size cap / scale enforcement, including {@link ReasonCode#CAP_EXCEEDED},</li>
 *   <li>every class has a blueprint and only uses safe palette blocks.</li>
 * </ul>
 * Plumbing check (synthetic), not classroom evidence.
 */
public final class BlueprintTests {
	private static final List<String> EXPECTED_IDS = List.of(
		"sun", "earth", "moon", "mars", "jupiter",
		"Venus", "Saturn", "Neptune", "Uranus", "mercury"
	);
	private static int assertions;

	public static void main(String[] args) {
		testTenClassContract();
		testPaletteSafety();
		testDeterminism();
		testEveryClassFitsDefaultCap();
		testScaleDownUnderTightCap();
		testCapExceededWhenImpossible();
		testInvalidInputs();
		testAllGridsUseOnlySafeBlocks();
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
		check(AstronomyClass.fromId("venus") == null, "fromId is case-sensitive: lowercase 'venus' is rejected");
		check(AstronomyClass.fromId("Earth") == null, "fromId is case-sensitive: capitalised 'Earth' is rejected");
	}

	private static void testPaletteSafety() {
		MaterialPalette palette = Palette.create();
		check(palette.size() == 19, "palette has 19 entries");
		for (String blockId : palette.allBlockIds()) {
			check(!MaterialPalette.FORBIDDEN_BLOCKS.contains(blockId),
				"palette entry is not forbidden: " + blockId);
		}
		try {
			MaterialPalette.builder("minecraft:stone").add("minecraft:sand").build();
			throw new AssertionError("builder accepted a gravity block");
		} catch (IllegalArgumentException expected) {
			check(expected.getMessage().contains("sand"), "builder rejects sand with a clear message");
		}
		try {
			MaterialPalette.builder("minecraft:lava");
			throw new AssertionError("builder accepted a fluid fallback");
		} catch (IllegalArgumentException expected) {
			check(expected.getMessage().contains("lava"), "builder rejects lava fallback with a clear message");
		}
	}

	// ------------------------------------------------------------------ Determinism

	private static void testDeterminism() {
		SolariaConfig config = SolariaConfig.defaults();
		for (AstronomyClass astronomyClass : AstronomyClass.values()) {
			Blueprint first = BlueprintGenerator.generate(astronomyClass, config.blocksPerUnit(), config.maxSizeBlocks());
			Blueprint second = BlueprintGenerator.generate(astronomyClass, config.blocksPerUnit(), config.maxSizeBlocks());
			check(first.hasSameVoxels(second),
				"deterministic across calls: " + astronomyClass.id());
			check(Arrays.equals(first.grid().snapshot(), second.grid().snapshot()),
				"byte-identical snapshot: " + astronomyClass.id());
			check(first.diameter() == second.diameter(),
				"deterministic diameter: " + astronomyClass.id());
			check(first.blocksPerUnit() == second.blocksPerUnit(),
				"deterministic effective scale: " + astronomyClass.id());
		}
	}

	// ------------------------------------------------------------------ Cap / scale

	private static void testEveryClassFitsDefaultCap() {
		SolariaConfig config = SolariaConfig.defaults();
		for (AstronomyClass astronomyClass : AstronomyClass.values()) {
			Blueprint blueprint = BlueprintGenerator.generate(astronomyClass, config.blocksPerUnit(), config.maxSizeBlocks());
			check(blueprint.blockCount() > 0, "non-empty blueprint: " + astronomyClass.id());
			check(blueprint.blockCount() <= config.maxSizeBlocks(),
				"blueprint fits default cap: " + astronomyClass.id()
					+ " used " + blueprint.blockCount() + "/" + config.maxSizeBlocks());
			check(blueprint.blocksPerUnit() == config.blocksPerUnit(),
				"no silent scale-down at default settings: " + astronomyClass.id());
		}
	}

	private static void testScaleDownUnderTightCap() {
		// Pick a cap below Jupiter's natural footprint so the generator must scale down.
		Blueprint jupiter = BlueprintGenerator.generate(AstronomyClass.JUPITER, 1.0, 32768);
		int tightCap = Math.max(1, jupiter.blockCount() / 4);
		Blueprint scaled = BlueprintGenerator.generate(AstronomyClass.JUPITER, 1.0, tightCap);
		check(scaled.blockCount() <= tightCap,
			"scale-down respects tight cap: " + scaled.blockCount() + " <= " + tightCap);
		check(scaled.blocksPerUnit() < jupiter.blocksPerUnit(),
			"effective scale is reduced: " + scaled.blocksPerUnit() + " < " + jupiter.blocksPerUnit());
		check(scaled.diameter() < jupiter.diameter(),
			"effective diameter is reduced: " + scaled.diameter() + " < " + jupiter.diameter());
	}

	private static void testCapExceededWhenImpossible() {
		try {
			BlueprintGenerator.generate(AstronomyClass.SUN, 1.0, 1);
			throw new AssertionError("tiny cap did not raise CAP_EXCEEDED");
		} catch (BlueprintException expected) {
			check(expected.reasonCode() == ReasonCode.CAP_EXCEEDED,
				"impossible cap raises CAP_EXCEEDED");
			check(expected.getMessage().contains("sun"), "error message names the class");
			check(expected.getMessage().contains("max_size_blocks"), "error names the cap setting");
		}
	}

	private static void testInvalidInputs() {
		assertInvalid(() -> BlueprintGenerator.generate(null, 1.0, 32768), "null class");
		assertInvalid(() -> BlueprintGenerator.generate(AstronomyClass.EARTH, 0.0, 32768), "zero scale");
		assertInvalid(() -> BlueprintGenerator.generate(AstronomyClass.EARTH, -1.0, 32768), "negative scale");
		assertInvalid(() -> BlueprintGenerator.generate(AstronomyClass.EARTH, Double.NaN, 32768), "NaN scale");
		assertInvalid(() -> BlueprintGenerator.generate(AstronomyClass.EARTH, Double.POSITIVE_INFINITY, 32768), "infinite scale");
		assertInvalid(() -> BlueprintGenerator.generate(AstronomyClass.EARTH, 1.0, 0), "zero cap");
		assertInvalid(() -> BlueprintGenerator.generate(AstronomyClass.EARTH, 1.0, -5), "negative cap");
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

	// ------------------------------------------------------------------ Palette use per class

	private static void testAllGridsUseOnlySafeBlocks() {
		SolariaConfig config = SolariaConfig.defaults();
		for (AstronomyClass astronomyClass : AstronomyClass.values()) {
			Blueprint blueprint = BlueprintGenerator.generate(astronomyClass, config.blocksPerUnit(), config.maxSizeBlocks());
			VoxelGrid grid = blueprint.grid();
			MaterialPalette palette = blueprint.palette();
			Set<Integer> seen = new HashSet<>();
			for (int x = 0; x < grid.sizeX(); x++) {
				for (int y = 0; y < grid.sizeY(); y++) {
					for (int z = 0; z < grid.sizeZ(); z++) {
						int index = grid.get(x, y, z);
						if (index != 0) {
							seen.add(index);
						}
					}
				}
			}
			check(!seen.isEmpty(), "non-air voxels present: " + astronomyClass.id());
			for (int index : seen) {
				check(index >= 1 && index <= palette.size(),
					"palette index in range for " + astronomyClass.id() + ": " + index);
				String blockId = palette.blockId(index);
				check(!MaterialPalette.FORBIDDEN_BLOCKS.contains(blockId),
					"used block is safe for " + astronomyClass.id() + ": " + blockId);
			}
		}
	}

	private static void testColourCoverage() {
		// Plan M2.3 colour list: yellow, orange, blue, green, white, grey, red, tan, brown, cyan.
		// The 19-block palette supplies 9 of those exactly (tan is approximated by white_concrete /
		// brown_concrete mixes, cyan by light_blue_concrete). This test documents the mapping and
		// will trip if a required swatch block is removed from Palette.
		MaterialPalette palette = Palette.create();
		String[] required = {
			Palette.YELLOW_CONCRETE, Palette.ORANGE_CONCRETE, Palette.BLUE_CONCRETE,
			Palette.GREEN_CONCRETE, Palette.WHITE_CONCRETE, Palette.LIGHT_GRAY_CONCRETE,
			Palette.GRAY_CONCRETE, Palette.RED_CONCRETE, Palette.BROWN_CONCRETE,
			Palette.LIGHT_BLUE_CONCRETE, Palette.RED_TERRACOTTA
		};
		for (String blockId : required) {
			check(palette.allBlockIds().contains(blockId),
				"palette supplies colour swatch: " + blockId);
		}
	}

	private static void check(boolean condition, String message) {
		assertions++;
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
