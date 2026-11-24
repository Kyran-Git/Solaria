package net.kyrangit.solaria;

import net.kyrangit.solaria.blueprint.AstronomyClass;
import net.kyrangit.solaria.blueprint.Blueprint;
import net.kyrangit.solaria.blueprint.BlueprintGenerator;
import net.kyrangit.solaria.blueprint.MaterialPalette;
import net.kyrangit.solaria.blueprint.MaterialRamp;
import net.kyrangit.solaria.blueprint.Palette;
import net.kyrangit.solaria.blueprint.RingSystem;
import net.kyrangit.solaria.blueprint.RingSystem.Band;
import net.kyrangit.solaria.blueprint.RingSystem.DensityProfile;
import net.kyrangit.solaria.blueprint.SurfacePainter;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Renders blueprints to PNGs under {@code build/previews/} using an average colour per block.
 * Each body gets three individual images ({@code front.png}, {@code cutaway.png}, {@code map.png})
 * plus a combined 3-panel sheet {@code <name>.png}, and prints surface variety metrics and timing.
 *
 * <p>Uses AWT/ImageIO headlessly on Java 25.
 */
public final class BlueprintPreview {
	private static final int BACKDROP = 0x10141A;
	private static final int ORTHO_MAX_PX = 256;
	private static final int RING_VIEW_MAX_PX = 512;
	private static final int MAP_WIDTH = 360;
	private static final int MAP_HEIGHT = 180;
	private static final int SURFACE_SAMPLES = 20_000;

	/** Approximate average sRGB colour for every palette block (for preview review only). */
	private static final Map<String, Integer> BLOCK_COLOURS = new LinkedHashMap<>();

