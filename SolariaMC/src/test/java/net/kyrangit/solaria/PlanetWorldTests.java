package net.kyrangit.solaria;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import net.kyrangit.solaria.blueprint.*;
import net.kyrangit.solaria.dimension.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.Heightmap;

import java.nio.file.*;
import java.util.List;

/**
 * Plumbing check (synthetic) for planet worldgen and the lesson return anchor.
 *
 * <p>Fills real {@link ProtoChunk}s with the real generator and compares every
 * block against {@link Blueprint#voxelAt}, so a chunk-bounds or clipping mistake
 * fails here rather than in a lesson. This is never evidence of in-game
 * rendering, frame rate, or classroom load: no client or server is started.
 */
public final class PlanetWorldTests {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var lookup = VanillaRegistries.createLookup();
        var biome = lookup.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.THE_VOID);
        // A one-entry biome registry is enough for chunk containers and keeps the test headless.
        var registry = new MappedRegistry<Biome>(Registries.BIOME, Lifecycle.stable());
        Registry.register(registry, Biomes.PLAINS, biome.value());
        registry.freeze();
        var factory = PalettedContainerFactory.create(
            new RegistryAccess.ImmutableRegistryAccess(List.of(registry)));
        var height = LevelHeightAccessor.create(-256, 512);
        // createSerializationContext (not RegistryOps.create) carries holder owners,
        // which the biome field needs when the generator is encoded back to JSON.
        var ops = lookup.createSerializationContext(JsonOps.INSTANCE);
        long started = System.nanoTime();

        for (var cls : AstronomyClass.values()) {
            checkShippedDimension(cls, ops, biome, factory, height);
        }
        checkRingsSurvive(biome);
        checkReturnAnchor();

        reject(() -> new PlanetChunkGenerator(biome, "unknown", 1.0), "unknown class is rejected");
        reject(() -> new PlanetChunkGenerator(biome, "sun", 2.0), "over-cap scale is rejected");
        reject(() -> new LessonReturn(Level.OVERWORLD, Double.NaN, 0, 0, 0, 0, "creative"),
            "non-finite anchor coordinate is rejected");
        reject(() -> new LessonReturn(Level.OVERWORLD, 0, 0, 0, 0, 0, "not_a_mode"),
            "unknown anchor game mode is rejected");

        System.out.println("Planet world plumbing check (synthetic): " + assertions
            + " assertions passed in " + ((System.nanoTime() - started) / 1_000_000)
            + " ms. Not a classroom benchmark.");
    }

    /**
     * Decodes the shipped dimension file with the real codec, then verifies the generated
     * chunks match the blueprint exactly at a small scale, and stay void far from the body.
     */
    private static void checkShippedDimension(AstronomyClass cls, RegistryOps<com.google.gson.JsonElement> ops,
        Holder<Biome> biome, PalettedContainerFactory factory, LevelHeightAccessor height) throws Exception {
        var file = Path.of("src/main/resources/data/solariamod/dimension/" + cls.id() + ".json");
        var generatorJson = JsonParser.parseString(Files.readString(file))
            .getAsJsonObject().get("generator");
        check(generatorJson.getAsJsonObject().get("biome").getAsString().equals("solariamod:" + cls.id()),
            cls.id() + ": own biome reference");
        // This isolated generator test uses the vanilla registry, not the datapack loader.
        generatorJson.getAsJsonObject().addProperty("biome", "minecraft:the_void");
        var shipped = PlanetChunkGenerator.CODEC.codec().parse(ops, generatorJson).getOrThrow();
        check(shipped.blueprint().astronomyClass() == cls,
            cls.id() + ".json decodes to its own class");

        // A save file stores the generator, so an asymmetric codec would corrupt the world.
        var encoded = PlanetChunkGenerator.CODEC.codec().encodeStart(ops, shipped).getOrThrow();
        var reloaded = PlanetChunkGenerator.CODEC.codec().parse(ops, encoded).getOrThrow();
        check(reloaded.blueprint().hasSameVoxels(shipped.blueprint()),
            cls.id() + ": codec round-trip keeps identical voxels");

        check(shipped.halfExtent() <= 256,
            cls.id() + ": structure fits the 512-block dimension height");
        check(shipped.blockAt(shipped.viewingDistance(), 0, 0).isAir(),
            cls.id() + ": arrival position is outside the structure");
        check(shipped.blockAt(Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE).isAir(),
            cls.id() + ": extreme coordinates do not overflow into solid blocks");

        // Exhaustive comparison at a small scale: every block of every chunk touching the body.
        var small = new PlanetChunkGenerator(biome, cls.id(), 0.05);
        int bound = small.halfExtent();
        for (int cx = -1; cx <= 0; cx++) {
            for (int cz = -1; cz <= 0; cz++) {
                var chunk = new ProtoChunk(new ChunkPos(cx, cz), UpgradeData.EMPTY, height, factory, null);
                check(small.fillFromNoise(null, null, null, chunk).join() == chunk,
                    cls.id() + ": generator completes with the chunk it was given");
                checkChunkMatchesBlueprint(cls, small, chunk, height, bound, cx, cz);
            }
        }

        // Distant chunks must cost nothing and contain nothing.
        var far = new ProtoChunk(new ChunkPos(100, -100), UpgradeData.EMPTY, height, factory, null);
        shipped.fillFromNoise(null, null, null, far).join();
        for (int y = -256; y < 256; y += 16) {
            check(far.getBlockState(new BlockPos(1600, y, -1600)).isAir(),
                cls.id() + ": chunks away from the body stay void");
        }
    }

    private static void checkChunkMatchesBlueprint(AstronomyClass cls, PlanetChunkGenerator generator,
        ProtoChunk chunk, LevelHeightAccessor height, int bound, int cx, int cz) {
        for (int x = cx * 16; x < cx * 16 + 16; x++) {
            for (int z = cz * 16; z < cz * 16 + 16; z++) {
                int firstFreeY = -256;
                for (int y = -bound - 1; y <= bound + 1; y++) {
                    var placed = chunk.getBlockState(new BlockPos(x, y, z));
                    check(placed.equals(generator.blockAt(x, y, z)),
                        cls.id() + ": chunk block matches the blueprint at " + x + "," + y + "," + z);
                    if (!placed.isAir()) {
                        firstFreeY = y + 1;
                    }
                }
                check(generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, height, null) == firstFreeY,
                    cls.id() + ": reported base height matches the blocks written");
                check(chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x & 15, z & 15) == firstFreeY - 1,
                    cls.id() + ": primed heightmap matches the blocks written");
                check(generator.getBaseColumn(x, z, height, null).getBlock(0)
                        .equals(generator.blockAt(x, 0, z)),
                    cls.id() + ": base column agrees with the blueprint");
            }
        }
    }

    /**
     * Ring extents are wider than the body, so clipping to the primary diameter would erase them.
     * Saturn's ring is equatorial and Uranus's is near-vertical, so this scans all three
     * axis-aligned planes through the origin rather than assuming an orientation.
     */
    private static void checkRingsSurvive(Holder<Biome> biome) {
        for (var cls : new AstronomyClass[]{AstronomyClass.SATURN, AstronomyClass.URANUS}) {
            var generator = new PlanetChunkGenerator(biome, cls.id(), 1.0);
            int bound = generator.halfExtent();
            // Strictly outside the primary body: anything solid here can only be ring.
            int beyondBody = generator.blueprint().diameter() / 2 + 1;
            check(bound > beyondBody, cls.id() + ": ring extends the bounds past the body");
            boolean ringFound = false;
            for (int a = beyondBody; a <= bound && !ringFound; a++) {
                for (int b = -bound; b <= bound; b++) {
                    if (!generator.blockAt(0, a, b).isAir() || !generator.blockAt(a, 0, b).isAir()
                        || !generator.blockAt(a, b, 0).isAir()) {
                        ringFound = true;
                        break;
                    }
                }
            }
            check(ringFound, cls.id() + ": ring blocks survive beyond the primary diameter");
        }
    }

    /** The anchor is written to player data, so it must survive a serialisation round-trip. */
    private static void checkReturnAnchor() {
        var anchor = new LessonReturn(Level.NETHER, 12.25, 80.5, -91.75, 73.5f, -22.25f, "adventure");
        var stored = LessonReturn.CODEC.encodeStart(JsonOps.INSTANCE, anchor).getOrThrow();
        check(LessonReturn.CODEC.parse(JsonOps.INSTANCE, stored).getOrThrow().equals(anchor),
            "lesson return anchor survives a persistence round-trip");
    }

    private static void reject(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException | BlueprintException expected) {
            check(true, message);
            return;
        }
        throw new AssertionError("Planet world check failed: " + message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Planet world check failed: " + message);
        }
        assertions++;
    }
}

