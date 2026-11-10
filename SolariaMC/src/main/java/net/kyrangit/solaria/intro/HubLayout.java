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
    public static final BlockPos SPAWN = new BlockPos(0, FLOOR_Y + 1, 0);
    public static final BlockPos TERMINAL = new BlockPos(0, FLOOR_Y + 1, 17);
    public static final BlockPos EXIT = new BlockPos(4, FLOOR_Y + 1, 17);
    public static final List<BlockPos> STATIONS = List.of(
        new BlockPos(-16, FLOOR_Y + 1, -13),
        new BlockPos(0, FLOOR_Y + 1, -17),
        new BlockPos(16, FLOOR_Y + 1, -13)
    );

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
                SignText text = new SignText().setColor(DyeColor.BLACK);
                for (int i = 0; i < Math.min(4, lines.length); i++) text = text.setMessage(i, Component.literal(lines[i]));
                entity.setText(text, true);
                entity.setText(text, false);
                entity.setWaxed(true);
                entity.setChanged();
            }
        });
    }

    public static void build(Sink sink) {
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                put(sink, new BlockPos(x, FLOOR_Y - 1, z), Blocks.POLISHED_DEEPSLATE);
                Block floor = (x * x + z * z <= 25) ? Blocks.CYAN_CONCRETE : Blocks.SMOOTH_QUARTZ;
                if ((Math.abs(x) % 8 == 0 && Math.abs(z) % 8 == 0)
                    || (x == 0 && z < -5) || (z == -6 && Math.abs(x) <= 16)) floor = Blocks.SEA_LANTERN;
                put(sink, new BlockPos(x, FLOOR_Y, z), floor);
                if (Math.abs(x) == RADIUS || Math.abs(z) == RADIUS) {
                    put(sink, new BlockPos(x, FLOOR_Y + 1, z), Blocks.QUARTZ_BRICKS);
                    put(sink, new BlockPos(x, FLOOR_Y + 2, z), Blocks.LIGHT_BLUE_STAINED_GLASS);
                    put(sink, new BlockPos(x, FLOOR_Y + 3, z), Blocks.LIGHT_BLUE_STAINED_GLASS);
                }
            }
        }

        // Three low, readable answer consoles. Questions also appear in chat on interaction.
        Block[] colours = {Blocks.BLUE_CONCRETE, Blocks.ORANGE_CONCRETE, Blocks.PURPLE_CONCRETE};
        for (int station = 0; station < IntroQuiz.COUNT; station++) {
            BlockPos centre = STATIONS.get(station);
            for (int dx = -5; dx <= 5; dx++) {
                for (int dy = 1; dy <= 3; dy++) put(sink, centre.offset(dx, dy, -1), colours[station]);
            }
            String[][] questionLines = {
                {"1 / PLANETS", "Which planet", "is our home?", "Choose below"},
                {"2 / THE SUN", "What kind of", "object is", "the Sun?"},
                {"3 / THE MOON", "Why does the", "Moon look", "bright?"}
            };
            sink.sign(centre.above().above(), Direction.SOUTH, questionLines[station]);
            for (int answer = 0; answer < 3; answer++) {
                BlockPos button = choice(station, answer);
                put(sink, button.offset(0, 0, -1), colours[station]);
                sink.block(button, buttonState(Direction.SOUTH));
                sink.sign(button.above(), Direction.SOUTH, "ANSWER " + (answer + 1),
                    IntroQuiz.QUESTIONS.get(station).choices().get(answer), "", "Press button");
            }
        }

        // Decorative, intentionally not-to-scale models, safely behind the question consoles.
        sphere(sink, new BlockPos(-16, 72, -19), 3, Blocks.BLUE_CONCRETE, Blocks.GREEN_CONCRETE);
        sphere(sink, new BlockPos(0, 73, -22), 3, Blocks.YELLOW_CONCRETE, Blocks.SHROOMLIGHT);
        sphere(sink, new BlockPos(16, 72, -19), 3, Blocks.LIGHT_GRAY_CONCRETE, Blocks.GRAY_CONCRETE);

        // Welcome boards sit ahead of the arrival point (players spawn facing north).
        board(sink, new BlockPos(-4, 67, -4), Direction.SOUTH, Blocks.CYAN_CONCRETE,
            "SOLARIA", "ACADEMY", "Welcome,", "explorer!");
        board(sink, new BlockPos(0, 67, -4), Direction.SOUTH, Blocks.CYAN_CONCRETE,
            "YOUR MISSION", "Solve all 3", "in any order", "Press buttons");
        board(sink, new BlockPos(4, 67, -4), Direction.SOUTH, Blocks.CYAN_CONCRETE,
            "WRONG ANSWER?", "Read the hint", "and try again!", "");
        // The depot and exit are behind the arrival point, so they face north toward the hub.
        board(sink, TERMINAL.above().above(), Direction.NORTH, Blocks.LIME_CONCRETE,
            "SCANNER DEPOT", "Finish all 3", "to claim your", "scanner below");
        board(sink, EXIT.above().above(), Direction.NORTH, Blocks.CYAN_CONCRETE,
            "LESSON EXIT", "Ready? Press", "below to go", "to the lesson");
        console(sink, TERMINAL, Direction.NORTH, Blocks.LIME_CONCRETE, "SCANNER", "Claim / replace");
        console(sink, EXIT, Direction.NORTH, Blocks.CYAN_CONCRETE, "EXIT", "Back to lesson");
        board(sink, new BlockPos(-10, 67, -4), Direction.SOUTH, Blocks.PURPLE_CONCRETE,
            "JUST MODELS", "Sizes and", "distances are", "NOT to scale");
    }

    private static void console(Sink sink, BlockPos button, Direction facing, Block colour, String title, String subtitle) {
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