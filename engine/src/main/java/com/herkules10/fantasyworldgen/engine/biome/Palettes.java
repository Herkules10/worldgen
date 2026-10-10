package com.herkules10.fantasyworldgen.engine.biome;

import com.herkules10.fantasyworldgen.engine.Block;
import com.herkules10.fantasyworldgen.engine.WorldShape;

/**
 * <b>Placeholder</b> surface blocks per biome: one base/accent pair each, just enough to tell biomes apart on a map.
 * The curated variant palettes come with the variant system.
 */
public final class Palettes {
    /** How many blocks below the surface the palette reaches; below that is stone (deepslate under y 0). */
    public static final int SOIL_DEPTH = 4;
    /** Above this, any land is bare rock... */
    private static final int ROCK_LINE = WorldShape.SEA_LEVEL + 200;
    /** ...and above this, snow-capped. */
    private static final int SNOW_LINE = WorldShape.SEA_LEVEL + 380;

    private Palettes() {}

    /** Block {@code depth} (0 = the surface block itself) below the surface at {@code surfaceY}, for depth < SOIL_DEPTH. */
    public static Block soil(BiomeType biome, int surfaceY, int depth) {
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

    private static Block grass(int depth, boolean submerged) {
        return depth == 0 && !submerged ? Block.GRASS_BLOCK : Block.DIRT;
    }

    private static boolean beach(int surfaceY) {
        return surfaceY >= WorldShape.SEA_LEVEL - 4 && surfaceY <= WorldShape.SEA_LEVEL + 1;
    }
}
