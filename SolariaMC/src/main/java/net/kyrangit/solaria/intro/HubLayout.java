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

/** Bounded observatory build, exclusively in the intro dimension. Also renderable into a test sink. */
public final class HubLayout {
    public static final int FLOOR_Y = 64;
    public static final int RADIUS = 25;
    /** Exclusive upper bound of the build volume; tall enough to clear the floating Sun. */
    public static final int TOP_Y = 100;
    /** Bottom of the void-floor pit: black base, then a carpeted light layer, then stacked glass sheets up to the floor. */
    public static final int VOID_BOTTOM = FLOOR_Y - 17;
    public static final int SUN_RADIUS = 10;
    /** Lowest Sun block sits 10 blocks above a standing player's head (feet at y=65, head top at y=67). */
    public static final BlockPos SUN_CENTRE = new BlockPos(0, FLOOR_Y + 3 + 10 + SUN_RADIUS, -3);
    public static final BlockPos SPAWN = new BlockPos(0, FLOOR_Y + 1, 0);
    public static final BlockPos TERMINAL = new BlockPos(0, FLOOR_Y + 1, 17);
    public static final BlockPos EXIT = new BlockPos(4, FLOOR_Y + 1, 17);
    public static final List<BlockPos> STATIONS = List.of(
        new BlockPos(-16, FLOOR_Y + 1, -13),
        new BlockPos(0, FLOOR_Y + 1, -17),
        new BlockPos(16, FLOOR_Y + 1, -13)
    );

    public static final BlockPos START = new BlockPos(12, FLOOR_Y + 1, 9);
    private static final List<BlockPos> PEDESTALS = List.of(
        new BlockPos(-17, 65, -8), new BlockPos(-13, 65, -8),
        new BlockPos(-9, 65, -8), new BlockPos(-5, 65, -8),
        new BlockPos(-8, 65, -18), new BlockPos(-4, 65, -18),
        new BlockPos(0, 65, -18), new BlockPos(4, 65, -18),
        new BlockPos(-9, 65, -4), new BlockPos(18, 65, -9),
        new BlockPos(18, 65, -3), new BlockPos(18, 65, 3), new BlockPos(18, 65, 9));

    public static BlockPos supply(int core) {
        java.util.Objects.checkIndex(core, OrbitPuzzle.CORES.size());
        return new BlockPos(-16 + core * 4, FLOOR_Y + 1, 10);
    }

    public static BlockPos pedestal(int slot) { return PEDESTALS.get(slot); }

    /** Air target above each label for per-player placed-core block previews and particles. */
    public static BlockPos marker(int slot) { return pedestal(slot).above(3); }

    public interface Sink {
        void block(BlockPos pos, BlockState state);
        void sign(BlockPos pos, Direction facing, String... lines);
    }

    private HubLayout() {
    }

    public static BlockPos choice(int station, int answer) {
        return STATIONS.get(station).offset((answer - 1) * 3, 0, 0);
    }

