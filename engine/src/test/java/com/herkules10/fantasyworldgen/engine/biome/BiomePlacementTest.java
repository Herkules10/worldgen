package com.herkules10.fantasyworldgen.engine.biome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.herkules10.fantasyworldgen.engine.TerrainEngine;
import com.herkules10.fantasyworldgen.engine.field.FieldSample;

/**
 * The placement rules of Artistic_Direction.md, checked statistically over a large area. If a tuning change breaks
 * the story the land is supposed to tell, these fail.
 */
class BiomePlacementTest {
    private static final long SEED = 42;
    private static final int HALF_EXTENT = 24_576;
    private static final int STEP = 96;

    private static final Map<BiomeType, List<FieldSample>> samples = new EnumMap<>(BiomeType.class);

    @BeforeAll
    static void sampleWorld() {
        TerrainEngine engine = new TerrainEngine(SEED);
        for (BiomeType b : BiomeType.VALUES) samples.put(b, new ArrayList<>());
        for (int z = -HALF_EXTENT; z < HALF_EXTENT; z += STEP) {
            for (int x = -HALF_EXTENT; x < HALF_EXTENT; x += STEP) {
                TerrainEngine.Column c = engine.column(x, z);
                samples.get(c.biome()).add(c.fields());
            }
        }
    }

    @Test
    void everyBiomeAppears() {
        for (BiomeType b : BiomeType.VALUES) assertTrue(!samples.get(b).isEmpty(), b + " never appears in a 49k x 49k area");
    }

    @Test
    void glassWasteFormsInTheRainShadowOfTheSpine() {
        List<FieldSample> waste = samples.get(BiomeType.GLASS_WASTE);
        double shadowed = waste.stream().filter(f -> f.upwindBarrier() > 100).count() / (double) waste.size();
        assertTrue(shadowed > 0.9, "only " + shadowed + " of the Glass Waste lies downwind of a range");
    }

    @Test
    void thousandStairsFormOnTheWetSide() {
        double stairs = mean(BiomeType.THOUSAND_STAIRS, FieldSample::moisture);
        double waste = mean(BiomeType.GLASS_WASTE, FieldSample::moisture);
        assertTrue(stairs > waste + 0.35, "Stairs moisture " + stairs + " vs Glass Waste " + waste);
        // Barrier above the ground itself: high on the lee side of a range, low on its windward flank.
        double sheltered = samples.get(BiomeType.THOUSAND_STAIRS).stream().filter(f -> f.upwindBarrier() - f.uplift() > 250).count()
                / (double) samples.get(BiomeType.THOUSAND_STAIRS).size();
        assertTrue(sheltered < 0.1, sheltered + " of the Stairs lie in a rain shadow");
    }

    @Test
    void dragonscaleNeverTouchesTheScar() {
        for (FieldSample f : samples.get(BiomeType.DRAGONSCALE)) {
            assertTrue(f.corruption() < 0.08, "Dragonscale at corruption " + f.corruption());
        }
        for (FieldSample f : samples.get(BiomeType.SCAR)) assertTrue(f.corruption() > 0.5, "Scar at corruption " + f.corruption());
    }

    @Test
    void climateBiomesRespectTheirClimate() {
        for (FieldSample f : samples.get(BiomeType.FROZEN_WAVE)) {
            assertTrue(f.heat() < 0.3, "Frozen Wave at heat " + f.heat());
            assertTrue(f.continentalness() < 0.16, "Frozen Wave inland at continentalness " + f.continentalness());
        }
        assertTrue(mean(BiomeType.CANOPY_CATHEDRAL, FieldSample::heat) > 0.6, "Canopy Cathedral is not warm");
        assertTrue(mean(BiomeType.CANOPY_CATHEDRAL, FieldSample::moisture) > 0.6, "Canopy Cathedral is not wet");
        assertTrue(mean(BiomeType.LANTERN_MARSH, FieldSample::moisture) > 0.75, "Lantern Marsh is not wet");
        assertTrue(mean(BiomeType.SPINE, FieldSample::uplift) > 150, "Spine is not on the ranges");
    }

    @Test
    void neighbouringBiomesBlendWithoutSeams() {
        TerrainEngine engine = new TerrainEngine(SEED);
        int boundaries = 0;
        for (int z = -8192; z < 8192; z += 257) {
            TerrainEngine.Column prev = engine.column(-8192, z);
            for (int x = -8191; x < 8192; x++) {
                TerrainEngine.Column c = engine.column(x, z);
                // Land biomes must blend; coasts may be cliffs.
                if (c.biome() != prev.biome() && c.biome() != BiomeType.OCEAN && prev.biome() != BiomeType.OCEAN) {
                    boundaries++;
                    int step = Math.abs(c.surfaceY() - prev.surfaceY());
                    assertTrue(step <= 8, "seam of " + step + " blocks at " + x + ", " + z + " between " + prev.biome() + " and " + c.biome());
                }
                prev = c;
            }
        }
        assertTrue(boundaries > 50, "too few biome boundaries crossed to judge: " + boundaries);
    }

    @Test
    void biomeIdsRoundTrip() {
        for (BiomeType b : BiomeType.VALUES) assertEquals(b, BiomeType.byId(b.id()));
    }

    private static double mean(BiomeType b, java.util.function.ToDoubleFunction<FieldSample> field) {
        return samples.get(b).stream().mapToDouble(field).average().orElseThrow();
    }
}