	static {
		BLOCK_COLOURS.put("minecraft:glowstone",              0xFFE89A);
		BLOCK_COLOURS.put("minecraft:gold_block",             0xF9D71C);
		BLOCK_COLOURS.put("minecraft:yellow_concrete",        0xF1AF15);
		BLOCK_COLOURS.put("minecraft:blue_concrete",          0x2C44A5);
		BLOCK_COLOURS.put("minecraft:green_concrete",         0x5D7C15);
		BLOCK_COLOURS.put("minecraft:snow_block",             0xF2FAFC);
		BLOCK_COLOURS.put("minecraft:iron_block",             0xDCDCDC);
		BLOCK_COLOURS.put("minecraft:light_gray_concrete",    0x9D9D97);
		BLOCK_COLOURS.put("minecraft:gray_concrete",          0x474F52);
		BLOCK_COLOURS.put("minecraft:stone",                  0x7F7F7F);
		BLOCK_COLOURS.put("minecraft:red_terracotta",         0x8F3D2D);
		BLOCK_COLOURS.put("minecraft:white_concrete",         0xCFD5D6);
		BLOCK_COLOURS.put("minecraft:orange_concrete",        0xE06100);
		BLOCK_COLOURS.put("minecraft:brown_concrete",         0x603A1F);
		BLOCK_COLOURS.put("minecraft:red_concrete",           0x8E2121);
		BLOCK_COLOURS.put("minecraft:diamond_block",          0x5DECF5);
		BLOCK_COLOURS.put("minecraft:black_concrete",         0x080A0F);
		BLOCK_COLOURS.put("minecraft:light_blue_concrete",    0x36A4D4);
		BLOCK_COLOURS.put("minecraft:quartz_block",           0xECE9D9);
		BLOCK_COLOURS.put("minecraft:orange_terracotta",      0xC17636);
		BLOCK_COLOURS.put("minecraft:yellow_terracotta",      0xC8AF55);
		BLOCK_COLOURS.put("minecraft:terracotta",             0x976B4A);
		BLOCK_COLOURS.put("minecraft:brown_terracotta",       0x7A4B38);
		BLOCK_COLOURS.put("minecraft:white_terracotta",       0xD2B58E);
		BLOCK_COLOURS.put("minecraft:smooth_sandstone",       0xE5D9A0);
		BLOCK_COLOURS.put("minecraft:end_stone",              0xDAD9A2);
		BLOCK_COLOURS.put("minecraft:andesite",               0x8E8E8E);
		BLOCK_COLOURS.put("minecraft:tuff",                   0x6A6B5E);
		BLOCK_COLOURS.put("minecraft:smooth_stone",           0xA0A0A0);
		BLOCK_COLOURS.put("minecraft:calcite",                0xE8E8E8);
		BLOCK_COLOURS.put("minecraft:cyan_concrete",          0x158991);
		BLOCK_COLOURS.put("minecraft:packed_ice",             0x7DAFFA);
		BLOCK_COLOURS.put("minecraft:lapis_block",            0x1B48AA);
		BLOCK_COLOURS.put("minecraft:blue_terracotta",        0x4A4F7C);
		BLOCK_COLOURS.put("minecraft:green_terracotta",       0x546B2F);
		BLOCK_COLOURS.put("minecraft:moss_block",             0x526A31);
		BLOCK_COLOURS.put("minecraft:lime_concrete",          0x5EA918);
		BLOCK_COLOURS.put("minecraft:lime_terracotta",        0x677535);
		BLOCK_COLOURS.put("minecraft:prismarine",             0x5A9C8C);
		BLOCK_COLOURS.put("minecraft:dark_prismarine",        0x2E6149);
		BLOCK_COLOURS.put("minecraft:blue_ice",               0x74C6F5);
		BLOCK_COLOURS.put("minecraft:light_blue_terracotta",  0x876A83);
		BLOCK_COLOURS.put("minecraft:cyan_terracotta",        0x576A6A);
		BLOCK_COLOURS.put("minecraft:oxidized_copper",        0x4AB38C);
		BLOCK_COLOURS.put("minecraft:red_sandstone",          0x9E4A16);
		BLOCK_COLOURS.put("minecraft:smooth_red_sandstone",   0xC36820);
		BLOCK_COLOURS.put("minecraft:granite",                0x9B5B3E);
		BLOCK_COLOURS.put("minecraft:packed_mud",             0x8A6648);
		BLOCK_COLOURS.put("minecraft:mud_bricks",             0x7A5642);
		BLOCK_COLOURS.put("minecraft:netherrack",             0x7A3333);
		BLOCK_COLOURS.put("minecraft:red_nether_bricks",      0x430E0E);
		BLOCK_COLOURS.put("minecraft:raw_iron_block",         0xB98954);
		BLOCK_COLOURS.put("minecraft:raw_copper_block",       0xA9592A);
		BLOCK_COLOURS.put("minecraft:raw_gold_block",         0xDDA63A);
		BLOCK_COLOURS.put("minecraft:honeycomb_block",        0xE58A18);
		BLOCK_COLOURS.put("minecraft:ochre_froglight",        0xF5E38C);
		BLOCK_COLOURS.put("minecraft:shroomlight",            0xF0803C);
		BLOCK_COLOURS.put("minecraft:sea_lantern",            0xA1CCC5);
		BLOCK_COLOURS.put("minecraft:end_stone_bricks",       0xE0DAAA);
		BLOCK_COLOURS.put("minecraft:smooth_quartz",          0xECEAE2);
		BLOCK_COLOURS.put("minecraft:diorite",                0xCCCCCC);
		BLOCK_COLOURS.put("minecraft:light_gray_terracotta",  0xA08272);
		BLOCK_COLOURS.put("minecraft:gray_terracotta",        0x6A5245);
		BLOCK_COLOURS.put("minecraft:deepslate",              0x3D3D45);
		BLOCK_COLOURS.put("minecraft:smooth_basalt",          0x3A3C3F);
		BLOCK_COLOURS.put("minecraft:blackstone",             0x1C1A1E);
		BLOCK_COLOURS.put("minecraft:coal_block",             0x131313);
		BLOCK_COLOURS.put("minecraft:copper_block",           0xC05C32);
		BLOCK_COLOURS.put("minecraft:redstone_block",         0xAD1717);
		BLOCK_COLOURS.put("minecraft:orange_stained_glass",   0xF28C28);
		BLOCK_COLOURS.put("minecraft:yellow_stained_glass",   0xF7E44C);
		BLOCK_COLOURS.put("minecraft:red_stained_glass",      0xD63B27);
	}

