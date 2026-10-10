package com.herkules10.fantasyworldgen.engine.noise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NoiseTest {
    @Test
    void gradientNoiseIsBoundedZeroOnLatticeAndContinuous() {
        GradientNoise noise = new GradientNoise(1234);
        double min = 0, max = 0;
        for (int i = 0; i < 200_000; i++) {
            double x = (Hashing.mix(i) % 100_000) / 97.0;
            double z = (Hashing.mix(~i) % 100_000) / 89.0;
            double v = noise.sample(x, z);
            min = Math.min(min, v);
            max = Math.max(max, v);
            assertTrue(Math.abs(v - noise.sample(x + 1e-4, z)) < 1e-2, "discontinuity at " + x + ", " + z);
        }
        assertTrue(min >= -1.0001 && max <= 1.0001, "range [" + min + ", " + max + "]");
        assertTrue(min < -0.6 && max > 0.6, "range too narrow [" + min + ", " + max + "]");
        assertEquals(0.0, noise.sample(-17, 42), 1e-12);
    }

    @Test
    void noiseDependsOnlyOnSeedAndPosition() {
        FractalNoise a = new FractalNoise(99, 5, 512);
        FractalNoise b = new FractalNoise(99, 5, 512);
        FractalNoise other = new FractalNoise(100, 5, 512);
        boolean anyDifferent = false;
        for (int x = -5000; x < 5000; x += 37) {
            assertEquals(a.sample(x, -x * 3), b.sample(x, -x * 3));
            anyDifferent |= a.sample(x, -x * 3) != other.sample(x, -x * 3);
        }
        assertTrue(anyDifferent);
    }
}
