package net.kyrangit.solaria.intro;

import com.mojang.serialization.Codec;
import java.util.List;

/** Server-independent Orbit Builder rules. Model sizes and distances are not to scale. */
public final class OrbitPuzzle {
    public static final List<String> CORES = List.of("mercury", "venus", "earth", "mars", "jupiter", "saturn", "uranus", "neptune", "moon");
    public static final int ALL = (1 << 13) - 1;
    public static final Codec<Integer> CODEC = Codec.intRange(0, ALL);
    public static final long ROUND_TICKS = 120 * 20;
    // NASA: https://science.nasa.gov/solar-system/planets/ and
    // https://science.nasa.gov/solar-system/planetary-rings/ — all four outer planets have rings.
    public static final String SOURCE = "https://science.nasa.gov/solar-system/planets/";
    private OrbitPuzzle() { }
    public static boolean valid(int mask) { return mask >= 0 && mask <= ALL; }
    public static boolean complete(int mask) { return mask == ALL; }
    public static int stage(int mask) {
        if (!valid(mask)) return 0;
        if ((mask & 15) != 15) return 0;
        if ((mask & 511) != 511) return 1;
        return complete(mask) ? 3 : 2;
    }
    public static boolean placed(int mask, int slot) {
        return valid(mask) && slot >= 0 && slot < 13 && (mask & (1 << slot)) != 0;
    }
    public static final int SLOTS = 13;

    /** Stage that owns a slot: 0 for slots 0-3, 1 for 4-8, 2 for 9-12, or -1 if invalid. */
    public static int slotStage(int slot) {
        return slot < 0 || slot >= SLOTS ? -1 : slot < 4 ? 0 : slot < 9 ? 1 : 2;
    }

    /** Core index that belongs on a slot, or -1 if invalid. */
    public static int expected(int slot) {
        return slotStage(slot) < 0 ? -1 : slot < 9 ? slot : slot - 5;
    }

    public static int count(int mask) {
        return valid(mask) ? Integer.bitCount(mask) : 0;
    }

    /** A wrong core, a slot from another stage, or invalid input leaves the progress unchanged. */
    public static int place(int mask, int slot, int core) {
        if (!valid(mask) || slot < 0 || slot >= SLOTS || core < 0 || core >= CORES.size()) return mask;
        if (stage(mask) != slotStage(slot)) return mask;
        return core == expected(slot) ? mask | (1 << slot) : mask;
    }
    public static int expire(int mask) { return valid(mask) ? mask & 511 : 0; }
    public static boolean expired(long start, long now) { return start >= 0 && (now < start || now - start >= ROUND_TICKS); }
    /** A wrong giant during the timed stage costs ten seconds. */
    public static final long PENALTY_TICKS = 10 * 20;
    /** Whole seconds left on a stage-3 round, never negative. */
    public static int secondsLeft(long start, long now) {
        if (start < 0 || expired(start, now)) return 0;
        return (int) ((ROUND_TICKS - (now - start) + 19) / 20);
    }
    public static String objective(int mask) {
        return switch (stage(mask)) {
            case 0 -> "Stage 1: select a core at the supply wall, then fill inner orbit slots 1-4 in order from the Sun.";
            case 1 -> "Stage 2: fill outer orbit slots 5-8, then place the Moon beside Earth at slot 9.";
            case 2 -> "Stage 3: press START, then match the four giant planets to the clue slots 10-13 within 120 seconds. A wrong guess costs 10 seconds.";
            default -> "Orbit Builder complete! Collect your scanner at the green depot. Your star chart (/solaria intro chart) records every body you visit.";
        };
    }
    /**
     * Stage-3 sign clues for slots 9..12. Sources (NASA Science planet overviews):
     * Jupiter is the largest planet (https://science.nasa.gov/jupiter/facts/);
     * Saturn has the most spectacular ring system (https://science.nasa.gov/saturn/facts/);
     * Uranus rotates at nearly 90 degrees, spinning on its side (https://science.nasa.gov/uranus/facts/);
     * Neptune is the most distant major planet (https://science.nasa.gov/neptune/facts/).
     */
    public static final List<String> CLUES = List.of("Biggest planet", "Brightest rings", "Spins on side", "Farthest planet");

    public static String hint(int slot) {
        if (slot == 8) return "The Moon orbits Earth, not the Sun. Pick up the Moon core for this slot.";
        if (slot >= 9 && slot < 13) return switch (slot - 9) {
            case 0 -> "Clue: the biggest planet. Hint: it has the Great Red Spot. All four giants have rings.";
            case 1 -> "Clue: the brightest, best-known rings. Hint: the sixth planet from the Sun.";
            case 2 -> "Clue: spins on its side, tilted nearly 90 degrees. Hint: the seventh planet.";
            default -> "Clue: the farthest planet from the Sun. Hint: the eighth planet.";
        };
        return "From the Sun: Mercury, Venus, Earth, Mars, Jupiter, Saturn, Uranus, Neptune. Count the orbits and try again.";
    }

    public static String name(int core) {
        String id = CORES.get(core);
        return Character.toUpperCase(id.charAt(0)) + id.substring(1);
    }
}
