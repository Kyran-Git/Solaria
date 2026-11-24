package net.kyrangit.solaria;

import net.kyrangit.solaria.blueprint.Blueprint;
import net.kyrangit.solaria.blueprint.MaterialPalette;
import net.kyrangit.solaria.blueprint.MaterialRamp;
import net.kyrangit.solaria.blueprint.Palette;
import net.kyrangit.solaria.blueprint.RingSystem;
import net.kyrangit.solaria.blueprint.RingSystem.Band;
import net.kyrangit.solaria.blueprint.RingSystem.DensityProfile;
import net.kyrangit.solaria.blueprint.SurfacePainter;

/**
 * Dependency-free unit tests for {@link RingSystem}:
 * parameter validation, byte-for-byte determinism and golden hash,
 * 1–2 block thickness bounds across tilts, measured fill-density vs profile match,
 * azimuthal arc profiles, and air-rejection + in-plane throughput benchmark.
 */
public final class RingSystemTests {
	private static final long GOLDEN_HASH = 0x41BCE0AE647E2064L;
	private static int assertions;

	private RingSystemTests() {
	}

	public static void main(String[] args) {
		testValidation();
		testDeterminismAndGoldenHash();
		testMaxThicknessAllTilts();
		testFillDensityMatchesProfile();
		testAzimuthalArcDensity();
		benchmarkRingSystem();
		System.out.println("Ring system check (synthetic): " + assertions + " assertions passed.");
	}

	private static void testValidation() {
		MaterialPalette palette = Palette.create();
		MaterialRamp ramp = MaterialRamp.of(palette, Palette.GRAY_CONCRETE, Palette.QUARTZ_BLOCK);
		DensityProfile p = DensityProfile.constant(0.5);

		expectInvalid(() -> new Band(1.0, 1.5, p, ramp, 1, 1), "innerMultiple <= 1.0 rejected");
		expectInvalid(() -> new Band(1.5, 1.5, p, ramp, 1, 1), "outerMultiple <= innerMultiple rejected");
		expectInvalid(() -> new Band(1.2, 1.6, null, ramp, 1, 1), "null density rejected");
		expectInvalid(() -> new Band(1.2, 1.6, p, null, 1, 1), "null ramp rejected");
		expectInvalid(() -> new Band(1.2, 1.6, p, ramp, 0, 1), "thickness 0 rejected");
		expectInvalid(() -> new Band(1.2, 1.6, p, ramp, 3, 1), "thickness 3 rejected");
		expectInvalid(() -> RingSystem.of(Double.NaN, 50.0, new Band(1.2, 1.6, p, ramp, 1, 1)), "NaN tilt rejected");
		expectInvalid(() -> RingSystem.of(0.0, 0.0, new Band(1.2, 1.6, p, ramp, 1, 1)), "bodyRadius <= 0 rejected");
		expectInvalid(() -> RingSystem.of(0.0, 50.0), "empty bands rejected");
	}

	private static void testDeterminismAndGoldenHash() {
		Blueprint a = BlueprintPreview.testRingSystem(101);
		Blueprint b = BlueprintPreview.testRingSystem(101);
		long hashA = hashRingBox(a, 122);
		long hashB = hashRingBox(b, 122);
		check(hashA == hashB, "testRingSystem produces identical FNV-1a hash across runs");
		System.out.printf("Observed testRingSystem(101) FNV-1a golden hash: 0x%016XL%n", hashA);
		if (GOLDEN_HASH != 0L) {
			check(hashA == GOLDEN_HASH,
				String.format("testRingSystem golden hash matches 0x%016XL (got 0x%016XL)", GOLDEN_HASH, hashA));
		}

		MaterialPalette palette = Palette.create();
		MaterialRamp ramp = MaterialRamp.of(palette, Palette.GRAY_TERRACOTTA, Palette.WHITE_TERRACOTTA, Palette.QUARTZ_BLOCK);
		for (double tilt : new double[]{26.73, 97.77}) {
			RingSystem r1 = RingSystem.of(tilt, 60.0,
				new Band(1.25, 1.75, DensityProfile.ringlets(0.65, 0.25, 5.0, 0.08), ramp, 2, 0x51A7));
			RingSystem r2 = RingSystem.of(tilt, 60.0,
				new Band(1.25, 1.75, DensityProfile.ringlets(0.65, 0.25, 5.0, 0.08), ramp, 2, 0x51A7));
			for (int x = -110; x <= 110; x += 3) {
				for (int y = -110; y <= 110; y += 5) {
					for (int z = -110; z <= 110; z += 4) {
						check(r1.voxelAt(x, y, z) == r2.voxelAt(x, y, z),
							"tilted RingSystem deterministic at tilt=" + tilt);
					}
				}
			}
		}
	}

