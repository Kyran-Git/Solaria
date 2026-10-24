package net.kyrangit.solaria.blueprint;

/**
 * The single hardcoded 69-block material palette for all Solaria blueprints.
 * All blocks are safe: no gravity, fluids, fire, or support-dependent blocks.
 * Palette indices are 1-based; 0 is always air.
 * Each block ID appears at most once. Generators look up indices via
 * {@code palette.indexOf(blockId)}.
 *
 * <p>Indices 1–19 are frozen and must never change (stable wire format).
 * Entries 20–69 were added in blueprint_v2 to cover the full v2 colour specification.
 */
public final class Palette {
	private Palette() {
	}

	// ------------------------------------------------------------------ indices 1–19 (frozen)
	public static final String GLOWSTONE             = "minecraft:glowstone";              // 1
	public static final String GOLD_BLOCK            = "minecraft:gold_block";             // 2
	public static final String YELLOW_CONCRETE       = "minecraft:yellow_concrete";        // 3
	public static final String BLUE_CONCRETE         = "minecraft:blue_concrete";          // 4
	public static final String GREEN_CONCRETE        = "minecraft:green_concrete";         // 5
	public static final String SNOW_BLOCK            = "minecraft:snow_block";             // 6
	public static final String IRON_BLOCK            = "minecraft:iron_block";             // 7
	public static final String LIGHT_GRAY_CONCRETE   = "minecraft:light_gray_concrete";   // 8
	public static final String GRAY_CONCRETE         = "minecraft:gray_concrete";         // 9
	public static final String STONE                 = "minecraft:stone";                 // 10
	public static final String RED_TERRACOTTA        = "minecraft:red_terracotta";        // 11
	public static final String WHITE_CONCRETE        = "minecraft:white_concrete";        // 12
	public static final String ORANGE_CONCRETE       = "minecraft:orange_concrete";       // 13
	public static final String BROWN_CONCRETE        = "minecraft:brown_concrete";        // 14
	public static final String RED_CONCRETE          = "minecraft:red_concrete";          // 15
	public static final String DIAMOND_BLOCK         = "minecraft:diamond_block";         // 16
	public static final String BLACK_CONCRETE        = "minecraft:black_concrete";        // 17
	public static final String LIGHT_BLUE_CONCRETE   = "minecraft:light_blue_concrete";  // 18
	public static final String QUARTZ_BLOCK          = "minecraft:quartz_block";         // 19

