package com.herkules10.fantasyworldgen.gametest;

import com.herkules10.fantasyworldgen.FantasyWorldgen;
import com.herkules10.fantasyworldgen.engine.Block;
import com.herkules10.fantasyworldgen.engine.TerrainEngine;
import com.herkules10.fantasyworldgen.engine.WorldShape;
import com.herkules10.fantasyworldgen.engine.biome.BiomeType;
import com.herkules10.fantasyworldgen.worldgen.BlockMapping;
import com.herkules10.fantasyworldgen.worldgen.FantasyBiomeSource;
import com.herkules10.fantasyworldgen.worldgen.FantasyChunkGenerator;
import com.herkules10.fantasyworldgen.worldgen.ModWorldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks that the adapter hands the engine's world to Minecraft unchanged. */
@GameTestHolder(FantasyWorldgen.MODID)
@PrefixGameTestTemplate(false)
public final class WorldgenGameTests {
    private static final String EMPTY = "empty";

    private WorldgenGameTests() {}

    @GameTest(template = EMPTY)
    public static void dimensionTypeMatchesEngineWorldShape(GameTestHelper helper) {
        DimensionType type = registries(helper).registryOrThrow(Registries.DIMENSION_TYPE).getOrThrow(ModWorldgen.OVERWORLD_TYPE);
        helper.assertValueEqual(type.minY(), WorldShape.MIN_Y, "dimension min_y");
        helper.assertValueEqual(type.height(), WorldShape.HEIGHT, "dimension height");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void presetOverworldUsesEngine(GameTestHelper helper) {
        LevelStem overworld = overworld(helper);
        helper.assertTrue(overworld.generator() instanceof FantasyChunkGenerator, "preset overworld generator is " + overworld.generator());
        helper.assertTrue(overworld.type().is(ModWorldgen.OVERWORLD_TYPE), "preset overworld dimension type");
        helper.assertTrue(overworld.generator().getBiomeSource() instanceof FantasyBiomeSource,
                "preset overworld biome source is " + overworld.generator().getBiomeSource());
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void everyEngineBiomeIsRegistered(GameTestHelper helper) {
        var biomes = registries(helper).registryOrThrow(Registries.BIOME);
        var possible = overworld(helper).generator().getBiomeSource().possibleBiomes();
        for (BiomeType type : BiomeType.VALUES) {
            var holder = biomes.getHolder(FantasyBiomeSource.key(type));
            helper.assertTrue(holder.isPresent(), "biome " + FantasyBiomeSource.key(type).location() + " is not registered");
            helper.assertTrue(possible.contains(holder.get()), type + " is missing from the biome source");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void everyEngineBlockHasAState(GameTestHelper helper) {
        for (Block block : Block.values()) {
            BlockState state = BlockMapping.state(block);
            helper.assertTrue(state != null, "no block state for " + block);
            helper.assertValueEqual(state.isAir(), block.isAir(), "isAir of " + block);
            helper.assertValueEqual(!state.getFluidState().isEmpty(), block.isFluid(), "isFluid of " + block);
        }
        helper.succeed();
    }

    /** Generates real chunks through the generator (as the server would) and compares every block with the engine. */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void generatorWritesEngineBlocks(GameTestHelper helper) {
        RegistryAccess access = registries(helper);
        LevelStem overworld = overworld(helper);
        FantasyChunkGenerator generator = (FantasyChunkGenerator) overworld.generator();
        DimensionType type = overworld.type().value();
        long seed = 42;
        RandomState randomState = RandomState.create(NoiseGeneratorSettings.dummy(), access.lookupOrThrow(Registries.NOISE), seed);
        generator.createState(access.lookupOrThrow(Registries.STRUCTURE_SET), randomState, seed);
        TerrainEngine engine = new TerrainEngine(seed);
        LevelHeightAccessor height = LevelHeightAccessor.create(type.minY(), type.height());

        for (ChunkPos pos : new ChunkPos[] {new ChunkPos(0, 0), new ChunkPos(-3, 5), new ChunkPos(-260, 40)}) {
            ProtoChunk chunk = new ProtoChunk(pos, UpgradeData.EMPTY, height, access.registryOrThrow(Registries.BIOME), null);
            generator.createBiomes(randomState, Blender.empty(), helper.getLevel().structureManager(), chunk).join();
            chunk.setPersistedStatus(ChunkStatus.BIOMES); // as the server does after this step; unlocks biome reads
            generator.fillFromNoise(Blender.empty(), randomState, helper.getLevel().structureManager(), chunk).join();
            Block[] column = new Block[WorldShape.HEIGHT];
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    int x = pos.getMinBlockX() + lx;
                    int z = pos.getMinBlockZ() + lz;
                    int surface = engine.fillColumn(x, z, column);
                    if ((lx & 3) == 2 && (lz & 3) == 2) {
                        var biome = chunk.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(surface), QuartPos.fromBlock(z));
                        helper.assertTrue(biome.is(FantasyBiomeSource.key(engine.biomeAt(x, z))),
                                "biome at " + x + ", " + z + " is " + biome.getRegisteredName() + ", engine says " + engine.biomeAt(x, z));
                        var sourceBiome = generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), 0, QuartPos.fromBlock(z), randomState.sampler());
                        helper.assertTrue(sourceBiome == biome, "biome source and chunk disagree at " + x + ", " + z);
                    }
                    for (int y = WorldShape.MIN_Y; y <= WorldShape.MAX_Y; y++) {
                        BlockState expected = BlockMapping.state(column[y - WorldShape.MIN_Y]);
                        BlockState actual = chunk.getBlockState(cursor.set(x, y, z));
                        if (actual != expected) helper.fail("block at " + x + " " + y + " " + z + " is " + actual + ", engine says " + expected);
                    }
                    int top = Math.max(surface, WorldShape.SEA_LEVEL - 1);
                    helper.assertValueEqual(chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, lx, lz), top, "WORLD_SURFACE_WG at " + x + ", " + z);
                    helper.assertValueEqual(chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, lx, lz), surface, "OCEAN_FLOOR_WG at " + x + ", " + z);
                    helper.assertValueEqual(generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, height, randomState), surface + 1,
                            "getBaseHeight at " + x + ", " + z);
                }
            }
        }
        helper.succeed();
    }

    private static RegistryAccess registries(GameTestHelper helper) {
        return helper.getLevel().registryAccess();
    }

    private static LevelStem overworld(GameTestHelper helper) {
        WorldPreset preset = registries(helper).registryOrThrow(Registries.WORLD_PRESET).getOrThrow(ModWorldgen.PRESET);
        return preset.overworld().orElseThrow();
    }
}
