package net.kyrangit.solaria.blueprint;

/**
 * A fully-resolved voxel structure for one astronomy class.
 * Immutable after construction. The grid uses palette indices referencing the palette.
 */
public record Blueprint(AstronomyClass astronomyClass, VoxelGrid grid, MaterialPalette palette) {

	/** Number of non-air blocks in the grid. */
	public int blockCount() {
		return grid.occupiedCount();
	}
}
