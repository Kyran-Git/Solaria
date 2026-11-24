package net.kyrangit.solaria.blueprint;

import java.util.List;

/**
 * Shared deterministic planetary ring model built on {@link VoxelNoise},
 * {@link SurfacePainter}, and {@link MaterialRamp}.
 *
 * <p>A {@code RingSystem} models a tilted circular disc composed of ordered radial
 * {@link Band}s around a planet of a given radius. Each band defines a continuous
 * radial/azimuthal density profile in {@code [0, 1]} realised statically via hash-based
 * dithering so sparse gaps read as scattered particles and dense bands as near-solid rings.
 *
 * <p>Out-of-plane and out-of-radius voxels are rejected in a few floating-point operations
 * without evaluating noise or hashes, keeping {@link #voxelAt} fast over mostly-air chunks.
 */
public final class RingSystem {

	/** Continuous radial and angular particle density profile in {@code [0, 1]}. */
	@FunctionalInterface
	public interface DensityProfile {
		/**
		 * Returns the target fill fraction at normalised radial position
		 * {@code radialT} in {@code [0, 1]} (from inner to outer edge) and
		 * in-plane azimuth {@code angleRad} in {@code [-pi, pi]}.
		 */
		double densityAt(double radialT, double angleRad);

		/** Uniform particle density across the entire band. */
		static DensityProfile constant(double density) {
			double d = SurfacePainter.clamp01(density);
			return (t, a) -> d;
		}

		/** Smooth flat-topped band fading cleanly at both edges over fraction {@code edgeFade}. */
		static DensityProfile smoothBand(double peakDensity, double edgeFade) {
			double peak = SurfacePainter.clamp01(peakDensity);
			double fade = Math.max(0.0, Math.min(0.5, edgeFade));
			return (t, a) -> {
				if (t < 0.0 || t > 1.0) {
					return 0.0;
				}
				if (fade <= 0.0) {
					return peak;
				}
				double edge = Math.min(
					SurfacePainter.smoothstep(0.0, fade, t),
					SurfacePainter.smoothstep(1.0, 1.0 - fade, t));
				return peak * edge;
			};
		}

		/** Concentric ringlets modulating {@code baseDensity} by sinusoidal waves across the band. */
		static DensityProfile ringlets(double baseDensity, double rippleAmp, double frequency, double edgeFade) {
			double fade = Math.max(0.0, Math.min(0.5, edgeFade));
			return (t, a) -> {
				if (t < 0.0 || t > 1.0) {
					return 0.0;
				}
				double wave = Math.sin(t * Math.PI * 2.0 * frequency);
				double local = SurfacePainter.clamp01(baseDensity + rippleAmp * wave);
				if (fade <= 0.0) {
					return local;
				}
				double edge = Math.min(
					SurfacePainter.smoothstep(0.0, fade, t),
					SurfacePainter.smoothstep(1.0, 1.0 - fade, t));
				return local * edge;
			};
		}
	}

	/** One concentric ring band measured in multiples of the parent planet's radius. */
	public record Band(
		double innerMultiple,
		double outerMultiple,
		DensityProfile density,
		MaterialRamp ramp,
		int thickness,
		int seed
	) {
		public Band {
			if (!Double.isFinite(innerMultiple) || innerMultiple <= 1.0) {
				throw new IllegalArgumentException("innerMultiple must be finite and > 1.0: " + innerMultiple);
			}
			if (!Double.isFinite(outerMultiple) || outerMultiple <= innerMultiple) {
				throw new IllegalArgumentException(
					"outerMultiple (" + outerMultiple + ") must exceed innerMultiple (" + innerMultiple + ")");
			}
			if (density == null) {
				throw new IllegalArgumentException("density profile must not be null");
			}
			if (ramp == null) {
				throw new IllegalArgumentException("material ramp must not be null");
			}
			if (thickness != 1 && thickness != 2) {
				throw new IllegalArgumentException("band thickness must be 1 or 2 blocks, got " + thickness);
			}
		}
	}

	private record PreparedBand(
		Band band,
		double innerSq,
		double outerSq,
		double innerR,
		double invSpan,
		double halfWidth
	) {
	}

	private final double tiltDegrees;
	private final double bodyRadius;
	private final List<Band> bands;
	private final PreparedBand[] prepared;
	private final double nx;
	private final double ny;
	private final double ux;
	private final double uy;
	private final double maxHalf;
	private final double minInnerSq;
	private final double maxOuterSq;
	private final double maxOuterMultiple;
	private final int maxThickness;