	// ------------------------------------------------------------------ indices 20–69 (v2)
	public static final String ORANGE_TERRACOTTA     = "minecraft:orange_terracotta";     // 20
	public static final String YELLOW_TERRACOTTA     = "minecraft:yellow_terracotta";     // 21
	public static final String TERRACOTTA            = "minecraft:terracotta";            // 22
	public static final String BROWN_TERRACOTTA      = "minecraft:brown_terracotta";      // 23
	public static final String WHITE_TERRACOTTA      = "minecraft:white_terracotta";      // 24
	public static final String SMOOTH_SANDSTONE      = "minecraft:smooth_sandstone";      // 25
	public static final String END_STONE             = "minecraft:end_stone";             // 26
	public static final String ANDESITE              = "minecraft:andesite";              // 27
	public static final String TUFF                  = "minecraft:tuff";                  // 28
	public static final String SMOOTH_STONE          = "minecraft:smooth_stone";          // 29
	public static final String CALCITE               = "minecraft:calcite";               // 30
	public static final String CYAN_CONCRETE         = "minecraft:cyan_concrete";         // 31
	public static final String PACKED_ICE            = "minecraft:packed_ice";            // 32
	public static final String LAPIS_BLOCK           = "minecraft:lapis_block";           // 33
	public static final String BLUE_TERRACOTTA       = "minecraft:blue_terracotta";       // 34
	public static final String GREEN_TERRACOTTA      = "minecraft:green_terracotta";      // 35
	public static final String MOSS_BLOCK            = "minecraft:moss_block";            // 36
	public static final String LIME_CONCRETE         = "minecraft:lime_concrete";         // 37
	public static final String LIME_TERRACOTTA       = "minecraft:lime_terracotta";       // 38
	public static final String PRISMARINE            = "minecraft:prismarine";            // 39
	public static final String DARK_PRISMARINE       = "minecraft:dark_prismarine";       // 40
	public static final String BLUE_ICE              = "minecraft:blue_ice";              // 41
	public static final String LIGHT_BLUE_TERRACOTTA = "minecraft:light_blue_terracotta"; // 42
	public static final String CYAN_TERRACOTTA       = "minecraft:cyan_terracotta";       // 43
	public static final String OXIDIZED_COPPER       = "minecraft:oxidized_copper";       // 44
	public static final String RED_SANDSTONE         = "minecraft:red_sandstone";         // 45
	public static final String SMOOTH_RED_SANDSTONE  = "minecraft:smooth_red_sandstone";  // 46
	public static final String GRANITE               = "minecraft:granite";               // 47
	public static final String PACKED_MUD            = "minecraft:packed_mud";            // 48
	public static final String MUD_BRICKS            = "minecraft:mud_bricks";            // 49
	public static final String NETHERRACK            = "minecraft:netherrack";            // 50
	public static final String RED_NETHER_BRICKS     = "minecraft:red_nether_bricks";     // 51
	public static final String RAW_IRON_BLOCK        = "minecraft:raw_iron_block";        // 52
	public static final String RAW_COPPER_BLOCK      = "minecraft:raw_copper_block";      // 53
	public static final String RAW_GOLD_BLOCK        = "minecraft:raw_gold_block";        // 54
	public static final String HONEYCOMB_BLOCK       = "minecraft:honeycomb_block";       // 55
	public static final String OCHRE_FROGLIGHT       = "minecraft:ochre_froglight";       // 56
	public static final String SHROOMLIGHT           = "minecraft:shroomlight";           // 57
	public static final String SEA_LANTERN           = "minecraft:sea_lantern";           // 58
	public static final String END_STONE_BRICKS      = "minecraft:end_stone_bricks";      // 59
	public static final String SMOOTH_QUARTZ         = "minecraft:smooth_quartz";         // 60
	public static final String DIORITE               = "minecraft:diorite";               // 61
	public static final String LIGHT_GRAY_TERRACOTTA = "minecraft:light_gray_terracotta"; // 62
	public static final String GRAY_TERRACOTTA       = "minecraft:gray_terracotta";       // 63
	public static final String DEEPSLATE             = "minecraft:deepslate";             // 64
	public static final String SMOOTH_BASALT         = "minecraft:smooth_basalt";         // 65
	public static final String BLACKSTONE            = "minecraft:blackstone";            // 66
	public static final String COAL_BLOCK            = "minecraft:coal_block";            // 67
	public static final String COPPER_BLOCK          = "minecraft:copper_block";          // 68
	public static final String REDSTONE_BLOCK        = "minecraft:redstone_block";        // 69

	/** Fallback block for unknown palette indices. */
	public static final String FALLBACK = STONE;