    public static void build(ServerLevel level) {
        build(new Sink() {
            @Override public void block(BlockPos pos, BlockState state) {
                level.setBlock(pos, state, Block.UPDATE_CLIENTS);
            }

            @Override public void sign(BlockPos pos, Direction facing, String... lines) {
                // The supporting block is always pos.relative(facing.getOpposite()).
                block(pos, Blocks.BIRCH_WALL_SIGN.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, facing));
                if (!(level.getBlockEntity(pos) instanceof SignBlockEntity entity)) {
                    throw new IllegalStateException("Intro hub sign placement failed");
                }
                // Glowing text stays readable whatever the local light level is.
                SignText text = new SignText().setColor(DyeColor.BLACK).setHasGlowingText(true);
                for (int i = 0; i < Math.min(4, lines.length); i++) text = text.setMessage(i, Component.literal(lines[i]));
                entity.setText(text, true);
                entity.setText(text, false);
                entity.setWaxed(true);
                entity.setChanged();
            }
        });
    }

    public static void build(Sink sink) {
        // Each rebuild clears the whole volume, removing old consoles, models and signs first.
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                for (int y = FLOOR_Y + 1; y < TOP_Y; y++) put(sink, new BlockPos(x, y, z), Blocks.AIR);
                boolean edge = Math.abs(x) == RADIUS || Math.abs(z) == RADIUS;
                voidFloor(sink, x, z, edge);
                double distance = Math.sqrt(x * x + (z + 3) * (z + 3));
                // Orbit paths glow on top of the starry glass, tracing identical circles around the Sun.
                Block floor = edge ? Blocks.BLACK_CONCRETE : Blocks.BLACK_STAINED_GLASS;
                for (int orbit = 4; orbit <= 20; orbit += 4) {
                    if (Math.abs(distance - orbit) < 0.4) floor = Blocks.SEA_LANTERN;
                }
                if (distance < 3) floor = Blocks.SHROOMLIGHT;
                put(sink, new BlockPos(x, FLOOR_Y, z), floor);
                if (edge) {
                    boolean lamp = Math.floorMod(x + z, 5) == 0;
                    put(sink, new BlockPos(x, 65, z), lamp ? Blocks.SEA_LANTERN : Blocks.QUARTZ_BRICKS);
                    put(sink, new BlockPos(x, 66, z), Blocks.LIGHT_BLUE_STAINED_GLASS);
                    put(sink, new BlockPos(x, 67, z), Blocks.LIGHT_BLUE_STAINED_GLASS);
                }
            }
        }
        for (int[] post : new int[][]{{-22, -22}, {0, -22}, {22, -22}, {-22, 0}, {22, 0}, {-22, 22}, {0, 22}, {22, 22}}) {
            lampPost(sink, post[0], post[1]);
        }
        sphere(sink, SUN_CENTRE, SUN_RADIUS, Blocks.SHROOMLIGHT, Blocks.YELLOW_CONCRETE);
        put(sink, new BlockPos(0, 65, -4), Blocks.QUARTZ_PILLAR);
        put(sink, new BlockPos(0, 66, -4), Blocks.QUARTZ_PILLAR);
        board(sink, new BlockPos(0, 67, -3), Direction.SOUTH, Blocks.YELLOW_CONCRETE,
            "SUN", "Look up!", "Sizes/distances", "NOT to scale");
        for (int core = 0; core < OrbitPuzzle.CORES.size(); core++) {
            console(sink, supply(core), Direction.NORTH, Blocks.CYAN_CONCRETE,
                OrbitPuzzle.name(core), "Select core");
        }
        for (int slot = 0; slot < 13; slot++) {
            Direction facing = slot < 9 ? Direction.SOUTH : Direction.WEST;
            Block colour = slot < 4 ? Blocks.BLUE_CONCRETE : slot < 9 ? Blocks.CYAN_CONCRETE : Blocks.PURPLE_CONCRETE;
            String title = slot == 8 ? "MOON SLOT" : slot < 8 ? "ORBIT " + (slot + 1) : "RING " + (slot - 8);
            String clue = slot == 8 ? "Circles Earth" : slot < 8 ? "From the Sun" : OrbitPuzzle.CLUES.get(slot - 9);
            console(sink, pedestal(slot), facing, colour, title, clue);
        }
        console(sink, START, Direction.WEST, Blocks.PURPLE_CONCRETE, "START", "120 seconds");
        console(sink, TERMINAL, Direction.NORTH, Blocks.LIME_CONCRETE, "SCANNER", "Claim / replace");
        console(sink, EXIT, Direction.NORTH, Blocks.CYAN_CONCRETE, "EXIT", "Back to lesson");
        board(sink, new BlockPos(-4, 67, 3), Direction.NORTH, Blocks.CYAN_CONCRETE,
            "ORBIT BUILDER", "1 Select core", "2 Press orbit", "Hints in chat");
        board(sink, new BlockPos(0, 67, 12), Direction.NORTH, Blocks.CYAN_CONCRETE,
            "SUPPLY WALL", "Pick one core", "Then find slot", "Repeat to fill");
        board(sink, TERMINAL.above(2), Direction.NORTH, Blocks.LIME_CONCRETE,
            "SCANNER DEPOT", "Finish 3 stages", "Claim reward");
        // Open arches identify zones without globally locking out another player's progress.
        arch(sink, new BlockPos(-12, 65, -1), Blocks.BLUE_CONCRETE,
            "1 INNER WORLDS", "Fill orbits 1-4");
        arch(sink, new BlockPos(0, 65, -12), Blocks.CYAN_CONCRETE,
            "2 OUTER + MOON", "Orbits 5-8", "Moon by Earth");
        arch(sink, new BlockPos(12, 65, 1), Blocks.PURPLE_CONCRETE,
            "3 RINGED GIANTS", "Press START", "Match 4 clues", "120 seconds");
    }

    private static void arch(Sink sink, BlockPos base, Block colour, String... lines) {
        for (int y = 0; y < 5; y++) {
            put(sink, base.offset(-2, y, 0), colour);
            put(sink, base.offset(2, y, 0), colour);
        }
        for (int x = -2; x <= 2; x++) put(sink, base.offset(x, 4, 0), Blocks.SEA_LANTERN);
        board(sink, base.offset(0, 4, 1), Direction.SOUTH, colour, lines);
    }

    /**
     * One column of the void floor below FLOOR_Y: black base, light hidden under black carpet, a foggy gap,
     * then black glass sheets every other block with end-rod "stars" scattered in the gaps. Edges are solid black.
     */
    private static void voidFloor(Sink sink, int x, int z, boolean edge) {
        put(sink, new BlockPos(x, VOID_BOTTOM, z), Blocks.BLACK_CONCRETE);
        for (int y = VOID_BOTTOM + 1; y < FLOOR_Y; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (edge) {
                put(sink, pos, Blocks.BLACK_CONCRETE);
            } else if (y == VOID_BOTTOM + 1) {
                put(sink, pos, Blocks.PEARLESCENT_FROGLIGHT);
            } else if (y == VOID_BOTTOM + 2) {
                put(sink, pos, Blocks.BLACK_CARPET);
            } else if (y > VOID_BOTTOM + 4 && Math.floorMod(FLOOR_Y - y, 2) == 0) {
                put(sink, pos, Blocks.BLACK_STAINED_GLASS);
            } else if (Math.floorMod(x * 73 + z * 37 + y * 151, 41) == 0) {
                put(sink, pos, Blocks.END_ROD);
            } else {
                put(sink, pos, Blocks.AIR);
            }
        }
    }

    private static void lampPost(Sink sink, int x, int z) {
        for (int y = FLOOR_Y + 1; y <= FLOOR_Y + 3; y++) put(sink, new BlockPos(x, y, z), Blocks.QUARTZ_PILLAR);
        put(sink, new BlockPos(x, FLOOR_Y + 4, z), Blocks.LANTERN);
    }

    /** Lit floor pad in the console's zone colour, so every station reads clearly from a distance. */
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

    private static void sphere(Sink sink, BlockPos centre, int radius, Block base, Block accent) {
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + y * y + z * z <= radius * radius) {
                        put(sink, centre.offset(x, y, z), Math.floorMod(x * 31 + y * 13 + z * 7, 5) == 0 ? accent : base);
                    }
                }
            }
        }
    }

    private static void put(Sink sink, BlockPos pos, Block block) {
        sink.block(pos, block.defaultBlockState());
    }
}