	private RingSystem(double tiltDegrees, double bodyRadius, List<Band> bands) {
		if (!Double.isFinite(tiltDegrees)) {
			throw new IllegalArgumentException("tiltDegrees must be finite: " + tiltDegrees);
		}
		if (!Double.isFinite(bodyRadius) || bodyRadius <= 0.0) {
			throw new IllegalArgumentException("bodyRadius must be finite and > 0: " + bodyRadius);
		}
		if (bands == null || bands.isEmpty()) {
			throw new IllegalArgumentException("RingSystem requires at least one band");
		}
		this.tiltDegrees = tiltDegrees;
		this.bodyRadius = bodyRadius;
		this.bands = List.copyOf(bands);

		double rad = Math.toRadians(tiltDegrees);
		this.nx = Math.sin(rad);
		this.ny = Math.cos(rad);
		this.ux = Math.cos(rad);
		this.uy = -Math.sin(rad);
		// Scaling thickness by dominant axis component gives an exact k-voxel half-open slab along columns.
		double dom = Math.max(Math.abs(nx), Math.abs(ny));

		this.prepared = new PreparedBand[this.bands.size()];
		double maxHalfAcc = 0.0;
		double minInnerAcc = Double.MAX_VALUE;
		double maxOuterAcc = 0.0;
		double maxMultAcc = 0.0;
		int maxThickAcc = 1;

		for (int i = 0; i < this.bands.size(); i++) {
			Band b = this.bands.get(i);
			double innerR = b.innerMultiple() * bodyRadius;
			double outerR = b.outerMultiple() * bodyRadius;
			double innerSq = innerR * innerR;
			double outerSq = outerR * outerR;
			double halfW = 0.5 * b.thickness() * dom;
			this.prepared[i] = new PreparedBand(b, innerSq, outerSq, innerR, 1.0 / (outerR - innerR), halfW);

			if (halfW > maxHalfAcc) {
				maxHalfAcc = halfW;
			}
			if (innerSq < minInnerAcc) {
				minInnerAcc = innerSq;
			}
			if (outerSq > maxOuterAcc) {
				maxOuterAcc = outerSq;
			}
			if (b.outerMultiple() > maxMultAcc) {
				maxMultAcc = b.outerMultiple();
			}
			if (b.thickness() > maxThickAcc) {
				maxThickAcc = b.thickness();
			}
		}

		this.maxHalf = maxHalfAcc;
		this.minInnerSq = minInnerAcc;
		this.maxOuterSq = maxOuterAcc;
		this.maxOuterMultiple = maxMultAcc;
		this.maxThickness = maxThickAcc;
	}

	/** Construct a ring system from an explicit list of bands. */
	public static RingSystem of(double tiltDegrees, double bodyRadius, List<Band> bands) {
		return new RingSystem(tiltDegrees, bodyRadius, bands);
	}

	/** Convenience overload accepting varargs bands. */
	public static RingSystem of(double tiltDegrees, double bodyRadius, Band... bands) {
		return new RingSystem(tiltDegrees, bodyRadius, List.of(bands));
	}

	public double tiltDegrees() {
		return tiltDegrees;
	}

	public double bodyRadius() {
		return bodyRadius;
	}

	public double maxOuterMultiple() {
		return maxOuterMultiple;
	}

	public int maxThickness() {
		return maxThickness;
	}

	public List<Band> bands() {
		return bands;
	}

	/**
	 * Deterministic palette index at coordinates {@code (x, y, z)} relative to the planet centre.
	 * Returns {@code 0} (air) when outside all ring slabs, radii, or filtered by particle dithering.
	 */
	public int voxelAt(int x, int y, int z) {
		double h = x * nx + y * ny;
		if (h < -maxHalf || h >= maxHalf) {
			return 0;
		}
		double pu = x * ux + y * uy;
		double rSq = pu * pu + (double) z * z;
		if (rSq < minInnerSq || rSq > maxOuterSq) {
			return 0;
		}

		double r = Math.sqrt(rSq);
		double angle = Double.NaN;
		for (PreparedBand pb : prepared) {
			if (rSq < pb.innerSq || rSq > pb.outerSq) {
				continue;
			}
			if (h < -pb.halfWidth || h >= pb.halfWidth) {
				continue;
			}
			if (Double.isNaN(angle)) {
				angle = Math.atan2(z, pu);
			}
			double t = (r - pb.innerR) * pb.invSpan;
			double density = SurfacePainter.clamp01(pb.band.density().densityAt(t, angle));
			if (density <= 0.0) {
				continue;
			}
			if (VoxelNoise.hashUnit(x, y, z, pb.band.seed()) < density) {
				double radialVar = VoxelNoise.noise3(r * 0.28, 0.0, 0.0, pb.band.seed() ^ 0x71E3);
				double matField = SurfacePainter.clamp01(0.65 * density + 0.35 * radialVar);
				return pb.band.ramp().sampleDithered(matField, 0.35, x, y, z, pb.band.seed() ^ 0x4A39);
			}
		}
		return 0;
	}
}
