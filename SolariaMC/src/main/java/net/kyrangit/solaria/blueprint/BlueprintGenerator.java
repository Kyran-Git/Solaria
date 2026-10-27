package net.kyrangit.solaria.blueprint;

import net.kyrangit.solaria.logging.ReasonCode;

import java.util.EnumMap;
import java.util.Map;

/**
 * Deterministic voxel generators for all ten astronomy classes (blueprint_v2).
 *
 * <h2>Determinism contract</h2>
 * Given the same {@link AstronomyClass} and the same {@code blocksPerUnit},
 * {@link #generate} returns a {@link Blueprint} whose {@link Blueprint#voxelAt}
 * produces an identical result at every coordinate on every machine. The generators:
 * <ul>
 *   <li>use {@link VoxelNoise} (integer hashes, trilinear value noise) instead of
 *       {@link java.util.Random},</li>
 *   <li>use a single, documented 32-bit seed per class so the output never silently
 *       drifts between builds,</li>
 *   <li>are pure functions: the result at (x,y,z) depends only on the class seed,
 *       the diameter, and the local coordinates — no mutable state, no previous voxels.</li>
 * </ul>
 *
 * <h2>Scale and cap</h2>
 * The caller supplies {@code blocksPerUnit} (default {@code 1.0}).
 * The generator computes an odd diameter = round(baseDiameter × blocksPerUnit).
 * If the full bounding box (including rings) exceeds {@value #MAX_EXTENT_BLOCKS} blocks
 * in any axis, it raises {@link BlueprintException} with {@link ReasonCode#CAP_EXCEEDED}.
 * The size is the whole point of this revision — shapes are either built at the requested
 * size or rejected. <em>There is no automatic shrinking.</em>
 */
public final class BlueprintGenerator {

	/**
	 * Maximum bounding-box edge length in blocks, including rings. Fits the 512-block dimension height.
	 * A shape whose bounding box exceeds this in any axis is rejected with CAP_EXCEEDED.
	 */
	public static final int MAX_EXTENT_BLOCKS = 500;

	/**
	 * Base diameters for each class at {@code blocks_per_unit = 1.0}.
	 * These are NOT proportional to real astronomical sizes; they are chosen so each
	 * shape is large enough for a student to fly around and identify features.
	 */
	private static final Map<AstronomyClass, Integer> BASE_DIAMETERS = new EnumMap<>(AstronomyClass.class);

	/** Per-class deterministic seed. Changing a seed changes every voxel for that class. */
	private static final Map<AstronomyClass, Integer> SEEDS = new EnumMap<>(AstronomyClass.class);

	static {
		BASE_DIAMETERS.put(AstronomyClass.SUN,     499);
		BASE_DIAMETERS.put(AstronomyClass.EARTH,   285);
		BASE_DIAMETERS.put(AstronomyClass.MOON,    175);
		BASE_DIAMETERS.put(AstronomyClass.MARS,    211);
		BASE_DIAMETERS.put(AstronomyClass.JUPITER, 475);
		BASE_DIAMETERS.put(AstronomyClass.VENUS,   281);
		BASE_DIAMETERS.put(AstronomyClass.SATURN,  261); // including rings: ceil(261 * 1.9) = 496
		BASE_DIAMETERS.put(AstronomyClass.URANUS,  311); // including rings: ceil(311 * 1.6) = 498
		BASE_DIAMETERS.put(AstronomyClass.NEPTUNE, 351);
		BASE_DIAMETERS.put(AstronomyClass.MERCURY, 151);

		SEEDS.put(AstronomyClass.SUN,     0x50170001);
		SEEDS.put(AstronomyClass.EARTH,   0xEA270002);
		SEEDS.put(AstronomyClass.MOON,    0x40070003);
		SEEDS.put(AstronomyClass.MARS,    0x3A750004);
		SEEDS.put(AstronomyClass.JUPITER, 0x37970005);
		SEEDS.put(AstronomyClass.VENUS,   0x7E700006);
		SEEDS.put(AstronomyClass.SATURN,  0x5A740007);
		SEEDS.put(AstronomyClass.URANUS,  0x7A700009);
		SEEDS.put(AstronomyClass.NEPTUNE, 0x7EB70008);
		SEEDS.put(AstronomyClass.MERCURY, 0x2E6C000A);
	}

