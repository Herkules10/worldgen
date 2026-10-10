package com.herkules10.fantasyworldgen.engine.variant;

import java.util.List;
import java.util.function.Predicate;

import com.herkules10.fantasyworldgen.engine.biome.BiomeType;
import com.herkules10.fantasyworldgen.engine.biome.CuratedPalette;

/**
 * A biome as a family of variants: its options on every axis, how large one region of it is, and how its legendary
 * variant is found.
 *
 * @param regionSpacing  mean distance in blocks between the seed points that cut this biome into regions; about the
 *                       size of one natural unit (a range, a creature, a wound)
 * @param legendSpacing  side in blocks of the squares that each hold at most one legendary region of this family
 * @param contextLegend  if non-null, a region is legendary exactly when its context passes this test (the Spine's
 *                       legendary peak already exists in the ranges); if null, legendary regions are placed at
 *                       world-scale sites
 * @param defaultPalette palette for columns of this biome whose region lookup misses (a dithered lookup at the very edge
 *                       of a sliver); null for families without curated palettes yet
 * @param nouns          fallback nouns for place names
 * @param adjectives     fallback adjectives for place names
 */
public record Family(BiomeType biome, int regionSpacing, int legendSpacing, List<Trait> traits,
                     Predicate<RegionContext> contextLegend, CuratedPalette defaultPalette, List<String> nouns,
                     List<String> adjectives) {
    public List<Trait> traits(Axis axis, Rarity rarity) {
        return traits.stream().filter(t -> t.axis() == axis && t.rarity() == rarity).toList();
    }

    public List<Trait> traits(Rarity rarity) {
        return traits.stream().filter(t -> t.rarity() == rarity).toList();
    }

    public boolean hasCuratedPalettes() {
        return defaultPalette != null;
    }

    /** True if legendary regions of this family are chosen by world-scale site rather than by context. */
    public boolean usesLegendSites() {
        return contextLegend == null && !traits(Rarity.LEGENDARY).isEmpty();
    }
}
