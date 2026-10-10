package com.herkules10.fantasyworldgen.engine.field;

import com.herkules10.fantasyworldgen.engine.Maths;
import com.herkules10.fantasyworldgen.engine.noise.FractalNoise;
import com.herkules10.fantasyworldgen.engine.noise.Hashing;

/**
 * The prevailing wind: one direction per world, bending slowly (up to about 25 degrees) over tens of thousands of
 * blocks. It carries moisture, so it decides which side of a range is wet and which lies in its rain shadow, and
 * it lines up the dunes of the Glass Waste.
 */
public final class Wind {
    private final double baseAngle;
    private final FractalNoise bend;

    public Wind(long seed) {
        this.baseAngle = Maths.unit(Hashing.derive(seed, "wind")) * 2 * Math.PI;
        this.bend = new FractalNoise(Hashing.derive(seed, "wind_bend"), 2, 24_000);
    }

    /** Base direction the wind blows towards, in radians (0 = +x, pi/2 = +z). */
    public double baseAngle() {
        return baseAngle;
    }

    /** Direction the wind blows towards at (x, z), in radians. */
    public double angle(double x, double z) {
        return baseAngle + 0.45 * bend.sample(x, z);
    }
}
