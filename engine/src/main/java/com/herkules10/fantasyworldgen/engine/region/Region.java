package com.herkules10.fantasyworldgen.engine.region;

import java.util.Locale;

import com.herkules10.fantasyworldgen.engine.biome.BiomeType;
import com.herkules10.fantasyworldgen.engine.variant.Rarity;
import com.herkules10.fantasyworldgen.engine.variant.RegionContext;
import com.herkules10.fantasyworldgen.engine.variant.Variant;

/**
 * One contiguous region of one biome family, with its rolled variant. Coordinates are in blocks unless they say
 * nodes; nodes are the {@link RegionMap#SPACING}-block lattice the partition is computed on.
 *
 * @param id      stable id from the world seed and the anchor node; unique per world
 * @param cellX   the family's partition cell the region lies in
 * @param anchorX the region's first lattice node in scan order (smallest z, then smallest x), in blocks
 * @param nodes   number of lattice nodes, so the area is about {@code nodes * SPACING^2}
 * @param minX    bounding box of the region's nodes, in blocks (inclusive)
 */
public record Region(long id, BiomeType biome, int cellX, int cellZ, int anchorX, int anchorZ, int nodes,
                     double centroidX, double centroidZ, int minX, int minZ, int maxX, int maxZ,
                     RegionContext context, Variant variant) {
    public String name() {
        return variant.name();
    }

    public Rarity rarity() {
        return variant.rarity();
    }

    public long areaBlocks() {
        return (long) nodes * RegionMap.SPACING * RegionMap.SPACING;
    }

    /** Short, readable, unique key: biome and anchor, e.g. {@code spine@-2048,1504}. */
    public String key() {
        return String.format(Locale.ROOT, "%s@%d,%d", biome.id(), anchorX, anchorZ);
    }
}
