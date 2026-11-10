package net.kyrangit.solaria.intro;

import com.mojang.serialization.Codec;

import java.util.List;

/** Pure, deterministic quiz rules. No timers, scores, names, or external service calls. */
public final class IntroQuiz {
    public record Question(String title, String prompt, List<String> choices, int answer, String explanation) {
        public Question {
            choices = List.copyOf(choices);
            if (choices.size() != 3 || answer < 0 || answer >= choices.size()) {
                throw new IllegalArgumentException("A station needs three choices and a valid answer");
            }
        }
    }

    public static final List<Question> QUESTIONS = List.of(
        new Question("1 / PLANETS", "Which planet is our home?", List.of("Mars", "Earth", "Jupiter"), 1,
            "Earth is our home planet. It orbits the Sun, just like the other seven planets."),
        new Question("2 / THE SUN", "What kind of object is the Sun?", List.of("A planet", "A moon", "A star"), 2,
            "The Sun is a star: it makes its own light and heat. Planets orbit it."),
        new Question("3 / THE MOON", "Why does the Moon look bright?", List.of("It makes light", "Sunlight", "City lights"), 1,
            "The Moon reflects sunlight. It does not make its own light, and it orbits Earth.")
    );
    public static final int COUNT = QUESTIONS.size();
    /** Bitmask with one bit per solved station; stations may be solved in any order. */
    public static final int ALL = (1 << COUNT) - 1;
    public static final Codec<Integer> MASK_CODEC = Codec.intRange(0, ALL);
    /** Earlier saves stored an in-order count (0..COUNT); kept only so those saves still load. */
    public static final Codec<Integer> LEGACY_COUNT_CODEC = Codec.intRange(0, COUNT);

    private IntroQuiz() {
    }

    /** A wrong, repeated, or invalid answer leaves the solved stations unchanged. */
    public static int answer(int solved, int station, int choice) {
        if (!valid(solved) || station < 0 || station >= COUNT || choice < 0 || choice >= 3) return solved;
        return choice == QUESTIONS.get(station).answer() ? solved | (1 << station) : solved;
    }

    public static boolean valid(int solved) {
        return solved >= 0 && solved <= ALL;
    }

    public static boolean solved(int solved, int station) {
        return valid(solved) && station >= 0 && station < COUNT && (solved & (1 << station)) != 0;
    }

    public static int count(int solved) {
        return valid(solved) ? Integer.bitCount(solved) : 0;
    }

    public static boolean complete(int solved) {
        return solved == ALL;
    }

    /** An old in-order count n means the first n stations were solved. */
    public static int fromLegacyCount(int count) {
        return count <= 0 ? 0 : count >= COUNT ? ALL : (1 << count) - 1;
    }

    /** Short names of unsolved stations, for "still to explore" hints, e.g. "Planets, Moon". */
    public static String remaining(int solved) {
        StringBuilder out = new StringBuilder();
        for (int station = 0; station < COUNT; station++) {
            if (solved(solved, station)) continue;
            if (!out.isEmpty()) out.append(", ");
            out.append(NAMES.get(station));
        }
        return out.toString();
    }

    public static final List<String> NAMES = List.of("Planets", "Sun", "Moon");
}