	private static long hashRingBox(Blueprint bp, int halfExt) {
		long fnv = 0xCBF29CE484222325L;
		for (int y = -2; y <= 2; y++) {
			for (int x = -halfExt; x <= halfExt; x += 2) {
				for (int z = -halfExt; z <= halfExt; z += 2) {
					int v = bp.voxelAt(x, y, z);
					fnv ^= (v & 0xFFL);
					fnv *= 0x100000001B3L;
				}
			}
		}
		return fnv;
	}

	private static void testMaxThicknessAllTilts() {
		MaterialPalette palette = Palette.create();
		MaterialRamp ramp = MaterialRamp.of(palette, Palette.QUARTZ_BLOCK, Palette.WHITE_CONCRETE);
		double bodyR = 50.0;
		double innerMult = 1.3;
		double outerMult = 1.9;
		double[] tilts = {0.0, 26.73, 45.0, 97.77};

		for (double tilt : tilts) {
			double rad = Math.toRadians(tilt);
			double nx = Math.sin(rad);
			double ny = Math.cos(rad);
			double ux = Math.cos(rad);
			double uy = -Math.sin(rad);
			double dom = Math.max(Math.abs(nx), Math.abs(ny));
			boolean yDominant = Math.abs(ny) >= Math.abs(nx);

			for (int thickness : new int[]{1, 2}) {
				RingSystem rings = RingSystem.of(tilt, bodyR,
					new Band(innerMult, outerMult, DensityProfile.constant(1.0), ramp, thickness, 0x741C));
				int bound = (int) Math.ceil(outerMult * bodyR) + 3;
				double maxPlaneH = 0.5 * thickness * dom + 1e-9;
				int solidColumns = 0;

				if (yDominant) {
					for (int x = -bound; x <= bound; x++) {
						for (int z = -bound; z <= bound; z++) {
							int count = 0;
							for (int y = -bound; y <= bound; y++) {
								if (rings.voxelAt(x, y, z) != 0) {
									count++;
									double h = Math.abs(x * nx + y * ny);
									check(h <= maxPlaneH, "perpendicular dist within thickness at tilt=" + tilt);
								}
							}
							check(count <= thickness,
								String.format("tilt=%.2f thickness=%d col(%d,%d) had %d blocks", tilt, thickness, x, z, count));
							double midPu = x * ux + (-x * nx / ny) * uy;
							double midR = Math.hypot(midPu, z);
							if (midR >= innerMult * bodyR + 2.5 && midR <= outerMult * bodyR - 2.5) {
								check(count == thickness,
									String.format("interior col(%d,%d) tilt=%.2f expected %d blocks, got %d",
										x, z, tilt, thickness, count));
								solidColumns++;
							}
						}
					}
				} else {
					for (int y = -bound; y <= bound; y++) {
						for (int z = -bound; z <= bound; z++) {
							int count = 0;
							for (int x = -bound; x <= bound; x++) {
								if (rings.voxelAt(x, y, z) != 0) {
									count++;
									double h = Math.abs(x * nx + y * ny);
									check(h <= maxPlaneH, "perpendicular dist within thickness at tilt=" + tilt);
								}
							}
							check(count <= thickness,
								String.format("tilt=%.2f thickness=%d col(%d,%d) had %d blocks", tilt, thickness, y, z, count));
							double midPu = (-y * ny / nx) * ux + y * uy;
							double midR = Math.hypot(midPu, z);
							if (midR >= innerMult * bodyR + 2.5 && midR <= outerMult * bodyR - 2.5) {
								check(count == thickness,
									String.format("interior col(%d,%d) tilt=%.2f expected %d blocks, got %d",
										y, z, tilt, thickness, count));
								solidColumns++;
							}
						}
					}
				}
				check(solidColumns > 1000, "checked substantial interior columns for tilt=" + tilt);
			}
		}
	}

