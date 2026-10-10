package com.herkules10.fantasyworldgen.worldgen;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;

import com.herkules10.fantasyworldgen.engine.Block;
import com.herkules10.fantasyworldgen.engine.ChunkBuffer;
import com.herkules10.fantasyworldgen.engine.TerrainEngine;
import com.herkules10.fantasyworldgen.engine.WorldShape;
import com.herkules10.fantasyworldgen.engine.biome.BiomeType;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;

/**
 * Replaces vanilla's noise pipeline: every block comes from the {@link TerrainEngine}. Surface rules, carvers and
 * aquifers are not used. Biomes come from the engine too ({@link FantasyBiomeSource}). Biome decoration (features) and structures still run
 * on top for now.
 */
public final class FantasyChunkGenerator extends ChunkGenerator {
    public static final MapCodec<FantasyChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource))
            .apply(instance, instance.stable(FantasyChunkGenerator::new)));

    /**
     * One engine per level. {@link RandomState} doesn't expose the seed, but every level's random state passes through
     * {@link #createState} (from {@code ChunkMap}) together with the seed before any generation, so the engine is
     * bound to it there. Keyed by identity; weak so closed worlds don't leak.
     */
    private final Map<RandomState, TerrainEngine> engines = Collections.synchronizedMap(new WeakHashMap<>());

    public FantasyChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSetLookup, RandomState randomState, long seed) {
        TerrainEngine engine = new TerrainEngine(seed);
        engines.put(randomState, engine);
        if (biomeSource instanceof FantasyBiomeSource fantasy) fantasy.bind(randomState.sampler(), engine);
        return super.createState(structureSetLookup, randomState, seed);
    }

    /** Fills chunk biomes straight from the engine, one lookup per 4x4 column instead of one per 4x4x4 cell. */
    @Override
    public CompletableFuture<ChunkAccess> createBiomes(RandomState randomState, Blender blender, StructureManager structureManager, ChunkAccess chunk) {
        if (!(biomeSource instanceof FantasyBiomeSource fantasy)) return super.createBiomes(randomState, blender, structureManager, chunk);
        TerrainEngine engine = engine(randomState);
        return CompletableFuture.supplyAsync(Util.wrapThreadWithTaskName("init_biomes", () -> {
            ChunkPos pos = chunk.getPos();
            int quartX0 = QuartPos.fromBlock(pos.getMinBlockX()), quartZ0 = QuartPos.fromBlock(pos.getMinBlockZ());
            @SuppressWarnings("unchecked")
            Holder<Biome>[] columns = new Holder[16];
            for (int qz = 0; qz < 4; qz++) {
                for (int qx = 0; qx < 4; qx++) {
                    BiomeType type = engine.biomeAt(pos.getMinBlockX() + qx * 4 + 2, pos.getMinBlockZ() + qz * 4 + 2);
                    columns[qz * 4 + qx] = fantasy.biome(type);
                }
            }
            chunk.fillBiomesFromNoise((qx, qy, qz, sampler) -> columns[(qz - quartZ0) * 4 + (qx - quartX0)], randomState.sampler());
            return chunk;
        }), Util.backgroundExecutor());
    }

    public TerrainEngine engine(RandomState randomState) {
        TerrainEngine engine = engines.get(randomState);
        if (engine == null) throw new IllegalStateException("No terrain engine bound to this RandomState; createState was not called");
        return engine;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
        TerrainEngine engine = engine(randomState);
        return CompletableFuture.supplyAsync(Util.wrapThreadWithTaskName("wgen_fill_noise", () -> fill(engine, chunk)), Util.backgroundExecutor());
    }

    private static ChunkAccess fill(TerrainEngine engine, ChunkAccess chunk) {
        ChunkPos pos = chunk.getPos();
        ChunkBuffer buffer = new ChunkBuffer();
        engine.fillChunk(pos.x, pos.z, buffer);

        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        for (int s = 0; s < WorldShape.SECTION_COUNT; s++) {
            if (buffer.isSectionEmpty(s)) continue;
            int sectionY = WorldShape.MIN_SECTION_Y + s;
            int index = chunk.getSectionIndexFromSectionY(sectionY);
            if (index < 0 || index >= chunk.getSectionsCount()) continue; // level shorter than the engine's world
            LevelChunkSection section = chunk.getSection(index);
            section.acquire();
            try {
                for (int ly = 0; ly < 16; ly++) {
                    int y = (sectionY << 4) + ly;
                    for (int lz = 0; lz < 16; lz++) {
                        for (int lx = 0; lx < 16; lx++) {
                            Block block = buffer.get(lx, y, lz);
                            if (block.isAir()) continue;
                            BlockState state = BlockMapping.state(block);
                            section.setBlockState(lx, ly, lz, state, false);
                            oceanFloor.update(lx, y, lz, state);
                            worldSurface.update(lx, y, lz, state);
                        }
                    }
                }
            } finally {
                section.release();
            }
        }
        return chunk;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        Block[] column = new Block[WorldShape.HEIGHT];
        engine(random).fillColumn(x, z, column);
        int bottom = Math.max(level.getMinBuildHeight(), WorldShape.MIN_Y);
        for (int y = Math.min(level.getMaxBuildHeight() - 1, WorldShape.MAX_Y); y >= bottom; y--) {
            if (type.isOpaque().test(BlockMapping.state(column[y - WorldShape.MIN_Y]))) return y + 1;
        }
        return level.getMinBuildHeight();
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        Block[] column = new Block[WorldShape.HEIGHT];
        engine(random).fillColumn(x, z, column);
        BlockState[] states = new BlockState[level.getHeight()];
        for (int i = 0; i < states.length; i++) {
            int y = level.getMinBuildHeight() + i;
            states[i] = BlockMapping.state(WorldShape.contains(y) ? column[y - WorldShape.MIN_Y] : Block.AIR);
        }
        return new NoiseColumn(level.getMinBuildHeight(), states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        TerrainEngine engine = engines.get(random);
        if (engine != null) info.add("Fantasy Worldgen surface y: " + engine.surfaceY(pos.getX(), pos.getZ()));
    }

    /** Terrain comes complete from the engine. */
    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structureManager, RandomState random, ChunkAccess chunk) {
    }

    /** No vanilla carvers: caves will come from the engine. */
    @Override
    public void applyCarvers(WorldGenRegion level, long seed, RandomState random, BiomeManager biomeManager,
                             StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving step) {
    }

    /** Same as vanilla's noise generator: initial animals from the biome's spawn settings. */
    @Override
    public void spawnOriginalMobs(WorldGenRegion level) {
        ChunkPos pos = level.getCenter();
        var biome = level.getBiome(pos.getWorldPosition().atY(level.getMaxBuildHeight() - 1));
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0)); // reseeded on the next line
        random.setDecorationSeed(level.getSeed(), pos.getMinBlockX(), pos.getMinBlockZ());
        NaturalSpawner.spawnMobsForChunkGeneration(level, biome, pos, random);
    }

    @Override
    public int getGenDepth() {
        return WorldShape.HEIGHT;
    }

    @Override
    public int getSeaLevel() {
        return WorldShape.SEA_LEVEL;
    }

    @Override
    public int getMinY() {
        return WorldShape.MIN_Y;
    }
}
