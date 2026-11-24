package net.kyrangit.solaria.blueprint;

import java.util.Arrays;

/**
 * Immutable, ordered list of palette block indices (from dark/low to light/high)
 * sampled monotonically by a continuous field value in {@code [0, 1]}.
 */
public final class MaterialRamp {
	private final int[] indices;

	private MaterialRamp(int[] indices) {
		this.indices = indices;
	}

	/** Build a ramp by resolving each Minecraft block ID against {@code palette}. */
	public static MaterialRamp of(MaterialPalette palette, String... blockIds) {
		if (palette == null) {
			throw new IllegalArgumentException("palette is required");
		}
		if (blockIds == null || blockIds.length == 0) {
			throw new IllegalArgumentException("ramp must contain at least one block ID");
		}
		int[] resolved = new int[blockIds.length];
		for (int i = 0; i < blockIds.length; i++) {
			resolved[i] = palette.indexOf(blockIds[i]);
		}
		return new MaterialRamp(resolved);
	}

	/** Build a ramp directly from validated 1-based palette indices. */
	public static MaterialRamp ofIndices(int... paletteIndices) {
		if (paletteIndices == null || paletteIndices.length == 0) {
			throw new IllegalArgumentException("ramp must contain at least one palette index");
		}
		int[] copy = Arrays.copyOf(paletteIndices, paletteIndices.length);
		for (int idx : copy) {
			if (idx < 1) {
				throw new IllegalArgumentException("palette index must be >= 1, got " + idx);
			}
		}
		return new MaterialRamp(copy);
	}

	public int size() {
		return indices.length;
	}

	public int at(int stop) {
		return indices[stop];
	}

	/** Monotonic stop index in {@code [0, size() - 1]} for a scalar {@code t} clamped to {@code [0, 1]}. */
	public int indexFor(double t) {
		double clamped = SurfacePainter.clamp01(t);
		return Math.min(indices.length - 1, (int) (clamped * indices.length));
	}

	/** Palette index at {@code t} using a hard quantised stop lookup. */
	public int sample(double t) {
		return indices[indexFor(t)];
	}

	/**
	 * Sample the ramp with hash-and-noise dithering across adjacent stops.
	 * {@code ditherWidth} is expressed in stop-fraction units ({@code 0..1}).
	 */
	public int sampleDithered(double t, double ditherWidth, int x, int y, int z, int seed) {
		if (indices.length == 1) {
			return indices[0];
		}
		double pos = SurfacePainter.clamp01(t) * (indices.length - 1);
		int lo = Math.min(indices.length - 2, (int) Math.floor(pos));
		double frac = pos - lo;
		return SurfacePainter.blend(indices[lo], indices[lo + 1], frac, 0.5, ditherWidth,
			x, y, z, seed ^ (lo * 0x45D9F3B));
	}
}
