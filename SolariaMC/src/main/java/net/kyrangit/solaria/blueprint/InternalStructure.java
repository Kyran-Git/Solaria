package net.kyrangit.solaria.blueprint;

import java.util.ArrayList;
import java.util.List;

/**
 * Standalone stratigraphic interior column placed east (+X) of each full-scale planet.
 *
 * <p>Instead of carving a quarter-sphere wedge into every globe, Step 10 displays each
 * celestial body's internal anatomy in a crisp 17×17-block vertical column ordered from
 * deepest core at the bottom to outer crust/atmosphere at the top. Thin crusts and outer
 * atmospheric/coronal envelopes use a 6-block minimum floor (marked {@code toScale = false})
 * so every stratum remains easy to inspect in-game and legible behind floating labels.
 */
public final class InternalStructure {

	public static final int HALF_WIDTH = 8;
	public static final int WIDTH = HALF_WIDTH * 2 + 1;
	public static final int CENTER_OFFSET_PAST_EXTENT = 42;
	public static final int CENTER_Y = 5;
	public static final int MIN_LAYER_BLOCKS = 6;
	public static final int LAYER_BLOCK_BUDGET = 72;

	/** Definition of one scientific interior stratum before vertical placement. */
	public record Layer(
		String name,
		String composition,
		String scaleLabel,
		boolean toScale,
		int blocks,
		MaterialRamp ramp
	) {
	}

	/** World-Y inclusive span assigned to one stratum inside a built column. */
	public record LayerSpan(Layer layer, int minY, int maxY) {
		public int height() {
			return maxY - minY + 1;
		}
	}

	private final AstronomyClass astronomyClass;
	private final int centerX;
	private final int centerY;
	private final int centerZ;
	private final int minY;
	private final int maxY;
	private final List<LayerSpan> spans;
	private final int seed;
	private final int frameBlock;
	private final int dividerBlock;
	private final int beaconBlock;
	private final int earthOceanDeep;
	private final int earthOceanShallow;
	private final int earthLand;
	private final int earthLandHigh;
	private final int earthSnow;

	private InternalStructure(
		AstronomyClass astronomyClass,
		int planetHalfExtent,
		MaterialPalette palette,
		List<Layer> layers,
		int seed
	) {
		this.astronomyClass = astronomyClass;
		this.centerX = planetHalfExtent + CENTER_OFFSET_PAST_EXTENT;
		this.centerY = CENTER_Y;
		this.centerZ = 0;
		this.seed = seed;

		this.frameBlock = palette.indexOf(Palette.BLACKSTONE);
		this.dividerBlock = palette.indexOf(Palette.BLACK_CONCRETE);
		this.beaconBlock = palette.indexOf(Palette.SEA_LANTERN);
		this.earthOceanDeep = palette.indexOf(Palette.BLUE_CONCRETE);
		this.earthOceanShallow = palette.indexOf(Palette.CYAN_CONCRETE);
		this.earthLand = palette.indexOf(Palette.MOSS_BLOCK);
		this.earthLandHigh = palette.indexOf(Palette.GREEN_TERRACOTTA);
		this.earthSnow = palette.indexOf(Palette.SNOW_BLOCK);

		int totalLayerBlocks = 0;
		for (Layer layer : layers) {
			totalLayerBlocks += layer.blocks();
		}
		int dividers = Math.max(0, layers.size() - 1);
		// Bottom plinth (1) + stacked layers + black_concrete dividers + top crown (1).
		int totalHeight = 2 + totalLayerBlocks + dividers;
		this.minY = CENTER_Y - totalHeight / 2;
		this.maxY = this.minY + totalHeight - 1;

		List<LayerSpan> built = new ArrayList<>(layers.size());
		int cursorY = this.minY + 1;
		for (int i = 0; i < layers.size(); i++) {
			Layer layer = layers.get(i);
			int spanMin = cursorY;
			int spanMax = cursorY + layer.blocks() - 1;
			built.add(new LayerSpan(layer, spanMin, spanMax));
			cursorY = spanMax + 2; // Skip the 1-block divider between adjacent layers.
		}
		this.spans = List.copyOf(built);
	}

