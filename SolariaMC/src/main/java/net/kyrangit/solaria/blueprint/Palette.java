package net.kyrangit.solaria.blueprint;

/**
 * The single hardcoded material palette for all Solaria blueprints.
 * All blocks are safe: no gravity, fluids, fire, or support-dependent blocks.
 * Palette indices are 1-based; 0 is always air.
 * Each block ID appears at most once. Generators look up indices via
 * {@code palette.indexOf(blockId)}.
 */
public final class Palette {
	private Palette() {
	}

	// Block ID constants used by generators. Grouped by primary use, but shared across classes.
	public static final String GLOWSTONE = "minecraft:glowstone";
	public static final String GOLD_BLOCK = "minecraft:gold_block";
	public static final String YELLOW_CONCRETE = "minecraft:yellow_concrete";
	public static final String BLUE_CONCRETE = "minecraft:blue_concrete";
	public static final String GREEN_CONCRETE = "minecraft:green_concrete";
	public static final String SNOW_BLOCK = "minecraft:snow_block";
	public static final String IRON_BLOCK = "minecraft:iron_block";
	public static final String LIGHT_GRAY_CONCRETE = "minecraft:light_gray_concrete";
	public static final String GRAY_CONCRETE = "minecraft:gray_concrete";
	public static final String STONE = "minecraft:stone";
	public static final String RED_TERRACOTTA = "minecraft:red_terracotta";
	public static final String WHITE_CONCRETE = "minecraft:white_concrete";
	public static final String ORANGE_CONCRETE = "minecraft:orange_concrete";
	public static final String BROWN_CONCRETE = "minecraft:brown_concrete";
	public static final String RED_CONCRETE = "minecraft:red_concrete";
	public static final String DIAMOND_BLOCK = "minecraft:diamond_block";
	public static final String BLACK_CONCRETE = "minecraft:black_concrete";
	public static final String LIGHT_BLUE_CONCRETE = "minecraft:light_blue_concrete";
	public static final String QUARTZ_BLOCK = "minecraft:quartz_block";

	/** Fallback block for unknown palette indices. */
	public static final String FALLBACK = STONE;

	/** Build the shared palette instance. Entry order is fixed for determinism. */
	public static MaterialPalette create() {
		return MaterialPalette.builder(FALLBACK)
			.add(GLOWSTONE)              //  1 — sun core / light source
			.add(GOLD_BLOCK)             //  2 — sun surface
			.add(YELLOW_CONCRETE)        //  3 — sun corona / venus cloud
			.add(BLUE_CONCRETE)          //  4 — earth ocean / neptune body
			.add(GREEN_CONCRETE)         //  5 — earth land
			.add(SNOW_BLOCK)             //  6 — earth ice / mars polar cap / pluto heart
			.add(IRON_BLOCK)             //  7 — earth core / mars core
			.add(LIGHT_GRAY_CONCRETE)    //  8 — moon surface
			.add(GRAY_CONCRETE)          //  9 — moon crater / uranus shading
			.add(STONE)                  // 10 — moon core (same as fallback, still valid)
			.add(RED_TERRACOTTA)         // 11 — mars surface
			.add(WHITE_CONCRETE)         // 12 — mars polar cap / saturn ring highlight
			.add(ORANGE_CONCRETE)        // 13 — jupiter light band / saturn band
			.add(BROWN_CONCRETE)         // 14 — jupiter dark band / pluto tan surface
			.add(RED_CONCRETE)           // 15 — jupiter great red spot / neptune storm
			.add(DIAMOND_BLOCK)          // 16 — jupiter core / uranus body
			.add(BLACK_CONCRETE)         // 17 — shadow / space backdrop
			.add(LIGHT_BLUE_CONCRETE)    // 18 — uranus pale cyan / neptune light streak
			.add(QUARTZ_BLOCK)           // 19 — saturn ring / pluto highlight
			.build();
	}
}
