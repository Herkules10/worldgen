package com.herkules10.fantasyworldgen.render;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Per-column samples of the render region, indexed {@code pz * pixels + px}, plus the optional vertical slice. */
final class RenderData {
    static final int MISSING = Integer.MIN_VALUE;

    final RenderSettings settings;
    final int pixels;
    final int minY;
    final int worldHeight;
    final int seaLevel;

    /** Y of the highest block with a map color (water surface over oceans, canopy over forests). */
    final int[] surfaceY;
    final int[] surfaceRgb;
    /** Fluid blocks directly below the surface; 0 on land. */
    final int[] fluidDepth;
    /** Y of the highest solid, non-leaf, non-fluid block: the terrain itself. */
    final int[] groundY;
    final int[] biome;
    final List<Holder<Biome>> biomes = new ArrayList<>();
    private final Map<Holder<Biome>, Integer> biomeIndex = new HashMap<>();

    /** Vertical cross-section along X at {@code settings.sliceZ}, row 0 = top of the world; null if outside the region. */
    final int[] slice;

    final List<StructureInfo> structures = new ArrayList<>();

    record StructureInfo(String id, BoundingBox box) {}

    RenderData(RenderSettings settings, int minY, int worldHeight, int seaLevel) {
        this.settings = settings;
        this.pixels = settings.pixels();
        this.minY = minY;
        this.worldHeight = worldHeight;
        this.seaLevel = seaLevel;
        int n = pixels * pixels;
        surfaceY = new int[n];
        Arrays.fill(surfaceY, MISSING);
        surfaceRgb = new int[n];
        fluidDepth = new int[n];
        groundY = new int[n];
        biome = new int[n];
        slice = settings.sliceInRegion() ? new int[pixels * worldHeight] : null;
    }

    int biomeIndex(Holder<Biome> holder) {
        return biomeIndex.computeIfAbsent(holder, h -> {
            biomes.add(h);
            return biomes.size() - 1;
        });
    }

    static String biomeId(Holder<Biome> holder) {
        return holder.unwrapKey().map(k -> k.location().toString()).orElse("unregistered");
    }
}
