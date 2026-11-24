package net.kyrangit.solaria.intro;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.List;

/**
 * Bounded enclosed academy build, exclusively in {@code solariamod:intro_hub}.
 *
 * <p>Layout matches the Step 11 radial hub-and-tunnels design: an enclosed central hub
 * connected via three lit 3-wide tunnels to dedicated stage rooms (West = Inner Worlds,
 * North = Outer Worlds + Moon, East = Ringed Giants timed challenge). Each room houses its
 * own stage pedestals and local core-supply wall, and all hazards and void drops are removed.
 */
public final class HubLayout {
	public static final int FLOOR_Y = 64;
	public static final int TUNNEL_CEILING_Y = 68;
	public static final int CEILING_Y = 70;
	public static final int RADIUS = 36;
	/** Bottom of legacy v5 void-floor volume cleared during rebuilds. */
	public static final int VOID_BOTTOM = FLOOR_Y - 17;
	/** Exclusive upper bound of the cleared rebuild volume (clears the old floating Sun). */
	public static final int TOP_Y = 100;

	public static final int HUB_RADIUS_SQ = 81;  // r = 9
	public static final int ROOM_RADIUS_SQ = 64; // r = 8
	public static final int ROOM_OFFSET = 26;

	public static final BlockPos SPAWN = new BlockPos(0, FLOOR_Y + 1, 0);
	public static final BlockPos TERMINAL = new BlockPos(-3, FLOOR_Y + 1, 7);
	public static final BlockPos EXIT = new BlockPos(3, FLOOR_Y + 1, 7);
	public static final BlockPos START = new BlockPos(33, FLOOR_Y + 1, 0);

	/** Pairs each local core supply button with the Orbit Builder stage that owns its room. */
	public record SupplyButton(int stage, int core, BlockPos pos) {
	}

	private static final List<BlockPos> PEDESTALS = List.of(
		// Stage 0 (West Room, center (-26, 0)): slots 0..3 along north arc facing SOUTH.
		new BlockPos(-30, FLOOR_Y + 1, -4),
		new BlockPos(-27, FLOOR_Y + 1, -4),
		new BlockPos(-24, FLOOR_Y + 1, -4),
		new BlockPos(-21, FLOOR_Y + 1, -4),
		// Stage 1 (North Room, center (0, -26)): slots 4..8 along west arc facing EAST.
		new BlockPos(-4, FLOOR_Y + 1, -32),
		new BlockPos(-4, FLOOR_Y + 1, -29),
		new BlockPos(-4, FLOOR_Y + 1, -26),
		new BlockPos(-4, FLOOR_Y + 1, -23),
		new BlockPos(-4, FLOOR_Y + 1, -20),
		// Stage 2 (East Room, center (+26, 0)): slots 9..12 along north arc facing SOUTH.
		new BlockPos(21, FLOOR_Y + 1, -4),
		new BlockPos(24, FLOOR_Y + 1, -4),
		new BlockPos(27, FLOOR_Y + 1, -4),
		new BlockPos(30, FLOOR_Y + 1, -4)
	);

	// Alphabetical per room so supply buttons don't give away the orbital answer order.
	private static final List<SupplyButton> SUPPLIES = List.of(
		// West Room (Stage 0): Earth(2), Mars(3), Mercury(0), Venus(1)
		new SupplyButton(0, 2, new BlockPos(-30, FLOOR_Y + 1, 4)),
		new SupplyButton(0, 3, new BlockPos(-27, FLOOR_Y + 1, 4)),
		new SupplyButton(0, 0, new BlockPos(-24, FLOOR_Y + 1, 4)),
		new SupplyButton(0, 1, new BlockPos(-21, FLOOR_Y + 1, 4)),
		// North Room (Stage 1): Jupiter(4), Moon(8), Neptune(7), Saturn(5), Uranus(6)
		new SupplyButton(1, 4, new BlockPos(4, FLOOR_Y + 1, -32)),
		new SupplyButton(1, 8, new BlockPos(4, FLOOR_Y + 1, -29)),
		new SupplyButton(1, 7, new BlockPos(4, FLOOR_Y + 1, -26)),
		new SupplyButton(1, 5, new BlockPos(4, FLOOR_Y + 1, -23)),
		new SupplyButton(1, 6, new BlockPos(4, FLOOR_Y + 1, -20)),
		// East Room (Stage 2): Jupiter(4), Neptune(7), Saturn(5), Uranus(6)
		new SupplyButton(2, 4, new BlockPos(21, FLOOR_Y + 1, 4)),
		new SupplyButton(2, 7, new BlockPos(24, FLOOR_Y + 1, 4)),
		new SupplyButton(2, 5, new BlockPos(27, FLOOR_Y + 1, 4)),
		new SupplyButton(2, 6, new BlockPos(30, FLOOR_Y + 1, 4))
	);