	private BlueprintGenerator() {
	}

	/** Base (unscaled) diameter for the primary body of {@code astronomyClass}. */
	public static int baseDiameter(AstronomyClass astronomyClass) {
		return BASE_DIAMETERS.get(astronomyClass);
	}

	/**
	 * Generate the blueprint for {@code astronomyClass} at the given scale.
	 *
	 * @param blocksPerUnit multiplier on the base diameter (default {@code 1.0})
	 * @throws BlueprintException with {@link ReasonCode#CAP_EXCEEDED} if the bounding
	 *                            box exceeds {@value #MAX_EXTENT_BLOCKS} blocks in any axis, or
	 *                            with {@link ReasonCode#INPUT_INVALID} if inputs are out of range.
	 */
	public static Blueprint generate(AstronomyClass astronomyClass, double blocksPerUnit) {
		if (astronomyClass == null) {
			throw new BlueprintException(ReasonCode.INPUT_INVALID, "astronomyClass is required");
		}
		if (!(blocksPerUnit > 0.0) || Double.isNaN(blocksPerUnit) || Double.isInfinite(blocksPerUnit)) {
			throw new BlueprintException(ReasonCode.INPUT_INVALID,
				"blocks_per_unit must be a finite positive number, got " + blocksPerUnit);
		}
		MaterialPalette palette = Palette.create();
		return buildAt(astronomyClass, palette, blocksPerUnit);
	}

	/** Build the blueprint at an exact scale. Package-private for tests. */
	static Blueprint buildAt(AstronomyClass astronomyClass, MaterialPalette palette, double scale) {
		int base = BASE_DIAMETERS.get(astronomyClass);
		int diameter = Math.max(3, (int) Math.round(base * scale));
		if ((diameter & 1) == 0) {
			diameter++;
		}
		int seed = SEEDS.get(astronomyClass);

		// Compute bounding-box extent (diameter for most; ring extends Saturn/Uranus further).
		int extent = blueprintExtent(astronomyClass, diameter);
		if (extent > MAX_EXTENT_BLOCKS) {
			throw new BlueprintException(ReasonCode.CAP_EXCEEDED,
				"Blueprint for " + astronomyClass.id() + " at scale " + scale
					+ " has bounding-box extent " + extent + " > max " + MAX_EXTENT_BLOCKS + ".");
		}

		return switch (astronomyClass) {
			case SUN     -> BlueprintShapes.sun(palette, diameter, scale, seed);
			case EARTH   -> BlueprintShapes.earth(palette, diameter, scale, seed);
			case MOON    -> BlueprintShapes.moon(palette, diameter, scale, seed);
			case MARS    -> BlueprintShapes.mars(palette, diameter, scale, seed);
			case JUPITER -> BlueprintShapes.jupiter(palette, diameter, scale, seed);
			case VENUS   -> BlueprintShapes.venus(palette, diameter, scale, seed);
			case SATURN  -> BlueprintShapes.saturn(palette, diameter, scale, seed);
			case URANUS  -> BlueprintShapes.uranus(palette, diameter, scale, seed);
			case NEPTUNE -> BlueprintShapes.neptune(palette, diameter, scale, seed);
			case MERCURY -> BlueprintShapes.mercury(palette, diameter, scale, seed);
		};
	}

	/**
	 * Returns the largest bounding-box side length for a class at the given body diameter.
	 * For ringed planets the ring extends the bounding box beyond the body diameter.
	 */
	static int blueprintExtent(AstronomyClass cls, int diameter) {
		return switch (cls) {
			// Saturn ring outer radius ≈ 1.9 × body radius → full extent = 2 × 1.9r = 3.8r
			case SATURN -> (int) Math.ceil(diameter * 1.9);
			// Uranus ring outer radius ≈ 1.6 × body radius → full extent ≈ 3.2r
			case URANUS -> (int) Math.ceil(diameter * 1.6);
			default     -> diameter;
		};
	}
}
