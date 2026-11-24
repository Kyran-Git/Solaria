package net.kyrangit.solaria;

import net.kyrangit.solaria.blueprint.Blueprint;
import net.kyrangit.solaria.blueprint.MaterialPalette;
import net.kyrangit.solaria.blueprint.MaterialRamp;
import net.kyrangit.solaria.blueprint.Palette;
import net.kyrangit.solaria.blueprint.SurfacePainter;

/**
 * Dependency-free tests for {@link SurfacePainter} and {@link MaterialRamp}:
 * determinism + golden hash, ramp monotonicity, dithered boundary width,
 * polar continuity (zero radial pinching), and throughput benchmark.
 */
public final class SurfacePainterTests {
	private static final long GOLDEN_HASH = 0xC1980B148160752CL;
	private static int assertions;

	private SurfacePainterTests() {
	}

	public static void main(String[] args) {
		testDeterminismAndGoldenHash();
		testRampMonotonicity();
		testDitheredBoundaryWidths();
		testPolarContinuity();
		benchmarkToolkit();
		System.out.println("Surface painter check (synthetic): " + assertions + " assertions passed.");
	}

	private static void testDeterminismAndGoldenHash() {
		int seed = 0x51A09C;
		for (int i = 0; i < 64; i++) {
			double x = (i - 32) * 0.13;
			double y = Math.sin(i * 0.7) * 0.95;
			double z = Math.cos(i * 0.4) * 0.85;

			double f1 = SurfacePainter.fbm(x, y, z, 4, seed);
			double f2 = SurfacePainter.fbm(x, y, z, 4, seed);
			check(Double.compare(f1, f2) == 0, "fbm is deterministic");
			check(f1 >= 0.0 && f1 <= 1.0, "fbm stays within [0, 1]");

			double s1 = SurfacePainter.sphereFbm(x, y, z, 3.5, 3, seed);
			double s2 = SurfacePainter.sphereFbm(x, y, z, 3.5, 3, seed);
			check(Double.compare(s1, s2) == 0, "sphereFbm is deterministic");
			check(s1 >= 0.0 && s1 <= 1.0, "sphereFbm stays within [0, 1]");

			double w1 = SurfacePainter.warpedSphereFbm(x, y, z, 3.0, 3, 2.0, 0.35, seed);
			double w2 = SurfacePainter.warpedSphereFbm(x, y, z, 3.0, 3, 2.0, 0.35, seed);
			check(Double.compare(w1, w2) == 0, "warpedSphereFbm is deterministic");
			check(w1 >= 0.0 && w1 <= 1.0, "warpedSphereFbm stays within [0, 1]");

			double wl1 = SurfacePainter.warpedLatitude(x, y, z, 3.0, 0.15, 3, seed);
			double wl2 = SurfacePainter.warpedLatitude(x, y, z, 3.0, 0.15, 3, seed);
			check(Double.compare(wl1, wl2) == 0, "warpedLatitude is deterministic");
			check(wl1 >= -1.0 && wl1 <= 1.0, "warpedLatitude stays within [-1, 1]");

			double lg = SurfacePainter.latitudeGradient(y, 0.70, 0.90);
			check(lg >= 0.0 && lg <= 1.0, "latitudeGradient stays within [0, 1]");
		}

		// Golden hash of deterministic test sphere grid (diameter 65, step 2).
		Blueprint sphereA = BlueprintPreview.testSphere(65);
		Blueprint sphereB = BlueprintPreview.testSphere(65);
		long hashA = hashSphereGrid(sphereA);
		long hashB = hashSphereGrid(sphereB);
		check(hashA == hashB, "testSphere produces identical grid hash across runs");
		System.out.printf("Observed testSphere(65) FNV-1a golden hash: 0x%016XL%n", hashA);
		if (GOLDEN_HASH != 0L) {
			check(hashA == GOLDEN_HASH,
				String.format("testSphere(65) golden hash matches expected 0x%016XL (got 0x%016XL)",
					GOLDEN_HASH, hashA));
		}
	}

	private static long hashSphereGrid(Blueprint bp) {
		long fnv = 0xCBF29CE484222325L;
		for (int x = -32; x <= 32; x += 2) {
			for (int y = -32; y <= 32; y += 2) {
				for (int z = -32; z <= 32; z += 2) {
					int v = bp.voxelAt(x, y, z);
					fnv ^= (v & 0xFFL);
					fnv *= 0x100000001B3L;
				}
			}
		}
		return fnv;
	}

