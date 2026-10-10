package com.herkules10.fantasyworldgen.engine.biome;

import static com.herkules10.fantasyworldgen.engine.Maths.band;
import static com.herkules10.fantasyworldgen.engine.Maths.smoothstep;

import com.herkules10.fantasyworldgen.engine.field.FieldSample;

/**
 * Where each biome may form, as smooth suitabilities read from the shared world fields (the placement rules of
 * Artistic_Direction.md). Suitabilities are sharpened and normalized into blend weights; because they vary smoothly,
 * neighbouring biomes blend their landforms instead of meeting at a seam.
 */
public final class BiomePlacement {
    /** Suitability of the calm filler; a set piece must beat this to appear. */
    private static final double WILDS_BASELINE = 0.3;

    private BiomePlacement() {}

    /** Share of land vs ocean at a point in [0, 1]; mountain ranges push land out into the sea. */
    public static double land(FieldSample f, double uplift) {
        return smoothstep(-0.06, 0.02, f.continentalness() + uplift / 700);
    }

    /**
     * Writes blend weights for the land biomes into {@code out} (indexed by ordinal, summing to 1; {@code OCEAN}
     * stays 0). {@code uplift} is the exact per-column uplift, sharper than the interpolated field.
     */
    public static void landWeights(FieldSample f, double uplift, double[] out) {
        double c = f.continentalness(), m = f.moisture(), t = f.heat(), k = f.corruption();
        double lowland = 1 - smoothstep(60, 150, uplift);
        double scar = smoothstep(0.55, 0.75, k);
        double calm = 1 - scar;

        out[BiomeType.OCEAN.ordinal()] = 0;
        out[BiomeType.WILDS.ordinal()] = WILDS_BASELINE * calm;
        out[BiomeType.SPINE.ordinal()] = smoothstep(120, 300, uplift);
        out[BiomeType.SCAR.ordinal()] = scar;
        // Rain shadow of the Spine: dry, warm, flat.
        out[BiomeType.GLASS_WASTE.ordinal()] = smoothstep(0.38, 0.22, m) * smoothstep(0.35, 0.5, t) * lowland * calm;
        // The dry margin between the Glass Waste and the foothills; never near a Scar (something keeps them apart).
        out[BiomeType.DRAGONSCALE.ordinal()] = band(m, 0.2, 0.3, 0.42, 0.52) * (1 - smoothstep(80, 200, uplift))
                * f.dragonMask() * (1 - smoothstep(0.0, 0.08, k));
        // Wide, flat basins sheltered by the Spine's foothills.
        out[BiomeType.STILLED_COLOSSI.ordinal()] = band(uplift, 2, 15, 90, 160) * band(m, 0.3, 0.4, 0.65, 0.75)
                * f.basinMask() * calm;
        // Warm, wet lowland.
        out[BiomeType.CANOPY_CATHEDRAL.ordinal()] = smoothstep(0.55, 0.7, t) * smoothstep(0.55, 0.7, m)
                * (1 - smoothstep(40, 120, uplift)) * calm;
        // Cold coasts only.
        out[BiomeType.FROZEN_WAVE.ordinal()] = smoothstep(0.3, 0.18, t) * band(c, -0.1, -0.03, 0.08, 0.16) * lowland * calm;
        // The wet, windward flanks of the Spine.
        out[BiomeType.THOUSAND_STAIRS.ordinal()] = band(uplift, 20, 60, 170, 250) * smoothstep(0.55, 0.75, m)
                * (1 - smoothstep(150, 300, f.upwindBarrier() - uplift)) * calm;
        // Where the water of the Stairs collects: wet, warm-ish coastal lowland.
        out[BiomeType.LANTERN_MARSH.ordinal()] = smoothstep(0.72, 0.85, m) * smoothstep(0.42, 0.55, t)
                * band(c, -0.05, 0.0, 0.12, 0.22) * (1 - smoothstep(20, 60, uplift)) * calm;

        double sum = 0;
        for (int i = 0; i < out.length; i++) {
            double w = out[i] * out[i] * out[i]; // cubing sharpens transitions: a higher power = narrower blends
            out[i] = w;
            sum += w;
        }
        for (int i = 0; i < out.length; i++) out[i] /= sum; // WILDS or SCAR is always > 0, so sum > 0
    }

    /**
     * The biome Minecraft is told at a point: ocean where the land share is below one half, else the dominant land
     * biome. {@code TerrainEngine.column} and the region lattice both use this, so they always agree.
     */
    public static BiomeType biomeAt(FieldSample f, double uplift, double[] weights) {
        if (land(f, uplift) < 0.5) return BiomeType.OCEAN;
        landWeights(f, uplift, weights);
        return dominant(weights);
    }

    /** Index of the largest weight. */
    public static BiomeType dominant(double[] weights) {
        int best = 1;
        for (int i = 2; i < weights.length; i++) if (weights[i] > weights[best]) best = i;
        return BiomeType.VALUES[best];
    }
}
