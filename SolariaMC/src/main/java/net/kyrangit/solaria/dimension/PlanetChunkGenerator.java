package net.kyrangit.solaria.dimension;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.kyrangit.solaria.blueprint.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/** Immutable, bounded, on-demand planet worldgen. No config reads or shared mutable chunk state. */
public final class PlanetChunkGenerator extends ChunkGenerator {
    public static final MapCodec<PlanetChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Biome.CODEC.fieldOf("biome").forGetter(g -> g.biome),
        Codec.STRING.fieldOf("planet").forGetter(g -> g.blueprint.astronomyClass().id()),
        Codec.doubleRange(0.001, 10.0).optionalFieldOf("scale", 1.0).forGetter(g -> g.blueprint.blocksPerUnit())
    ).apply(i, PlanetChunkGenerator::new));
    private final Holder<Biome> biome;
    private final Blueprint blueprint;
    private final BlockState[] palette;

    public PlanetChunkGenerator(Holder<Biome> biome, String planet, double scale) {
        super(new FixedBiomeSource(biome));
        this.biome = biome;
        blueprint = BlueprintGenerator.generate(AstronomyClass.fromId(planet), scale);
        palette = new BlockState[blueprint.palette().size() + 1];
        palette[0] = Blocks.AIR.defaultBlockState();
        for (int n = 1; n < palette.length; n++) {
            palette[n] = BuiltInRegistries.BLOCK.getValue(Identifier.parse(blueprint.palette().blockId(n))).defaultBlockState();
        }
    }

    public Blueprint blueprint() { return blueprint; }

    /** Half-extent of the whole structure, rings included. Voxels outside this are always air. */
    public int halfExtent() {
        return BlueprintGenerator.blueprintExtent(blueprint.astronomyClass(), blueprint.diameter()) / 2 + 1;
    }

    /** A clear standing distance on the +X axis: outside the structure, inside the loaded area. */
    public int viewingDistance() { return halfExtent() + 24; }

    /**
     * Diagonal viewpoint in the +X,+Z quadrant that faces the quarter-sphere cutaway opening,
     * raised above the equator so interior layers are visible on arrival.
     */
    private double arrivalDistance() { return halfExtent() + 24.0; }
    public double arrivalX() { return arrivalDistance() * Math.cos(Math.toRadians(45.0)) + 0.5; }
    public double arrivalZ() { return arrivalDistance() * Math.sin(Math.toRadians(45.0)) + 0.5; }
    public double arrivalY() { return halfExtent() * 0.35 + 0.5; }

    /** Yaw in degrees pointing from the arrival point toward the origin (Minecraft yaw: 0 = +Z, 90 = -X). */
    public float arrivalYaw() {
        double dx = -arrivalX();
        double dz = -arrivalZ();
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /** Pitch in degrees aiming slightly down at the origin from the raised arrival point. */
    public float arrivalPitch() {
        double dx = -arrivalX();
        double dy = -arrivalY();
        double dz = -arrivalZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        return (float) Math.toDegrees(-Math.atan2(dy, horizontal));
    }

    public BlockState blockAt(int x, int y, int z) {
        // Includes both ring systems, independently of the primary-body diameter.
        int bound = halfExtent();
        if (Math.abs((long) x) > bound || Math.abs((long) y) > bound || Math.abs((long) z) > bound) return palette[0];
        return palette[blueprint.voxelAt(x, y, z)];
    }
    @Override protected MapCodec<? extends ChunkGenerator> codec() { return CODEC; }
    @Override public int getMinY() { return -256; }
    @Override public int getGenDepth() { return 512; }
    @Override public int getSeaLevel() { return -256; }
    @Override public int getSpawnHeight(LevelHeightAccessor h) { return 0; }

    @Override public CompletableFuture<ChunkAccess> fillFromNoise(Blender b, RandomState r, StructureManager s, ChunkAccess chunk) {
        int bound = halfExtent();
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        if (x0 <= bound && x0 + 15 >= -bound && z0 <= bound && z0 + 15 >= -bound) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            int yMin = Math.max(chunk.getMinY(), -bound);
            int yMax = Math.min(chunk.getMinY() + chunk.getHeight() - 1, bound);
            for (int x = Math.max(x0, -bound); x <= Math.min(x0 + 15, bound); x++) {
                for (int z = Math.max(z0, -bound); z <= Math.min(z0 + 15, bound); z++) {
                    for (int y = yMin; y <= yMax; y++) {
                        BlockState state = blockAt(x, y, z);
                        if (!state.isAir()) chunk.setBlockState(pos.set(x, y, z), state);
                    }
                }
            }
        }
        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG));
        return CompletableFuture.completedFuture(chunk);
    }

    @Override public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor h, RandomState r) {
        BlockState[] column = new BlockState[h.getHeight()];
        for (int n = 0; n < column.length; n++) column[n] = blockAt(x, h.getMinY() + n, z);
        return new NoiseColumn(h.getMinY(), column);
    }
    @Override public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor h, RandomState r) {
        int bound = halfExtent();
        for (int y = Math.min(h.getMinY() + h.getHeight() - 1, bound); y >= Math.max(h.getMinY(), -bound); y--) {
            if (type.isOpaque().test(blockAt(x, y, z))) return y + 1;
        }
        return h.getMinY();
    }
    @Override public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> sets, RandomState r, long seed) {
        return ChunkGeneratorStructureState.createForFlat(r, seed, biomeSource, Stream.empty());
    }
    @Override public void applyBiomeDecoration(WorldGenLevel l, ChunkAccess c, StructureManager s) { }
    @Override public void buildSurface(WorldGenRegion l, StructureManager s, RandomState r, ChunkAccess c) { }
    @Override public void applyCarvers(WorldGenRegion l, long seed, RandomState r, BiomeManager b, StructureManager s, ChunkAccess c) { }
    @Override public void spawnOriginalMobs(WorldGenRegion l) { }
    @Override public void addDebugScreenInfo(List<String> lines, RandomState r, BlockPos pos) {
        lines.add("Solaria planet: " + blueprint.astronomyClass().id() + " (not to scale)");
    }
}
