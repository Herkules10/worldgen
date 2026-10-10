package com.herkules10.fantasyworldgen.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.herkules10.fantasyworldgen.engine.biome.BiomeType;

class SpawnFinderTest {
    private static final long[] SEEDS = {42, 0, 1, -1, 7, 1234567, -7_316_093_120_845_571_839L, Long.MAX_VALUE};

    @Test
    void spawnIsOnDryGentleLandNearTheOrigin() {
        for (long seed : SEEDS) {
            TerrainEngine engine = new TerrainEngine(seed);
            TerrainEngine.Spawn spawn = engine.findSpawn();
            String where = "seed " + seed + ": " + spawn;
            TerrainEngine.Column c = engine.column(spawn.x(), spawn.z());
            assertNotEquals(BiomeType.OCEAN, spawn.biome(), where);
            assertNotEquals(BiomeType.SCAR, spawn.biome(), where);
            assertEquals(c.biome(), spawn.biome(), where);
            assertEquals(c.surfaceY() + 1, spawn.y(), where);
            assertTrue(c.surfaceY() >= WorldShape.SEA_LEVEL + SpawnFinder.MIN_HEIGHT_ABOVE_SEA, where);
            Block ground = TerrainEngine.block(c, c.surfaceY());
            assertTrue(!ground.isAir() && !ground.isFluid(), where);
            assertTrue(engine.blockAt(spawn.x(), spawn.y(), spawn.z()).isAir(), where + " feet are not in air");
            assertTrue(engine.blockAt(spawn.x(), spawn.y() + 1, spawn.z()).isAir(), where + " head is not in air");
            int reach = SpawnFinder.STEP * SpawnFinder.MAX_RINGS + SpawnFinder.STEP;
            assertTrue(Math.abs(spawn.x()) <= reach && Math.abs(spawn.z()) <= reach, where);
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int y = engine.surfaceY(spawn.x() + d[0] * SpawnFinder.SLOPE_REACH, spawn.z() + d[1] * SpawnFinder.SLOPE_REACH);
                assertTrue(Math.abs(y - c.surfaceY()) <= SpawnFinder.MAX_RISE, where + " is too steep");
            }
        }
    }

    @Test
    void sameSeedGivesSameSpawn() {
        for (long seed : new long[] {42, -7_316_093_120_845_571_839L}) {
            assertEquals(new TerrainEngine(seed).findSpawn(), new TerrainEngine(seed).findSpawn(), "seed " + seed);
        }
    }

    @Test
    void differentSeedsSpawnInDifferentPlaces() {
        long distinct = java.util.Arrays.stream(SEEDS).mapToObj(s -> new TerrainEngine(s).findSpawn()).distinct().count();
        assertTrue(distinct >= SEEDS.length - 1, "only " + distinct + " distinct spawns");
    }

    @Test
    void wildsIsPreferredWhenNearby() {
        int wilds = 0;
        for (long seed : SEEDS) if (new TerrainEngine(seed).findSpawn().biome() == BiomeType.WILDS) wilds++;
        assertTrue(wilds >= SEEDS.length / 2, "only " + wilds + " of " + SEEDS.length + " spawns are in the Wilds");
    }
}
