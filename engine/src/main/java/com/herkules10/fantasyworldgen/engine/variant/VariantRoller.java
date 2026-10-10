package com.herkules10.fantasyworldgen.engine.variant;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.herkules10.fantasyworldgen.engine.noise.Hashing;
import com.herkules10.fantasyworldgen.engine.noise.Rng;

/**
 * Rolls a region's variant: first its tier, then one trait per axis. Context comes first through the traits'
 * affinities; the seeded roll only chooses among what the context allows.
 * <ul>
 * <li><b>Legendary</b> if the family's context test passes, or if the region holds the family's world-scale legend
 * site. The legendary trait takes its axis.</li>
 * <li><b>Rare</b> with probability 1 / {@link #RARE_ODDS}: one rare trait takes its axis.</li>
 * <li>Every other axis gets a common trait.</li>
 * </ul>
 * The number of random draws is fixed, so changing one family's tables never shifts another axis's roll.
 */
public final class VariantRoller {
    /** One region in this many is rare. */
    public static final int RARE_ODDS = 25;
    private static final long NAME_SALT = 0x6E616D6573L; // "names"

    private VariantRoller() {}

    /**
     * @param seed          the region's own seed (from the world seed and its position)
     * @param holdsLegendSite true if the region contains its family's legend site; ignored for families whose legendary
     *                        is decided by context
     */
    public static Variant roll(Family family, RegionContext context, long seed, boolean holdsLegendSite) {
        Rng rng = new Rng(seed);
        double tierRoll = rng.next();
        double specialRoll = rng.next();

        boolean legendary = family.contextLegend() != null ? family.contextLegend().test(context) : holdsLegendSite;
        Rarity tier = legendary && !family.traits(Rarity.LEGENDARY).isEmpty() ? Rarity.LEGENDARY
                : tierRoll * RARE_ODDS < 1 && !family.traits(Rarity.RARE).isEmpty() ? Rarity.RARE
                : Rarity.COMMON;
        Trait special = tier == Rarity.COMMON ? null : pick(family.traits(tier), context, specialRoll);

        Map<Axis, Trait> traits = new EnumMap<>(Axis.class);
        for (Axis axis : Axis.VALUES) {
            double roll = rng.next();
            Trait t = special != null && special.axis() == axis ? special : pick(family.traits(axis, Rarity.COMMON), context, roll);
            if (t != null) traits.put(axis, t);
        }
        // Names draw from their own sequence, so new name words never change which traits a region rolls.
        return new Variant(tier, traits, PlaceNames.name(family, traits, tier, context, Hashing.mix(seed ^ NAME_SALT)));
    }

    /** Weighted pick by affinity; uniform if the context rules out every option. Null for an empty list. */
    static Trait pick(List<Trait> options, RegionContext context, double roll) {
        if (options.isEmpty()) return null;
        double total = 0;
        for (Trait t : options) total += t.weight(context);
        if (total <= 0) return options.get(Math.min(options.size() - 1, (int) (roll * options.size())));
        double target = roll * total;
        for (Trait t : options) {
            target -= t.weight(context);
            if (target < 0) return t;
        }
        return options.get(options.size() - 1);
    }
}