	/** Build the shared 69-entry palette instance. Entry order is fixed for determinism. */
	public static MaterialPalette create() {
		return MaterialPalette.builder(FALLBACK)
			// indices 1–19 (frozen)
			.add(GLOWSTONE)              //  1 sun core
			.add(GOLD_BLOCK)             //  2 sun surface / venus+saturn accent
			.add(YELLOW_CONCRETE)        //  3 sun corona / venus clouds
			.add(BLUE_CONCRETE)          //  4 earth ocean / neptune body
			.add(GREEN_CONCRETE)         //  5 earth land
			.add(SNOW_BLOCK)             //  6 earth polar ice
			.add(IRON_BLOCK)             //  7 earth/mars core
			.add(LIGHT_GRAY_CONCRETE)    //  8 moon surface / saturn ring edge
			.add(GRAY_CONCRETE)          //  9 moon mare / uranus shading
			.add(STONE)                  // 10 moon core / fallback
			.add(RED_TERRACOTTA)         // 11 mars surface
			.add(WHITE_CONCRETE)         // 12 polar caps / clouds / saturn band
			.add(ORANGE_CONCRETE)        // 13 sun / jupiter / saturn bands
			.add(BROWN_CONCRETE)         // 14 jupiter dark band
			.add(RED_CONCRETE)           // 15 great red spot / neptune storm
			.add(DIAMOND_BLOCK)          // 16 gas-giant core / uranus body
			.add(BLACK_CONCRETE)         // 17 reserved shadow/backdrop
			.add(LIGHT_BLUE_CONCRETE)    // 18 uranus body / neptune streak
			.add(QUARTZ_BLOCK)           // 19 saturn ring / uranus ring
			// indices 20–69 (v2 additions)
			.add(ORANGE_TERRACOTTA)      // 20 mars dust / venus+jupiter warm bands
			.add(YELLOW_TERRACOTTA)      // 21 saturn body / venus haze / jupiter pale zones
			.add(TERRACOTTA)             // 22 mars plains / jupiter+saturn tan / earth desert
			.add(BROWN_TERRACOTTA)       // 23 mars dark regions / jupiter belts
			.add(WHITE_TERRACOTTA)       // 24 jupiter cream zones / saturn pale bands
			.add(SMOOTH_SANDSTONE)       // 25 venus body / saturn pale gold
			.add(END_STONE)              // 26 venus cream clouds
			.add(ANDESITE)               // 27 moon highlands
			.add(TUFF)                   // 28 moon crater shading
			.add(SMOOTH_STONE)           // 29 moon bright highlands
			.add(CALCITE)                // 30 saturn ring gaps / moon bright craters
			.add(CYAN_CONCRETE)          // 31 uranus body / neptune accent
			.add(PACKED_ICE)             // 32 uranus pale atmosphere
			.add(LAPIS_BLOCK)            // 33 neptune deep blue
			.add(BLUE_TERRACOTTA)        // 34 neptune dark spot / earth deep shadow
			.add(GREEN_TERRACOTTA)       // 35 earth forest / land variation
			.add(MOSS_BLOCK)             // 36 earth vegetation
			.add(LIME_CONCRETE)          // 37 earth grassland / bright vegetation
			.add(LIME_TERRACOTTA)        // 38 earth dry plains / olive hills
			.add(PRISMARINE)             // 39 earth shallow ocean / uranus teal tint
			.add(DARK_PRISMARINE)        // 40 earth deep ocean / neptune deep shadow
			.add(BLUE_ICE)               // 41 uranus pale ice-blue / neptune / earth glacier
			.add(LIGHT_BLUE_TERRACOTTA)  // 42 neptune haze / uranus muted shading
			.add(CYAN_TERRACOTTA)        // 43 uranus shading / earth muted coast
			.add(OXIDIZED_COPPER)        // 44 uranus teal-green / earth lagoon
			.add(RED_SANDSTONE)          // 45 mars rock
			.add(SMOOTH_RED_SANDSTONE)   // 46 mars smooth plains
			.add(GRANITE)                // 47 mars rusty highlands
			.add(PACKED_MUD)             // 48 mars dust / jupiter tan zones
			.add(MUD_BRICKS)             // 49 mars+jupiter tan-brown bands
			.add(NETHERRACK)             // 50 mars dark rock / canyon shadow
			.add(RED_NETHER_BRICKS)      // 51 mars deep canyon
			.add(RAW_IRON_BLOCK)         // 52 mars rust dust / jupiter peach bands
			.add(RAW_COPPER_BLOCK)       // 53 mars orange-brown / jupiter warm belts
			.add(RAW_GOLD_BLOCK)         // 54 venus golden haze / saturn dull gold
			.add(HONEYCOMB_BLOCK)        // 55 venus amber haze / sun corona
			.add(OCHRE_FROGLIGHT)        // 56 sun pale corona / venus bright cloud tops
			.add(SHROOMLIGHT)            // 57 sun granulation
			.add(SEA_LANTERN)            // 58 reserved
			.add(END_STONE_BRICKS)       // 59 saturn cream bands / venus clouds
			.add(SMOOTH_QUARTZ)          // 60 saturn ring bright
			.add(DIORITE)                // 61 moon highlands
			.add(LIGHT_GRAY_TERRACOTTA)  // 62 moon dusty regolith / mercury dusty terrain
			.add(GRAY_TERRACOTTA)        // 63 saturn ring gap
			.add(DEEPSLATE)              // 64 mercury dark crater
			.add(SMOOTH_BASALT)          // 65 moon dark mare
			.add(BLACKSTONE)             // 66 reserved backdrop alternative
			.add(COAL_BLOCK)             // 67 reserved
			.add(COPPER_BLOCK)           // 68 reserved
			.add(REDSTONE_BLOCK)         // 69 reserved
			.build();
	}
}
