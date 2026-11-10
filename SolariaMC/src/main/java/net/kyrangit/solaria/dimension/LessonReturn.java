package net.kyrangit.solaria.dimension;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.kyrangit.solaria.SolariaMod;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameType;

/** Server-only player attachment. Persisted with player data, never logged or sent to the service. */
public record LessonReturn(ResourceKey<Level> dimension, double x, double y, double z,
                           float yaw, float pitch, String gameMode) {
    public static final Codec<LessonReturn> CODEC = RecordCodecBuilder.create(i -> i.group(
        Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(LessonReturn::dimension),
        Codec.DOUBLE.fieldOf("x").forGetter(LessonReturn::x),
        Codec.DOUBLE.fieldOf("y").forGetter(LessonReturn::y),
        Codec.DOUBLE.fieldOf("z").forGetter(LessonReturn::z),
        Codec.FLOAT.fieldOf("yaw").forGetter(LessonReturn::yaw),
        Codec.FLOAT.fieldOf("pitch").forGetter(LessonReturn::pitch),
        Codec.STRING.fieldOf("game_mode").forGetter(LessonReturn::gameMode)
    ).apply(i, LessonReturn::new));

    public LessonReturn {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
            || !Float.isFinite(yaw) || !Float.isFinite(pitch)
            || Math.abs(x) > 29999984 || Math.abs(z) > 29999984 || y < -2032 || y > 2032
            || GameType.byName(gameMode, null) == null) throw new IllegalArgumentException("Invalid lesson return anchor");
    }

    /**
     * Holder for the registered attachment type.
     *
     * <p>Registration needs Fabric's mixin environment, so it lives in a nested class:
     * that keeps {@link #CODEC} usable from a plain headless test JVM, which would
     * otherwise fail with {@code UnsupportedOperationException: Implemented via mixin}.
     */
    public static final class Attachment {
        /** Persisted with player data and kept across death. Server-side only; never synced. */
        public static final AttachmentType<LessonReturn> ANCHOR = AttachmentRegistry.create(
            SolariaMod.id("lesson_return"), b -> b.persistent(CODEC).copyOnDeath());

        private Attachment() {
        }
    }

    /** Classloads {@link Attachment} so the type is registered during mod initialisation. */
    public static void register() {
        var unused = Attachment.ANCHOR;
    }
}
