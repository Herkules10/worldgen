package com.herkules10.fantasyworldgen.engine.noise;

/**
 * Stateless, seeded integer hashing. All engine randomness derives from these functions, never from a stateful
 * {@code Random}, so a value depends only on the seed and the coordinates and never on generation order or threads.
 */
public final class Hashing {
    private Hashing() {}

    /** SplitMix64 finalizer: a fast bijective mix with good avalanche. */
    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public static long hash(long seed, int x, int z) {
        return mix(seed ^ mix(x * 0x9E3779B97F4A7C15L ^ mix(z * 0xC2B2AE3D27D4EB4FL)));
    }

    /** Derives an independent seed for a named sub-system, so different noise layers aren't correlated. */
    public static long derive(long seed, String salt) {
        long h = seed;
        for (int i = 0; i < salt.length(); i++) h = mix(h ^ salt.charAt(i));
        return mix(h);
    }
}