	/** Build the deterministic stratigraphic column placed east of {@code astronomyClass}. */
	public static InternalStructure forClass(
		AstronomyClass astronomyClass,
		int planetHalfExtent,
		MaterialPalette palette
	) {
		if (astronomyClass == null || palette == null) {
			throw new IllegalArgumentException("astronomyClass and palette are required");
		}
		int baseSeed = astronomyClass.id().hashCode() ^ 0x51A7B309;
		List<Layer> layers = switch (astronomyClass) {
			case SUN -> List.of(
				new Layer("Core", "H -> He nuclear fusion (~15M K)", "~25% R", true, 15,
					MaterialRamp.of(palette, Palette.GOLD_BLOCK, Palette.OCHRE_FROGLIGHT, Palette.YELLOW_CONCRETE, Palette.GLOWSTONE)),
				new Layer("Radiative Zone", "Dense photon-diffusing plasma", "~45% R", true, 27,
					MaterialRamp.of(palette, Palette.ORANGE_TERRACOTTA, Palette.HONEYCOMB_BLOCK, Palette.RAW_GOLD_BLOCK, Palette.GOLD_BLOCK)),
				new Layer("Convection Zone", "Rising boiling plasma cells", "~30% R", true, 18,
					MaterialRamp.of(palette, Palette.REDSTONE_BLOCK, Palette.RED_CONCRETE, Palette.ORANGE_CONCRETE, Palette.SHROOMLIGHT)),
				new Layer("Photosphere", "Visible solar surface & sunspots", "~500 km", false, MIN_LAYER_BLOCKS,
					MaterialRamp.of(palette, Palette.RED_CONCRETE, Palette.ORANGE_CONCRETE, Palette.YELLOW_CONCRETE, Palette.GLOWSTONE)),
				new Layer("Corona", "Superheated magnetic plasma aura", "Outer aura", false, MIN_LAYER_BLOCKS,
					MaterialRamp.of(palette, Palette.RED_STAINED_GLASS, Palette.ORANGE_STAINED_GLASS, Palette.YELLOW_STAINED_GLASS, Palette.GLOWSTONE))
			);
			case MERCURY -> List.of(
				new Layer("Metallic Core", "Giant solid/molten Fe-Ni core", "~85% R", true, 58,
					MaterialRamp.of(palette, Palette.SMOOTH_STONE, Palette.LIGHT_GRAY_CONCRETE, Palette.RAW_IRON_BLOCK, Palette.IRON_BLOCK)),
				new Layer("Silicate Mantle", "Thin magnesium-silicate mantle", "~12% R", true, 8,
					MaterialRamp.of(palette, Palette.BROWN_TERRACOTTA, Palette.GRANITE, Palette.TERRACOTTA)),
				new Layer("Cratered Crust", "Silicate plains & impact basins", "~3% R", false, MIN_LAYER_BLOCKS,
					MaterialRamp.of(palette, Palette.DEEPSLATE, Palette.ANDESITE, Palette.STONE, Palette.LIGHT_GRAY_TERRACOTTA))
			);
			case VENUS -> List.of(
				new Layer("Metallic Core", "Fe-Ni alloy core (~3,100 km R)", "~52% R", true, 31,
					MaterialRamp.of(palette, Palette.RAW_IRON_BLOCK, Palette.IRON_BLOCK, Palette.GOLD_BLOCK)),
				new Layer("Rocky Mantle", "Hot convecting silicate mantle", "~47% R", true, 29,
					MaterialRamp.of(palette, Palette.BROWN_CONCRETE, Palette.RED_TERRACOTTA, Palette.NETHERRACK, Palette.GRANITE)),
				new Layer("Basalt Crust", "Rigid volcanic basalt plains", "~1% R", false, MIN_LAYER_BLOCKS,
					MaterialRamp.of(palette, Palette.BLACKSTONE, Palette.SMOOTH_BASALT, Palette.DEEPSLATE)),
				new Layer("Sulfuric Clouds", "Dense CO2 & H2SO4 cloud deck", "Atmosphere", false, MIN_LAYER_BLOCKS,
					MaterialRamp.of(palette, Palette.SMOOTH_SANDSTONE, Palette.RAW_GOLD_BLOCK, Palette.HONEYCOMB_BLOCK, Palette.YELLOW_CONCRETE, Palette.OCHRE_FROGLIGHT))
			);
			case EARTH -> List.of(
				new Layer("Inner Core", "Solid Fe-Ni crystal sphere", "~19% R", true, 13,
					MaterialRamp.of(palette, Palette.RAW_GOLD_BLOCK, Palette.GOLD_BLOCK, Palette.OCHRE_FROGLIGHT)),
				new Layer("Outer Core", "Liquid Fe-Ni magnetic dynamo", "~35% R", true, 23,
					MaterialRamp.of(palette, Palette.REDSTONE_BLOCK, Palette.RAW_IRON_BLOCK, Palette.IRON_BLOCK)),
				new Layer("Mantle", "Convecting silicate rock", "~45% R", true, 30,
					MaterialRamp.of(palette, Palette.RED_NETHER_BRICKS, Palette.NETHERRACK, Palette.BROWN_TERRACOTTA, Palette.GRANITE)),
				new Layer("Crust & Ocean", "Continental plates & liquid oceans", "<1% R", false, MIN_LAYER_BLOCKS,
					MaterialRamp.of(palette, Palette.DEEPSLATE, Palette.STONE, Palette.ANDESITE))
			);
			case MOON -> List.of(
				new Layer("Metallic Core", "Small iron-rich lunar core", "~20% R", true, 14,
					MaterialRamp.of(palette, Palette.STONE, Palette.RAW_IRON_BLOCK, Palette.IRON_BLOCK)),
				new Layer("Silicate Mantle", "Olivine & pyroxene rocky mantle", "~76% R", true, 52,
					MaterialRamp.of(palette, Palette.SMOOTH_BASALT, Palette.DEEPSLATE, Palette.TUFF)),
				new Layer("Lunar Crust", "Anorthosite highlands & basalt maria", "~4% R", false, MIN_LAYER_BLOCKS,
					MaterialRamp.of(palette, Palette.GRAY_CONCRETE, Palette.ANDESITE, Palette.STONE, Palette.LIGHT_GRAY_CONCRETE))
			);
			case MARS -> List.of(
				new Layer("Liquid Iron Core", "Molten Fe-Ni-S core (InSight)", "~54% R", true, 36,
					MaterialRamp.of(palette, Palette.REDSTONE_BLOCK, Palette.RAW_IRON_BLOCK, Palette.IRON_BLOCK)),
				new Layer("Silicate Mantle", "Dormant dense rocky mantle", "~44% R", true, 30,
					MaterialRamp.of(palette, Palette.BROWN_CONCRETE, Palette.RED_TERRACOTTA, Palette.NETHERRACK)),
				new Layer("Iron-Oxide Crust", "Basalt crust & rusty dust", "~2% R", false, MIN_LAYER_BLOCKS,
					MaterialRamp.of(palette, Palette.STONE, Palette.RED_SANDSTONE, Palette.RAW_COPPER_BLOCK, Palette.COPPER_BLOCK))
			);
			case JUPITER -> List.of(
				new Layer("Rock-Ice Core", "Dense compressed rock & ice core", "~15% R", true, 10,
					MaterialRamp.of(palette, Palette.BLACKSTONE, Palette.DEEPSLATE, Palette.IRON_BLOCK)),
				new Layer("Metallic Hydrogen", "Electrically conducting liquid H", "~60% R", true, 40,
					MaterialRamp.of(palette, Palette.IRON_BLOCK, Palette.SMOOTH_STONE, Palette.LIGHT_GRAY_CONCRETE, Palette.CYAN_CONCRETE)),
				new Layer("Molecular Hydrogen", "Supercritical H2 & He envelope", "~24% R", true, 16,
					MaterialRamp.of(palette, Palette.BLUE_TERRACOTTA, Palette.LAPIS_BLOCK, Palette.BLUE_CONCRETE, Palette.CYAN_CONCRETE)),
				new Layer("Ammonia Clouds", "Banded cloud decks & Great Red Spot", "<1% R", false, MIN_LAYER_BLOCKS,
					MaterialRamp.of(palette, Palette.BROWN_TERRACOTTA, Palette.TERRACOTTA, Palette.ORANGE_TERRACOTTA, Palette.WHITE_TERRACOTTA, Palette.RED_CONCRETE))
			);
			case SATURN -> List.of(
				new Layer("Rock-Ice Core", "Dense rocky & icy planetary seed", "~20% R", true, 13,
					MaterialRamp.of(palette, Palette.BLACKSTONE, Palette.DEEPSLATE, Palette.IRON_BLOCK)),
				new Layer("Metallic Hydrogen", "Liquid metallic hydrogen shell", "~38% R", true, 26,
					MaterialRamp.of(palette, Palette.BLUE_TERRACOTTA, Palette.IRON_BLOCK, Palette.SMOOTH_STONE, Palette.LIGHT_GRAY_CONCRETE)),
				new Layer("Molecular Hydrogen", "Deep liquid H2 & He envelope", "~41% R", true, 27,
					MaterialRamp.of(palette, Palette.LAPIS_BLOCK, Palette.BLUE_CONCRETE, Palette.CYAN_CONCRETE)),
				new Layer("Golden Clouds", "Butter-gold ammonia haze bands", "<1% R", false, MIN_LAYER_BLOCKS,
					MaterialRamp.of(palette, Palette.YELLOW_TERRACOTTA, Palette.RAW_GOLD_BLOCK, Palette.END_STONE, Palette.SMOOTH_SANDSTONE))
			);
			case URANUS -> List.of(
				new Layer("Rocky Core", "Small silicate & Fe-Ni core", "~20% R", true, 14,
					MaterialRamp.of(palette, Palette.BLACKSTONE, Palette.DEEPSLATE, Palette.TUFF)),
				new Layer("Water-Ammonia Ice", "Hot superionic H2O/NH3/CH4 mantle", "~60% R", true, 44,
					MaterialRamp.of(palette, Palette.DARK_PRISMARINE, Palette.PRISMARINE, Palette.CYAN_CONCRETE, Palette.PACKED_ICE, Palette.BLUE_ICE)),
				new Layer("Methane-H2 Envelope", "Cold pale-cyan H2/He/CH4 atmosphere", "~20% R", true, 14,
					MaterialRamp.of(palette, Palette.CYAN_TERRACOTTA, Palette.OXIDIZED_COPPER, Palette.LIGHT_BLUE_CONCRETE, Palette.BLUE_ICE))
			);
			case NEPTUNE -> List.of(
				new Layer("Rocky Core", "Dense silicate & Fe-Ni core", "~22% R", true, 16,
					MaterialRamp.of(palette, Palette.BLACKSTONE, Palette.DEEPSLATE, Palette.TUFF)),
				new Layer("Water-Ammonia Ice", "High-pressure ionic H2O/NH3/CH4 mantle", "~62% R", true, 44,
					MaterialRamp.of(palette, Palette.DARK_PRISMARINE, Palette.PRISMARINE, Palette.PACKED_ICE, Palette.BLUE_ICE)),
				new Layer("Methane-H2 Envelope", "Deep azure stormy H2/He/CH4 layer", "~16% R", true, 12,
					MaterialRamp.of(palette, Palette.LAPIS_BLOCK, Palette.BLUE_CONCRETE, Palette.CYAN_CONCRETE, Palette.LIGHT_BLUE_CONCRETE))
			);
		};
		return new InternalStructure(astronomyClass, planetHalfExtent, palette, layers, baseSeed);
	}

