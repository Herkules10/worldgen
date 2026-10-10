package com.herkules10.fantasyworldgen.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.LongFunction;

import com.herkules10.fantasyworldgen.engine.noise.Hashing;

/**
 * Reusable determinism checks for anything that turns a seed into chunks. Every terrain change should keep
 * {@link #assertDeterministic} passing for its engine.
 */
public final class Determinism {
    /** Chunks spread over both signs, chunk borders and far-out coordinates (~1 million blocks). */
    public static final int[][] SAMPLE_CHUNKS = {
            {0, 0}, {-1, -1}, {-1, 0}, {0, -1}, {1, 1}, {7, -13}, {-200, 340}, {62_500, -62_500}, {-62_499, 62_501},
    };

    private Determinism() {}

    /** Hash of every block in the chunk. */
    public static long fingerprint(TerrainEngine engine, int chunkX, int chunkZ) {
        ChunkBuffer buffer = new ChunkBuffer();
        engine.fillChunk(chunkX, chunkZ, buffer);
        long h = 17;
        for (int y = WorldShape.MIN_Y; y <= WorldShape.MAX_Y; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) h = Hashing.mix(h * 31 + buffer.get(x, y, z).id());
            }
        }
        return h;
    }

    /**
     * Asserts that two independently built engines with the same seed agree on every sample chunk, regardless of
     * generation order or concurrency.
     */
    public static void assertDeterministic(LongFunction<TerrainEngine> factory, long seed) throws Exception {
        assertDeterministic(factory, seed, SAMPLE_CHUNKS);
    }

    /** As above, for chosen chunks (e.g. inside a feature the default samples miss). */
    public static void assertDeterministic(LongFunction<TerrainEngine> factory, long seed, int[][] chunks) throws Exception {
        TerrainEngine first = factory.apply(seed);
        long[] expected = new long[chunks.length];
        for (int i = 0; i < chunks.length; i++) expected[i] = fingerprint(first, chunks[i][0], chunks[i][1]);

        TerrainEngine reversed = factory.apply(seed);
        for (int i = chunks.length - 1; i >= 0; i--) {
            assertEquals(expected[i], fingerprint(reversed, chunks[i][0], chunks[i][1]),
                    "chunk " + chunks[i][0] + ", " + chunks[i][1] + " depends on generation order");
        }

        TerrainEngine shared = factory.apply(seed);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<Long>> results = new ArrayList<>();
            for (int round = 0; round < 3; round++) {
                for (int[] c : chunks) results.add(pool.submit(() -> fingerprint(shared, c[0], c[1])));
            }
            for (int i = 0; i < results.size(); i++) {
                int[] c = chunks[i % chunks.length];
                assertEquals(expected[i % chunks.length], results.get(i).get(),
                        "chunk " + c[0] + ", " + c[1] + " differs when generated concurrently");
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
