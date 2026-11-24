package net.kyrangit.solaria.blueprint;

/**
 * Deterministic surface painting primitives built strictly on {@link VoxelNoise}.
 *
 * <p>All sampling functions map coordinates onto 3D space or the unit sphere
 * {@code S^2} so there is zero polar pinching and zero radial seam artefacts.
 * Every method is pure and allocation-free.
 */
public final class SurfacePainter {
	private SurfacePainter() {
	}

	/** Clamp {@code v} into {@code [0, 1]}. */
	public static double clamp01(double v) {
		if (v <= 0.0) {
			return 0.0;
		}
		return v >= 1.0 ? 1.0 : v;
	}

	/** Hermite smoothstep from {@code edge0} to {@code edge1}, clamped to {@code [0, 1]}. */
	public static double smoothstep(double edge0, double edge1, double x) {
		if (edge0 == edge1) {
			return x < edge0 ? 0.0 : 1.0;
		}
		double t = clamp01((x - edge0) / (edge1 - edge0));
		return t * t * (3.0 - 2.0 * t);
	}

	/** Multi-octave fractal Brownian motion (fBm) in {@code [0, 1]}. */
	public static double fbm(double x, double y, double z, int octaves,
	                         double persistence, double lacunarity, int seed) {
		if (octaves < 1) {
			throw new IllegalArgumentException("octaves must be >= 1: " + octaves);
		}
		double sum = 0.0;
		double amp = 1.0;
		double freq = 1.0;
		double maxAmp = 0.0;
		for (int i = 0; i < octaves; i++) {
			sum += VoxelNoise.noise3(x * freq, y * freq, z * freq, seed + i * 0x9E3779B9) * amp;
			maxAmp += amp;
			amp *= persistence;
			freq *= lacunarity;
		}
		return clamp01(sum / maxAmp);
	}

	/** Multi-octave fBm with standard defaults (persistence 0.5, lacunarity 2.0). */
	public static double fbm(double x, double y, double z, int octaves, int seed) {
		return fbm(x, y, z, octaves, 0.5, 2.0, seed);
	}

	/**
	 * Sample multi-octave fBm from the normalised 3D direction on the unit sphere,
	 * eliminating polar pinching and longitude seams. Output is in {@code [0, 1]}.
	 */
	public static double sphereFbm(double x, double y, double z, double frequency, int octaves,
	                               double persistence, double lacunarity, int seed) {
		double lenSq = x * x + y * y + z * z;
		double ux;
		double uy;
		double uz;
		if (lenSq <= 0.0) {
			ux = 0.0;
			uy = 1.0;
			uz = 0.0;
		} else {
			double inv = 1.0 / Math.sqrt(lenSq);
			ux = x * inv;
			uy = y * inv;
			uz = z * inv;
		}
		return fbm(ux * frequency, uy * frequency, uz * frequency, octaves, persistence, lacunarity, seed);
	}

	/** Sample unit-sphere fBm with default persistence 0.5 and lacunarity 2.0. */
	public static double sphereFbm(double x, double y, double z, double frequency, int octaves, int seed) {
		return sphereFbm(x, y, z, frequency, octaves, 0.5, 2.0, seed);
	}

	/**
	 * Domain-warped fBm sampled on the 3D unit sphere for organic swirls and eddies.
	 * Output is strictly in {@code [0, 1]}.
	 */
	public static double warpedSphereFbm(double x, double y, double z, double frequency, int octaves,
	                                     double warpFreq, double warpStrength, int seed) {
		double lenSq = x * x + y * y + z * z;
		double ux;
		double uy;
		double uz;
		if (lenSq <= 0.0) {
			ux = 0.0;
			uy = 1.0;
			uz = 0.0;
		} else {
			double inv = 1.0 / Math.sqrt(lenSq);
			ux = x * inv;
			uy = y * inv;
			uz = z * inv;
		}
		double wfX = ux * warpFreq;
		double wfY = uy * warpFreq;
		double wfZ = uz * warpFreq;
		double wx = (fbm(wfX, wfY, wfZ, 2, 0.5, 2.0, seed ^ 0x1F123BB5) - 0.5) * 2.0;
		double wy = (fbm(wfX, wfY, wfZ, 2, 0.5, 2.0, seed ^ 0x5C4B9E37) - 0.5) * 2.0;
		double wz = (fbm(wfX, wfY, wfZ, 2, 0.5, 2.0, seed ^ 0x2A8D7F13) - 0.5) * 2.0;
		return fbm((ux + wx * warpStrength) * frequency,
			(uy + wy * warpStrength) * frequency,
			(uz + wz * warpStrength) * frequency,
			octaves, 0.5, 2.0, seed);
	}

	/** Symmetric latitude gradient in {@code [0, 1]} from equator toward poles. */
	public static double latitudeGradient(double uy, double startLat, double endLat) {
		return smoothstep(startLat, endLat, Math.abs(uy));
	}

	/**
	 * Domain-warped latitude coordinate in {@code [-1, 1]} using 3D unit-sphere fBm
	 * so atmospheric bands curve and wave naturally without polar pinching.
	 */
	public static double warpedLatitude(double ux, double uy, double uz,
	                                    double warpFreq, double warpAmp, int octaves, int seed) {
		double w = (fbm(ux * warpFreq, uy * warpFreq, uz * warpFreq, octaves, 0.5, 2.0, seed) - 0.5) * 2.0;
		double val = uy + w * warpAmp;
		if (val < -1.0) {
			return -1.0;
		}
		return val > 1.0 ? 1.0 : val;
	}

	/**
	 * Softly blend between {@code matA} and {@code matB} across a boundary centred at
	 * {@code threshold} of total width {@code width}, using smooth spatial noise plus
	 * a deterministic per-voxel hash dither.
	 */
	public static int blend(int matA, int matB, double field, double threshold, double width,
	                        int x, int y, int z, int seed) {
		if (width <= 0.0) {
			return field < threshold ? matA : matB;
		}
		double lo = threshold - width * 0.5;
		double hi = threshold + width * 0.5;
		if (field <= lo) {
			return matA;
		}
		if (field >= hi) {
			return matB;
		}
		double t = (field - lo) / width;
		double n = VoxelNoise.noise3(x * 0.15, y * 0.15, z * 0.15, seed ^ 0x6A09E667);
		double p = clamp01(t + (n - 0.5) * 0.4);
		double h = VoxelNoise.hashUnit(x, y, z, seed ^ 0xBB67AE85);
		return h < p ? matB : matA;
	}

	/** Deterministic per-voxel hash in {@code [0, 1)}. */
	public static double ditherHash(int x, int y, int z, int seed) {
		return VoxelNoise.hashUnit(x, y, z, seed);
	}
}