	private BlueprintPreview() {
	}

	/**
	 * Synthetic test sphere built solely with {@link SurfacePainter} and {@link MaterialRamp}
	 * to verify polar continuity, warped latitude bands, swirling fBm, and smooth polar caps.
	 */
	public static Blueprint testSphere(int diameter) {
		MaterialPalette palette = Palette.create();
		int seed = 0x573E0002;
		MaterialRamp bandRamp = MaterialRamp.of(palette,
			Palette.BROWN_CONCRETE,
			Palette.BROWN_TERRACOTTA,
			Palette.RAW_COPPER_BLOCK,
			Palette.TERRACOTTA,
			Palette.ORANGE_TERRACOTTA,
			Palette.YELLOW_TERRACOTTA,
			Palette.WHITE_TERRACOTTA);
		MaterialRamp coreRamp = MaterialRamp.of(palette,
			Palette.GLOWSTONE,
			Palette.SHROOMLIGHT,
			Palette.NETHERRACK);
		int ice = palette.indexOf(Palette.SNOW_BLOCK);
		int iceEdge = palette.indexOf(Palette.WHITE_CONCRETE);
		double radius = diameter / 2.0;

		return new Blueprint(AstronomyClass.JUPITER, (x, y, z) -> {
			double distSq = (double) x * x + (double) y * y + (double) z * z;
			if (distSq > radius * radius) {
				return 0;
			}
			double dist = Math.sqrt(distSq);
			double norm = dist / radius;
			if (norm < 0.85) {
				if (norm < 0.28) {
					return coreRamp.at(0);
				}
				return coreRamp.sampleDithered((norm - 0.28) / 0.57, 0.22, x, y, z, seed ^ 0xC04E);
			}
			double inv = dist > 0.0 ? 1.0 / dist : 0.0;
			double ux = x * inv;
			double uy = dist > 0.0 ? y * inv : 1.0;
			double uz = z * inv;

			double wLat = SurfacePainter.warpedLatitude(ux, uy, uz, 3.0, 0.14, 3, seed);
			double bandWave = 0.5 + 0.5 * Math.sin(wLat * Math.PI * 5.0);
			double swirl = SurfacePainter.warpedSphereFbm(ux, uy, uz, 4.0, 3, 2.2, 0.32, seed ^ 0x51A1);
			double field = SurfacePainter.clamp01(bandWave * 0.65 + swirl * 0.35);
			int surfaceMat = bandRamp.sampleDithered(field, 0.35, x, y, z, seed ^ 0xBA4D);

			double polar = SurfacePainter.latitudeGradient(uy, 0.72, 0.90);
			int capMat = SurfacePainter.blend(iceEdge, ice, polar, 0.75, 0.30, x, y, z, seed ^ 0x1CE0);
			return SurfacePainter.blend(surfaceMat, capMat, polar, 0.50, 0.24, x, y, z, seed ^ 0xCA90);
		}, palette, 1.0, diameter);
	}

