package com.herkules10.fantasyworldgen.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.herkules10.fantasyworldgen.engine.noise.Hashing;

class TerrainEngineTest {
    @Test
    void sameSeedGivesSameChunksInAnyOrderAndOnAnyThread() throws Exception {
        Determinism.assertDeterministic(TerrainEngine::new, 42);
        Determinism.assertDeterministic(TerrainEngine::new, -7_316_093_120_845_571_839L);
    }

    @Test
    void differentSeedsGiveDifferentTerrain() {
        TerrainEngine a = new TerrainEngine(42);
        TerrainEngine b = new TerrainEngine(43);
        int differing = 0;
        for (int[] c : Determinism.SAMPLE_CHUNKS) {
            if (Determinism.fingerprint(a, c[0], c[1]) != Determinism.fingerprint(b, c[0], c[1])) differing++;
        }
        assertTrue(differing >= Determinism.SAMPLE_CHUNKS.length - 1, "only " + differing + " sample chunks differ");
    }

    @Test
    void originDependsOnSeed() {
        Set<Double> continentalness = new HashSet<>();
        Set<Integer> surface = new HashSet<>();
        for (long seed = 0; seed < 24; seed++) {
            TerrainEngine.Column c = new TerrainEngine(Hashing.mix(seed)).column(0, 0);
            continentalness.add(c.fields().continentalness());
            surface.add(c.surfaceY());
        }
        assertEquals(24, continentalness.size(), "continentalness at the origin repeats across seeds");
        assertTrue(surface.size() >= 8, "only " + surface.size() + " distinct origin heights over 24 seeds");
    }

    @Test
    void chunkColumnAndPointQueriesAgree() {
        TerrainEngine engine = new TerrainEngine(42);
        Block[] column = new Block[WorldShape.HEIGHT];
        ChunkBuffer buffer = new ChunkBuffer();
        for (int[] c : Determinism.SAMPLE_CHUNKS) {
            engine.fillChunk(c[0], c[1], buffer);
            for (int lz = 0; lz < 16; lz += 5) {
                for (int lx = 0; lx < 16; lx += 3) {
                    int x = (c[0] << 4) + lx;
                    int z = (c[1] << 4) + lz;
                    int surface = engine.fillColumn(x, z, column);
                    assertEquals(engine.surfaceY(x, z), surface);
                    for (int y = WorldShape.MIN_Y; y <= WorldShape.MAX_Y; y++) {
                        Block expected = column[y - WorldShape.MIN_Y];
                        assertEquals(expected, buffer.get(lx, y, lz), "chunk vs column at " + x + " " + y + " " + z);
                        if ((y & 63) == 0) assertEquals(expected, engine.blockAt(x, y, z), "point vs column at " + x + " " + y + " " + z);
                    }
                    assertTrue(!column[surface - WorldShape.MIN_Y].isAir() && !column[surface - WorldShape.MIN_Y].isFluid(),
                            "surface block must be solid at " + x + ", " + z);
                    assertTrue(column[surface + 1 - WorldShape.MIN_Y].isAir() || column[surface + 1 - WorldShape.MIN_Y].isFluid(),
                            "block above surface must be air or water at " + x + ", " + z);
                }
            }
        }
    }

    @Test
    void worldHasBedrockFloorSeaAndTerrainAboveVanillaLimit() {
        TerrainEngine engine = new TerrainEngine(42);
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int x = -16384; x < 16384; x += 64) {
            for (int z = -16384; z < 16384; z += 64) {
                int y = engine.surfaceY(x, z);
                min = Math.min(min, y);
                max = Math.max(max, y);
            }
        }
        assertTrue(min < WorldShape.SEA_LEVEL - 10, "no ocean in sample, min " + min);
        assertTrue(max > 320, "placeholder terrain should exceed vanilla's build limit, max " + max);
        assertTrue(max < WorldShape.MAX_Y, "terrain hits the world ceiling, max " + max);
        assertEquals(Block.BEDROCK, engine.blockAt(123, WorldShape.MIN_Y, -456));
    }

    @Test
    void skyStaysUnallocated() {
        ChunkBuffer buffer = new ChunkBuffer();
        new TerrainEngine(42).fillChunk(0, 0, buffer);
        assertTrue(buffer.isSectionEmpty(WorldShape.SECTION_COUNT - 1));
        assertNotEquals(Block.AIR, buffer.get(0, WorldShape.MIN_Y, 0));
    }
}
