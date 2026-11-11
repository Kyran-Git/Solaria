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

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.nio.file.*;
import java.util.EnumMap;
import java.util.HashSet;
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
        checkCutawayAndArrival(biome);
        checkPinnedFeatures(biome);
        checkLightingJson();

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

    /**
     * Each body is cut open on the +X/+Z quadrant, and the arrival camera sits on that diagonal,
     * so the layered interior and a multi-material surface must both be in view on arrival.
     */
    private static void checkCutawayAndArrival(Holder<Biome> biome) {
        var cores = new EnumMap<AstronomyClass, Block>(AstronomyClass.class);
        cores.put(AstronomyClass.SUN, Blocks.GLOWSTONE);
        cores.put(AstronomyClass.EARTH, Blocks.GOLD_BLOCK);
        cores.put(AstronomyClass.MOON, Blocks.IRON_BLOCK);
        cores.put(AstronomyClass.MARS, Blocks.RAW_IRON_BLOCK);
        cores.put(AstronomyClass.JUPITER, Blocks.DEEPSLATE);
        cores.put(AstronomyClass.VENUS, Blocks.IRON_BLOCK);
        cores.put(AstronomyClass.SATURN, Blocks.DEEPSLATE);
        cores.put(AstronomyClass.URANUS, Blocks.DEEPSLATE);
        cores.put(AstronomyClass.NEPTUNE, Blocks.DEEPSLATE);
        cores.put(AstronomyClass.MERCURY, Blocks.IRON_BLOCK);

        for (var cls : AstronomyClass.values()) {
            var gen = new PlanetChunkGenerator(biome, cls.id(), 1.0);
            int bound = gen.halfExtent();
            int radius = gen.blueprint().diameter() / 2;

            check(!gen.blockAt(0, 0, 0).isAir(), cls.id() + ": centre stays solid beside the cutaway");
            check(gen.blockAt(1, 0, 1).is(cores.get(cls)),
                cls.id() + ": cutaway exposes the " + cores.get(cls) + " core");

            var interior = new HashSet<Block>();
            for (int k = 1; k * Math.sqrt(2) < radius; k++) {
                var state = gen.blockAt(k, 0, k);
                if (!state.isAir()) interior.add(state.getBlock());
            }
            check(interior.size() >= 2, cls.id() + ": cutaway shows several interior layers, got " + interior);

            // Surface shell outside the cutaway, sampled over latitude and longitude.
            var surface = new HashSet<Block>();
            for (int lat = -60; lat <= 60; lat += 15) {
                for (int lon = 90; lon < 360; lon += 5) {
                    double phi = Math.toRadians(lat), theta = Math.toRadians(lon);
                    double r = radius * 0.97;
                    var state = gen.blockAt((int) Math.round(r * Math.cos(phi) * Math.cos(theta)),
                        (int) Math.round(r * Math.sin(phi)), (int) Math.round(r * Math.cos(phi) * Math.sin(theta)));
                    if (!state.isAir()) surface.add(state.getBlock());
                }
            }
            check(surface.size() >= 2, cls.id() + ": surface uses several palette blocks, got " + surface);

            double ax = gen.arrivalX(), ay = gen.arrivalY(), az = gen.arrivalZ();
            check(gen.blockAt((int) Math.floor(ax), (int) Math.floor(ay), (int) Math.floor(az)).isAir()
                    && gen.blockAt((int) Math.floor(ax), (int) Math.floor(ay) + 1, (int) Math.floor(az)).isAir(),
                cls.id() + ": arrival point is open space");
            check(Math.hypot(ax, az) > bound, cls.id() + ": arrival point is outside the structure");
            check(ax > 0 && az > 0, cls.id() + ": arrival point faces the cutaway quadrant");
            check(ay > 0 && ay < bound, cls.id() + ": arrival point is raised but within the dimension");
            check(Math.abs(gen.arrivalYaw() - 135.0f) < 0.01f, cls.id() + ": arrival yaw faces the origin");
            check(gen.arrivalPitch() > 0f && gen.arrivalPitch() < 45f,
                cls.id() + ": arrival pitch looks down at the planet");
        }
    }

    /** Features students are asked to identify must face the arrival camera, not be left to the seed. */
    private static void checkPinnedFeatures(Holder<Biome> biome) {
        var jupiter = new PlanetChunkGenerator(biome, AstronomyClass.JUPITER.id(), 1.0);
        double jr = jupiter.blueprint().diameter() / 2.0;
        double spotX = 0.9701425001453319, spotZ = -0.24253562503633297;
        check(jupiter.blockAt((int) Math.round(jr * 0.95 * spotX), (int) Math.round(-0.25 * jr),
                (int) Math.round(jr * 0.95 * spotZ)).is(Blocks.RED_CONCRETE),
            "jupiter: Great Red Spot is on the +X face");

        var neptune = new PlanetChunkGenerator(biome, AstronomyClass.NEPTUNE.id(), 1.0);
        double nr = neptune.blueprint().diameter() / 2.0;
        double[] storm = {0.9407208683835973, 0.18814417367671946, -0.2822162605150792};
        check(neptune.blockAt((int) Math.round(nr * 0.98 * storm[0]), (int) Math.round(nr * 0.98 * storm[1]),
                (int) Math.round(nr * 0.98 * storm[2])).is(Blocks.BLACKSTONE),
            "neptune: Great Dark Spot is on the +X face");
    }

    /** Planets must be lit well enough to read dark palette blocks, and fog must not hide the body. */
    private static void checkLightingJson() throws Exception {
        for (var cls : AstronomyClass.values()) {
            var type = readJson("src/main/resources/data/solariamod/dimension_type/" + cls.id() + ".json");
            double expected = cls == AstronomyClass.SUN ? 1.0 : 0.75;
            check(type.get("ambient_light").getAsDouble() == expected,
                cls.id() + ": ambient_light is " + expected);
            check(type.getAsJsonObject("attributes").get("minecraft:visual/fog_end_distance").getAsInt() == 768,
                cls.id() + ": dimension fog reaches past the planet");
            var biome = readJson("src/main/resources/data/solariamod/worldgen/biome/" + cls.id() + ".json");
            check(biome.getAsJsonObject("attributes").get("minecraft:visual/fog_end_distance").getAsInt() == 768,
                cls.id() + ": biome fog reaches past the planet");
        }
    }

    private static com.google.gson.JsonObject readJson(String path) throws Exception {
        String text = Files.readString(Path.of(path));
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        return JsonParser.parseString(text).getAsJsonObject();
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