	/**
	 * Synthetic test blueprint showcasing {@link RingSystem}: faint inner band, dense main band with ringlets,
	 * speckled gap, mid-density outer band, and thin outer ringlet around a neutral sphere.
	 */
	public static Blueprint testRingSystem(int bodyDiameter) {
		MaterialPalette palette = Palette.create();
		double bodyRadius = bodyDiameter / 2.0;
		double bodyRadiusSq = bodyRadius * bodyRadius;
		int sphereA = palette.indexOf(Palette.SMOOTH_STONE);
		int sphereB = palette.indexOf(Palette.ANDESITE);

		MaterialRamp faintRamp = MaterialRamp.of(palette,
			Palette.GRAY_TERRACOTTA, Palette.LIGHT_GRAY_TERRACOTTA, Palette.LIGHT_GRAY_CONCRETE);
		MaterialRamp mainRamp = MaterialRamp.of(palette,
			Palette.WHITE_TERRACOTTA, Palette.END_STONE, Palette.SMOOTH_QUARTZ, Palette.WHITE_CONCRETE);
		MaterialRamp gapRamp = MaterialRamp.of(palette,
			Palette.GRAY_TERRACOTTA, Palette.LIGHT_GRAY_TERRACOTTA);
		MaterialRamp outerRamp = MaterialRamp.of(palette,
			Palette.LIGHT_GRAY_CONCRETE, Palette.QUARTZ_BLOCK, Palette.SMOOTH_QUARTZ);
		MaterialRamp thinRamp = MaterialRamp.of(palette,
			Palette.QUARTZ_BLOCK, Palette.WHITE_CONCRETE);

		RingSystem rings = RingSystem.of(0.0, bodyRadius,
			new Band(1.20, 1.45, DensityProfile.constant(0.20), faintRamp, 1, 0x6101),
			new Band(1.45, 1.85, DensityProfile.ringlets(0.84, 0.14, 7.0, 0.06), mainRamp, 2, 0x6102),
			new Band(1.85, 1.98, DensityProfile.constant(0.06), gapRamp, 1, 0x6103),
			new Band(1.98, 2.22, DensityProfile.ringlets(0.55, 0.18, 5.0, 0.08), outerRamp, 1, 0x6104),
			new Band(2.28, 2.34, DensityProfile.smoothBand(0.48, 0.25), thinRamp, 1, 0x6105)
		);

		return new Blueprint(AstronomyClass.SATURN, (x, y, z) -> {
			double distSq = (double) x * x + (double) y * y + (double) z * z;
			if (distSq <= bodyRadiusSq) {
				return ((x + y + z) & 1) == 0 ? sphereA : sphereB;
			}
			return rings.voxelAt(x, y, z);
		}, palette, 1.0, bodyDiameter);
	}

	public static void main(String[] args) throws IOException {
		verifyPaletteColours();
		Path outDir = Path.of("build", "previews");
		Files.createDirectories(outDir);

		boolean renderAll = args.length == 0;
		if (renderAll || containsArg(args, "test_sphere")) {
			renderTarget("test_sphere", testSphere(161), 161, outDir);
		}
		if (renderAll || containsArg(args, "test_ring")) {
			int diameter = 101;
			int halfExt = (int) Math.ceil((diameter / 2.0) * 2.34) + 4;
			renderRingTarget("test_ring", testRingSystem(diameter), halfExt, outDir);
		}
		for (AstronomyClass cls : AstronomyClass.values()) {
			if (renderAll || containsArg(args, cls.id())) {
				long t0 = System.nanoTime();
				Blueprint bp = BlueprintGenerator.generate(cls, 1.0);
				long genMs = (System.nanoTime() - t0) / 1_000_000;
				int halfExt = BlueprintGenerator.blueprintExtent(cls, bp.diameter()) / 2 + 2;
				System.out.printf("[%s] generator setup %d ms (diameter=%d, halfExt=%d)%n",
					cls.id(), genMs, bp.diameter(), halfExt);
				renderTarget(cls.id(), bp, halfExt, outDir);
			}
		}
	}

	private static void verifyPaletteColours() {
		MaterialPalette palette = Palette.create();
		for (String blockId : palette.allBlockIds()) {
			if (!BLOCK_COLOURS.containsKey(blockId)) {
				throw new IllegalStateException("Missing preview colour for palette block: " + blockId);
			}
		}
	}

	private static boolean containsArg(String[] args, String target) {
		for (String a : args) {
			if (target.equalsIgnoreCase(a)) {
				return true;
			}
		}
		return false;
	}