	private static final List<List<BlockPos>> PROGRESS_LAMPS = List.of(
		List.of(new BlockPos(-10, 69, 0), new BlockPos(-1, 67, 8)),
		List.of(new BlockPos(0, 69, -10), new BlockPos(0, 67, 8)),
		List.of(new BlockPos(10, 69, 0), new BlockPos(1, 67, 8))
	);

	public interface Sink {
		void block(BlockPos pos, BlockState state);
		void sign(BlockPos pos, Direction facing, String... lines);
	}

	private HubLayout() {
	}

	public static BlockPos pedestal(int slot) {
		return PEDESTALS.get(slot);
	}

	/** Air target above each pedestal label for per-player placed-core block previews. */
	public static BlockPos marker(int slot) {
		return pedestal(slot).above(3);
	}

	public static List<SupplyButton> supplyButtons() {
		return SUPPLIES;
	}

	/** Resolves the button at {@code pos}, or {@code null} if it is not a local core supply button. */
	public static SupplyButton supplyAt(BlockPos pos) {
		for (SupplyButton button : SUPPLIES) {
			if (button.pos().equals(pos)) {
				return button;
			}
		}
		return null;
	}

	/** Progress indicator blocks (tunnel header lamp + hub status board lamp) for {@code stage in 0..2}. */
	public static List<BlockPos> progressLamps(int stage) {
		return PROGRESS_LAMPS.get(stage);
	}

	public static boolean isHub(int x, int z) {
		return x * x + z * z <= HUB_RADIUS_SQ;
	}

	/** True when {@code (x, z)} lies inside the room floor circle for {@code stage in 0..2}. */
	public static boolean isRoom(int stage, int x, int z) {
		return switch (stage) {
			case 0 -> (x + ROOM_OFFSET) * (x + ROOM_OFFSET) + z * z <= ROOM_RADIUS_SQ;
			case 1 -> x * x + (z + ROOM_OFFSET) * (z + ROOM_OFFSET) <= ROOM_RADIUS_SQ;
			case 2 -> (x - ROOM_OFFSET) * (x - ROOM_OFFSET) + z * z <= ROOM_RADIUS_SQ;
			default -> false;
		};
	}

	private static boolean isWestTunnel(int x, int z) {
		return z >= -1 && z <= 1 && x >= -18 && x <= -9;
	}

	private static boolean isNorthTunnel(int x, int z) {
		return x >= -1 && x <= 1 && z >= -18 && z <= -9;
	}

	private static boolean isEastTunnel(int x, int z) {
		return z >= -1 && z <= 1 && x >= 9 && x <= 18;
	}

	private static boolean isTunnelInterior(int x, int z) {
		return (z >= -1 && z <= 1 && ((x >= -18 && x <= -10) || (x >= 10 && x <= 18)))
			|| (x >= -1 && x <= 1 && z >= -18 && z <= -10);
	}

	public static boolean isWalkable(int x, int z) {
		return isHub(x, z)
			|| isRoom(0, x, z) || isRoom(1, x, z) || isRoom(2, x, z)
			|| isWestTunnel(x, z) || isNorthTunnel(x, z) || isEastTunnel(x, z);
	}

