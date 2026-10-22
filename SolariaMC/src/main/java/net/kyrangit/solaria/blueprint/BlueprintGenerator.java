package net.kyrangit.solaria.blueprint;

import net.kyrangit.solaria.logging.ReasonCode;

import java.util.EnumMap;
import java.util.Map;

/**
 * Deterministic voxel generators for all ten astronomy classes.
 *
 * <h2>Determinism contract</h2>
 * Given the same {@link AstronomyClass}, the same {@code blocks_per_unit}, and the
 * same {@code max_size_blocks}, {@link #generate} returns a {@link Blueprint} whose
 * grid snapshot is byte-identical on every machine. The generators:
 * <ul>
 *   <li>iterate voxels in {@code x -> y -> z} order (matching {@link VoxelGrid}),</li>
 *   <li>use {@link VoxelNoise} (integer hashes, trilinear value noise) instead of
 *       {@link java.util.Random},</li>
 *   <li>use a single, documented 32-bit seed per class so the output never silently
 *       drifts between builds.</li>
 * </ul>
 *
 * <h2>Scale and cap</h2>
 * The caller supplies {@code blocksPerUnit} (config scale, default {@code 1.0}) and
 * {@code maxSizeBlocks} (config cap, default {@code 32768}). The generator computes
 * an effective scale that keeps the structure under the cap:
 * <ol>
 *   <li>Start with the requested scale.</li>
 *   <li>If the resulting block count exceeds the cap, halve the scale and try again,
 *       up to {@value #MAX_SCALE_ATTEMPTS} attempts.</li>
 *   <li>If the smallest attempted structure still exceeds the cap, throw
 *       {@link BlueprintException} with {@link ReasonCode#CAP_EXCEEDED}.</li>
 * </ol>
 * This is the "scale down or reject" rule from plan M2.3; it is never silently
 * truncated.
 *
 * <h2>Palette use</h2>
 * All generators share the single {@link Palette#create() hardcoded palette}. Every
 * block id used here is one of the 19 safe blocks in that palette, so the palette's
 * {@link MaterialPalette.Builder} validation guards against any gravity, fluid, or
 * harmful blocks slipping in through either path.
 */
public final class BlueprintGenerator {

	/** Hard upper bound on scale-down iterations before we give up and raise {@code CAP_EXCEEDED}. */
	public static final int MAX_SCALE_ATTEMPTS = 8;

	/**
	 * Base diameters for each class at {@code blocks_per_unit = 1.0}. These are
	 * NOT proportional to real astronomical sizes (plan M2.3 bullet 1); they are
	 * chosen so each shape is recognisable at the default cap of 32768 voxels.
	 */
	private static final Map<AstronomyClass, Integer> BASE_DIAMETERS = new EnumMap<>(AstronomyClass.class);
	/** Per-class deterministic seed. Change this and every voxel for that class may change. */
	private static final Map<AstronomyClass, Integer> SEEDS = new EnumMap<>(AstronomyClass.class);

	static {
		BASE_DIAMETERS.put(AstronomyClass.SUN, 17);
		BASE_DIAMETERS.put(AstronomyClass.EARTH, 15);
		BASE_DIAMETERS.put(AstronomyClass.MOON, 13);
		BASE_DIAMETERS.put(AstronomyClass.MARS, 13);
		BASE_DIAMETERS.put(AstronomyClass.JUPITER, 19);
		BASE_DIAMETERS.put(AstronomyClass.VENUS, 15);
		BASE_DIAMETERS.put(AstronomyClass.SATURN, 15); // + ring, see generator
		BASE_DIAMETERS.put(AstronomyClass.NEPTUNE, 15);
		BASE_DIAMETERS.put(AstronomyClass.URANUS, 13); // + near-vertical ring
		BASE_DIAMETERS.put(AstronomyClass.MERCURY, 11);

		SEEDS.put(AstronomyClass.SUN, 0x50170001);
		SEEDS.put(AstronomyClass.EARTH, 0xEA270002);
		SEEDS.put(AstronomyClass.MOON, 0x40070003);
		SEEDS.put(AstronomyClass.MARS, 0x3A750004);
		SEEDS.put(AstronomyClass.JUPITER, 0x37970005);
		SEEDS.put(AstronomyClass.VENUS, 0x7E700006);
		SEEDS.put(AstronomyClass.SATURN, 0x5A740007);
		SEEDS.put(AstronomyClass.NEPTUNE, 0x7EB70008);
		SEEDS.put(AstronomyClass.URANUS, 0x7A700009);
		SEEDS.put(AstronomyClass.MERCURY, 0x3E7C000A);
	}

