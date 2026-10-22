package net.kyrangit.solaria;

import net.kyrangit.solaria.blueprint.AstronomyClass;
import net.kyrangit.solaria.blueprint.Blueprint;
import net.kyrangit.solaria.blueprint.BlueprintGenerator;
import net.kyrangit.solaria.blueprint.MaterialPalette;
import net.kyrangit.solaria.blueprint.VoxelGrid;
import net.kyrangit.solaria.config.SolariaConfig;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Renders every blueprint to PNG slices under {@code build/previews/}, so a human
 * can review the structures without launching Minecraft (plan M2.4).
 *
 * <p>Pure JDK: AWT/{@link ImageIO} are available at compile time on the Java 25
 * toolchain and work headlessly on both Windows and CI. No external image library.
 */
public final class BlueprintPreview {
	/** Pixels per voxel. One block = {@value} x {@value} pixels makes small previews legible. */
	private static final int SCALE = 10;

	/** Hex colours used in the preview, matched to each palette block. Not shipped. */
	private static final Map<String, Integer> BLOCK_COLOURS = new LinkedHashMap<>();

	static {
		BLOCK_COLOURS.put("minecraft:glowstone", 0xFFE89A);
		BLOCK_COLOURS.put("minecraft:gold_block", 0xF9D71C);
		BLOCK_COLOURS.put("minecraft:yellow_concrete", 0xF1AF15);
		BLOCK_COLOURS.put("minecraft:blue_concrete", 0x2C44A5);
		BLOCK_COLOURS.put("minecraft:green_concrete", 0x5D7C15);
		BLOCK_COLOURS.put("minecraft:snow_block", 0xF2FAFC);
		BLOCK_COLOURS.put("minecraft:iron_block", 0xDCDCDC);
		BLOCK_COLOURS.put("minecraft:light_gray_concrete", 0x9D9D97);
		BLOCK_COLOURS.put("minecraft:gray_concrete", 0x474F52);
		BLOCK_COLOURS.put("minecraft:stone", 0x7F7F7F);
		BLOCK_COLOURS.put("minecraft:red_terracotta", 0x8F3D2D);
		BLOCK_COLOURS.put("minecraft:white_concrete", 0xCFD5D6);
		BLOCK_COLOURS.put("minecraft:orange_concrete", 0xE06100);
		BLOCK_COLOURS.put("minecraft:brown_concrete", 0x603A1F);
		BLOCK_COLOURS.put("minecraft:red_concrete", 0x8E2121);
		BLOCK_COLOURS.put("minecraft:diamond_block", 0x5DECF5);
		BLOCK_COLOURS.put("minecraft:black_concrete", 0x080A0F);
		BLOCK_COLOURS.put("minecraft:light_blue_concrete", 0x36A4D4);
		BLOCK_COLOURS.put("minecraft:quartz_block", 0xECE9D9);
	}

	public static void main(String[] args) throws IOException {
		Path outDir = Path.of("build", "previews");
		Files.createDirectories(outDir);
		SolariaConfig config = SolariaConfig.defaults();
		int totalSlices = 0;
		for (AstronomyClass astronomyClass : AstronomyClass.values()) {
			Blueprint blueprint = BlueprintGenerator.generate(
				astronomyClass, config.blocksPerUnit(), config.maxSizeBlocks());
			Path classDir = outDir.resolve(astronomyClass.id());
			Files.createDirectories(classDir);
			totalSlices += renderSlices(blueprint, classDir);
		}
		System.out.println("Rendered " + totalSlices + " PNG slices to " + outDir.toAbsolutePath() + ".");
	}

	private static int renderSlices(Blueprint blueprint, Path classDir) throws IOException {
		VoxelGrid grid = blueprint.grid();
		MaterialPalette palette = blueprint.palette();
		int width = grid.sizeX();
		int depth = grid.sizeZ();
		int height = grid.sizeY();
		int written = 0;
		for (int y = 0; y < height; y++) {
			if (sliceIsEmpty(grid, y)) {
				continue;
			}
			BufferedImage image = new BufferedImage(width * SCALE, depth * SCALE, BufferedImage.TYPE_INT_RGB);
			for (int px = 0; px < image.getWidth(); px++) {
				for (int py = 0; py < image.getHeight(); py++) {
					image.setRGB(px, py, 0x1A1D21);
				}
			}
			for (int x = 0; x < width; x++) {
				for (int z = 0; z < depth; z++) {
					int paletteIndex = grid.get(x, y, z);
					if (paletteIndex == 0) {
						continue;
					}
					int colour = BLOCK_COLOURS.getOrDefault(palette.blockId(paletteIndex), 0xFF00FF);
					fillCell(image, x, z, colour);
				}
			}
			String name = String.format("y%03d.png", y);
			ImageIO.write(image, "png", classDir.resolve(name).toFile());
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

	private static boolean sliceIsEmpty(VoxelGrid grid, int y) {
		for (int x = 0; x < grid.sizeX(); x++) {
			for (int z = 0; z < grid.sizeZ(); z++) {
				if (grid.get(x, y, z) != 0) {
					return false;
				}
			}
		}
		return true;
	}
}