	private static void testRampMonotonicity() {
		MaterialPalette palette = Palette.create();
		MaterialRamp ramp = MaterialRamp.of(palette,
			Palette.BLACK_CONCRETE,
			Palette.GRAY_CONCRETE,
			Palette.STONE,
			Palette.ANDESITE,
			Palette.LIGHT_GRAY_CONCRETE,
			Palette.WHITE_CONCRETE);
		check(ramp.size() == 6, "ramp has 6 stops");
		check(ramp.indexFor(0.0) == 0, "ramp indexFor(0.0) == 0");
		check(ramp.indexFor(1.0) == 5, "ramp indexFor(1.0) == 5");

		int prevIdx = ramp.indexFor(-0.2);
		boolean[] seen = new boolean[ramp.size()];
		for (int step = 0; step <= 1400; step++) {
			double t = -0.2 + step * 0.001;
			int idx = ramp.indexFor(t);
			check(idx >= prevIdx, "ramp indexFor is monotonic at t=" + t);
			seen[idx] = true;
			prevIdx = idx;
		}
		for (int i = 0; i < seen.length; i++) {
			check(seen[i], "ramp hits stop " + i);
		}

		// Verify sampleDithered average stop strictly increases across t.
		double prevAvg = -1.0;
		for (double t = 0.0; t <= 1.0001; t += 0.2) {
			long sum = 0;
			int count = 0;
			for (int i = 0; i < 1000; i++) {
				int v = ramp.sampleDithered(t, 0.30, i * 3, (i * 7) & 31, (i * 11) & 31, 0x3333);
				int stop = stopIndexOf(ramp, v);
				check(stop >= 0, "sampleDithered returned member of ramp");
				sum += stop;
				count++;
			}
			double avg = (double) sum / count;
			check(avg > prevAvg, "sampleDithered mean stop increases at t=" + t);
			prevAvg = avg;
		}
	}

	private static int stopIndexOf(MaterialRamp ramp, int mat) {
		for (int i = 0; i < ramp.size(); i++) {
			if (ramp.at(i) == mat) {
				return i;
			}
		}
		return -1;
	}

	private static void testDitheredBoundaryWidths() {
		double[] widths = {0.08, 0.20, 0.40};
		int seed = 0x1234;
		for (double width : widths) {
			double lo = 0.5 - width * 0.5;
			double hi = 0.5 + width * 0.5;
			check(fractionOfB(lo, width, 4096, seed) == 0.0,
				"zero bleed at lower edge for width=" + width);
			check(fractionOfB(lo - 0.01, width, 4096, seed) == 0.0,
				"zero bleed below lower edge for width=" + width);
			check(fractionOfB(hi, width, 4096, seed) == 1.0,
				"full coverage at upper edge for width=" + width);
			check(fractionOfB(hi + 0.01, width, 4096, seed) == 1.0,
				"full coverage above upper edge for width=" + width);

			double mid = fractionOfB(0.5, width, 4096, seed);
			check(mid >= 0.42 && mid <= 0.58,
				"midpoint blend balanced (" + mid + ") for width=" + width);

			double fLow = -1.0;
			double fHigh = -1.0;
			for (int step = 0; step <= 200; step++) {
				double f = step * 0.005;
				double frac = fractionOfB(f, width, 2048, seed);
				if (fLow < 0.0 && frac > 0.01) {
					fLow = f;
				}
				if (frac < 0.99) {
					fHigh = f;
				}
			}
			double measuredWidth = fHigh - fLow;
			double tol = Math.max(0.025, width * 0.20);
			check(Math.abs(measuredWidth - width) <= tol,
				String.format("measured dither width %.3f matches target %.3f (tol %.3f)",
					measuredWidth, width, tol));
		}
	}

	private static double fractionOfB(double field, double width, int samples, int seed) {
		int countB = 0;
		for (int i = 0; i < samples; i++) {
			int x = (i & 31) - 16;
			int y = ((i >> 5) & 15) - 8;
			int z = (i >> 9) - 4;
			if (SurfacePainter.blend(1, 2, field, 0.5, width, x, y, z, seed) == 2) {
				countB++;
			}
		}
		return (double) countB / samples;
	}