	private BlueprintGenerator() {
	}

	/** Base (unscaled) diameter for the primary body of {@code astronomyClass}. */
	public static int baseDiameter(AstronomyClass astronomyClass) {
		return BASE_DIAMETERS.get(astronomyClass);
	}

	/**
	 * Generate the blueprint for {@code astronomyClass}, scaling down automatically
	 * if the requested size exceeds {@code maxSizeBlocks}.
	 *
	 * @throws BlueprintException with {@link ReasonCode#CAP_EXCEEDED} if even the
	 *                            smallest attempted scale still exceeds the cap, or
	 *                            with {@link ReasonCode#INPUT_INVALID} if the inputs
	 *                            are out of range.
	 */
	public static Blueprint generate(AstronomyClass astronomyClass, double blocksPerUnit, int maxSizeBlocks) {
		if (astronomyClass == null) {
			throw new BlueprintException(ReasonCode.INPUT_INVALID, "astronomyClass is required");
		}
		if (!(blocksPerUnit > 0.0) || Double.isNaN(blocksPerUnit) || Double.isInfinite(blocksPerUnit)) {
			throw new BlueprintException(ReasonCode.INPUT_INVALID,
				"blocks_per_unit must be a finite positive number, got " + blocksPerUnit);
		}
		if (maxSizeBlocks <= 0) {
			throw new BlueprintException(ReasonCode.INPUT_INVALID,
				"max_size_blocks must be positive, got " + maxSizeBlocks);
		}
		MaterialPalette palette = Palette.create();
		double scale = blocksPerUnit;
		for (int attempt = 0; attempt < MAX_SCALE_ATTEMPTS; attempt++) {
			Blueprint candidate = buildAt(astronomyClass, palette, scale);
			if (candidate.blockCount() <= maxSizeBlocks) {
				return candidate;
			}
			scale *= 0.5;
			if (scale < 1.0 / 1024.0) {
				break;
			}
		}
		throw new BlueprintException(ReasonCode.CAP_EXCEEDED,
			"Blueprint for " + astronomyClass.id() + " exceeds max_size_blocks=" + maxSizeBlocks
				+ " at every attempted scale (started at blocks_per_unit=" + blocksPerUnit + ").");
	}

	/** Build the blueprint at an exact scale, without any cap check. Package-private for tests. */
	static Blueprint buildAt(AstronomyClass astronomyClass, MaterialPalette palette, double scale) {
		int base = BASE_DIAMETERS.get(astronomyClass);
		int diameter = Math.max(3, (int) Math.round(base * scale));
		// odd diameters give a true integer centre, which keeps the sphere symmetric.
		if ((diameter & 1) == 0) {
			diameter++;
		}
		int seed = SEEDS.get(astronomyClass);
		return switch (astronomyClass) {
			case SUN -> BlueprintShapes.sun(palette, diameter, scale, seed);
			case EARTH -> BlueprintShapes.earth(palette, diameter, scale, seed);
			case MOON -> BlueprintShapes.moon(palette, diameter, scale, seed);
			case MARS -> BlueprintShapes.mars(palette, diameter, scale, seed);
			case JUPITER -> BlueprintShapes.jupiter(palette, diameter, scale, seed);
			case VENUS -> BlueprintShapes.venus(palette, diameter, scale, seed);
			case SATURN -> BlueprintShapes.saturn(palette, diameter, scale, seed);
			case NEPTUNE -> BlueprintShapes.neptune(palette, diameter, scale, seed);
			case URANUS -> BlueprintShapes.uranus(palette, diameter, scale, seed);
			case MERCURY -> BlueprintShapes.mercury(palette, diameter, scale, seed);
		};
	}
}
