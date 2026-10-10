package com.herkules10.fantasyworldgen.engine;

/**
 * Small numeric helpers. Only StrictMath for transcendental functions: Math.sin and friends may differ in the last
 * bit between JVMs and CPUs, which would break "same seed, same world".
 */
public final class Maths {
    private Maths() {}

    public static double clamp(double v, double min, double max) {
        return v < min ? min : v > max ? max : v;
    }

    /** 0 below {@code edge0}, 1 above {@code edge1}, smooth in between. {@code edge0 > edge1} gives the falling edge. */
    public static double smoothstep(double edge0, double edge1, double v) {
        double t = clamp((v - edge0) / (edge1 - edge0), 0, 1);
        return t * t * (3 - 2 * t);
    }

    /** Rises from 0 to 1 over [a, b], stays 1 until c, falls back to 0 over [c, d]. */
    public static double band(double v, double a, double b, double c, double d) {
        return smoothstep(a, b, v) * (1 - smoothstep(c, d, v));
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** Uniform double in [0, 1) from a hash. */
    public static double unit(long hash) {
        return (hash >>> 11) * 0x1.0p-53;
    }

    public static double pow(double base, double exponent) {
        return StrictMath.pow(base, exponent);
    }

    public static double sin(double radians) {
        return StrictMath.sin(radians);
    }

    public static double cos(double radians) {
        return StrictMath.cos(radians);
    }
}
