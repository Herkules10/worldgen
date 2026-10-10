package com.herkules10.fantasyworldgen.engine.noise;

import com.herkules10.fantasyworldgen.engine.Maths;

/**
 * Sequential numbers from one hash, for building a single feature or rolling one region's traits. Local to one call
 * and never shared, so the sequence depends only on the hash it started from.
 */
public final class Rng {
    private long state;

    public Rng(long seed) {
        this.state = seed;
    }

    /** Uniform in [0, 1). */
    public double next() {
        state += 0x9E3779B97F4A7C15L;
        return Maths.unit(Hashing.mix(state));
    }

    /** Uniform in [0, bound). */
    public int nextInt(int bound) {
        return Math.min(bound - 1, (int) (next() * bound));
    }
}
