package net.kyrangit.solaria.intro;

import com.mojang.serialization.Codec;
import net.kyrangit.solaria.blueprint.AstronomyClass;

/** Pure visited-bodies bitmask: one bit per {@link AstronomyClass} ordinal. */
public final class StarChart {
    public static final int TOTAL = AstronomyClass.values().length;
    public static final int ALL = (1 << TOTAL) - 1;
    public static final Codec<Integer> CODEC = Codec.intRange(0, ALL);

    private StarChart() {
    }

    public static int visit(int mask, AstronomyClass body) {
        return body == null || mask < 0 || mask > ALL ? mask : mask | (1 << body.ordinal());
    }

    public static boolean visited(int mask, AstronomyClass body) {
        return body != null && mask >= 0 && mask <= ALL && (mask & (1 << body.ordinal())) != 0;
    }

    public static int count(int mask) {
        return mask < 0 || mask > ALL ? 0 : Integer.bitCount(mask);
    }

    public static String describe(int mask) {
        StringBuilder seen = new StringBuilder();
        StringBuilder unseen = new StringBuilder();
        for (AstronomyClass body : AstronomyClass.values()) {
            StringBuilder out = visited(mask, body) ? seen : unseen;
            if (!out.isEmpty()) out.append(", ");
            out.append(Character.toUpperCase(body.id().charAt(0))).append(body.id().substring(1));
        }
        return "Star chart " + count(mask) + "/" + TOTAL + ". Visited: " + (seen.isEmpty() ? "none yet" : seen)
            + (unseen.isEmpty() ? ". Every body explored!" : ". Still to explore: " + unseen + ".");
    }
}
