package com.herkules10.fantasyworldgen.engine.noise;

/** Fractal sum of {@link GradientNoise} octaves (each octave twice the frequency, half the amplitude). */
public final class FractalNoise {
    private final GradientNoise[] octaves;
    private final double wavelength;
    private final double normalization;

    /**
     * @param wavelength size in blocks of the largest features
     */
    public FractalNoise(long seed, int octaveCount, double wavelength) {
        if (octaveCount < 1) throw new IllegalArgumentException("octaveCount must be >= 1");
        this.octaves = new GradientNoise[octaveCount];
        for (int i = 0; i < octaveCount; i++) octaves[i] = new GradientNoise(Hashing.mix(seed + i));
        this.wavelength = wavelength;
        double sum = 0;
        for (int i = 0; i < octaveCount; i++) sum += 1.0 / (1 << i);
        this.normalization = 1 / sum;
    }

    /** Roughly in [-1, 1] at block coordinates (x, z). */
    public double sample(double x, double z) {
        double fx = x / wavelength;
        double fz = z / wavelength;
        double total = 0;
        double amplitude = 1;
        for (GradientNoise octave : octaves) {
            total += amplitude * octave.sample(fx, fz);
            fx *= 2;
            fz *= 2;
            amplitude *= 0.5;
        }
        return total * normalization;
    }
}
