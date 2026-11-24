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
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.kyrangit.solaria.blueprint.InternalStructure;
import net.kyrangit.solaria.blueprint.InternalStructure.Layer;
import net.kyrangit.solaria.blueprint.InternalStructure.LayerSpan;
import net.kyrangit.solaria.intro.HubLayout;
import net.kyrangit.solaria.intro.OrbitPuzzle;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

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

		boolean hubOnly = containsArg(args, "hub");
		boolean wikiInternalOnly = !hubOnly && containsArg(args, "wiki-internal");
		boolean includeInternal = wikiInternalOnly || (!hubOnly && containsArg(args, "internal"));
		boolean renderAll = !hubOnly && !wikiInternalOnly && args.length == 0;
		if (hubOnly || renderAll) {
			renderHubTopDown(outDir);
		}
		if (hubOnly) {
			return;
		}
		Path wikiInternalDir = Path.of("wiki", "assets", "solariamod", "internal");
		if (renderAll || includeInternal) {
			Files.createDirectories(wikiInternalDir);
		}
		if (renderAll || containsArg(args, "test_sphere")) {
			renderTarget("test_sphere", testSphere(161), 161, outDir);
		}
		if (renderAll || containsArg(args, "test_ring")) {
			int diameter = 101;
			int halfExt = (int) Math.ceil((diameter / 2.0) * 2.34) + 4;
			renderRingTarget("test_ring", testRingSystem(diameter), halfExt, outDir);
		}
		for (AstronomyClass cls : AstronomyClass.values()) {
			if (renderAll || wikiInternalOnly || containsArg(args, cls.id())) {
				long t0 = System.nanoTime();
				Blueprint bp = BlueprintGenerator.generate(cls, 1.0);
				long genMs = (System.nanoTime() - t0) / 1_000_000;
				int halfExt = BlueprintGenerator.blueprintExtent(cls, bp.diameter()) / 2 + 2;
				if (!wikiInternalOnly) {
					System.out.printf("[%s] generator setup %d ms (diameter=%d, halfExt=%d)%n",
						cls.id(), genMs, bp.diameter(), halfExt);
					renderTarget(cls.id(), bp, halfExt, outDir);
				}
				if (renderAll || includeInternal) {
					renderInternalPreviews(cls, bp, outDir, wikiInternalDir);
				}
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

	private static void renderInternalPreviews(
		AstronomyClass cls,
		Blueprint bp,
		Path outDir,
		Path wikiInternalDir
	) throws IOException {
		int genHalfExtent = BlueprintGenerator.blueprintExtent(cls, bp.diameter()) / 2 + 1;
		InternalStructure structure = InternalStructure.forClass(cls, genHalfExtent, bp.palette());
		int[] colours = buildColourLookup(bp.palette());

		BufferedImage wikiImg = renderWikiInternalDiagram(structure, colours);
		Path wikiFile = wikiInternalDir.resolve(cls.id() + ".png");
		ImageIO.write(wikiImg, "png", wikiFile.toFile());

		Path planetOut = outDir.resolve(cls.id());
		Files.createDirectories(planetOut);
		BufferedImage sideImg = renderInternalSideView(bp, structure, genHalfExtent, colours);
		Path sideFile = planetOut.resolve("internal-side.png");
		ImageIO.write(sideImg, "png", sideFile.toFile());
		System.out.printf("[%s] wrote %s and %s%n", cls.id(), wikiFile, sideFile);
	}

	private static BufferedImage renderWikiInternalDiagram(InternalStructure structure, int[] colours) {
		final int w = 760;
		final int h = 520;
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		g.setColor(new Color(0x0B101D));
		g.fillRect(0, 0, w, h);
		g.setColor(new Color(0x111827));
		g.fillRoundRect(16, 14, w - 32, h - 28, 16, 16);
		g.setColor(new Color(0x26344D));
		g.drawRoundRect(16, 14, w - 32, h - 28, 16, 16);

		String id = structure.astronomyClass().id();
		String titleName = Character.toUpperCase(id.charAt(0)) + id.substring(1);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
		g.setColor(new Color(0xF8FAFC));
		g.drawString(titleName + " — Internal Structure", 36, 46);
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
		g.setColor(new Color(0x94A3B8));
		g.drawString("17x17 stratigraphic column (in-world east of body) • Core at bottom -> Surface at top", 36, 67);

		final int px = 4;
		final int depthStep = 2;
		final int totalHeightBlocks = structure.maxY() - structure.minY() + 1;
		final int colLeft = 56;
		final int colBottom = 468;

		// Back-to-front painter order creates a crisp 2.5D isometric-style voxel stack directly from InternalStructure.
		for (int dz = -InternalStructure.HALF_WIDTH; dz <= InternalStructure.HALF_WIDTH; dz++) {
			int depth = InternalStructure.HALF_WIDTH - dz;
			for (int y = structure.minY(); y <= structure.maxY(); y++) {
				for (int dx = -InternalStructure.HALF_WIDTH; dx <= InternalStructure.HALF_WIDTH; dx++) {
					int idx = structure.voxelAt(structure.centerX() + dx, y, structure.centerZ() + dz);
					if (idx == 0) {
						continue;
					}
					int rgb = colours[idx];
					int sx = colLeft + (dx + InternalStructure.HALF_WIDTH) * px + depth * depthStep;
					int sy = colBottom - (y - structure.minY() + 1) * px - depth * depthStep;

					g.setColor(new Color(scaleRgb(rgb, 1.12)));
					g.fillRect(sx, sy - depthStep, px, depthStep);
					g.setColor(new Color(scaleRgb(rgb, 0.78)));
					g.fillRect(sx + px, sy, depthStep, px);
					g.setColor(new Color(rgb));
					g.fillRect(sx, sy, px, px);
				}
			}
		}

		List<LayerSpan> spans = structure.layers();
		int count = spans.size();
		int cardX = 250;
		int cardW = 474;
		int cardH = count >= 5 ? 58 : 66;
		int minCenterY = 104 + cardH / 2;
		int maxCenterY = 468 - cardH / 2;
		int[] cardCenters = new int[count];
		int[] anchorYs = new int[count];

		// Top-to-bottom ordering matching physical top-of-column to bottom-of-column.
		for (int i = 0; i < count; i++) {
			LayerSpan span = spans.get(count - 1 - i);
			double midBlockY = (span.minY() + span.maxY()) * 0.5 + 0.5;
			int anchorY = colBottom - (int) Math.round((midBlockY - structure.minY()) * px) - InternalStructure.HALF_WIDTH;
			anchorYs[i] = anchorY;
			cardCenters[i] = Math.max(minCenterY, Math.min(maxCenterY, anchorY));
		}
		int minGap = cardH + 10;
		for (int pass = 0; pass < 4; pass++) {
			for (int i = 1; i < count; i++) {
				if (cardCenters[i] < cardCenters[i - 1] + minGap) {
					cardCenters[i] = cardCenters[i - 1] + minGap;
				}
			}
			if (cardCenters[count - 1] > maxCenterY) {
				int excess = cardCenters[count - 1] - maxCenterY;
				cardCenters[count - 1] -= excess;
				for (int i = count - 2; i >= 0; i--) {
					if (cardCenters[i] > cardCenters[i + 1] - minGap) {
						cardCenters[i] = cardCenters[i + 1] - minGap;
					}
				}
			}
			cardCenters[0] = Math.max(minCenterY, cardCenters[0]);
		}

		int colRightEdge = colLeft + InternalStructure.WIDTH * px + 4;
		g.setStroke(new BasicStroke(1.6f));
		for (int i = 0; i < count; i++) {
			LayerSpan span = spans.get(count - 1 - i);
			Layer layer = span.layer();
			int midY = (span.minY() + span.maxY()) / 2;
			int sampleIdx = structure.voxelAt(structure.centerX(), midY, structure.centerZ() + InternalStructure.HALF_WIDTH);
			Color swatch = new Color(sampleIdx == 0 ? colours[layer.ramp().at(layer.ramp().size() - 1)] : colours[sampleIdx]);
			int cy = cardCenters[i];
			int ay = anchorYs[i];

			g.setColor(new Color(0x475569));
			int midX = 216;
			g.drawLine(colRightEdge, ay, midX, ay);
			g.drawLine(midX, ay, midX + 12, cy);
			g.drawLine(midX + 12, cy, cardX, cy);
			g.setColor(swatch);
			g.fillOval(colRightEdge - 3, ay - 3, 6, 6);

			int cardTop = cy - cardH / 2;
			g.setColor(new Color(0x182238));
			g.fillRoundRect(cardX, cardTop, cardW, cardH, 10, 10);
			g.setColor(new Color(0x334155));
			g.drawRoundRect(cardX, cardTop, cardW, cardH, 10, 10);
			g.setColor(swatch);
			g.fillRoundRect(cardX + 2, cardTop + 6, 6, cardH - 12, 4, 4);

			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
			FontMetrics titleFm = g.getFontMetrics();
			g.setColor(new Color(0xF8FAFC));
			int textX = cardX + 18;
			int line1Y = cardTop + (cardH >= 64 ? 26 : 23);
			g.drawString(layer.name(), textX, line1Y);

			String badge = layer.toScale()
				? layer.scaleLabel()
				: layer.scaleLabel() + " (not to scale)";
			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
			g.setColor(layer.toScale() ? new Color(0xFBBF24) : new Color(0x38BDF8));
			int badgeX = Math.max(textX + titleFm.stringWidth(layer.name()) + 12, cardX + cardW - g.getFontMetrics().stringWidth(badge) - 14);
			g.drawString(badge, badgeX, line1Y);

			g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
			g.setColor(new Color(0xCBD5E1));
			int line2Y = cardTop + (cardH >= 64 ? 48 : 43);
			g.drawString(layer.composition() + "  •  " + span.height() + " blocks", textX, line2Y);
		}

		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
		g.setColor(new Color(0x64748B));
		g.drawString("Thin crusts & outer envelopes use a 6-block minimum floor so layers remain inspectable (" + totalHeightBlocks + " blocks tall).", 36, 496);
		g.dispose();
		return img;
	}

	private static BufferedImage renderInternalSideView(
		Blueprint bp,
		InternalStructure structure,
		int genHalfExtent,
		int[] colours
	) {
		final int w = 760;
		final int h = 440;
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(new Color(BACKDROP));
		g.fillRect(0, 0, w, h);

		int worldMinX = -genHalfExtent - 6;
		int worldMaxX = structure.maxX() + 10;
		int worldMinY = Math.min(-genHalfExtent, structure.minY()) - 6;
		int worldMaxY = Math.max(genHalfExtent, structure.maxY()) + 6;
		double spanX = worldMaxX - worldMinX + 1.0;
		double spanY = worldMaxY - worldMinY + 1.0;

		int viewW = w - 24;
		int viewH = h - 46;
		double scale = Math.min(viewW / spanX, viewH / spanY);
		double drawW = spanX * scale;
		double drawH = spanY * scale;
		int offX = (int) Math.round((w - drawW) * 0.5);
		int offY = (int) Math.round((h - 24 - drawH) * 0.5);
		double zStep = Math.max(1.0, genHalfExtent / 90.0);

		for (int py = 0; py < (int) Math.ceil(drawH); py++) {
			int wy = (int) Math.round(worldMaxY - (py + 0.5) / scale);
			int iy = offY + py;
			if (iy < 0 || iy >= h) {
				continue;
			}
			for (int px = 0; px < (int) Math.ceil(drawW); px++) {
				int ix = offX + px;
				if (ix < 0 || ix >= w) {
					continue;
				}
				int wx = (int) Math.round(worldMinX + (px + 0.5) / scale);
				int hitIdx = 0;
				double shade = 1.0;

				if (Math.abs(wx) <= genHalfExtent && Math.abs(wy) <= genHalfExtent) {
					double step = Math.abs(wy) < 35 ? 1.0 : zStep;
					for (double z = genHalfExtent; z >= -genHalfExtent; z -= step) {
						int wz = (int) Math.round(z);
						int v = bp.voxelAt(wx, wy, wz);
						if (v != 0) {
							hitIdx = v;
							double nz = (wz + genHalfExtent) / (2.0 * genHalfExtent);
							shade = 0.62 + 0.44 * nz;
							break;
						}
					}
				} else if (wx >= structure.minX() && wx <= structure.maxX()
					&& wy >= structure.minY() && wy <= structure.maxY()) {
					for (int wz = structure.maxZ(); wz >= structure.minZ(); wz--) {
						int v = structure.voxelAt(wx, wy, wz);
						if (v != 0) {
							hitIdx = v;
							double nz = (wz - structure.minZ()) / (double) InternalStructure.WIDTH;
							shade = 0.72 + 0.34 * nz;
							break;
						}
					}
				}

				if (hitIdx != 0) {
					img.setRGB(ix, iy, scaleRgb(colours[hitIdx], shade));
				}
			}
		}

		String id = structure.astronomyClass().id();
		String label = Character.toUpperCase(id.charAt(0)) + id.substring(1);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
		g.setColor(new Color(0xE2E8F0));
		g.drawString(label + " (+Z side view)  |  Planet halfExtent=" + genHalfExtent
			+ "   •   Structure X=[" + structure.minX() + ".." + structure.maxX() + "], Y=["
			+ structure.minY() + ".." + structure.maxY() + "]", 16, h - 14);
		g.dispose();
		return img;
	}

	private static void renderHubTopDown(Path outDir) throws IOException {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		Map<BlockPos, BlockState> blocks = new HashMap<>();
		HubLayout.build(new HubLayout.Sink() {
			@Override public void block(BlockPos pos, BlockState state) {
				blocks.put(pos.immutable(), state);
			}

			@Override public void sign(BlockPos pos, Direction facing, String... lines) {
				blocks.put(pos.immutable(), Blocks.OAK_SIGN.defaultBlockState());
			}
		});

		final int cell = 9;
		final int span = HubLayout.RADIUS * 2 + 1; // 73 blocks -> 657 px
		final int w = 760;
		final int h = 760;
		final int mapX = (w - span * cell) / 2;
		final int mapY = 66;
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		g.setColor(new Color(0x090D16));
		g.fillRect(0, 0, w, h);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
		g.setColor(new Color(0xF8FAFC));
		g.drawString("Solaria Academy — Top-Down Quiz Layout (Step 11)", 24, 34);
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		g.setColor(new Color(0x94A3B8));
		g.drawString("Radius " + HubLayout.RADIUS + "  •  West: Stage 1 (Inner)  |  North: Stage 2 (Outer+Moon)  |  East: Stage 3 (Giants Timed)", 24, 54);

		for (int x = -HubLayout.RADIUS; x <= HubLayout.RADIUS; x++) {
			for (int z = -HubLayout.RADIUS; z <= HubLayout.RADIUS; z++) {
				int px = mapX + (x + HubLayout.RADIUS) * cell;
				int py = mapY + (z + HubLayout.RADIUS) * cell;
				BlockState floor = blocks.getOrDefault(new BlockPos(x, HubLayout.FLOOR_Y, z), Blocks.AIR.defaultBlockState());
				BlockState feet = blocks.getOrDefault(new BlockPos(x, HubLayout.FLOOR_Y + 1, z), Blocks.AIR.defaultBlockState());
				BlockState roofLow = blocks.getOrDefault(new BlockPos(x, HubLayout.TUNNEL_CEILING_Y, z), Blocks.AIR.defaultBlockState());
				if (floor.isAir()) {
					g.setColor(new Color(0x090D16));
					g.fillRect(px, py, cell, cell);
					continue;
				}
				Color c;
				if (!feet.isAir() && !feet.is(Blocks.STONE_BUTTON) && !feet.is(Blocks.OAK_SIGN)) {
					c = hubPreviewColour(feet.getBlock(), true);
				} else {
					c = hubPreviewColour(floor.getBlock(), false);
					if (!roofLow.isAir()) {
						c = new Color(
							Math.min(255, c.getRed() + 14),
							Math.min(255, c.getGreen() + 18),
							Math.min(255, c.getBlue() + 26));
					}
				}
				g.setColor(c);
				g.fillRect(px, py, cell, cell);
				if (HubLayout.isWall(x, z)) {
					g.setColor(new Color(0x334155));
					g.drawRect(px, py, cell - 1, cell - 1);
				}
			}
		}

		// Highlight interactive controls and lamps so every station is readable at a glance.
		drawHubMarker(g, mapX, mapY, cell, HubLayout.SPAWN, new Color(0xF8FAFC), true);
		drawHubMarker(g, mapX, mapY, cell, HubLayout.TERMINAL, new Color(0x22C55E), true);
		drawHubMarker(g, mapX, mapY, cell, HubLayout.EXIT, new Color(0x06B6D4), true);
		drawHubMarker(g, mapX, mapY, cell, HubLayout.START, new Color(0xF59E0B), true);
		for (int slot = 0; slot < OrbitPuzzle.SLOTS; slot++) {
			drawHubMarker(g, mapX, mapY, cell, HubLayout.pedestal(slot), new Color(0xFACC15), false);
		}
		for (HubLayout.SupplyButton supply : HubLayout.supplyButtons()) {
			drawHubMarker(g, mapX, mapY, cell, supply.pos(), new Color(0xE2E8F0), false);
		}
		for (int stage = 0; stage < 3; stage++) {
			for (BlockPos lamp : HubLayout.progressLamps(stage)) {
				drawHubMarker(g, mapX, mapY, cell, lamp, new Color(0x38BDF8), true);
			}
		}

		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
		drawCenteredLabel(g, "CENTRAL HUB", mapX + (HubLayout.RADIUS) * cell + cell / 2, mapY + HubLayout.RADIUS * cell + cell / 2 - 14, new Color(0xF8FAFC));
		drawCenteredLabel(g, "ROOM 1 (Stage 1)", mapX + (HubLayout.RADIUS - HubLayout.ROOM_OFFSET) * cell, mapY + HubLayout.RADIUS * cell - 4, new Color(0x93C5FD));
		drawCenteredLabel(g, "Inner Worlds", mapX + (HubLayout.RADIUS - HubLayout.ROOM_OFFSET) * cell, mapY + HubLayout.RADIUS * cell + 12, new Color(0xCBD5E1));
		drawCenteredLabel(g, "ROOM 2 (Stage 2)", mapX + HubLayout.RADIUS * cell + 4, mapY + (HubLayout.RADIUS - HubLayout.ROOM_OFFSET) * cell - 4, new Color(0x67E8F9));
		drawCenteredLabel(g, "Outer + Moon", mapX + HubLayout.RADIUS * cell + 4, mapY + (HubLayout.RADIUS - HubLayout.ROOM_OFFSET) * cell + 12, new Color(0xCBD5E1));
		drawCenteredLabel(g, "ROOM 3 (Stage 3)", mapX + (HubLayout.RADIUS + HubLayout.ROOM_OFFSET) * cell, mapY + HubLayout.RADIUS * cell - 4, new Color(0xD8B4FE));
		drawCenteredLabel(g, "Ringed Giants", mapX + (HubLayout.RADIUS + HubLayout.ROOM_OFFSET) * cell, mapY + HubLayout.RADIUS * cell + 12, new Color(0xCBD5E1));

		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
		g.setColor(new Color(0xCBD5E1));
		g.drawString("Markers: White=Spawn | Yellow=Orbit Slots | LightGray=Local Supply | Green=Depot | Cyan=Exit | Amber=START | Blue=Progress Lamps", 24, h - 14);
		g.dispose();

		Path out = outDir.resolve("intro_hub_topdown.png");
		ImageIO.write(img, "png", out.toFile());
		System.out.println("[hub] wrote " + out);
	}

	private static void drawHubMarker(Graphics2D g, int mapX, int mapY, int cell, BlockPos pos, Color c, boolean filledSquare) {
		int px = mapX + (pos.getX() + HubLayout.RADIUS) * cell;
		int py = mapY + (pos.getZ() + HubLayout.RADIUS) * cell;
		g.setColor(c);
		if (filledSquare) {
			g.fillRect(px + 1, py + 1, cell - 2, cell - 2);
		} else {
			g.fillOval(px + 2, py + 2, cell - 4, cell - 4);
		}
	}

	private static void drawCenteredLabel(Graphics2D g, String text, int cx, int y, Color color) {
		int width = g.getFontMetrics().stringWidth(text);
		g.setColor(new Color(0x090D16));
		g.fillRoundRect(cx - width / 2 - 4, y - 11, width + 8, 14, 4, 4);
		g.setColor(color);
		g.drawString(text, cx - width / 2, y);
	}

	private static Color hubPreviewColour(Block b, boolean wall) {
		if (b == Blocks.SEA_LANTERN) return new Color(0x7DE7FF);
		if (b == Blocks.BLUE_CONCRETE) return wall ? new Color(0x1D4ED8) : new Color(0x2563EB);
		if (b == Blocks.CYAN_CONCRETE) return wall ? new Color(0x0E7490) : new Color(0x06B6D4);
		if (b == Blocks.PURPLE_CONCRETE) return wall ? new Color(0x7E22CE) : new Color(0x9333EA);
		if (b == Blocks.LIME_CONCRETE) return new Color(0x22C55E);
		if (b == Blocks.QUARTZ_BRICKS) return wall ? new Color(0xCBD5E1) : new Color(0xE2E8F0);
		if (b == Blocks.POLISHED_DEEPSLATE) return wall ? new Color(0x1E293B) : new Color(0x273449);
		if (b == Blocks.DEEPSLATE_TILES) return new Color(0x1A2233);
		if (b == Blocks.GRAY_CONCRETE) return new Color(0x64748B);
		return new Color(0x334155);
	}

	private record VarietyReport(int distinctBlocks, String topBlock, double topSharePct,
	                             double smoothTransitionPct, double minHitR, double maxHitR) {
	}
}
