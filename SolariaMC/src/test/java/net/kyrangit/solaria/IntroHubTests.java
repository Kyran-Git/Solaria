package net.kyrangit.solaria;

import net.kyrangit.solaria.blueprint.AstronomyClass;
import net.kyrangit.solaria.intro.HubLayout;
import net.kyrangit.solaria.intro.IntroHub;
import net.kyrangit.solaria.intro.StarChart;
import net.kyrangit.solaria.intro.IntroQuiz;
import net.kyrangit.solaria.intro.OrbitPuzzle;
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
        orbitRules();
        layout();
        starChart();
        coreColours();
        System.out.println("Intro hub plumbing check (synthetic): " + assertions + " assertions passed.");
    }

    private static void starChart() {
        check(StarChart.TOTAL == 10 && AstronomyClass.values().length == 10, "star chart tracks exactly the ten bodies");
        check(StarChart.count(0) == 0 && StarChart.describe(0).contains("none yet"), "empty chart");
        int mask = 0;
        for (AstronomyClass body : AstronomyClass.values()) {
            int before = mask;
            check(!StarChart.visited(mask, body), body.id() + " starts unvisited");
            mask = StarChart.visit(mask, body);
            check(StarChart.visited(mask, body), body.id() + " is recorded on arrival");
            check((mask & before) == before, "visiting never forgets earlier bodies");
            check(StarChart.visit(mask, body) == mask, "revisiting " + body.id() + " is idempotent");
            check(StarChart.count(mask) == StarChart.count(before) + 1, "each body counts once");
        }
        check(mask == StarChart.ALL && StarChart.describe(mask).contains("Every body explored"), "all ten complete the chart");
        check(!StarChart.describe(mask).toLowerCase().contains("pluto"), "no Pluto on the chart");
        check(StarChart.visit(-1, AstronomyClass.SUN) == -1 && StarChart.count(-1) == 0 && !StarChart.visited(-1, AstronomyClass.SUN),
            "corrupt masks are not progress");
        check(StarChart.visit(5, null) == 5 && !StarChart.visited(StarChart.ALL, null), "null body is ignored");
        var json = com.mojang.serialization.JsonOps.INSTANCE;
        check(StarChart.CODEC.parse(json, new com.google.gson.JsonPrimitive(StarChart.ALL + 1)).error().isPresent(), "codec rejects unknown bits");
        check(StarChart.CODEC.parse(json, new com.google.gson.JsonPrimitive(3)).result().orElse(-1) == 3, "codec round-trips");
    }

    private static void coreColours() {
        Set<BlockState> seen = new HashSet<>();
        for (int core = 0; core < OrbitPuzzle.CORES.size(); core++) {
            BlockState state = IntroHub.coreBlock(core);
            check(!state.isAir(), OrbitPuzzle.CORES.get(core) + " core is visible");
            check(seen.add(state), OrbitPuzzle.CORES.get(core) + " core colour is distinct");
        }
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

    private static void orbitRules() {
        check(OrbitPuzzle.CORES.equals(java.util.List.of("mercury", "venus", "earth", "mars", "jupiter",
            "saturn", "uranus", "neptune", "moon")), "core indices follow orbital order plus Moon");
        check(OrbitPuzzle.ALL == 8191 && OrbitPuzzle.SLOTS == 13, "thirteen placement bits");
        var json = com.mojang.serialization.JsonOps.INSTANCE;
        for (int mask = 0; mask <= OrbitPuzzle.ALL; mask++) {
            int stage = (mask & 15) != 15 ? 0 : (mask & 511) != 511 ? 1 : mask == 8191 ? 3 : 2;
            check(OrbitPuzzle.valid(mask), "all persisted masks valid");
            check(OrbitPuzzle.stage(mask) == stage, "stage requires earlier stages");
            check(OrbitPuzzle.complete(mask) == (mask == 8191), "all slots required");
            check(OrbitPuzzle.count(mask) == Integer.bitCount(mask), "count placed slots");
            check(OrbitPuzzle.expire(mask) == (mask & 511), "timeout preserves earlier stages only");
            check(!OrbitPuzzle.objective(mask).isBlank(), "every state has instructions");
            check(OrbitPuzzle.CODEC.parse(json, OrbitPuzzle.CODEC.encodeStart(json, mask).getOrThrow())
                .getOrThrow() == mask, "progress codec round trip");
            for (int slot = -1; slot <= 13; slot++) {
                int owner = slot < 0 || slot >= 13 ? -1 : slot < 4 ? 0 : slot < 9 ? 1 : 2;
                int expectedCore = slot < 0 || slot >= 13 ? -1 : slot < 9 ? slot : slot - 5;
                check(OrbitPuzzle.slotStage(slot) == owner, "slot ownership");
                check(OrbitPuzzle.expected(slot) == expectedCore, "slot core mapping");
                check(OrbitPuzzle.placed(mask, slot) == (owner >= 0 && (mask & (1 << slot)) != 0), "placed query");
                for (int core = -1; core <= 9; core++) {
                    int expected = owner >= 0 && owner == stage && core == expectedCore ? mask | (1 << slot) : mask;
                    check(OrbitPuzzle.place(mask, slot, core) == expected, "exhaustive placement and stage locks");
                }
            }
        }
        for (int invalid : new int[]{Integer.MIN_VALUE, -1, 8192, Integer.MAX_VALUE}) {
            check(!OrbitPuzzle.valid(invalid) && !OrbitPuzzle.complete(invalid), "invalid progress rejected");
            check(OrbitPuzzle.count(invalid) == 0 && OrbitPuzzle.expire(invalid) == 0, "invalid count/reset safe");
            check(OrbitPuzzle.CODEC.parse(json, new com.google.gson.JsonPrimitive(invalid)).error().isPresent(), "invalid save rejected");
            for (int slot = 0; slot < 13; slot++) {
                check(!OrbitPuzzle.placed(invalid, slot), "invalid masks have no placements");
                check(OrbitPuzzle.place(invalid, slot, slot < 9 ? slot : slot - 5) == invalid, "invalid input unchanged");
            }
        }
        check(OrbitPuzzle.ROUND_TICKS == 2400, "two minute challenge");
        for (long start : new long[]{0, 1, 1000}) {
            check(!OrbitPuzzle.expired(start, start), "timer just started");
            check(!OrbitPuzzle.expired(start, start + 2399), "last live tick");
            check(OrbitPuzzle.expired(start, start + 2400), "expires exactly on deadline");
            check(OrbitPuzzle.expired(start, start + 2401), "stays expired");
            check(OrbitPuzzle.expired(start, start - 1), "clock rollback expires safely");
        }
        check(!OrbitPuzzle.expired(-1, Long.MAX_VALUE), "inactive timer does not expire");
        check(!OrbitPuzzle.expired(Long.MAX_VALUE - 2400, Long.MAX_VALUE - 1)
            && OrbitPuzzle.expired(Long.MAX_VALUE - 2400, Long.MAX_VALUE), "large clock deadline");
        int mask = 0;
        for (int slot = 0; slot < 13; slot++) mask = OrbitPuzzle.place(mask, slot, slot < 9 ? slot : slot - 5);
        check(OrbitPuzzle.complete(mask), "full learner path completes");
        mask = OrbitPuzzle.expire(mask);
        for (int slot = 12; slot >= 9; slot--) mask = OrbitPuzzle.place(mask, slot, slot - 5);
        check(OrbitPuzzle.complete(mask), "timed stage retries in any slot order");
    }

    private static void layout() {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        Set<BlockPos> signs = new HashSet<>();
        Map<BlockPos, java.util.List<String>> labels = new HashMap<>();
        // A leftover old floating model must be erased by the v3 rebuild.
        blocks.put(new BlockPos(-16, 74, -19), Blocks.BLUE_CONCRETE.defaultBlockState());
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
                check(signs.add(pos.immutable()), "sign positions unique");
                labels.put(pos.immutable(), java.util.List.of(lines));
            }
        });
        check(blocks.keySet().stream().allMatch(p -> Math.abs(p.getX()) <= HubLayout.RADIUS
            && Math.abs(p.getZ()) <= HubLayout.RADIUS && p.getY() >= HubLayout.FLOOR_Y - 1 && p.getY() < 80),
            "the build stays inside its bounded footprint");
        check(blocks.get(HubLayout.SPAWN.below()).isSolid(), "arrival point has solid floor");
        check(blocks.getOrDefault(HubLayout.SPAWN, Blocks.AIR.defaultBlockState()).isAir()
            && blocks.getOrDefault(HubLayout.SPAWN.above(), Blocks.AIR.defaultBlockState()).isAir(), "arrival point has headroom");
        check(blocks.get(new BlockPos(-16, 74, -19)).isAir(), "old floating model removed");
        for (int x = -HubLayout.RADIUS; x <= HubLayout.RADIUS; x++) {
            for (int z = -HubLayout.RADIUS; z <= HubLayout.RADIUS; z++) {
                check(blocks.get(new BlockPos(x, HubLayout.FLOOR_Y, z)).isSolid(), "continuous floor");
            }
        }
        Set<BlockPos> buttons = new HashSet<>();
        Set<BlockPos> markers = new HashSet<>();
        for (int core = 0; core < 9; core++) {
            BlockPos pos = HubLayout.supply(core);
            button(blocks, signs, pos);
            check(buttons.add(pos), "supply unique");
            check(labels.get(pos.above()).contains(OrbitPuzzle.name(core)), "supply identifies core");
        }
        for (int slot = 0; slot < 13; slot++) {
            BlockPos pos = HubLayout.pedestal(slot);
            button(blocks, signs, pos);
            check(buttons.add(pos), "pedestal unique");
            BlockPos marker = HubLayout.marker(slot);
            check(markers.add(marker), "marker unique");
            check(blocks.get(marker).isAir(), "marker is air, not sign/button/support");
            check(marker.equals(pos.above(3)), "marker directly above pedestal");
            var text = labels.get(pos.above());
            check(text.contains(slot == 8 ? "MOON SLOT" : slot < 8 ? "ORBIT " + (slot + 1)
                : OrbitPuzzle.CLUES.get(slot - 9)), "pedestal teaches orbit or clue");
            for (String name : OrbitPuzzle.CORES) {
                check(text.stream().noneMatch(line -> line.equalsIgnoreCase(name)), "pedestal does not reveal core name");
            }
        }
        for (BlockPos pos : java.util.List.of(HubLayout.START, HubLayout.TERMINAL, HubLayout.EXIT)) {
            button(blocks, signs, pos);
            check(buttons.add(pos), "control unique");
        }
        check(buttons.size() == 25, "all 25 controls exist");
        check(blocks.values().stream().filter(state -> state.is(Blocks.STONE_BUTTON)).count() == 25,
            "no obsolete quiz buttons remain");
        for (int stage = 0; stage < 3; stage++) {
            BlockPos arch = java.util.List.of(new BlockPos(-12, 65, -1), new BlockPos(0, 65, -12),
                new BlockPos(12, 65, 1)).get(stage);
            check(blocks.get(arch).isAir() && blocks.get(arch.above()).isAir(), "stage arch open with headroom");
            check(signs.contains(arch.offset(0, 4, 1)), "stage zone labelled");
        }
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