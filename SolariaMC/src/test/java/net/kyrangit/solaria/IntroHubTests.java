package net.kyrangit.solaria;

import net.kyrangit.solaria.intro.HubLayout;
import net.kyrangit.solaria.intro.IntroQuiz;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Plumbing check (synthetic) for Solaria Academy. Exercises the real quiz rules and renders the
 * real hub layout into memory. It does not start a server or prove the hub is fun or readable.
 */
public final class IntroHubTests {
    private static int assertions;

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        quizRules();
        layout();
        System.out.println("Intro hub plumbing check (synthetic): " + assertions + " assertions passed.");
    }

    private static void quizRules() {
        check(IntroQuiz.COUNT == 3, "three stations: planets, sun, moon");
        check(IntroQuiz.NAMES.size() == IntroQuiz.COUNT, "every station has a short name for hints");
        for (int station = 0; station < IntroQuiz.COUNT; station++) {
            var q = IntroQuiz.QUESTIONS.get(station);
            check(q.choices().size() == 3, q.title() + " has three answers");
            check(q.choices().stream().allMatch(c -> c.length() <= 15), q.title() + " answers fit on a sign");
            check(!q.explanation().isBlank(), q.title() + " teaches after an answer");
        }
        // Every one of the 3! visiting orders must finish the mission with the same rules.
        int[][] orders = {{0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}};
        for (int[] order : orders) {
            int solved = 0;
            for (int step = 0; step < order.length; step++) {
                int station = order[step];
                var q = IntroQuiz.QUESTIONS.get(station);
                check(!IntroQuiz.solved(solved, station), q.title() + " starts unsolved");
                for (int wrong = 0; wrong < 3; wrong++) {
                    if (wrong != q.answer()) check(IntroQuiz.answer(solved, station, wrong) == solved,
                        q.title() + " wrong answer allows a retry without losing progress");
                }
                int before = solved;
                solved = IntroQuiz.answer(solved, station, q.answer());
                check(IntroQuiz.solved(solved, station), q.title() + " correct answer solves it in any order");
                check(IntroQuiz.count(solved) == step + 1, "each correct answer counts exactly once");
                check((solved & before) == before, "solving a station never clears another");
                check(IntroQuiz.answer(solved, station, q.answer()) == solved, "repeating a solved answer cannot advance");
                check(IntroQuiz.complete(solved) == (step == order.length - 1), "mission completes only after all three");
            }
            check(IntroQuiz.remaining(solved).isEmpty(), "nothing remains after the mission");
        }
        int sunOnly = IntroQuiz.answer(0, 1, IntroQuiz.QUESTIONS.get(1).answer());
        check(IntroQuiz.remaining(sunOnly).equals("Planets, Moon"), "hint lists unsolved stations");
        check(IntroQuiz.remaining(0).equals("Planets, Sun, Moon"), "hint lists all stations at the start");
        check(IntroQuiz.answer(IntroQuiz.ALL, 2, 1) == IntroQuiz.ALL, "progress never exceeds the mission");
        check(IntroQuiz.answer(-1, 0, 1) == -1 && IntroQuiz.answer(0, 0, 9) == 0
            && IntroQuiz.answer(0, 3, 0) == 0 && IntroQuiz.answer(0, -1, 0) == 0 && IntroQuiz.answer(8, 0, 1) == 8,
            "invalid inputs are ignored");
        check(IntroQuiz.count(-1) == 0 && !IntroQuiz.complete(-1) && !IntroQuiz.solved(-1, 0), "invalid masks are not progress");
        var json = com.mojang.serialization.JsonOps.INSTANCE;
        check(IntroQuiz.MASK_CODEC.parse(json, new com.google.gson.JsonPrimitive(8)).error().isPresent()
            && IntroQuiz.MASK_CODEC.parse(json, new com.google.gson.JsonPrimitive(-1)).error().isPresent(),
            "corrupt saved progress is rejected");
        check(IntroQuiz.MASK_CODEC.parse(json, new com.google.gson.JsonPrimitive(5)).result().orElse(-1) == 5,
            "saved any-order progress round-trips");
        // Saves from the earlier in-order version stored a count; it maps to the first n stations.
        check(IntroQuiz.fromLegacyCount(0) == 0, "legacy 0/3 migrates to nothing solved");
        check(IntroQuiz.fromLegacyCount(1) == 0b001 && IntroQuiz.fromLegacyCount(2) == 0b011, "legacy partial progress keeps solved stations");
        check(IntroQuiz.fromLegacyCount(3) == IntroQuiz.ALL, "legacy 3/3 migrates to complete");
        check(IntroQuiz.LEGACY_COUNT_CODEC.parse(json, new com.google.gson.JsonPrimitive(4)).error().isPresent(),
            "corrupt legacy progress is rejected");
        check(IntroQuiz.QUESTIONS.get(0).choices().get(IntroQuiz.QUESTIONS.get(0).answer()).equals("Earth"), "Earth is home");
        check(IntroQuiz.QUESTIONS.get(1).choices().get(IntroQuiz.QUESTIONS.get(1).answer()).equals("A star"), "the Sun is a star");
        check(IntroQuiz.QUESTIONS.get(2).choices().get(IntroQuiz.QUESTIONS.get(2).answer()).equals("Sunlight"), "the Moon reflects sunlight");
    }

    private static void layout() {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        Set<BlockPos> signs = new HashSet<>();
        HubLayout.build(new HubLayout.Sink() {
            @Override public void block(BlockPos pos, BlockState state) {
                blocks.put(pos.immutable(), state);
            }

            @Override public void sign(BlockPos pos, Direction facing, String... lines) {
                check(lines.length <= 4, "sign at " + pos + " fits four lines");
                for (String line : lines) check(line.length() <= 15, "sign line fits without clipping: " + line);
                BlockState support = blocks.get(pos.relative(facing.getOpposite()));
                check(support != null && support.isSolid(), "sign at " + pos + " has a solid support");
                blocks.put(pos.immutable(), Blocks.BIRCH_WALL_SIGN.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, facing));
                signs.add(pos.immutable());
            }
        });
        check(blocks.keySet().stream().allMatch(p -> Math.abs(p.getX()) <= HubLayout.RADIUS
            && Math.abs(p.getZ()) <= HubLayout.RADIUS && p.getY() >= HubLayout.FLOOR_Y - 1 && p.getY() < 80),
            "the build stays inside its bounded footprint");
        check(blocks.get(HubLayout.SPAWN.below()).isSolid(), "arrival point has solid floor");
        check(blocks.getOrDefault(HubLayout.SPAWN, Blocks.AIR.defaultBlockState()).isAir()
            && blocks.getOrDefault(HubLayout.SPAWN.above(), Blocks.AIR.defaultBlockState()).isAir(), "arrival point has headroom");
        for (int station = 0; station < IntroQuiz.COUNT; station++) {
            check(signs.contains(HubLayout.STATIONS.get(station).above().above()), "station question sign exists");
            for (int answer = 0; answer < 3; answer++) button(blocks, signs, HubLayout.choice(station, answer));
        }
        button(blocks, signs, HubLayout.TERMINAL);
        button(blocks, signs, HubLayout.EXIT);
        check(new HashSet<>(java.util.List.of(HubLayout.TERMINAL, HubLayout.EXIT,
            HubLayout.choice(0, 0), HubLayout.choice(1, 1), HubLayout.choice(2, 2))).size() == 5, "interactive buttons do not overlap");
    }

    private static void button(Map<BlockPos, BlockState> blocks, Set<BlockPos> signs, BlockPos pos) {
        BlockState state = blocks.get(pos);
        check(state != null && state.is(Blocks.STONE_BUTTON), "button exists at " + pos);
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        check(blocks.get(pos.relative(facing.getOpposite())).isSolid(), "button at " + pos + " has a solid support");
        check(blocks.get(pos.below()).isSolid(), "button at " + pos + " sits above the floor");
        check(signs.contains(pos.above()), "button at " + pos + " has a readable label");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError("Intro hub check failed: " + message);
        assertions++;
    }
}