	private static void testFillDensityMatchesProfile() {
		MaterialPalette palette = Palette.create();
		MaterialRamp ramp = MaterialRamp.of(palette, Palette.GRAY_CONCRETE, Palette.LIGHT_GRAY_CONCRETE, Palette.QUARTZ_BLOCK);
		double bodyR = 80.0;
		double innerMult = 1.25;
		double outerMult = 1.95;
		double[] densities = {0.0, 0.08, 0.25, 0.50, 0.85, 1.0};
		double[] tilts = {0.0, 26.73, 97.77};

		for (double tilt : tilts) {
			double rad = Math.toRadians(tilt);
			double nx = Math.sin(rad);
			double ny = Math.cos(rad);
			double ux = Math.cos(rad);
			double uy = -Math.sin(rad);
			double dom = Math.max(Math.abs(nx), Math.abs(ny));
			int bound = (int) Math.ceil(outerMult * bodyR) + 2;

			for (double targetDensity : densities) {
				RingSystem rings = RingSystem.of(tilt, bodyR,
					new Band(innerMult, outerMult, DensityProfile.constant(targetDensity), ramp, 2, 0xD345));
				long totalSlots = 0;
				long filledSlots = 0;
				double halfW = 0.5 * 2 * dom;
				double rMin = innerMult * bodyR + 2.0;
				double rMax = outerMult * bodyR - 2.0;

				// Scan exact slab voxels inside safe radial margin so edge boundaries don't bias fill rate.
				if (Math.abs(ny) >= Math.abs(nx)) {
					for (int x = -bound; x <= bound; x++) {
						int yMid = (int) Math.round(-(x * nx) / ny);
						for (int z = -bound; z <= bound; z++) {
							for (int y = yMid - 2; y <= yMid + 2; y++) {
								double h = x * nx + y * ny;
								if (h < -halfW || h >= halfW) continue;
								double r = Math.hypot(x * ux + y * uy, z);
								if (r < rMin || r > rMax) continue;
								totalSlots++;
								if (rings.voxelAt(x, y, z) != 0) filledSlots++;
							}
						}
					}
				} else {
					for (int y = -bound; y <= bound; y++) {
						int xMid = (int) Math.round(-(y * ny) / nx);
						for (int z = -bound; z <= bound; z++) {
							for (int x = xMid - 2; x <= xMid + 2; x++) {
								double h = x * nx + y * ny;
								if (h < -halfW || h >= halfW) continue;
								double r = Math.hypot(x * ux + y * uy, z);
								if (r < rMin || r > rMax) continue;
								totalSlots++;
								if (rings.voxelAt(x, y, z) != 0) filledSlots++;
							}
						}
					}
				}

				check(totalSlots > 20_000, "sampled enough slab voxels for density test");
				double measured = (double) filledSlots / totalSlots;
				if (targetDensity == 0.0) {
					check(filledSlots == 0, "density 0.0 emits zero blocks at tilt=" + tilt);
				} else if (targetDensity == 1.0) {
					check(filledSlots == totalSlots, "density 1.0 fills 100% of slab slots at tilt=" + tilt);
				} else {
					check(Math.abs(measured - targetDensity) <= 0.015,
						String.format("tilt=%.2f target=%.2f measured=%.4f out of tolerance ±0.015",
							tilt, targetDensity, measured));
				}
			}
		}
	}

