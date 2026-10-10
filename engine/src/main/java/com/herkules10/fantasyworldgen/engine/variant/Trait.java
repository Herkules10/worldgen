package com.herkules10.fantasyworldgen.engine.variant;

import java.util.List;
import java.util.function.ToDoubleFunction;

import com.herkules10.fantasyworldgen.engine.biome.CuratedPalette;

/**
 * One option on one axis of a family, e.g. the Spine's "red rock" palette or its "needle spires" form.
 *
 * @param affinity   how strongly the region's context calls for this option (>= 0; 0 rules it out unless nothing else
 *                   is possible). This is how context comes first and chance only fills in the rest.
 * @param adjectives words place names may use for regions with this trait ("Ashen", "Red")
 * @param nouns      words place names may use as the noun ("Coil", "Horns"); for legendary traits, the epithet
 * @param palette    the curated blocks, for palette traits of families that have them; null otherwise
 */
public record Trait(Axis axis, String id, String label, Rarity rarity, ToDoubleFunction<RegionContext> affinity,
                    List<String> adjectives, List<String> nouns, CuratedPalette palette) {
    public double weight(RegionContext context) {
        return Math.max(0, affinity.applyAsDouble(context));
    }
}
