package com.herkules10.fantasyworldgen.engine.variant;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import com.herkules10.fantasyworldgen.engine.biome.CuratedPalette;

/**
 * The rolled traits of one region and the place name they produced.
 *
 * @param rarity the region's tier: the rarest of its traits
 * @param traits one trait per axis the family has options on (axes without options are absent)
 */
public record Variant(Rarity rarity, Map<Axis, Trait> traits, String name) {
    public Variant {
        traits = Collections.unmodifiableMap(new EnumMap<>(traits));
    }

    /** The trait on {@code axis}, or null if the family has no options there. */
    public Trait trait(Axis axis) {
        return traits.get(axis);
    }

    /** True if any of the region's traits has this id. */
    public boolean has(String traitId) {
        for (Trait t : traits.values()) if (t.id().equals(traitId)) return true;
        return false;
    }

    /** The curated blocks of the palette trait, or null if the family has none yet. */
    public CuratedPalette palette() {
        Trait t = traits.get(Axis.PALETTE);
        return t == null ? null : t.palette();
    }
}
