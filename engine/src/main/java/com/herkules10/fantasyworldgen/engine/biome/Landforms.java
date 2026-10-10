package com.herkules10.fantasyworldgen.engine.biome;

import com.herkules10.fantasyworldgen.engine.Maths;
import com.herkules10.fantasyworldgen.engine.field.FieldSample;
import com.herkules10.fantasyworldgen.engine.noise.FractalNoise;
import com.herkules10.fantasyworldgen.engine.noise.Hashing;

/**
 * <b>Placeholder</b> landform per biome: a height in blocks relative to sea level. Each is a crude stand-in that
 * only has to be recognizable on a map (ranges, dunes along the wind, terraces, a lake basin, a stepped pit) so
 * placement and blending can be judged. Every biome will get its real landform logic in its own step.
 */
public final class Landforms {
    private final FractalNoise hills;
    private final FractalNoise detail;
    private final FractalNoise ridges;
    private final FractalNoise duneWarp;
    private final FractalNoise plates;

    public Landforms(long seed) {
        this.hills = new FractalNoise(Hashing.derive(seed, "hills"), 4, 256);
        this.detail = new FractalNoise(Hashing.derive(seed, "detail"), 3, 64);
        this.ridges = new FractalNoise(Hashing.derive(seed, "ridges"), 4, 400);
        this.duneWarp = new FractalNoise(Hashing.derive(seed, "dune_warp"), 2, 500);
        this.plates = new FractalNoise(Hashing.derive(seed, "plates"), 3, 300);
    }

    /** Ocean floor relative to sea level: shelves near coasts, deeper further out. */
    public double ocean(int x, int z, FieldSample f) {
        return -6 - 55 * Maths.smoothstep(0.0, 0.45, -f.continentalness()) + 4 * hills.sample(x, z);
    }

    /** Land height of {@code biome} relative to sea level. */
    public double land(BiomeType biome, int x, int z, FieldSample f, double uplift, double windAngle) {
        double rolling = 0.5 + 0.5 * hills.sample(x, z);
        return switch (biome) {
            case OCEAN -> ocean(x, z, f);
            case WILDS -> 6 + 16 * rolling + 2 * detail.sample(x, z);
            case SPINE -> {
                double rib = 1 - Math.abs(ridges.sample(x, z));
                yield 10 + uplift * (0.85 + 0.25 * rib * rib);
            }
            case GLASS_WASTE -> {
                // Long ridges at right angles to the wind, about 90 blocks apart.
                double along = x * Maths.cos(windAngle) + z * Maths.sin(windAngle);
                double phase = along / 90 + 1.5 * duneWarp.sample(x, z);
                double dune = 0.5 + 0.5 * Maths.sin(2 * Math.PI * phase);
                yield 8 + 16 * dune * dune * (0.6 + 0.4 * rolling);
            }
            case DRAGONSCALE -> 20 + terrace(0.5 + 0.5 * plates.sample(x, z), 7) * 55;
            case SCAR -> {
                double pit = Maths.smoothstep(0.6, 0.97, f.corruption());
                yield 3 + Math.floor((1 - pit) * 36 / 6) * 6; // stepped down towards the wound
            }
            case STILLED_COLOSSI -> -7 + 1.5 * detail.sample(x, z);
            case CANOPY_CATHEDRAL -> 8 + 12 * rolling;
            case FROZEN_WAVE -> 12 + 45 * Maths.smoothstep(0.12, -0.02, f.continentalness()) + 4 * detail.sample(x, z);
            case THOUSAND_STAIRS -> Math.floor((15 + 0.75 * uplift + 30 * rolling) / 5) * 5 + 1;
            case LANTERN_MARSH -> 0.5 + 2.5 * detail.sample(x, z);
        };
    }

    /** Quantizes v in [0, 1] into steps with steep risers. */
    private static double terrace(double v, int steps) {
        double s = v * steps;
        double base = Math.floor(s);
        double frac = s - base;
        return (base + frac * frac * frac * frac) / steps;
    }
}