	private static void testPolarContinuity() {
		double lat = Math.toRadians(89.0);
		double r = Math.cos(lat);
		double uy = Math.sin(lat);
		int steps = 72;
		double prevSphere = Double.NaN;
		double prevWarped = Double.NaN;
		double maxDeltaSphere = 0.0;
		double maxDeltaWarped = 0.0;

		for (int i = 0; i <= steps; i++) {
			double lon = (2.0 * Math.PI * (i % steps)) / steps;
			double ux = r * Math.cos(lon);
			double uz = r * Math.sin(lon);
			double s = SurfacePainter.sphereFbm(ux, uy, uz, 4.0, 3, 0xA11CE);
			double w = SurfacePainter.warpedSphereFbm(ux, uy, uz, 4.0, 3, 2.2, 0.32, 0xB0B);
			if (!Double.isNaN(prevSphere)) {
				maxDeltaSphere = Math.max(maxDeltaSphere, Math.abs(s - prevSphere));
				maxDeltaWarped = Math.max(maxDeltaWarped, Math.abs(w - prevWarped));
			}
			prevSphere = s;
			prevWarped = w;
		}
		check(maxDeltaSphere < 0.08, "polar ring sphereFbm continuous (maxDelta=" + maxDeltaSphere + ")");
		check(maxDeltaWarped < 0.08, "polar ring warpedSphereFbm continuous (maxDelta=" + maxDeltaWarped + ")");

		// Verify testSphere(161) north polar cap transitions cleanly to snow/white without wedge streaks.
		Blueprint sphere = BlueprintPreview.testSphere(161);
		MaterialPalette palette = sphere.palette();
		int ice = palette.indexOf(Palette.SNOW_BLOCK);
		int iceEdge = palette.indexOf(Palette.WHITE_CONCRETE);
		int capSamples = 0;
		int capMatches = 0;
		double radius = 80.5;
		for (int i = 0; i < 36; i++) {
			double lon = (2.0 * Math.PI * i) / 36.0;
			double polarLat = Math.toRadians(85.0);
			double vx = Math.round(radius * Math.cos(polarLat) * Math.cos(lon));
			double vy = Math.round(radius * Math.sin(polarLat));
			double vz = Math.round(radius * Math.cos(polarLat) * Math.sin(lon));
			int v = sphere.voxelAt((int) vx, (int) vy, (int) vz);
			if (v != 0) {
				capSamples++;
				if (v == ice || v == iceEdge) {
					capMatches++;
				}
			}
		}
		check(capSamples >= 30 && capMatches == capSamples,
			"testSphere north polar ring is clean polar cap (" + capMatches + "/" + capSamples + ")");
	}

	private static void benchmarkToolkit() {
		Blueprint sphere = BlueprintPreview.testSphere(161);
		MaterialPalette palette = sphere.palette();
		MaterialRamp ramp = MaterialRamp.of(palette,
			Palette.BROWN_CONCRETE, Palette.TERRACOTTA, Palette.ORANGE_TERRACOTTA, Palette.YELLOW_TERRACOTTA);
		double sink = 0.0;
		int intSink = 0;

		// Warmup JIT
		for (int i = 0; i < 50_000; i++) {
			double ux = Math.sin(i * 0.01);
			double uy = Math.cos(i * 0.013);
			double uz = Math.sin(i * 0.017);
			sink += SurfacePainter.sphereFbm(ux, uy, uz, 4.0, 3, 0x123);
			sink += SurfacePainter.warpedSphereFbm(ux, uy, uz, 4.0, 3, 2.0, 0.3, 0x456);
			intSink += ramp.sampleDithered((i & 255) / 255.0, 0.3, i, i >> 2, i >> 4, 0x789);
			intSink += sphere.voxelAt((i % 150) - 75, 40, ((i / 150) % 150) - 75);
		}

		final int iters = 200_000;

		long t0 = System.nanoTime();
		for (int i = 0; i < iters; i++) {
			sink += SurfacePainter.sphereFbm(0.31, 0.67, -0.67 + (i & 1023) * 1e-4, 4.0, 3, 0x123);
		}
		long sphereNs = System.nanoTime() - t0;

		t0 = System.nanoTime();
		for (int i = 0; i < iters; i++) {
			sink += SurfacePainter.warpedSphereFbm(0.31, 0.67, -0.67 + (i & 1023) * 1e-4, 4.0, 3, 2.0, 0.3, 0x456);
		}
		long warpedNs = System.nanoTime() - t0;

		t0 = System.nanoTime();
		for (int i = 0; i < iters; i++) {
			intSink += ramp.sampleDithered((i & 255) / 255.0, 0.3, i & 63, (i >> 6) & 63, (i >> 12) & 63, 0x789);
		}
		long rampNs = System.nanoTime() - t0;

		t0 = System.nanoTime();
		for (int i = 0; i < iters; i++) {
			intSink += sphere.voxelAt((i & 63) - 32, 75, ((i >> 6) & 63) - 32);
		}
		long voxelNs = System.nanoTime() - t0;

		if (sink == Double.NEGATIVE_INFINITY && intSink == Integer.MIN_VALUE) {
			throw new AssertionError("unreachable");
		}
		System.out.printf("Toolkit benchmark (%d calls): sphereFbm=%.1f ns (%.1f ms/1M), "
				+ "warpedSphereFbm=%.1f ns (%.1f ms/1M), sampleDithered=%.1f ns (%.1f ms/1M), "
				+ "testSphere.voxelAt=%.1f ns (%.1f ms/1M)%n",
			iters,
			(double) sphereNs / iters, sphereNs / 1e6 * (1_000_000.0 / iters),
			(double) warpedNs / iters, warpedNs / 1e6 * (1_000_000.0 / iters),
			(double) rampNs / iters, rampNs / 1e6 * (1_000_000.0 / iters),
			(double) voxelNs / iters, voxelNs / 1e6 * (1_000_000.0 / iters));
	}

	private static void check(boolean condition, String message) {
		assertions++;
		if (!condition) {
			throw new AssertionError("SurfacePainter test failed: " + message);
		}
	}
}
