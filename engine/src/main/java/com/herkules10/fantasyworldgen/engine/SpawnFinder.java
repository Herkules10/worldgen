package com.herkules10.fantasyworldgen.engine;

import com.herkules10.fantasyworldgen.engine.biome.BiomeType;

/**
 * Picks the world spawn: the first good column on a fixed square spiral of chunk centres around the origin. Good means
 * dry land a little above the sea, gently sloped, outside the Ocean and the Scar. Calm {@link BiomeType#WILDS} is
 * preferred if it is not much further away than the first good column of any other biome. A pure function of the
 * engine's seed.
 */
final class SpawnFinder {
    /** Grid spacing of the spiral: one probe per chunk, at its centre. */
    static final int STEP = 16;
    /** Rings searched before giving up (about 4096 blocks out). */
    static final int MAX_RINGS = 256;
    /** How many rings past the first acceptable column to keep looking for Wilds (512 blocks). */
    static final int PREFER_RINGS = 32;
    /** The surface block must be at least this far above the water, so the spawn isn't on a beach that floods. */
    static final int MIN_HEIGHT_ABOVE_SEA = 2;
    /** Land share at least this, so the probe isn't on a coastline blend. */
    static final double MIN_LAND = 0.9;
    /** Neighbours this far away on each axis... */
    static final int SLOPE_REACH = 3;
    /** ...may differ in height by at most this. */
    static final int MAX_RISE = 2;

    private SpawnFinder() {}

    static TerrainEngine.Spawn find(TerrainEngine engine) {
        TerrainEngine.Spawn fallback = null;
        int fallbackRing = 0;
        for (int ring = 0; ring <= MAX_RINGS; ring++) {
            if (fallback != null && ring > fallbackRing + PREFER_RINGS) return fallback;
            // Clockwise around the ring, starting at its north-west corner; ring 0 is the origin chunk alone.
            int side = Math.max(1, 2 * ring);
            for (int i = 0; i < 4 * side; i++) {
                int edge = i / side, t = i % side;
                int cx, cz;
                switch (edge) {
                    case 0 -> { cx = -ring + t; cz = -ring; }
                    case 1 -> { cx = ring; cz = -ring + t; }
                    case 2 -> { cx = ring - t; cz = ring; }
                    default -> { cx = -ring; cz = ring - t; }
                }
                if (ring == 0 && i > 0) break;
                TerrainEngine.Spawn spawn = probe(engine, cx * STEP + STEP / 2, cz * STEP + STEP / 2);
                if (spawn == null) continue;
                if (spawn.biome() == BiomeType.WILDS) return spawn;
                if (fallback == null) {
                    fallback = spawn;
                    fallbackRing = ring;
                }
            }
        }
        if (fallback != null) return fallback;
        // No land anywhere near: spawn on the water at the origin rather than fail world creation.
        TerrainEngine.Column c = engine.column(0, 0);
        return new TerrainEngine.Spawn(0, Math.max(c.surfaceY(), WorldShape.SEA_LEVEL - 1) + 1, 0, c.biome());
    }

    /** A spawn on column (x, z), or null if the column is not a good place to start. */
    private static TerrainEngine.Spawn probe(TerrainEngine engine, int x, int z) {
        TerrainEngine.Column c = engine.column(x, z);
        if (c.biome() == BiomeType.OCEAN || c.biome() == BiomeType.SCAR) return null;
        if (c.land() < MIN_LAND || c.surfaceY() < WorldShape.SEA_LEVEL + MIN_HEIGHT_ABOVE_SEA) return null;
        int y = c.surfaceY();
        if (!flat(engine, x + SLOPE_REACH, z, y) || !flat(engine, x - SLOPE_REACH, z, y)
                || !flat(engine, x, z + SLOPE_REACH, y) || !flat(engine, x, z - SLOPE_REACH, y)) return null;
        return new TerrainEngine.Spawn(x, y + 1, z, c.biome());
    }

    private static boolean flat(TerrainEngine engine, int x, int z, int y) {
        int other = engine.surfaceY(x, z);
        return other >= WorldShape.SEA_LEVEL && Math.abs(other - y) <= MAX_RISE;
    }
}
