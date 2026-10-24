package net.kyrangit.solaria.blueprint;

/**
 * A fully-resolved voxel descriptor for one astronomy class (blueprint_v2).
 *
 * <p>A blueprint is <em>not</em> a stored grid. Voxels are computed on demand by
 * calling {@link #voxelAt(int, int, int)} with coordinates relative to the planet
 * centre. This makes million-voxel planets practical: chunks are generated as a
 * student flies near them, not upfront.
 *
 * <p>The descriptor is immutable after construction. It holds enough information to
 * reproduce every voxel deterministically:
 * <ul>
 *   <li>{@code astronomyClass} — selects the generator and its 32-bit seed.</li>
 *   <li>{@code palette} — the single shared 69-block palette.</li>
 *   <li>{@code blocksPerUnit} — the scale applied to the base diameter.</li>
 *   <li>{@code diameter} — primary-body diameter in blocks, always odd and ≥ 3.</li>
 * </ul>
 *
 * <p>The compact constructor rejects non-finite or non-positive scales and
 * non-positive diameters, so a Blueprint always describes a buildable structure.
 *
 * @param astronomyClass the class this structure represents
 * @param fn             pure function: (x,y,z) relative to centre → palette index (0=air)
 * @param palette        the 69-block shared palette the indices refer to
 * @param blocksPerUnit  scale actually applied by the generator
 * @param diameter       diameter of the primary body in blocks, after scaling
 */
public record Blueprint(
	AstronomyClass astronomyClass,
	VoxelFunction fn,
	MaterialPalette palette,
	double blocksPerUnit,
	int diameter
) {

	/** Pure function from position to palette index. 0 is air. Must be free of side-effects. */
	@FunctionalInterface
	public interface VoxelFunction {
		int voxelAt(int x, int y, int z);
	}

	public Blueprint {
		if (!(blocksPerUnit > 0.0) || Double.isNaN(blocksPerUnit) || Double.isInfinite(blocksPerUnit)) {
			throw new IllegalArgumentException("blocksPerUnit must be finite and positive: " + blocksPerUnit);
		}
		if (diameter <= 0) {
			throw new IllegalArgumentException("diameter must be positive: " + diameter);
		}
	}

	/**
	 * Returns the palette index at coordinates {@code (x, y, z)} relative to the planet centre.
	 * Returns 0 (air) for any position outside the shape.
	 */
	public int voxelAt(int x, int y, int z) {
		return fn.voxelAt(x, y, z);
	}

	/**
	 * Determinism oracle: sample a fixed set of positions for both blueprints and compare.
	 * Returns true when the two blueprints are for the same class and produce the same voxel
	 * at every sampled position within the bounding box.
	 *
	 * <p>Uses a cheap, evenly-spaced grid of sample points rather than an exhaustive scan,
	 * so it runs quickly even for 1,001-wide planets.
	 */
	public boolean hasSameVoxels(Blueprint other) {
		if (other == null || astronomyClass != other.astronomyClass || diameter != other.diameter) {
			return false;
		}
		int step = Math.max(1, diameter / 20);
		int half = diameter / 2;
		for (int x = -half; x <= half; x += step) {
			for (int y = -half; y <= half; y += step) {
				for (int z = -half; z <= half; z += step) {
					if (voxelAt(x, y, z) != other.voxelAt(x, y, z)) {
						return false;
					}
				}
			}
		}
		return true;
	}
}