	private static void renderTarget(String name, Blueprint bp, int halfExt, Path outDir) throws IOException {
		long start = System.nanoTime();
		int[] colours = buildColourLookup(bp.palette());

		BufferedImage front = renderFront(bp, halfExt, colours);
		BufferedImage cutaway = renderCutaway(bp, halfExt, colours);
		BufferedImage map = renderMap(bp, colours);

		Path itemDir = outDir.resolve(name);
		Files.createDirectories(itemDir);
		ImageIO.write(front, "png", itemDir.resolve("front.png").toFile());
		ImageIO.write(cutaway, "png", itemDir.resolve("cutaway.png").toFile());
		ImageIO.write(map, "png", itemDir.resolve("map.png").toFile());

		BufferedImage sheet = composeSheet(front, cutaway, map);
		ImageIO.write(sheet, "png", outDir.resolve(name + ".png").toFile());

		VarietyReport variety = measureVariety(bp, map);
		long elapsedMs = (System.nanoTime() - start) / 1_000_000;
		if (name.equals("saturn") || name.equals("uranus") || name.equals("neptune")) {
			boolean vertical = name.equals("uranus");
			for (boolean edge : new boolean[]{false, true}) {
				BufferedImage ring = new BufferedImage(2 * halfExt + 1, 2 * halfExt + 1, BufferedImage.TYPE_INT_RGB);
				for (int v = -halfExt; v <= halfExt; v++) {
					for (int u = -halfExt; u <= halfExt; u++) {
						int material = 0;
						for (int d = halfExt; d >= -halfExt && material == 0; d--) {
							material = edge ? bp.voxelAt(u, v, d)
								: vertical ? bp.voxelAt(d, v, u) : bp.voxelAt(u, d, v);
						}
						ring.setRGB(u + halfExt, halfExt - v, colours[material]);
					}
				}
				ImageIO.write(ring, "png", itemDir.resolve(edge ? "ring-side.png" : "ring-top.png").toFile());
			}
		}
		System.out.printf(
			"[%s] distinctBlocks=%d, topBlock=%s (%.1f%%), smoothTransitions=%.1f%%, reliefR=[%.1f..%.1f] (span=%.1f), rendered in %d ms -> %s%n",
			name, variety.distinctBlocks, variety.topBlock, variety.topSharePct,
			variety.smoothTransitionPct, variety.minHitR, variety.maxHitR,
			variety.maxHitR - variety.minHitR, elapsedMs, outDir.resolve(name + ".png").toAbsolutePath());
	}

	private static void renderRingTarget(String name, Blueprint bp, int halfExt, Path outDir) throws IOException {
		long start = System.nanoTime();
		int[] colours = buildColourLookup(bp.palette());
		BufferedImage topdown = renderTopDown(bp, halfExt, colours);
		BufferedImage side = renderSide(bp, halfExt, colours);

		Path itemDir = outDir.resolve(name);
		Files.createDirectories(itemDir);
		ImageIO.write(topdown, "png", itemDir.resolve("topdown.png").toFile());
		ImageIO.write(side, "png", itemDir.resolve("side.png").toFile());

		int pad = 8;
		int rowH = Math.max(topdown.getHeight(), side.getHeight());
		BufferedImage sheet = blankImage(pad + topdown.getWidth() + pad + side.getWidth() + pad, pad + rowH + pad);
		Graphics2D g = sheet.createGraphics();
		g.drawImage(topdown, pad, pad + (rowH - topdown.getHeight()) / 2, null);
		g.drawImage(side, pad + topdown.getWidth() + pad, pad + (rowH - side.getHeight()) / 2, null);
		g.dispose();
		ImageIO.write(sheet, "png", outDir.resolve(name + ".png").toFile());

		long elapsedMs = (System.nanoTime() - start) / 1_000_000;
		System.out.printf("[%s] ring top-down + side rendered in %d ms -> %s%n",
			name, elapsedMs, outDir.resolve(name + ".png").toAbsolutePath());
	}

