package net.kyrangit.solaria;

import net.kyrangit.solaria.blueprint.AstronomyClass;
import net.kyrangit.solaria.blueprint.Blueprint;
import net.kyrangit.solaria.blueprint.BlueprintGenerator;
import net.kyrangit.solaria.blueprint.MaterialPalette;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Renders every blueprint to PNG slices under {@code build/previews/}, so a human
 * can review the shapes without launching Minecraft.
 *
 * <p>Slices are sampled through {@link Blueprint#voxelAt}: equator (y=0), two mid
 * latitudes (y = ±diameter/4), and the poles (y = ±diameter/2). Because planets
 * are now 301–1001 blocks wide, only five slices per class are written (not every y).
 *
 * <p>Scale factor: each voxel renders as {@value SCALE}×{@value SCALE} pixels.
 * For the Sun (diameter 1001) at scale 0.05, the resulting blueprint is ~51 blocks,
 * producing a 510 × 510 pixel image.
 *
 * <p>Uses AWT/ImageIO — available headlessly on Java 25 and on most CI machines.
 */
public final class BlueprintPreview {
	/** Pixels per voxel. */
	private static final int SCALE = 4;

	/**
	 * Scale fed to the generator. 0.05 gives a 51-wide Sun preview in ~100 ms.
	 * A teacher wanting full-size previews can set this to 1.0 and wait longer.
	 */
	private static final double PREVIEW_SCALE = 0.05;

	/** Approximate colour for each palette block (for human review only). */
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
	}

	public static void main(String[] args) throws IOException {
		Path outDir = Path.of("build", "previews");
		Files.createDirectories(outDir);
		int totalSlices = 0;
		for (AstronomyClass cls : AstronomyClass.values()) {
			Blueprint bp = BlueprintGenerator.generate(cls, PREVIEW_SCALE);
			Path classDir = outDir.resolve(cls.id());
			Files.createDirectories(classDir);
			totalSlices += renderSlices(bp, classDir);
		}
		System.out.println("Rendered " + totalSlices + " PNG slices to " + outDir.toAbsolutePath() + ".");
	}

	private static int renderSlices(Blueprint bp, Path classDir) throws IOException {
		int half = bp.diameter() / 2;
		// Five representative slices: south-pole, mid-south, equator, mid-north, north-pole.
		int[] yOffsets = {-half, -half / 2, 0, half / 2, half};
		String[] names  = {"south_pole", "mid_south", "equator", "mid_north", "north_pole"};

		// For ringed planets extend the window to include the ring.
		int halfExt = (int) Math.ceil(half * 2.0); // generous enough for Saturn (1.9×)

		MaterialPalette palette = bp.palette();
		int written = 0;
		for (int i = 0; i < yOffsets.length; i++) {
			int y = yOffsets[i];
			int imgSize = halfExt * 2 + 1;
			BufferedImage image = new BufferedImage(imgSize * SCALE, imgSize * SCALE, BufferedImage.TYPE_INT_RGB);
			// Background: dark space colour.
			for (int px = 0; px < image.getWidth(); px++) {
				for (int py = 0; py < image.getHeight(); py++) {
					image.setRGB(px, py, 0x1A1D21);
				}
			}
			boolean anyVoxel = false;
			for (int xi = 0; xi < imgSize; xi++) {
				for (int zi = 0; zi < imgSize; zi++) {
					int x = xi - halfExt;
					int z = zi - halfExt;
					int idx = bp.voxelAt(x, y, z);
					if (idx == 0) continue;
					anyVoxel = true;
					String blockId = palette.blockId(idx);
					int colour = BLOCK_COLOURS.getOrDefault(blockId, 0xFF00FF);
					fillCell(image, xi, zi, colour);
				}
			}
			if (!anyVoxel) continue; // skip fully-air slices (e.g. poles of small test planets)
			String fname = names[i] + ".png";
			ImageIO.write(image, "png", classDir.resolve(fname).toFile());
			written++;
		}
		return written;
	}

	private static void fillCell(BufferedImage image, int gridX, int gridZ, int rgb) {
		int x0 = gridX * SCALE;
		int y0 = gridZ * SCALE;
		for (int px = 0; px < SCALE; px++) {
			for (int py = 0; py < SCALE; py++) {
				image.setRGB(x0 + px, y0 + py, rgb);
			}
		}
	}
}
