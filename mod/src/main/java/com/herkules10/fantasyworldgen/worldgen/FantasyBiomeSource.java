package com.herkules10.fantasyworldgen.worldgen;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.herkules10.fantasyworldgen.FantasyWorldgen;
import com.herkules10.fantasyworldgen.engine.TerrainEngine;
import com.herkules10.fantasyworldgen.engine.biome.BiomeType;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * Biomes come from the engine: {@code fantasyworldgen:<id>} for each {@link BiomeType}.
 * <p>
 * Vanilla only hands a biome source the level's {@link Climate.Sampler}, never the seed. Every level's sampler comes
 * from its RandomState, which {@link FantasyChunkGenerator#createState} sees together with the seed, so the generator
 * {@linkplain #bind binds} the sampler to that level's engine. Bindings are by identity and weak, so closed worlds
 * are released.
 */
public final class FantasyBiomeSource extends BiomeSource {
    public static final MapCodec<FantasyBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(RegistryOps.retrieveGetter(Registries.BIOME))
            .apply(instance, instance.stable(FantasyBiomeSource::new)));

    private final List<Holder<Biome>> biomes = new ArrayList<>(); // indexed by BiomeType ordinal
    private final List<Binding> bindings = new ArrayList<>();

    private record Binding(WeakReference<Climate.Sampler> sampler, TerrainEngine engine) {}

    public FantasyBiomeSource(HolderGetter<Biome> biomeGetter) {
        for (BiomeType type : BiomeType.VALUES) biomes.add(biomeGetter.getOrThrow(key(type)));
    }

    public static ResourceKey<Biome> key(BiomeType type) {
        return ResourceKey.create(Registries.BIOME, FantasyWorldgen.id(type.id()));
    }

    public Holder<Biome> biome(BiomeType type) {
        return biomes.get(type.ordinal());
    }

    synchronized void bind(Climate.Sampler sampler, TerrainEngine engine) {
        bindings.removeIf(b -> b.sampler().get() == null || b.sampler().get() == sampler);
        bindings.add(new Binding(new WeakReference<>(sampler), engine));
    }

    private synchronized TerrainEngine engine(Climate.Sampler sampler) {
        for (Binding b : bindings) {
            if (b.sampler().get() == sampler) return b.engine();
        }
        throw new IllegalStateException("No terrain engine bound to this climate sampler; the chunk generator's createState was not called");
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return biomes.stream();
    }

    /** Biomes don't vary with height (yet): the biome of the column at the quart's center. */
    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        return biome(engine(sampler).biomeAt(QuartPos.toBlock(quartX) + 2, QuartPos.toBlock(quartZ) + 2));
    }
}