	private static BufferedImage renderTopDown(Blueprint bp, int halfExt, int[] colours) {
		int span = halfExt * 2 + 1;
		int step = Math.max(1, (span + RING_VIEW_MAX_PX - 1) / RING_VIEW_MAX_PX);
		int size = (span + step - 1) / step;
		BufferedImage img = blankImage(size, size);
		for (int py = 0; py < size; py++) {
			int z = -halfExt + py * step;
			for (int px = 0; px < size; px++) {
				int x = -halfExt + px * step;
				for (int y = halfExt; y >= -halfExt; y -= step) {
					int idx = bp.voxelAt(x, y, z);
					if (idx != 0) {
						img.setRGB(px, py, colours[idx]);
						break;
					}
				}
			}
		}
		return img;
	}

	private static BufferedImage renderSide(Blueprint bp, int halfExt, int[] colours) {
		int span = halfExt * 2 + 1;
		int step = Math.max(1, (span + RING_VIEW_MAX_PX - 1) / RING_VIEW_MAX_PX);
		int size = (span + step - 1) / step;
		BufferedImage img = blankImage(size, size);
		for (int py = 0; py < size; py++) {
			int y = halfExt - py * step;
			for (int px = 0; px < size; px++) {
				int x = -halfExt + px * step;
				for (int z = halfExt; z >= -halfExt; z -= step) {
					int idx = bp.voxelAt(x, y, z);
					if (idx != 0) {
						img.setRGB(px, py, colours[idx]);
						break;
					}
				}
			}
		}
		return img;
	}

	private static int[] buildColourLookup(MaterialPalette palette) {
		int[] colours = new int[palette.size() + 1];
		colours[0] = BACKDROP;
		for (int i = 1; i < colours.length; i++) {
			String blockId = palette.blockId(i);
			Integer rgb = BLOCK_COLOURS.get(blockId);
			if (rgb == null) {
				throw new IllegalStateException("Missing preview colour for block: " + blockId);
			}
			colours[i] = rgb;
		}
		return colours;
	}

	private static BufferedImage renderFront(Blueprint bp, int halfExt, int[] colours) {
		int span = halfExt * 2 + 1;
		int step = Math.max(1, (span + ORTHO_MAX_PX - 1) / ORTHO_MAX_PX);
		int size = (span + step - 1) / step;
		BufferedImage img = blankImage(size, size);
		int[][] hitIdx = new int[size][size];
		int[][] hitX = new int[size][size];

		for (int py = 0; py < size; py++) {
			int y = halfExt - py * step;
			for (int px = 0; px < size; px++) {
				int z = -halfExt + px * step;
				for (int x = halfExt; x >= -halfExt; x -= step) {
					int idx = bp.voxelAt(x, y, z);
					if (idx != 0) {
						int exactX = x;
						int exactIdx = idx;
						for (int probe = Math.min(halfExt, x + step - 1); probe > x; probe--) {
							int pIdx = bp.voxelAt(probe, y, z);
							if (pIdx != 0) {
								exactX = probe;
								exactIdx = pIdx;
								break;
							}
						}
						hitIdx[py][px] = exactIdx;
						hitX[py][px] = exactX;
						break;
					}
				}
			}
		}

		// Normalized key light from (+X, +Y, -Z) so hills, ridges, and craters cast readable relief shading.
		double lLen = Math.sqrt(0.70 * 0.70 + 0.45 * 0.45 + 0.55 * 0.55);
		double lx = 0.70 / lLen;
		double ly = 0.45 / lLen;
		double lz = -0.55 / lLen;

		for (int py = 0; py < size; py++) {
			for (int px = 0; px < size; px++) {
				int idx = hitIdx[py][px];
				if (idx == 0) continue;
				int x0 = hitX[py][px];
				boolean hasL = px > 0 && hitIdx[py][px - 1] != 0;
				boolean hasR = px + 1 < size && hitIdx[py][px + 1] != 0;
				boolean hasU = py > 0 && hitIdx[py - 1][px] != 0;
				boolean hasD = py + 1 < size && hitIdx[py + 1][px] != 0;
				int xL = hasL ? hitX[py][px - 1] : x0;
				int xR = hasR ? hitX[py][px + 1] : x0;
				int xU = hasU ? hitX[py - 1][px] : x0;
				int xD = hasD ? hitX[py + 1][px] : x0;
				double dzSpan = (hasL && hasR) ? 2.0 * step : (double) step;
				double dySpan = (hasU && hasD) ? 2.0 * step : (double) step;
				double ny = -(xU - xD) / dySpan;
				double nz = -(xR - xL) / dzSpan;
				double invN = 1.0 / Math.sqrt(1.0 + ny * ny + nz * nz);
				double ndl = Math.max(0.0, (lx + ny * ly + nz * lz) * invN);
				double shade = Math.max(0.55, Math.min(1.05, 0.56 + 0.48 * ndl));
				img.setRGB(px, py, scaleRgb(colours[idx], shade));
			}
		}
		return img;
	}

