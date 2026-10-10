package com.herkules10.fantasyworldgen.engine.noise;

/**
 * Seeded 2D gradient (Perlin) noise. Gradients come from {@link Hashing}, so there are no permutation tables and
 * no state. Uses only + - * and floor, which Java evaluates identically on every platform.
 */
public final class GradientNoise {
    private static final int GRADIENTS = 16;
    private static final double[] GX = new double[GRADIENTS];
    private static final double[] GZ = new double[GRADIENTS];
    /** Rescales the theoretical 2D Perlin range (+-sqrt(0.5)) to about +-1. */
    private static final double SCALE = 1.4142135623730951;

    static {
        // 16 unit vectors at 22.5 degree steps, from a fixed cosine table instead of runtime trigonometry.
        for (int i = 0; i < GRADIENTS; i++) {
            GX[i] = cos(i);
            GZ[i] = cos(i + 12); // sin(a) = cos(a - 90 degrees)
        }
    }

    /** cos(step * 22.5 degrees). */
    private static double cos(int step) {
        double[] firstHalf = {1, 0.9238795325112867, 0.7071067811865476, 0.3826834323650898, 0,
                -0.3826834323650898, -0.7071067811865476, -0.9238795325112867};
        int s = step % GRADIENTS;
        return s < 8 ? firstHalf[s] : -firstHalf[s - 8];
    }

    private final long seed;

    public GradientNoise(long seed) {
        this.seed = seed;
    }

    /** Noise at (x, z) in lattice units; roughly in [-1, 1], 0 at every lattice point. */
    public double sample(double x, double z) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        double fx = x - x0;
        double fz = z - z0;
        double n00 = dot(x0, z0, fx, fz);
        double n10 = dot(x0 + 1, z0, fx - 1, fz);
        double n01 = dot(x0, z0 + 1, fx, fz - 1);
        double n11 = dot(x0 + 1, z0 + 1, fx - 1, fz - 1);
        double u = fade(fx);
        double v = fade(fz);
        double nx0 = n00 + u * (n10 - n00);
        double nx1 = n01 + u * (n11 - n01);
        return SCALE * (nx0 + v * (nx1 - nx0));
    }

    private double dot(int ix, int iz, double dx, double dz) {
        int g = (int) (Hashing.hash(seed, ix, iz) >>> 60); // top 4 bits: 0..15
        return GX[g] * dx + GZ[g] * dz;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }
}
