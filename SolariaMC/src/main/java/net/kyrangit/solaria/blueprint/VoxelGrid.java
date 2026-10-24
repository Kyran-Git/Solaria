package net.kyrangit.solaria.blueprint;

import java.util.Arrays;

/**
 * Dense voxel grid addressed (x, y, z) with palette indices.
 * Index 0 is air (empty); positive indices reference the palette.
 *
 * <p>This class is used for previews and tests — it materialises a region of a
 * {@link Blueprint} into memory so slices can be inspected. It is <em>not</em> the
 * primary representation in blueprint_v2: voxels are generated on demand via
 * {@link Blueprint#voxelAt}.
 */
public final class VoxelGrid {
	private final int sizeX;
	private final int sizeY;
	private final int sizeZ;
	private final byte[] data; // palette index per voxel; 0 = air

	public VoxelGrid(int sizeX, int sizeY, int sizeZ) {
		if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
			throw new IllegalArgumentException("Grid dimensions must be positive.");
		}
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.sizeZ = sizeZ;
		this.data = new byte[sizeX * sizeY * sizeZ];
	}

	/**
	 * Sample a square slice of {@code blueprint} at the given Y offset from the planet centre.
	 * The grid is {@code diameter × 1 × diameter}; coordinate origin = planet centre.
	 *
	 * @param blueprint  the blueprint to sample
	 * @param yOffset    y coordinate relative to planet centre
	 * @param halfExtent half the sampling window in x and z (use diameter/2 for a full slice)
	 */
	public static VoxelGrid sampleSlice(Blueprint blueprint, int yOffset, int halfExtent) {
		int size = halfExtent * 2 + 1;
		VoxelGrid grid = new VoxelGrid(size, 1, size);
		for (int xi = 0; xi < size; xi++) {
			for (int zi = 0; zi < size; zi++) {
				int x = xi - halfExtent;
				int z = zi - halfExtent;
				grid.set(xi, 0, zi, blueprint.voxelAt(x, yOffset, z));
			}
		}
		return grid;
	}

	public int sizeX() { return sizeX; }
	public int sizeY() { return sizeY; }
	public int sizeZ() { return sizeZ; }

	public void set(int x, int y, int z, int paletteIndex) {
		data[index(x, y, z)] = (byte) paletteIndex;
	}

	public int get(int x, int y, int z) {
		return data[index(x, y, z)] & 0xFF;
	}

	/** Number of non-air voxels. */
	public int occupiedCount() {
		int count = 0;
		for (byte b : data) {
			if (b != 0) count++;
		}
		return count;
	}

	/** Return a defensive copy of the raw data for deterministic comparison. */
	public byte[] snapshot() {
		return Arrays.copyOf(data, data.length);
	}

	private int index(int x, int y, int z) {
		return (x * sizeY + y) * sizeZ + z;
	}
}
