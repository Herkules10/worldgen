package com.herkules10.fantasyworldgen.engine.variant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import com.herkules10.fantasyworldgen.engine.biome.BiomeType;
import com.herkules10.fantasyworldgen.engine.noise.Hashing;

/**
 * The variant rules on synthetic contexts: fast enough to roll thousands of regions, so frequencies and context rules
 * can be checked tightly. {@code RegionMapTest} checks the same rules on a real world.
 */
class VariantRollerTest {
    private static final int ROLLS = 20_000;

    /** A temperate, moist, uncorrupted region bordered only by the Wilds. */
    private static RegionContext context(double heat, double moisture, double maxCorruption, double maxUplift, BiomeType neighbour,
                                         double neighbourShare) {
        double[] neighbours = new double[BiomeType.VALUES.length];
        neighbours[BiomeType.WILDS.ordinal()] = 1 - neighbourShare;
        neighbours[neighbour.ordinal()] += neighbourShare;
        return new RegionContext(heat, moisture, maxCorruption / 2, maxCorruption, maxUplift / 2, maxUplift, 0.3, 1 << 20, neighbours);
    }

    private static RegionContext neutral() {
        return context(0.5, 0.5, 0, 400, BiomeType.WILDS, 0);
    }

    /** Share of {@code ROLLS} seeded rolls of {@code biome} in {@code context} whose variant passes {@code test}. */
    private static double share(BiomeType biome, RegionContext context, Predicate<Variant> test) {
        int hits = 0;
        for (int i = 0; i < ROLLS; i++) {
            if (test.test(VariantRoller.roll(Families.of(biome), context, Hashing.hash(99, i, biome.ordinal()), false))) hits++;
        }
        return hits / (double) ROLLS;
    }

    @Test
    void sameSeedSameVariant() {
        for (BiomeType b : BiomeType.VALUES) {
            for (int i = 0; i < 200; i++) {
                long seed = Hashing.hash(7, i, 3);
                assertEquals(VariantRoller.roll(Families.of(b), neutral(), seed, false), VariantRoller.roll(Families.of(b), neutral(), seed, false));
            }
        }
    }

    @Test
    void rareVariantsAreAboutOneInTwentyFive() {
        for (BiomeType b : BiomeType.VALUES) {
            Family f = Families.of(b);
            double rare = share(b, neutral(), v -> v.rarity() == Rarity.RARE);
            if (f.traits(Rarity.RARE).isEmpty()) {
                assertEquals(0, rare, b + " has no rare traits but rolled rare variants");
            } else {
                assertTrue(rare > 0.03 && rare < 0.05, b + " rare share " + rare);
            }
            assertEquals(0, share(b, neutral(), v -> v.rarity() == Rarity.LEGENDARY), b + " rolled a legendary without a site or context");
        }
    }

    @Test
    void rareVariantsCarryExactlyOneRareTrait() {
        for (int i = 0; i < 2000; i++) {
            Variant v = VariantRoller.roll(Families.of(BiomeType.SPINE), neutral(), Hashing.hash(5, i, 0), false);
            long rareTraits = v.traits().values().stream().filter(t -> t.rarity() == Rarity.RARE).count();
            assertEquals(v.rarity() == Rarity.RARE ? 1 : 0, rareTraits, v.toString());
        }
    }

    @Test
    void legendSitesMakeLegendsExceptWhereContextDecides() {
        for (BiomeType b : BiomeType.VALUES) {
            Family f = Families.of(b);
            Variant v = VariantRoller.roll(f, neutral(), 1234, true);
            if (f.usesLegendSites()) {
                assertEquals(Rarity.LEGENDARY, v.rarity(), b.toString());
                assertTrue(v.name().contains(", the "), "legendary name " + v.name());
            } else {
                assertFalse(v.rarity() == Rarity.LEGENDARY, b + " became legendary through a site");
            }
        }
    }

    @Test
    void spinePeaksAboveTheCloudsAreLegendary() {
        RegionContext towering = context(0.2, 0.5, 0, Families.SPINE_CLOUD_UPLIFT + 1, BiomeType.WILDS, 0);
        RegionContext high = context(0.2, 0.5, 0, Families.SPINE_CLOUD_UPLIFT - 1, BiomeType.WILDS, 0);
        assertEquals(1.0, share(BiomeType.SPINE, towering, v -> v.rarity() == Rarity.LEGENDARY && v.has("cloudpiercer")));
        assertEquals(0.0, share(BiomeType.SPINE, high, v -> v.rarity() == Rarity.LEGENDARY));
    }