	/** 8-connected enclosure shell around all walkable spaces so no diagonal void gaps exist. */
	public static boolean isWall(int x, int z) {
		if (isWalkable(x, z)) {
			return false;
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (isWalkable(x + dx, z + dz)) {
					return true;
				}
			}
		}
		return false;
	}

	public static void build(ServerLevel level) {
		build(new Sink() {
			@Override public void block(BlockPos pos, BlockState state) {
				level.setBlock(pos, state, Block.UPDATE_CLIENTS);
			}

			@Override public void sign(BlockPos pos, Direction facing, String... lines) {
				block(pos, Blocks.BIRCH_WALL_SIGN.defaultBlockState()
					.setValue(BlockStateProperties.HORIZONTAL_FACING, facing));
				if (!(level.getBlockEntity(pos) instanceof SignBlockEntity entity)) {
					throw new IllegalStateException("Intro hub sign placement failed");
				}
				SignText text = new SignText().setColor(DyeColor.BLACK).setHasGlowingText(true);
				for (int i = 0; i < Math.min(4, lines.length); i++) {
					text = text.setMessage(i, Component.literal(lines[i]));
				}
				entity.setText(text, true);
				entity.setText(text, false);
				entity.setWaxed(true);
				entity.setChanged();
			}
		});
	}

	public static void build(Sink sink) {
		// Clear the full legacy/current rebuild envelope so upgrades from v5 leave zero stray blocks.
		for (int x = -RADIUS; x <= RADIUS; x++) {
			for (int z = -RADIUS; z <= RADIUS; z++) {
				for (int y = VOID_BOTTOM; y < TOP_Y; y++) {
					put(sink, new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}

		// Build solid floors, ceilings, and full-height enclosure walls.
		for (int x = -RADIUS; x <= RADIUS; x++) {
			for (int z = -RADIUS; z <= RADIUS; z++) {
				boolean walk = isWalkable(x, z);
				boolean wall = isWall(x, z);
				if (!walk && !wall) {
					continue;
				}
				put(sink, new BlockPos(x, FLOOR_Y - 1, z), Blocks.BLACK_CONCRETE);
				if (wall) {
					put(sink, new BlockPos(x, FLOOR_Y, z), Blocks.POLISHED_DEEPSLATE);
					for (int y = FLOOR_Y + 1; y <= CEILING_Y; y++) {
						put(sink, new BlockPos(x, y, z), wallBlock(x, z, y));
					}
					continue;
				}

				put(sink, new BlockPos(x, FLOOR_Y, z), floorBlock(x, z));
				if (isTunnelInterior(x, z)) {
					boolean midLight = Math.abs(x) == Math.abs(z) || (Math.abs(x) > 9 && z == 0) || (Math.abs(z) > 9 && x == 0);
					put(sink, new BlockPos(x, TUNNEL_CEILING_Y, z), midLight ? Blocks.SEA_LANTERN : Blocks.QUARTZ_BRICKS);
					put(sink, new BlockPos(x, TUNNEL_CEILING_Y + 1, z), tunnelAccent(x, z));
					put(sink, new BlockPos(x, CEILING_Y, z), Blocks.BLACK_CONCRETE);
				} else {
					put(sink, new BlockPos(x, CEILING_Y, z), ceilingBlock(x, z));
				}
			}
		}

		// South progress display wall inside the hub (x in -1..1, z = 8, y = 65..68), with quartz plinth at z = 7.
		for (int x = -1; x <= 1; x++) {
			put(sink, new BlockPos(x, FLOOR_Y + 1, 7), Blocks.QUARTZ_BRICKS);
			for (int y = FLOOR_Y + 1; y <= FLOOR_Y + 4; y++) {
				put(sink, new BlockPos(x, y, 8), y == FLOOR_Y + 4 ? Blocks.CYAN_CONCRETE : Blocks.QUARTZ_BRICKS);
			}
		}
		for (int stage = 0; stage < 3; stage++) {
			for (BlockPos lamp : progressLamps(stage)) {
				put(sink, lamp, Blocks.GRAY_CONCRETE);
			}
		}
		sink.sign(new BlockPos(-1, FLOOR_Y + 2, 7), Direction.NORTH,
			"STAGE 1", "West Tunnel", "Inner Worlds");
		sink.sign(new BlockPos(0, FLOOR_Y + 2, 7), Direction.NORTH,
			"STAGE 2", "North Tunnel", "Outer + Moon");
		sink.sign(new BlockPos(1, FLOOR_Y + 2, 7), Direction.NORTH,
			"STAGE 3", "East Tunnel", "Ringed Giants");
		sink.sign(new BlockPos(0, FLOOR_Y + 4, 7), Direction.NORTH,
			"ORBIT BUILDER", "1:West 2:North", "3:East -> Depot", "Light 3 lamps");

		// Hub-side tunnel entrance labels + room-side return signs.
		sink.sign(new BlockPos(-9, FLOOR_Y + 4, 0), Direction.EAST,
			"1 INNER WORLDS", "Stage 1 Room", "Fill orbits 1-4");
		sink.sign(new BlockPos(0, FLOOR_Y + 4, -9), Direction.SOUTH,
			"2 OUTER + MOON", "Stage 2 Room", "Orbits 5-8", "+ Moon slot");
		sink.sign(new BlockPos(9, FLOOR_Y + 4, 0), Direction.WEST,
			"3 RINGED GIANTS", "Stage 3 Timed", "Press START", "Match 4 clues");

		sink.sign(new BlockPos(-19, FLOOR_Y + 4, 0), Direction.WEST, "TO CENTRAL HUB", "Follow tunnel");
		sink.sign(new BlockPos(0, FLOOR_Y + 4, -19), Direction.NORTH, "TO CENTRAL HUB", "Follow tunnel");
		sink.sign(new BlockPos(19, FLOOR_Y + 4, 0), Direction.EAST, "TO CENTRAL HUB", "Follow tunnel");

		// Hub reward depot & lesson exit controls.
		console(sink, TERMINAL, Direction.NORTH, Blocks.LIME_CONCRETE, "SCANNER", "Claim / replace");
		console(sink, EXIT, Direction.NORTH, Blocks.CYAN_CONCRETE, "EXIT", "Back to lesson");

		// Room 1 (West): Stage 0 pedestals, local cores, and room instructions.
		put(sink, new BlockPos(-34, FLOOR_Y + 1, 0), Blocks.BLUE_CONCRETE);
		board(sink, new BlockPos(-34, FLOOR_Y + 2, 0), Direction.EAST, Blocks.BLUE_CONCRETE,
			"STAGE 1: INNER", "Pick core south", "Place north 1-4", "Order from Sun");

		// Room 2 (North): Stage 1 pedestals, local cores, and room instructions.
		put(sink, new BlockPos(0, FLOOR_Y + 1, -34), Blocks.CYAN_CONCRETE);
		board(sink, new BlockPos(0, FLOOR_Y + 2, -34), Direction.SOUTH, Blocks.CYAN_CONCRETE,
			"STAGE 2: OUTER", "Pick core east", "Place west 5-8", "+ Moon slot");

		// Room 3 (East): Stage 2 START button, room instructions, pedestals, and local cores.
		console(sink, START, Direction.WEST, Blocks.PURPLE_CONCRETE, "START", "120 seconds");
		put(sink, new BlockPos(34, FLOOR_Y + 3, 0), Blocks.PURPLE_CONCRETE);
		put(sink, new BlockPos(34, FLOOR_Y + 4, 0), Blocks.PURPLE_CONCRETE);
		sink.sign(new BlockPos(33, FLOOR_Y + 4, 0), Direction.WEST,
			"STAGE 3: GIANTS", "1 Press START", "2 Pick south", "3 Match north");

		for (SupplyButton supply : SUPPLIES) {
			Direction facing = supply.stage() == 1 ? Direction.WEST : Direction.NORTH;
			Block colour = stageColour(supply.stage());
			console(sink, supply.pos(), facing, colour, OrbitPuzzle.name(supply.core()), "Select core");
		}

		for (int slot = 0; slot < OrbitPuzzle.SLOTS; slot++) {
			int stage = OrbitPuzzle.slotStage(slot);
			Direction facing = stage == 1 ? Direction.EAST : Direction.SOUTH;
			Block colour = stageColour(stage);
			String title = slot == 8 ? "MOON SLOT" : slot < 8 ? "ORBIT " + (slot + 1) : "RING " + (slot - 8);
			String clue = slot == 8 ? "Circles Earth" : slot < 8 ? "From the Sun" : OrbitPuzzle.CLUES.get(slot - 9);
			console(sink, pedestal(slot), facing, colour, title, clue);
		}
	}

	private static Block stageColour(int stage) {
		return switch (stage) {
			case 0 -> Blocks.BLUE_CONCRETE;
			case 1 -> Blocks.CYAN_CONCRETE;
			default -> Blocks.PURPLE_CONCRETE;
		};
	}

	private static Block tunnelAccent(int x, int z) {
		if (x <= -10) return Blocks.BLUE_CONCRETE;
		if (z <= -10) return Blocks.CYAN_CONCRETE;
		return Blocks.PURPLE_CONCRETE;
	}

	private static Block floorBlock(int x, int z) {
		if (isHub(x, z)) {
			int dSq = x * x + z * z;
			// Clean quartz-and-lantern centerpiece compass (zero orange/lava blocks).
			if (dSq <= 2) return (x == 0 && z == 0) ? Blocks.SEA_LANTERN : Blocks.QUARTZ_BRICKS;
			if (z == 0 && x < -2 && x >= -9) return (x == -5 || x == -9) ? Blocks.SEA_LANTERN : Blocks.BLUE_CONCRETE;
			if (x == 0 && z < -2 && z >= -9) return (z == -5 || z == -9) ? Blocks.SEA_LANTERN : Blocks.CYAN_CONCRETE;
			if (z == 0 && x > 2 && x <= 9) return (x == 5 || x == 9) ? Blocks.SEA_LANTERN : Blocks.PURPLE_CONCRETE;
			if ((x == -3 || x == 3) && z == 6) return Blocks.SEA_LANTERN;
			if (dSq >= 64) return Blocks.POLISHED_DEEPSLATE;
			return ((x + z) & 1) == 0 ? Blocks.DEEPSLATE_TILES : Blocks.POLISHED_DEEPSLATE;
		}
		if (isWestTunnel(x, z) || isNorthTunnel(x, z) || isEastTunnel(x, z)) {
			if ((Math.abs(x) >= 9 && z == 0) || (Math.abs(z) >= 9 && x == 0)) {
				return ((x + z) & 1) == 0 ? Blocks.SEA_LANTERN : Blocks.QUARTZ_BRICKS;
			}
			return Blocks.DEEPSLATE_TILES;
		}
		if (isRoom(0, x, z)) {
			return roomFloor(x + ROOM_OFFSET, z, Blocks.BLUE_CONCRETE);
		}
		if (isRoom(1, x, z)) {
			return roomFloor(x, z + ROOM_OFFSET, Blocks.CYAN_CONCRETE);
		}
		if (isRoom(2, x, z)) {
			return roomFloor(x - ROOM_OFFSET, z, Blocks.PURPLE_CONCRETE);
		}
		return Blocks.POLISHED_DEEPSLATE;
	}

	private static Block roomFloor(int rx, int rz, Block accent) {
		int dSq = rx * rx + rz * rz;
		if (dSq == 0) return Blocks.SEA_LANTERN;
		if (dSq <= 2) return Blocks.QUARTZ_BRICKS;
		if (dSq >= 49) return accent;
		return ((rx + rz) & 1) == 0 ? Blocks.DEEPSLATE_TILES : Blocks.POLISHED_DEEPSLATE;
	}

	private static Block ceilingBlock(int x, int z) {
		if (isHub(x, z) || isWestTunnel(x, z) || isNorthTunnel(x, z) || isEastTunnel(x, z)) {
			int dSq = x * x + z * z;
			if (dSq == 0 || ((Math.abs(x) == 4 || x == 0) && (Math.abs(z) == 4 || z == 0))) {
				return Blocks.SEA_LANTERN;
			}
			return dSq <= 16 ? Blocks.QUARTZ_BRICKS : Blocks.POLISHED_DEEPSLATE;
		}
		int rx = isRoom(0, x, z) ? x + ROOM_OFFSET : isRoom(2, x, z) ? x - ROOM_OFFSET : x;
		int rz = isRoom(1, x, z) ? z + ROOM_OFFSET : z;
		if ((rx == 0 || Math.abs(rx) == 4) && (rz == 0 || Math.abs(rz) == 4)) {
			return Blocks.SEA_LANTERN;
		}
		return (rx * rx + rz * rz <= 16) ? Blocks.QUARTZ_BRICKS : Blocks.POLISHED_DEEPSLATE;
	}

	private static Block wallBlock(int x, int z, int y) {
		if (y == CEILING_Y) return Blocks.BLACK_CONCRETE;
		if (y == FLOOR_Y + 1) return Blocks.POLISHED_DEEPSLATE;
		if (y == FLOOR_Y + 3) {
			if (x <= -10) return Blocks.BLUE_CONCRETE;
			if (z <= -10) return Blocks.CYAN_CONCRETE;
			if (x >= 10) return Blocks.PURPLE_CONCRETE;
			return Blocks.LIGHT_GRAY_CONCRETE;
		}
		return (Math.floorMod(x + z, 4) == 0 && y == FLOOR_Y + 5) ? Blocks.QUARTZ_BRICKS : Blocks.DEEPSLATE_TILES;
	}

	private static Block glow(Block colour) {
		if (colour == Blocks.PURPLE_CONCRETE) return Blocks.PEARLESCENT_FROGLIGHT;
		if (colour == Blocks.LIME_CONCRETE) return Blocks.VERDANT_FROGLIGHT;
		return Blocks.SEA_LANTERN;
	}

	private static void console(Sink sink, BlockPos button, Direction facing, Block colour, String title, String subtitle) {
		put(sink, button.below(), glow(colour));
		Direction back = facing.getOpposite();
		put(sink, button.relative(back), colour);
		put(sink, button.above().relative(back), colour);
		sink.block(button, buttonState(facing));
		sink.sign(button.above(), facing, title, subtitle, "", "Press button");
	}

	private static BlockState buttonState(Direction facing) {
		return Blocks.STONE_BUTTON.defaultBlockState()
			.setValue(BlockStateProperties.ATTACH_FACE, AttachFace.WALL)
			.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
	}

	private static void board(Sink sink, BlockPos pos, Direction facing, Block backing, String... lines) {
		put(sink, pos.relative(facing.getOpposite()), backing);
		sink.sign(pos, facing, lines);
	}

	private static void put(Sink sink, BlockPos pos, Block block) {
		sink.block(pos, block.defaultBlockState());
	}
}
