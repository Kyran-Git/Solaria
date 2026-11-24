package net.kyrangit.solaria;

import net.kyrangit.solaria.blueprint.AstronomyClass;
import net.kyrangit.solaria.blueprint.Blueprint;
import net.kyrangit.solaria.blueprint.BlueprintException;
import net.kyrangit.solaria.blueprint.BlueprintGenerator;
import net.kyrangit.solaria.blueprint.MaterialPalette;
import net.kyrangit.solaria.blueprint.Palette;
import net.kyrangit.solaria.logging.ReasonCode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
		testScaledCapExamples();
		testCapExceededWhenImpossible();
		testInvalidInputs();
		testAllClassesEmitVoxels();
		testColourCoverage();
		testStep3NaturalPatterns();
		testStep4SurfaceRelief();
		testStep5Earth();
		testStep7SaturnRings();
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
		for (AstronomyClass cls : AstronomyClass.values()) {
			Blueprint bp = BlueprintGenerator.generate(cls, 1.0);
			int extent = BlueprintGenerator.blueprintExtent(cls, bp.diameter());
			check(extent <= BlueprintGenerator.MAX_EXTENT_BLOCKS,
				"default including relief/rings fits 500: " + cls.id() + " (extent=" + extent + ")");
		}
	}

	private static void testScaledCapExamples() {
		// Ringed bodies fit by default; explicitly enlarged requests must still reject.
		Blueprint saturnOk = BlueprintGenerator.generate(AstronomyClass.SATURN, 0.9);
		check(saturnOk != null, "saturn at 0.9 scale accepted");
		check(saturnOk.diameter() > 0, "saturn diameter positive");

		try {
			BlueprintGenerator.generate(AstronomyClass.SATURN, 1.1);
			throw new AssertionError("saturn at scale 1.1 should exceed cap");
		} catch (BlueprintException e) {
			check(e.reasonCode() == ReasonCode.CAP_EXCEEDED, "CAP_EXCEEDED for saturn at 1.1");
			check(e.getMessage().contains("saturn"), "error message names the class");
		}

		// Sun at scale 1.0 has a 499-block diameter.
		Blueprint sunOk = BlueprintGenerator.generate(AstronomyClass.SUN, 1.0);
		check(sunOk != null && sunOk.diameter() == 499, "sun at 1.0 accepted with correct diameter");

		// Uranus including rings fits the default envelope.
		Blueprint uranusOk = BlueprintGenerator.generate(AstronomyClass.URANUS, 1.0);
		check(uranusOk != null, "uranus at 1.0 scale accepted");
	}

	private static void testCapExceededWhenImpossible() {
		// Doubling the Sun exceeds the 500-block envelope.
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

	private static void testStep3NaturalPatterns() {
		AstronomyClass[] targets = {
			AstronomyClass.SUN, AstronomyClass.MOON, AstronomyClass.MARS,
			AstronomyClass.JUPITER, AstronomyClass.SATURN
		};
		double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));
		for (AstronomyClass cls : targets) {
			Blueprint a = BlueprintGenerator.generate(cls, 1.0);
			Blueprint b = BlueprintGenerator.generate(cls, 1.0);
			double baseR = a.diameter() / 2.0;
			double maxR = Math.ceil(baseR * (1.0 + BlueprintGenerator.MAX_RELIEF_FRAC)) + 1.0;
			double minR = Math.max(1.0, Math.floor(baseR * 0.90));
			Map<Integer, Integer> counts = new HashMap<>();
			int total = 0;

			for (int i = 0; i < 10_000; i++) {
				double uy = 1.0 - (2.0 * (i + 0.5) / 10_000.0);
				double ring = Math.sqrt(Math.max(0.0, 1.0 - uy * uy));
				double theta = goldenAngle * i;
				double ux = ring * Math.cos(theta);
				double uz = ring * Math.sin(theta);
				for (double r = maxR; r >= minR; r -= 0.75) {
					int vx = (int) Math.round(r * ux);
					int vy = (int) Math.round(r * uy);
					int vz = (int) Math.round(r * uz);
					int va = a.voxelAt(vx, vy, vz);
					if (va != 0) {
						if (i < 2_000) {
							check(va == b.voxelAt(vx, vy, vz), cls.id() + ": deterministic full-scale surface voxel");
						}
						counts.merge(va, 1, Integer::sum);
						total++;
						break;
					}
				}
			}

			check(counts.size() >= 6, cls.id() + ": uses >= 6 distinct surface blocks, got " + counts.size());
			int maxCount = 0;
			for (int count : counts.values()) {
				if (count > maxCount) maxCount = count;
			}
			double topShare = (100.0 * maxCount) / total;
			check(topShare < 40.0,
				String.format("%s: single most common surface block share %.1f%% >= 40%%", cls.id(), topShare));
		}

		// Jupiter north & south polar rings must vary smoothly without repeating Cartesian wedges.
		Blueprint jupiter = BlueprintGenerator.generate(AstronomyClass.JUPITER, 1.0);
		double jr = jupiter.diameter() / 2.0 - 0.5;
		for (int sign : new int[]{-1, 1}) {
			double lat = sign * Math.toRadians(82.0);
			double cosLat = Math.cos(lat);
			int vy = (int) Math.round(jr * Math.sin(lat));
			int distinctPolar = 0;
			int prev = -1;
			Set<Integer> seenPolar = new HashSet<>();
			for (int step = 0; step < 36; step++) {
				double lon = (2.0 * Math.PI * step) / 36.0;
				int vx = (int) Math.round(jr * cosLat * Math.cos(lon));
				int vz = (int) Math.round(jr * cosLat * Math.sin(lon));
				int v = jupiter.voxelAt(vx, vy, vz);
				check(v != 0, "jupiter polar sample is solid");
				seenPolar.add(v);
				if (v != prev) {
					distinctPolar++;
					prev = v;
				}
			}
			check(!seenPolar.isEmpty() && distinctPolar <= 28,
				"jupiter polar cap transitions naturally without wedge stripes (transitions=" + distinctPolar + ")");
		}
	}

	private static void testStep4SurfaceRelief() {
		AstronomyClass[] rocky = {
			AstronomyClass.MOON, AstronomyClass.MARS, AstronomyClass.EARTH,
			AstronomyClass.VENUS, AstronomyClass.MERCURY
		};
		double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));

		for (AstronomyClass cls : rocky) {
			Blueprint a = BlueprintGenerator.generate(cls, 1.0);
			Blueprint b = BlueprintGenerator.generate(cls, 1.0);
			double radius = a.diameter() / 2.0;
			double cap = BlueprintGenerator.MAX_RELIEF_FRAC;
			int extent = BlueprintGenerator.blueprintExtent(cls, a.diameter());
			int bound = extent / 2 + 1;
			check(bound <= 256, cls.id() + ": relief halfExtent fits 512-block dimension");

			double maxScanR = Math.ceil(radius * (1.0 + cap)) + 3.0;
			double minScanR = Math.max(1.0, Math.floor(radius * (1.0 - cap)) - 4.0);
			double minHitR = Double.MAX_VALUE;
			double maxHitR = 0.0;

			for (int i = 0; i < 6_000; i++) {
				double uy = 1.0 - (2.0 * (i + 0.5) / 6_000.0);
				double ring = Math.sqrt(Math.max(0.0, 1.0 - uy * uy));
				double theta = goldenAngle * i;
				double ux = ring * Math.cos(theta);
				double uz = ring * Math.sin(theta);
				for (double r = maxScanR; r >= minScanR; r -= 0.5) {
					int vx = (int) Math.round(ux * r);
					int vy = (int) Math.round(uy * r);
					int vz = (int) Math.round(uz * r);
					int va = a.voxelAt(vx, vy, vz);
					if (va != 0) {
						check(va == b.voxelAt(vx, vy, vz), cls.id() + ": deterministic relief voxel");
						double hitR = Math.sqrt((double) vx * vx + (double) vy * vy + (double) vz * vz);
						if (hitR < minHitR) minHitR = hitR;
						if (hitR > maxHitR) maxHitR = hitR;
						break;
					}
				}
			}

			check(maxHitR <= radius * (1.0 + cap) + 1.0,
				String.format("%s: max relief %.2f <= cap %.2f", cls.id(), maxHitR, radius * (1.0 + cap) + 1.0));
			check(minHitR >= radius * (1.0 - cap) - 1.0,
				String.format("%s: min relief %.2f >= cap %.2f", cls.id(), minHitR, radius * (1.0 - cap) - 1.0));
			check(maxHitR - minHitR >= 4.0,
				String.format("%s: relief span %.2f >= 4.0 blocks", cls.id(), maxHitR - minHitR));
			check(maxHitR <= bound - 0.5,
				String.format("%s: max relief %.2f fits inside bound %d", cls.id(), maxHitR, bound));

			// Zero solids just outside bound on axial and spherical samples.
			check(a.voxelAt(bound + 1, 0, 0) == 0 && a.voxelAt(-(bound + 1), 0, 0) == 0, cls.id() + ": X bound clean");
			check(a.voxelAt(0, bound + 1, 0) == 0 && a.voxelAt(0, -(bound + 1), 0) == 0, cls.id() + ": Y bound clean");
			check(a.voxelAt(0, 0, bound + 1) == 0 && a.voxelAt(0, 0, -(bound + 1)) == 0, cls.id() + ": Z bound clean");
			for (int i = 0; i < 500; i++) {
				double uy = 1.0 - (2.0 * (i + 0.5) / 500.0);
				double ring = Math.sqrt(Math.max(0.0, 1.0 - uy * uy));
				double theta = goldenAngle * i;
				int vx = (int) Math.round((bound + 1.0) * ring * Math.cos(theta));
				int vy = (int) Math.round((bound + 1.0) * uy);
				int vz = (int) Math.round((bound + 1.0) * ring * Math.sin(theta));
				check(a.voxelAt(vx, vy, vz) == 0, cls.id() + ": outside bound is air");
			}
		}

		// Earth: land sits higher than oceans.
		Blueprint earth = BlueprintGenerator.generate(AstronomyClass.EARTH, 1.0);
		MaterialPalette pal = earth.palette();
		Set<Integer> oceanIds = Set.of(
			pal.indexOf(Palette.BLUE_TERRACOTTA),
			pal.indexOf(Palette.LAPIS_BLOCK),
			pal.indexOf(Palette.BLUE_CONCRETE),
			pal.indexOf(Palette.CYAN_CONCRETE),
			pal.indexOf(Palette.PRISMARINE),
			pal.indexOf(Palette.LIGHT_BLUE_CONCRETE));
		Set<Integer> landIds = Set.of(
			pal.indexOf(Palette.GREEN_TERRACOTTA),
			pal.indexOf(Palette.MOSS_BLOCK),
			pal.indexOf(Palette.GREEN_CONCRETE),
			pal.indexOf(Palette.LIME_TERRACOTTA),
			pal.indexOf(Palette.LIME_CONCRETE),
			pal.indexOf(Palette.BROWN_TERRACOTTA),
			pal.indexOf(Palette.PACKED_MUD),
			pal.indexOf(Palette.TERRACOTTA),
			pal.indexOf(Palette.ORANGE_TERRACOTTA),
			pal.indexOf(Palette.YELLOW_TERRACOTTA),
			pal.indexOf(Palette.SMOOTH_SANDSTONE),
			pal.indexOf(Palette.END_STONE),
			pal.indexOf(Palette.BROWN_CONCRETE),
			pal.indexOf(Palette.GRAY_TERRACOTTA),
			pal.indexOf(Palette.TUFF),
			pal.indexOf(Palette.STONE),
			pal.indexOf(Palette.LIGHT_GRAY_TERRACOTTA));
		double er = earth.diameter() / 2.0;
		double landRSum = 0.0, oceanRSum = 0.0;
		int landCount = 0, oceanCount = 0;
		for (int i = 0; i < 4_000; i++) {
			double uy = 1.0 - (2.0 * (i + 0.5) / 4_000.0);
			if (Math.abs(uy) > 0.78) continue;
			double ring = Math.sqrt(Math.max(0.0, 1.0 - uy * uy));
			double theta = goldenAngle * i;
			double ux = ring * Math.cos(theta);
			double uz = ring * Math.sin(theta);
			if (ux > 0.0 && uz > 0.0) continue; // skip cutaway quadrant
			for (double r = Math.ceil(er * 1.05); r >= Math.floor(er * 0.94); r -= 0.5) {
				int vx = (int) Math.round(ux * r);
				int vy = (int) Math.round(uy * r);
				int vz = (int) Math.round(uz * r);
				int idx = earth.voxelAt(vx, vy, vz);
				if (idx != 0) {
					double hitR = Math.sqrt((double) vx * vx + (double) vy * vy + (double) vz * vz);
					if (oceanIds.contains(idx)) {
						oceanRSum += hitR;
						oceanCount++;
					} else if (landIds.contains(idx)) {
						landRSum += hitR;
						landCount++;
					}
					break;
				}
			}
		}
		check(landCount > 100 && oceanCount > 100, "earth sampled enough land and ocean voxels");
		double landMean = landRSum / landCount;
		double oceanMean = oceanRSum / oceanCount;
		check(landMean - oceanMean >= 1.0,
			String.format("earth land mean %.2f sits above ocean mean %.2f by >= 1.0", landMean, oceanMean));

		// Mars: Olympus Mons rises sharply and Valles Marineris cuts a deep canyon.
		Blueprint mars = BlueprintGenerator.generate(AstronomyClass.MARS, 1.0);
		double mr = mars.diameter() / 2.0;
		double olympusR = surfaceRadiusAlong(mars, 0.76, 0.33, 0.56);
		double vallesR = surfaceRadiusAlong(mars, 0.95, -0.18, -0.25);
		check(olympusR > mr + 2.0, String.format("mars Olympus Mons %.2f > radius %.2f + 2.0", olympusR, mr));
		check(vallesR < mr - 1.5, String.format("mars Valles Marineris %.2f < radius %.2f - 1.5", vallesR, mr));

		// Non-rocky bodies: Sun and gas/ice giants have no solid hills.
		AstronomyClass[] nonRocky = {
			AstronomyClass.SUN, AstronomyClass.JUPITER, AstronomyClass.SATURN,
			AstronomyClass.URANUS, AstronomyClass.NEPTUNE
		};
		for (AstronomyClass cls : nonRocky) {
			Blueprint bp = BlueprintGenerator.generate(cls, 1.0);
			double r = bp.diameter() / 2.0;
			double minHit = Double.MAX_VALUE;
			double maxHit = 0.0;
			for (int i = 0; i < 1_000; i++) {
				double uy = 1.0 - (2.0 * (i + 0.5) / 1_000.0);
				if (cls == AstronomyClass.SATURN && Math.abs(uy) <= 0.15) continue;
				double ring = Math.sqrt(Math.max(0.0, 1.0 - uy * uy));
				double theta = goldenAngle * i;
				double ux = ring * Math.cos(theta);
				if (cls == AstronomyClass.URANUS && Math.abs(ux) <= 0.15) continue;
				double uz = ring * Math.sin(theta);
				double hit = surfaceRadiusAlong(bp, ux, uy, uz);
				if (hit < minHit) minHit = hit;
				if (hit > maxHit) maxHit = hit;
			}
			// Cubic integer rounding can place the outermost inside-sphere voxel up to sqrt(3) inside r.
			check(maxHit <= r + 0.5 && minHit >= r - 1.8,
				String.format("%s non-rocky smoothness: [%.2f..%.2f] around %.2f", cls.id(), minHit, maxHit, r));
		}
	}

	private static void testStep5Earth() {
		Blueprint a = BlueprintGenerator.generate(AstronomyClass.EARTH, 1.0);
		Blueprint b = BlueprintGenerator.generate(AstronomyClass.EARTH, 1.0);
		MaterialPalette pal = a.palette();
		double baseR = a.diameter() / 2.0;
		double maxScan = Math.ceil(baseR * (1.0 + BlueprintGenerator.MAX_RELIEF_FRAC)) + 2.0;
		double minScan = Math.max(1.0, Math.floor(baseR * 0.90));
		double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));

		Set<Integer> cloudSet = Set.of(
			pal.indexOf(Palette.WHITE_CONCRETE),
			pal.indexOf(Palette.CALCITE),
			pal.indexOf(Palette.SNOW_BLOCK));
		Set<Integer> iceSet = Set.of(
			pal.indexOf(Palette.PACKED_ICE),
			pal.indexOf(Palette.WHITE_CONCRETE),
			pal.indexOf(Palette.CALCITE),
			pal.indexOf(Palette.SNOW_BLOCK));
		Set<Integer> oceanSet = Set.of(
			pal.indexOf(Palette.BLUE_TERRACOTTA),
			pal.indexOf(Palette.LAPIS_BLOCK),
			pal.indexOf(Palette.BLUE_CONCRETE),
			pal.indexOf(Palette.CYAN_CONCRETE),
			pal.indexOf(Palette.PRISMARINE),
			pal.indexOf(Palette.LIGHT_BLUE_CONCRETE));
		Set<Integer> forestSet = Set.of(
			pal.indexOf(Palette.GREEN_TERRACOTTA),
			pal.indexOf(Palette.MOSS_BLOCK),
			pal.indexOf(Palette.GREEN_CONCRETE),
			pal.indexOf(Palette.LIME_TERRACOTTA),
			pal.indexOf(Palette.LIME_CONCRETE));
		Set<Integer> aridSet = Set.of(
			pal.indexOf(Palette.BROWN_TERRACOTTA),
			pal.indexOf(Palette.PACKED_MUD),
			pal.indexOf(Palette.TERRACOTTA),
			pal.indexOf(Palette.ORANGE_TERRACOTTA),
			pal.indexOf(Palette.YELLOW_TERRACOTTA),
			pal.indexOf(Palette.SMOOTH_SANDSTONE),
			pal.indexOf(Palette.END_STONE));
		Set<Integer> allLandSet = new HashSet<>(forestSet);
		allLandSet.addAll(aridSet);
		allLandSet.addAll(Set.of(
			pal.indexOf(Palette.BROWN_CONCRETE),
			pal.indexOf(Palette.GRAY_TERRACOTTA),
			pal.indexOf(Palette.TUFF),
			pal.indexOf(Palette.STONE),
			pal.indexOf(Palette.LIGHT_GRAY_TERRACOTTA)));

		Map<Integer, Integer> counts = new HashMap<>();
		int total = 0;
		int lowMidTotal = 0;
		int lowMidClouds = 0;

		for (int i = 0; i < 10_000; i++) {
			double uy = 1.0 - (2.0 * (i + 0.5) / 10_000.0);
			double ring = Math.sqrt(Math.max(0.0, 1.0 - uy * uy));
			double theta = goldenAngle * i;
			double ux = ring * Math.cos(theta);
			double uz = ring * Math.sin(theta);
			for (double r = maxScan; r >= minScan; r -= 0.75) {
				int vx = (int) Math.round(ux * r);
				int vy = (int) Math.round(uy * r);
				int vz = (int) Math.round(uz * r);
				int idx = a.voxelAt(vx, vy, vz);
				if (idx != 0) {
					if (i < 2_000) {
						check(idx == b.voxelAt(vx, vy, vz), "earth: deterministic full-scale surface voxel");
					}
					counts.merge(idx, 1, Integer::sum);
					total++;
					if (Math.abs(uy) < 0.75) {
						lowMidTotal++;
						if (cloudSet.contains(idx)) lowMidClouds++;
					}
					break;
				}
			}
		}

		check(counts.size() >= 10, "earth: uses >= 10 distinct surface blocks, got " + counts.size());
		int maxCount = 0;
		for (int count : counts.values()) {
			if (count > maxCount) maxCount = count;
		}
		double topShare = (100.0 * maxCount) / total;
		check(topShare < 40.0, String.format("earth: top block share %.1f%% >= 40%%", topShare));

		double cloudPct = (100.0 * lowMidClouds) / lowMidTotal;
		check(cloudPct >= 8.0 && cloudPct <= 35.0,
			String.format("earth: sparse low/mid-latitude clouds %.1f%% not in [8%%..35%%]", cloudPct));

		// Verify recognizable NASA geography around iconic coordinates (tolerating local cloud wisps).
		checkRegionMatches(a, 0.0, -150.0, oceanSet, cloudSet, "Pacific Ocean");
		checkRegionMatches(a, 25.0, -40.0, oceanSet, cloudSet, "Atlantic Ocean");
		checkRegionMatches(a, 23.0, 13.0, aridSet, cloudSet, "Sahara Desert");
		checkRegionMatches(a, -4.0, -62.0, forestSet, cloudSet, "Amazon Rainforest");
		checkRegionMatches(a, -25.0, 134.0, allLandSet, cloudSet, "Australia");
		checkRegionMatches(a, 55.0, 80.0, allLandSet, cloudSet, "Eurasia");
		checkRegionMatches(a, -82.0, 0.0, iceSet, Set.of(), "Antarctica");
	}

	private static void checkRegionMatches(Blueprint bp, double latDeg, double lonDeg,
	                                       Set<Integer> expected, Set<Integer> cloudIgnore, String label) {
		double[][] offsets = {
			{0, 0}, {3, 0}, {-3, 0}, {0, 4}, {0, -4},
			{3, 4}, {3, -4}, {-3, 4}, {-3, -4}
		};
		int clearSamples = 0;
		int matches = 0;
		for (double[] off : offsets) {
			double lat = Math.toRadians(latDeg + off[0]);
			double lon = Math.toRadians(lonDeg + off[1]);
			double uy = Math.sin(lat);
			double cosLat = Math.cos(lat);
			double ux = cosLat * Math.cos(lon);
			double uz = cosLat * Math.sin(lon);
			double radius = bp.diameter() / 2.0;
			double maxScan = Math.ceil(radius * (1.0 + BlueprintGenerator.MAX_RELIEF_FRAC)) + 2.0;
			for (double r = maxScan; r >= radius * 0.90; r -= 0.5) {
				int vx = (int) Math.round(ux * r);
				int vy = (int) Math.round(uy * r);
				int vz = (int) Math.round(uz * r);
				int idx = bp.voxelAt(vx, vy, vz);
				if (idx != 0) {
					if (!cloudIgnore.contains(idx)) {
						clearSamples++;
						if (expected.contains(idx)) matches++;
					}
					break;
				}
			}
		}
		check(clearSamples >= 2 && matches * 2 >= clearSamples,
			String.format("earth region %s: matched %d/%d non-cloud samples", label, matches, clearSamples));
	}

	private static void testStep7SaturnRings() {
		Blueprint a = BlueprintGenerator.generate(AstronomyClass.SATURN, 1.0);
		Blueprint b = BlueprintGenerator.generate(AstronomyClass.SATURN, 1.0);
		check(a.diameter() == 213, "saturn default diameter shrunk to 213 for true NASA F-ring extent");
		int extent = BlueprintGenerator.blueprintExtent(AstronomyClass.SATURN, a.diameter());
		check(extent == 499 && extent <= BlueprintGenerator.MAX_EXTENT_BLOCKS,
			"saturn full ring extent " + extent + " fits <= 500");

		double r = a.diameter() / 2.0;
		double innerGap = ringFillInBand(a, 1.05 * r, 1.20 * r, 0, 0);
		double cFill = ringFillInBand(a, 1.27 * r, 1.49 * r, 0, 0);
		double bFill = ringFillInBand(a, 1.56 * r, 1.92 * r, -1, 0);
		double bFillLower = ringFillInBand(a, 1.56 * r, 1.92 * r, -1, -1);
		double cassiniFill = ringFillInBand(a, 1.96 * r, 2.02 * r, 0, 0);
		double aInnerFill = ringFillInBand(a, 2.05 * r, 2.18 * r, 0, 0);
		double enckeFill = ringFillInBand(a, 2.205 * r, 2.221 * r, 0, 0);
		double aOuterFill = ringFillInBand(a, 2.235 * r, 2.262 * r, 0, 0);
		double rocheGap = ringFillInBand(a, 2.280 * r, 2.302 * r, 0, 0);
		double fFill = ringFillInBand(a, 2.314 * r, 2.326 * r, 0, 0);
		double outsideFill = ringFillInBand(a, 2.345 * r, 2.55 * r, -2, 2);

		System.out.printf(
			"Saturn ring fill rates: C=%.3f, B=%.3f (lower=%.3f), Cassini=%.3f, A_in=%.3f, Encke=%.3f, A_out=%.3f, F=%.3f%n",
			cFill, bFill, bFillLower, cassiniFill, aInnerFill, enckeFill, aOuterFill, fFill);

		check(innerGap == 0.0, "saturn inner clearance empty");
		check(cFill >= 0.12 && cFill <= 0.38, "saturn C ring faint fill=" + cFill);
		check(bFill >= 0.72 && bFillLower >= 0.72, "saturn B ring dense 2-block thick fill=" + bFill);
		check(cassiniFill >= 0.01 && cassiniFill <= 0.15 && cassiniFill < cFill && cassiniFill < aInnerFill,
			"saturn Cassini Division sparse non-empty fill=" + cassiniFill);
		check(aInnerFill >= 0.45 && aInnerFill <= 0.78, "saturn A ring inner fill=" + aInnerFill);
		check(enckeFill <= 0.12 && enckeFill < aInnerFill * 0.25 && enckeFill < aOuterFill * 0.25,
			"saturn Encke gap distinct fill=" + enckeFill);
		check(aOuterFill >= 0.38 && aOuterFill <= 0.75, "saturn A ring outer fill=" + aOuterFill);
		check(rocheGap == 0.0, "saturn Roche gap clear before F ring");
		check(fFill >= 0.15 && fFill <= 0.68, "saturn F ring visible fill=" + fFill);
		check(outsideFill == 0.0, "saturn rings stay within extent");

		// Thinness & full-scale determinism outside primary sphere.
		int bound = extent / 2;
		for (int x = -bound; x <= bound; x += 2) {
			for (int z = -bound; z <= bound; z += 2) {
				double pr = Math.hypot(x, z);
				if (pr > r + 2.0) {
					check(a.voxelAt(x, 1, z) == 0 && a.voxelAt(x, 2, z) == 0 && a.voxelAt(x, -2, z) == 0,
						"saturn ring thickness <= 2 blocks");
					check(a.voxelAt(x, 0, z) == b.voxelAt(x, 0, z) && a.voxelAt(x, -1, z) == b.voxelAt(x, -1, z),
						"saturn ring deterministic across runs");
				}
			}
		}
	}

	private static double ringFillInBand(Blueprint bp, double rInner, double rOuter, int minY, int maxY) {
		double rInSq = rInner * rInner;
		double rOutSq = rOuter * rOuter;
		int maxCoord = (int) Math.ceil(rOuter);
		long total = 0;
		long solid = 0;
		for (int x = -maxCoord; x <= maxCoord; x++) {
			for (int z = -maxCoord; z <= maxCoord; z++) {
				double rSq = (double) x * x + (double) z * z;
				if (rSq < rInSq || rSq > rOutSq) continue;
				for (int y = minY; y <= maxY; y++) {
					total++;
					if (bp.voxelAt(x, y, z) != 0) solid++;
				}
			}
		}
		check(total > 200, "sampled sufficient ring slots in band");
		return (double) solid / total;
	}

	private static double surfaceRadiusAlong(Blueprint bp, double x, double y, double z) {
		double len = Math.sqrt(x * x + y * y + z * z);
		double ux = x / len, uy = y / len, uz = z / len;
		double radius = bp.diameter() / 2.0;
		double maxScan = Math.ceil(radius * (1.0 + BlueprintGenerator.MAX_RELIEF_FRAC)) + 2.0;
		double minScan = Math.max(1.0, Math.floor(radius * (1.0 - BlueprintGenerator.MAX_RELIEF_FRAC)) - 2.0);
		for (double r = maxScan; r >= minScan; r -= 0.5) {
			int vx = (int) Math.round(ux * r);
			int vy = (int) Math.round(uy * r);
			int vz = (int) Math.round(uz * r);
			if (bp.voxelAt(vx, vy, vz) != 0) {
				return Math.sqrt((double) vx * vx + (double) vy * vy + (double) vz * vz);
			}
		}
		return 0.0;
	}

	// ------------------------------------------------------------------ util

	private static void check(boolean condition, String message) {
		assertions++;
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