	public AstronomyClass astronomyClass() {
		return astronomyClass;
	}

	public int centerX() {
		return centerX;
	}

	public int centerY() {
		return centerY;
	}

	public int centerZ() {
		return centerZ;
	}

	public int minX() {
		return centerX - HALF_WIDTH;
	}

	public int maxX() {
		return centerX + HALF_WIDTH;
	}

	public int minY() {
		return minY;
	}

	public int maxY() {
		return maxY;
	}

	public int minZ() {
		return centerZ - HALF_WIDTH;
	}

	public int maxZ() {
		return centerZ + HALF_WIDTH;
	}

	public List<LayerSpan> layers() {
		return spans;
	}

	public int dividerBlock() {
		return dividerBlock;
	}

	public int frameBlock() {
		return frameBlock;
	}

	public int beaconBlock() {
		return beaconBlock;
	}

	public boolean contains(int x, int y, int z) {
		return x >= minX() && x <= maxX()
			&& y >= minY && y <= maxY
			&& z >= minZ() && z <= maxZ();
	}

	/** Palette index (1-based) at world block {@code (x, y, z)}, or {@code 0} (air) outside. */
	public int voxelAt(int x, int y, int z) {
		if (!contains(x, y, z)) {
			return 0;
		}
		int dx = x - centerX;
		int dz = z - centerZ;
		boolean corner = Math.abs(dx) == HALF_WIDTH && Math.abs(dz) == HALF_WIDTH;
		boolean perimeter = Math.abs(dx) == HALF_WIDTH || Math.abs(dz) == HALF_WIDTH;

		if (y == minY) {
			return corner ? beaconBlock : frameBlock;
		}
		if (y == maxY) {
			if (corner) {
				return beaconBlock;
			}
			// Keep the top collar open inside the frame so the top stratum face stays visible from above.
			return perimeter ? frameBlock : 0;
		}
		if (corner) {
			return frameBlock;
		}

		for (LayerSpan span : spans) {
			if (y >= span.minY() && y <= span.maxY()) {
				return sampleLayer(span, x, y, z, dx, dz);
			}
			if (y == span.maxY() + 1) {
				return dividerBlock;
			}
		}
		return 0;
	}