	private static void testAzimuthalArcDensity() {
		MaterialPalette palette = Palette.create();
		MaterialRamp ramp = MaterialRamp.of(palette, Palette.LIGHT_GRAY_CONCRETE, Palette.QUARTZ_BLOCK);
		double arcHalfRad = Math.toRadians(30.0);
		RingSystem arcRing = RingSystem.of(0.0, 80.0,
			new Band(1.4, 1.7,
				(t, a) -> Math.abs(a) <= arcHalfRad ? 0.75 : 0.08,
				ramp, 1, 0xA2C5));

		long insideTotal = 0, insideFilled = 0;
		long outsideTotal = 0, outsideFilled = 0;
		for (int x = -140; x <= 140; x++) {
			for (int z = -140; z <= 140; z++) {
				double r = Math.hypot(x, z);
				if (r < 114.0 || r > 134.0) continue;
				double ang = Math.atan2(z, x);
				int v = arcRing.voxelAt(x, 0, z);
				if (Math.abs(ang) <= arcHalfRad - 0.04) {
					insideTotal++;
					if (v != 0) insideFilled++;
				} else if (Math.abs(ang) >= arcHalfRad + 0.04) {
					outsideTotal++;
					if (v != 0) outsideFilled++;
				}
			}
		}
		double inRate = (double) insideFilled / insideTotal;
		double outRate = (double) outsideFilled / outsideTotal;
		check(Math.abs(inRate - 0.75) <= 0.03,
			String.format("arc dense region fill %.4f not within 0.75±0.03", inRate));
		check(Math.abs(outRate - 0.08) <= 0.02,
			String.format("arc sparse region fill %.4f not within 0.08±0.02", outRate));
	}

	private static void benchmarkRingSystem() {
		Blueprint bp = BlueprintPreview.testRingSystem(101);
		MaterialPalette palette = Palette.create();
		MaterialRamp ramp = MaterialRamp.of(palette,
			Palette.WHITE_TERRACOTTA, Palette.END_STONE, Palette.SMOOTH_QUARTZ, Palette.QUARTZ_BLOCK);
		RingSystem benchRing = RingSystem.of(26.73, 120.0,
			new Band(1.22, 1.50, DensityProfile.smoothBand(0.25, 0.1), ramp, 1, 0x101),
			new Band(1.52, 1.95, DensityProfile.ringlets(0.82, 0.15, 6.0, 0.06), ramp, 2, 0x202),
			new Band(1.98, 2.30, DensityProfile.ringlets(0.55, 0.18, 5.0, 0.08), ramp, 1, 0x303));

		// Warmup
		long sink = 0;
		for (int i = 0; i < 40_000; i++) {
			int x = (i * 37) % 560 - 280;
			int y = (i * 53) % 560 - 280;
			int z = (i * 97) % 560 - 280;
			sink += benchRing.voxelAt(x, y, z) + bp.voxelAt(x, y, z);
		}

		// 1) Full bounding-box mixed workload (mostly air, matching PlanetChunkGenerator)
		int boxCalls = 1_000_000;
		long t0 = System.nanoTime();
		for (int i = 0; i < boxCalls; i++) {
			int x = (i * 73) % 560 - 280;
			int y = (i * 101) % 560 - 280;
			int z = (i * 139) % 560 - 280;
			sink += benchRing.voxelAt(x, y, z);
		}
		long boxNs = System.nanoTime() - t0;

		// 2) Concentrated in-plane ring workload (density + hash + ramp path)
		RingSystem flatBench = RingSystem.of(0.0, 120.0, benchRing.bands());
		int ringCalls = 200_000;
		long t1 = System.nanoTime();
		for (int i = 0; i < ringCalls; i++) {
			int x = 185 + (i % 80);
			int z = (i * 19) % 200 - 100;
			sink += flatBench.voxelAt(x, 0, z);
		}
		long ringNs = System.nanoTime() - t1;

		double boxPerCall = (double) boxNs / boxCalls;
		double ringPerCall = (double) ringNs / ringCalls;
		System.out.printf(
			"RingSystem benchmark: fullBox=%.1f ns/call (%.1f ms/1M), inPlane=%.1f ns/call (%.1f ms/1M), sink=%d%n",
			boxPerCall, boxPerCall, ringPerCall, ringPerCall, sink & 0);
	}

	private static void expectInvalid(Runnable action, String label) {
		try {
			action.run();
			throw new AssertionError("Expected IllegalArgumentException for " + label);
		} catch (IllegalArgumentException expected) {
			check(true, label);
		}
	}

	private static void check(boolean condition, String message) {
		assertions++;
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
