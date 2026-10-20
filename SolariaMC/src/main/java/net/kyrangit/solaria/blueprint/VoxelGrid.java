package net.kyrangit.solaria.blueprint;

import java.util.Arrays;

/**
 * Dense voxel grid addressed (x, y, z) with palette indices.
 * Index 0 is air (empty); positive indices reference the palette.
 * Iteration order is always x → y → z for determinism.
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

	public int sizeX() {
		return sizeX;
	}

	public int sizeY() {
		return sizeY;
	}

	public int sizeZ() {
		return sizeZ;
	}

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
			if (b != 0) {
				count++;
			}
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
