package net.kyrangit.solaria.blueprint;

import java.util.Arrays;

/**
 * A fully-resolved voxel structure for one astronomy class.
 * Immutable after construction. The grid uses palette indices referencing the palette.
 *
 * <p>This is the {@code blueprint_v1} payload described in {@code docs/BLUEPRINTS.md}:
 * a class id, a material palette reference, a scale, and a size cap. It is independent
 * of any Minecraft save-file format.
 *
 * @param astronomyClass the class this structure represents
 * @param grid           the voxel grid; palette indices, 0 = air
 * @param palette        the material palette the indices refer to
 * @param blocksPerUnit  scale actually applied by the generator (may be smaller than
 *                       the requested {@code blocks_per_unit} if the structure had
 *                       to be scaled down to fit {@code max_size_blocks})
 * @param diameter       diameter of the primary body in blocks, after scaling
 */
public record Blueprint(
	AstronomyClass astronomyClass,
	VoxelGrid grid,
	MaterialPalette palette,
	double blocksPerUnit,
	int diameter
) {

	public Blueprint {
		if (!(blocksPerUnit > 0.0) || Double.isNaN(blocksPerUnit) || Double.isInfinite(blocksPerUnit)) {
			throw new IllegalArgumentException("blocksPerUnit must be finite and positive: " + blocksPerUnit);
		}
		if (diameter <= 0) {
			throw new IllegalArgumentException("diameter must be positive: " + diameter);
		}
	}

	/** Number of non-air blocks in the grid. */
	public int blockCount() {
		return grid.occupiedCount();
	}

	/** True when the two blueprints have identical class, grid dimensions, and voxel data. */
	public boolean hasSameVoxels(Blueprint other) {
		return other != null
			&& astronomyClass == other.astronomyClass
			&& grid.sizeX() == other.grid.sizeX()
			&& grid.sizeY() == other.grid.sizeY()
			&& grid.sizeZ() == other.grid.sizeZ()
			&& Arrays.equals(grid.snapshot(), other.grid.snapshot());
	}
}

