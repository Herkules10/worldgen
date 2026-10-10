package com.herkules10.fantasyworldgen.engine.biome;

import com.herkules10.fantasyworldgen.engine.Block;
import com.herkules10.fantasyworldgen.engine.Maths;
import com.herkules10.fantasyworldgen.engine.WorldShape;
import com.herkules10.fantasyworldgen.engine.noise.Hashing;
import com.herkules10.fantasyworldgen.engine.region.Region;
import com.herkules10.fantasyworldgen.engine.variant.Axis;
import com.herkules10.fantasyworldgen.engine.variant.Families;
import com.herkules10.fantasyworldgen.engine.variant.Trait;

/**
 * Surface blocks per biome. The Spine uses its region's curated palette (see {@link CuratedPalette}); every other biome
 * still has a <b>placeholder</b> base/accent pair, just enough to tell biomes apart on a map.
 */
public final class Palettes {
    /** How many blocks below the surface the placeholder palettes reach; below that is stone (deepslate under y 0). */
    public static final int SOIL_DEPTH = 4;
    /** Above this, any land is bare rock... */
    private static final int ROCK_LINE = WorldShape.SEA_LEVEL + 200;
    /** ...and above this, snow-capped. */
    private static final int SNOW_LINE = WorldShape.SEA_LEVEL + 380;
    /** The Spine's rock reaches this deep, so steep faces show the range's own stone and not generic stone. */
    private static final int SPINE_ROCK_DEPTH = 24;
    /** Thickness in blocks of one stratum of the Spine's rock band. */
    private static final int STRATUM = 5;
    /** Share of strata in the accent and highlight blocks. */
    private static final double ACCENT_SHARE = 0.32, HIGHLIGHT_SHARE = 0.07;
    /** Most strata slope at most this much (blocks of height per block across). */
    private static final double MAX_TILT = 0.18;

    private Palettes() {}

    /**
     * Block at height {@code y} of a column whose surface is at {@code surfaceY}, or null where the palette does not
     * reach (the caller fills stone or deepslate).
     *
     * @param region the column's palette region; null for biomes without curated palettes, or if the lookup missed
     */
    public static Block block(BiomeType biome, Region region, int x, int y, int z, int surfaceY) {
        int depth = surfaceY - y;
        if (biome == BiomeType.SPINE) return spine(region, x, y, z, surfaceY, depth);
        return depth < SOIL_DEPTH ? soil(biome, surfaceY, depth) : null;
    }

    private static Block soil(BiomeType biome, int surfaceY, int depth) {
        boolean submerged = surfaceY < WorldShape.SEA_LEVEL - 1;
        if (biome == BiomeType.OCEAN) return surfaceY < WorldShape.SEA_LEVEL - 12 ? Block.GRAVEL : Block.SAND;
        if (surfaceY > SNOW_LINE) return depth == 0 ? Block.SNOW_BLOCK : Block.STONE;
        if (surfaceY > ROCK_LINE) return Block.STONE;
        return switch (biome) {
            case GLASS_WASTE -> depth < 3 ? Block.SAND : Block.SANDSTONE;
            case DRAGONSCALE -> Block.TUFF;
            case SCAR -> Block.BLACKSTONE;
            case STILLED_COLOSSI -> submerged ? Block.CALCITE : grass(depth, submerged);
            case THOUSAND_STAIRS -> depth == 0 ? Block.CALCITE : Block.DIORITE;
            case LANTERN_MARSH -> Block.MUD;
            case FROZEN_WAVE -> depth == 0 ? Block.SNOW_BLOCK : Block.PACKED_ICE;
            case CANOPY_CATHEDRAL -> beach(surfaceY) ? Block.SAND : depth == 0 && !submerged ? Block.PODZOL : Block.DIRT;
            default -> beach(surfaceY) ? Block.SAND : grass(depth, submerged);
        };
    }

    /**
     * The Spine: meadows low down, then the region's rock in tilted strata, then snow. The state trait moves the
     * lines: glaciated ranges carry snow far lower, dry and bare ones show rock lower and hardly any snow.
     */
    private static Block spine(Region region, int x, int y, int z, int surfaceY, int depth) {
        if (depth >= SPINE_ROCK_DEPTH) return null;
        CuratedPalette palette = region != null ? region.variant().palette() : Families.of(BiomeType.SPINE).defaultPalette();
        Trait state = region != null ? region.variant().trait(Axis.STATE) : null;
        String stateId = state == null ? "" : state.id();
        int rockLine = ROCK_LINE + switch (stateId) {
            case "bare" -> -90;
            case "glaciated" -> -30;
            default -> 0;
        };
        int snowLine = SNOW_LINE + switch (stateId) {
            case "glaciated" -> -150;
            case "bare" -> 220;
            case "old" -> 60;
            default -> 0;
        };
        if (surfaceY > snowLine && depth == 0) return Block.SNOW_BLOCK;
        if (surfaceY > rockLine) return depth < SPINE_ROCK_DEPTH ? rock(palette, region, x, y, z) : null;
        return depth < SOIL_DEPTH ? soil(BiomeType.SPINE, surfaceY, depth) : null;
    }

    /** Strata of base, accent and highlight, tilted at one angle across the whole region. */
    private static Block rock(CuratedPalette palette, Region region, int x, int y, int z) {
        long seed = region != null ? region.id() : 0;
        double tiltX = MAX_TILT * (2 * unit(seed, 1) - 1), tiltZ = MAX_TILT * (2 * unit(seed, 2) - 1);
        long stratum = (long) Math.floor((y + tiltX * x + tiltZ * z) / STRATUM);
        double roll = unit(seed ^ Hashing.mix(stratum), 3);
        if (roll < HIGHLIGHT_SHARE) return palette.highlight();
        if (roll < HIGHLIGHT_SHARE + ACCENT_SHARE) return palette.accent();
        return palette.base();
    }

    private static double unit(long seed, int salt) {
        return Maths.unit(Hashing.mix(seed + salt * 0x9E3779B97F4A7C15L));
    }

    private static Block grass(int depth, boolean submerged) {
        return depth == 0 && !submerged ? Block.GRASS_BLOCK : Block.DIRT;
    }

    private static boolean beach(int surfaceY) {
        return surfaceY >= WorldShape.SEA_LEVEL - 4 && surfaceY <= WorldShape.SEA_LEVEL + 1;
    }
}