    @Test
    void coldSpineRangesAreGlaciated() {
        double cold = share(BiomeType.SPINE, context(0.1, 0.5, 0, 500, BiomeType.WILDS, 0), v -> v.has("glaciated"));
        double mild = share(BiomeType.SPINE, context(0.55, 0.5, 0, 500, BiomeType.WILDS, 0), v -> v.has("glaciated"));
        assertTrue(cold > 0.6, "cold ranges glaciated " + cold);
        assertTrue(mild < 0.15, "mild ranges glaciated " + mild);
    }

    @Test
    void spineRangesNearTheScarTurnVolcanicAndDesertRangesRed() {
        double blighted = share(BiomeType.SPINE, context(0.4, 0.5, 0.4, 500, BiomeType.WILDS, 0), v -> v.has("black_volcanic"));
        double clean = share(BiomeType.SPINE, neutral(), v -> v.has("black_volcanic"));
        assertTrue(blighted > 0.6 && clean < 0.2, "black volcanic near the Scar " + blighted + ", elsewhere " + clean);

        double desert = share(BiomeType.SPINE, context(0.65, 0.2, 0, 500, BiomeType.GLASS_WASTE, 0.4), v -> v.has("red_rock"));
        double wet = share(BiomeType.SPINE, context(0.4, 0.7, 0, 500, BiomeType.WILDS, 0), v -> v.has("red_rock"));
        assertTrue(desert > 0.6 && wet < 0.2, "red rock beside the desert " + desert + ", on wet ranges " + wet);
    }

    @Test
    void dragonscaleHidesFollowTheirNeighbours() {
        double frost = share(BiomeType.DRAGONSCALE, context(0.3, 0.4, 0, 100, BiomeType.SPINE, 0.4), v -> v.has("frost"));
        double gilded = share(BiomeType.DRAGONSCALE, context(0.6, 0.3, 0, 50, BiomeType.GLASS_WASTE, 0.4), v -> v.has("gilded"));
        double abyssal = share(BiomeType.DRAGONSCALE, context(0.5, 0.45, 0, 50, BiomeType.OCEAN, 0.4), v -> v.has("abyssal"));
        assertTrue(frost > 0.4, "frost hides beside the Spine " + frost);
        assertTrue(gilded > 0.4, "gilded hides beside the Glass Waste " + gilded);
        assertTrue(abyssal > 0.4, "abyssal hides on coasts " + abyssal);
    }

    @Test
    void corruptedVariantsOnlyNearTheScar() {
        assertEquals(0, share(BiomeType.LANTERN_MARSH, neutral(), v -> v.has("corrupted")));
        assertEquals(0, share(BiomeType.CANOPY_CATHEDRAL, neutral(), v -> v.has("crimson")));
        RegionContext blighted = context(0.6, 0.8, 0.5, 0, BiomeType.WILDS, 0);
        assertTrue(share(BiomeType.LANTERN_MARSH, blighted, v -> v.has("corrupted")) > 0.8);
        assertTrue(share(BiomeType.CANOPY_CATHEDRAL, blighted, v -> v.has("crimson")) > 0.6);
    }

    @Test
    void everyNamedFamilyGetsVariedNames() {
        for (BiomeType b : BiomeType.VALUES) {
            Set<String> names = new HashSet<>();
            for (int i = 0; i < 500; i++) {
                String name = VariantRoller.roll(Families.of(b), neutral(), Hashing.hash(11, i, b.ordinal()), false).name();
                assertNotNull(name);
                assertFalse(name.isBlank());
                String[] words = name.split(" ");
                for (int w = 1; w < words.length; w++) assertFalse(words[w].equals(words[w - 1]), "repeated word in " + name);
                names.add(name);
            }
            assertTrue(names.size() > 250, b + " has only " + names.size() + " distinct names in 500 regions");
        }
    }

    @Test
    void curatedPalettesOnlyWhereDefined() {
        for (BiomeType b : BiomeType.VALUES) {
            Variant v = VariantRoller.roll(Families.of(b), neutral(), 77, false);
            assertEquals(b == BiomeType.SPINE, v.palette() != null, b.toString());
        }
    }
}
