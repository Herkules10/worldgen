package com.herkules10.fantasyworldgen.engine.variant;

import java.util.Locale;

/**
 * How often a variant appears. Most regions are common; about one in {@link VariantRoller#RARE_ODDS} is rare; legendary
 * variants are tied to world-scale sites or to exceptional context, so there are only a handful per world.
 */
public enum Rarity {
    COMMON,
    RARE,
    LEGENDARY;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
