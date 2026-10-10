package com.herkules10.fantasyworldgen.worldgen;

import com.herkules10.fantasyworldgen.FantasyWorldgen;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Worldgen registrations. The world preset, dimension type and biomes themselves are data under
 * {@code data/fantasyworldgen/}: {@code worldgen/world_preset/fantasy.json}, {@code dimension_type/overworld.json} and
 * {@code worldgen/biome/<id>.json} (one per engine BiomeType).
 */
public final class ModWorldgen {
    private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, FantasyWorldgen.MODID);
    private static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, FantasyWorldgen.MODID);

    static {
        CHUNK_GENERATORS.register("fantasy", () -> FantasyChunkGenerator.CODEC);
        BIOME_SOURCES.register("fantasy", () -> FantasyBiomeSource.CODEC);
    }

    public static final ResourceKey<WorldPreset> PRESET = ResourceKey.create(Registries.WORLD_PRESET, FantasyWorldgen.id("fantasy"));
    public static final ResourceKey<DimensionType> OVERWORLD_TYPE = ResourceKey.create(Registries.DIMENSION_TYPE, FantasyWorldgen.id("overworld"));

    private ModWorldgen() {}

    public static void register(IEventBus modEventBus) {
        CHUNK_GENERATORS.register(modEventBus);
        BIOME_SOURCES.register(modEventBus);
    }
}
