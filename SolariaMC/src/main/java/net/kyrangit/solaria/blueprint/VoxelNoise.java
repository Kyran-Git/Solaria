package net.kyrangit.solaria.blueprint;

/**
 * Deterministic hash and value-noise helpers for the blueprint generators.
 *
 * <p>No {@link java.util.Random}, no floating-point platform variance beyond
 * IEEE-754 basic operations, and no iteration-order dependence. The same inputs
 * always produce the same output on every machine, which is what makes
 * {@code Blueprint} generation reproducible byte for byte.
 */
final class VoxelNoise {
	private VoxelNoise() {
	}

	/** SplitMix32 finaliser: a strong, cheap integer avalanche. */
	private static int mix(int value) {
		int h = value;
		h ^= h >>> 16;
		h *= 0x7feb352d;
		h ^= h >>> 15;
		h *= 0x846ca68b;
		h ^= h >>> 16;
		return h;
	}

	/** Deterministic hash of three integer coordinates plus a seed. */
	static int hash(int x, int y, int z, int seed) {
		int h = mix(x * 0x9E3779B1 + seed);
		h = mix(h ^ (y * 0x85EBCA77));
		h = mix(h ^ (z * 0xC2B2AE3D));
		return h;
	}

	/** Hash mapped to {@code [0, 1)}. */
	static double hashUnit(int x, int y, int z, int seed) {
		return (hash(x, y, z, seed) >>> 8) / (double) (1 << 24);
	}

	private static double smooth(double t) {
		return t * t * (3.0 - 2.0 * t);
	}

	private static double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}

	private static int floor(double value) {
		int i = (int) value;
		return value < i ? i - 1 : i;
	}

	/** Trilinearly interpolated value noise in {@code [0, 1]}. */
	static double noise3(double x, double y, double z, int seed) {
		int xi = floor(x);
		int yi = floor(y);
		int zi = floor(z);
		double fx = smooth(x - xi);
		double fy = smooth(y - yi);
		double fz = smooth(z - zi);

		double c000 = hashUnit(xi, yi, zi, seed);
		double c100 = hashUnit(xi + 1, yi, zi, seed);
		double c010 = hashUnit(xi, yi + 1, zi, seed);
		double c110 = hashUnit(xi + 1, yi + 1, zi, seed);
		double c001 = hashUnit(xi, yi, zi + 1, seed);
		double c101 = hashUnit(xi + 1, yi, zi + 1, seed);
		double c011 = hashUnit(xi, yi + 1, zi + 1, seed);
		double c111 = hashUnit(xi + 1, yi + 1, zi + 1, seed);

		double x00 = lerp(c000, c100, fx);
		double x10 = lerp(c010, c110, fx);
		double x01 = lerp(c001, c101, fx);
		double x11 = lerp(c011, c111, fx);
		return lerp(lerp(x00, x10, fy), lerp(x01, x11, fy), fz);
	}

	/**
	 * Deterministic unit vector on the sphere. Used to place craters and storm
	 * spots without a random-number generator.
	 */
	static double[] unitVector(int seed) {
		double u = hashUnit(seed, 0, 0, 0x51ED);
		double v = hashUnit(seed, 1, 0, 0x51ED);
		double theta = 2.0 * Math.PI * u;
		double z = 2.0 * v - 1.0;
		double r = Math.sqrt(Math.max(0.0, 1.0 - z * z));
		return new double[]{r * Math.cos(theta), z, r * Math.sin(theta)};
	}
}