	private static BufferedImage renderCutaway(Blueprint bp, int halfExt, int[] colours) {
		int span = halfExt * 2 + 1;
		int step = Math.max(1, (span + ORTHO_MAX_PX - 1) / ORTHO_MAX_PX);
		int size = (span + step - 1) / step;
		BufferedImage img = blankImage(size, size);

		for (int py = 0; py < size; py++) {
			int y = halfExt - py * step;
			for (int px = 0; px < size; px++) {
				int x = -halfExt + px * step;
				int idx = bp.voxelAt(x, y, 0);
				if (idx != 0) {
					img.setRGB(px, py, colours[idx]);
				}
			}
		}
		return img;
	}

	private static BufferedImage renderMap(Blueprint bp, int[] colours) {
		BufferedImage img = blankImage(MAP_WIDTH, MAP_HEIGHT);
		double radius = bp.diameter() / 2.0;
		double maxR = Math.ceil(radius * (1.0 + BlueprintGenerator.MAX_RELIEF_FRAC)) + 2.0;
		double minR = Math.max(1.0, Math.floor(radius * (1.0 - BlueprintGenerator.MAX_RELIEF_FRAC)) - 4.0);

		for (int py = 0; py < MAP_HEIGHT; py++) {
			double lat = Math.toRadians(90.0 - ((py + 0.5) * 180.0 / MAP_HEIGHT));
			double cosLat = Math.cos(lat);
			double uy = Math.sin(lat);
			for (int px = 0; px < MAP_WIDTH; px++) {
				double lon = Math.toRadians(-180.0 + ((px + 0.5) * 360.0 / MAP_WIDTH));
				double ux = cosLat * Math.cos(lon);
				double uz = cosLat * Math.sin(lon);
				for (double r = maxR; r >= minR; r -= 0.75) {
					int vx = (int) Math.round(ux * r);
					int vy = (int) Math.round(uy * r);
					int vz = (int) Math.round(uz * r);
					int idx = bp.voxelAt(vx, vy, vz);
					if (idx != 0) {
						img.setRGB(px, py, colours[idx]);
						break;
					}
				}
			}
		}
		return img;
	}

	private static BufferedImage composeSheet(BufferedImage front, BufferedImage cutaway, BufferedImage map) {
		int pad = 8;
		int rowH = Math.max(front.getHeight(), Math.max(cutaway.getHeight(), map.getHeight()));
		int width = pad + front.getWidth() + pad + cutaway.getWidth() + pad + map.getWidth() + pad;
		int height = pad + rowH + pad;
		BufferedImage sheet = blankImage(width, height);
		Graphics2D g = sheet.createGraphics();
		int x = pad;
		g.drawImage(front, x, pad + (rowH - front.getHeight()) / 2, null);
		x += front.getWidth() + pad;
		g.drawImage(cutaway, x, pad + (rowH - cutaway.getHeight()) / 2, null);
		x += cutaway.getWidth() + pad;
		g.drawImage(map, x, pad + (rowH - map.getHeight()) / 2, null);
		g.dispose();
		return sheet;
	}

