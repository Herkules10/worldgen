package com.herkules10.fantasyworldgen.engine.noise;

/**
 * Fractal sum of {@link GradientNoise} octaves (each octave twice the frequency, half the amplitude). Each octave
 * is shifted by its own seeded offset: gradient noise is 0 on its lattice, and unshifted octaves would all share
 * the lattice points at multiples of the wavelength, pinning the sum to 0 there for every seed.
 */
public final class FractalNoise {
    /** Offsets are in [0, OFFSET_RANGE) lattice units, small enough to keep full precision far from the origin. */
    private static final double OFFSET_RANGE = 1024;

    private final GradientNoise[] octaves;
    private final double[] offsetX;
    private final double[] offsetZ;
    private final double wavelength;
    private final double normalization;

    /**
     * @param wavelength size in blocks of the largest features
     */
    public FractalNoise(long seed, int octaveCount, double wavelength) {
        if (octaveCount < 1) throw new IllegalArgumentException("octaveCount must be >= 1");
        this.octaves = new GradientNoise[octaveCount];
        this.offsetX = new double[octaveCount];
        this.offsetZ = new double[octaveCount];
        for (int i = 0; i < octaveCount; i++) {
            long octaveSeed = Hashing.mix(seed + i);
            octaves[i] = new GradientNoise(octaveSeed);
            offsetX[i] = unit(Hashing.mix(octaveSeed ^ 0x5851F42D4C957F2DL)) * OFFSET_RANGE;
            offsetZ[i] = unit(Hashing.mix(octaveSeed ^ 0x14057B7EF767814FL)) * OFFSET_RANGE;
        }
        this.wavelength = wavelength;
        double sum = 0;
        for (int i = 0; i < octaveCount; i++) sum += 1.0 / (1 << i);
        this.normalization = 1 / sum;
    }

    /** Top 53 bits as a double in [0, 1). */
    private static double unit(long bits) {
        return (bits >>> 11) * 0x1.0p-53;
    }

    /** Roughly in [-1, 1] at block coordinates (x, z). */
    public double sample(double x, double z) {
        double fx = x / wavelength;
        double fz = z / wavelength;
        double total = 0;
        double amplitude = 1;
        for (int i = 0; i < octaves.length; i++) {
            total += amplitude * octaves[i].sample(fx + offsetX[i], fz + offsetZ[i]);
            fx *= 2;
            fz *= 2;
            amplitude *= 0.5;
        }
        return total * normalization;
    }
}