	private int sampleLayer(LayerSpan span, int x, int y, int z, int dx, int dz) {
		Layer layer = span.layer();
		int h = Math.max(1, span.height() - 1);
		double relY = (double) (y - span.minY()) / h;

		if (astronomyClass == AstronomyClass.EARTH && span == spans.getLast() && y >= span.maxY() - 1) {
			double continent = SurfacePainter.fbm(dx * 0.24, y * 0.20, dz * 0.24, 3, seed ^ 0xEA50);
			if (continent > 0.54) {
				return continent > 0.69 ? earthSnow : (continent > 0.62 ? earthLandHigh : earthLand);
			}
			return continent > 0.46 ? earthOceanShallow : earthOceanDeep;
		}

		if (astronomyClass == AstronomyClass.SUN && span == spans.getLast()) {
			double swirl = SurfacePainter.fbm(dx * 0.30, y * 0.28, dz * 0.30, 2, seed ^ 0xC070);
			double t = SurfacePainter.clamp01(0.15 + relY * 0.55 + (swirl - 0.5) * 0.45);
			return layer.ramp().sampleDithered(t, 0.35, x, y, z, seed ^ 0xC07E);
		}

		double grain = SurfacePainter.fbm(dx * 0.22, y * 0.22, dz * 0.22, 2, seed ^ layer.name().hashCode());
		double t = SurfacePainter.clamp01(0.12 + relY * 0.68 + (grain - 0.5) * 0.42);
		return layer.ramp().sampleDithered(t, 0.32, x, y, z, seed ^ layer.name().hashCode());
	}
}