	private static VarietyReport measureVariety(Blueprint bp, BufferedImage map) {
		double radius = bp.diameter() / 2.0;
		double maxR = Math.ceil(radius * (1.0 + BlueprintGenerator.MAX_RELIEF_FRAC)) + 2.0;
		double minR = Math.max(1.0, Math.floor(radius * (1.0 - BlueprintGenerator.MAX_RELIEF_FRAC)) - 4.0);
		Map<Integer, Integer> counts = new HashMap<>();
		int total = 0;
		double minHitR = Double.MAX_VALUE;
		double maxHitR = 0.0;
		double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));

		for (int i = 0; i < SURFACE_SAMPLES; i++) {
			double uy = 1.0 - (2.0 * (i + 0.5) / SURFACE_SAMPLES);
			double ring = Math.sqrt(Math.max(0.0, 1.0 - uy * uy));
			double theta = goldenAngle * i;
			double ux = ring * Math.cos(theta);
			double uz = ring * Math.sin(theta);
			for (double r = maxR; r >= minR; r -= 0.75) {
				int vx = (int) Math.round(ux * r);
				int vy = (int) Math.round(uy * r);
				int vz = (int) Math.round(uz * r);
				int idx = bp.voxelAt(vx, vy, vz);
				if (idx != 0) {
					counts.merge(idx, 1, Integer::sum);
					total++;
					double hitR = Math.sqrt((double) vx * vx + (double) vy * vy + (double) vz * vz);
					if (hitR < minHitR) minHitR = hitR;
					if (hitR > maxHitR) maxHitR = hitR;
					break;
				}
			}
		}

		int topIdx = 0;
		int topCount = 0;
		for (var e : counts.entrySet()) {
			if (e.getValue() > topCount) {
				topCount = e.getValue();
				topIdx = e.getKey();
			}
		}
		String topBlock = topIdx == 0 ? "none" : bp.palette().blockId(topIdx);
		double topSharePct = total == 0 ? 0.0 : (100.0 * topCount) / total;

		int transitions = 0;
		int blended = 0;
		for (int y = 0; y < MAP_HEIGHT - 1; y++) {
			for (int x = 0; x < MAP_WIDTH - 1; x++) {
				int c = map.getRGB(x, y);
				int r = map.getRGB(x + 1, y);
				if (c != r) {
					transitions++;
					if (x + 2 < MAP_WIDTH && map.getRGB(x + 2, y) == c) {
						blended++;
					} else if (x > 0 && map.getRGB(x - 1, y) == r) {
						blended++;
					}
				}
			}
		}
		double smoothPct = transitions == 0 ? 100.0 : (100.0 * blended) / transitions;
		if (total == 0) minHitR = 0.0;
		return new VarietyReport(counts.size(), topBlock, topSharePct, smoothPct, minHitR, maxHitR);
	}

	private static BufferedImage blankImage(int width, int height) {
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				img.setRGB(x, y, BACKDROP);
			}
		}
		return img;
	}

	private static int scaleRgb(int rgb, double factor) {
		int r = Math.min(255, Math.max(0, (int) Math.round(((rgb >> 16) & 0xFF) * factor)));
		int g = Math.min(255, Math.max(0, (int) Math.round(((rgb >> 8) & 0xFF) * factor)));
		int b = Math.min(255, Math.max(0, (int) Math.round((rgb & 0xFF) * factor)));
		return (r << 16) | (g << 8) | b;
	}

	private record VarietyReport(int distinctBlocks, String topBlock, double topSharePct,
	                             double smoothTransitionPct, double minHitR, double maxHitR) {
	}
}
