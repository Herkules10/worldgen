package com.herkules10.fantasyworldgen.engine.variant;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.herkules10.fantasyworldgen.engine.biome.BiomeType;
import com.herkules10.fantasyworldgen.engine.noise.Rng;

/**
 * Place names from a region's traits: "The Ashen Coil", "Verdigris Hide", "The Horns of Kaldrun", and for legendary
 * regions "Ostmir, the Cloudpiercer". Adjectives come from the context first (a blighted region is "Ashen" more often
 * than not), then from the palette, scale and state; nouns from the form, else from the family.
 */
public final class PlaceNames {
    private static final String[] ONSETS = {"Kal", "Mor", "Ves", "Thar", "Ul", "Bra", "Ith", "Gor", "Sel", "Ny", "Aer", "Dun",
            "Hal", "Kor", "Ost", "Vael", "Zir", "Am", "Er", "Fen", "Gal", "Rhun", "Tir", "Syl", "Orm", "Cal", "Dra", "Hel"};
    private static final String[] VOWELS = {"a", "e", "i", "o", "u", "ae", "y", "ei", "au"};
    private static final String[] CODAS = {"drun", "mir", "goth", "wen", "thas", "ros", "dor", "ith", "vane", "mar", "holt",
            "gard", "esh", "orn", "al", "ys", "ruk", "dell", "nor", "eth", "ion", "rath", "vel", "um"};

    private PlaceNames() {}

    static String name(Family family, Map<Axis, Trait> traits, Rarity tier, RegionContext context, long seed) {
        Rng rng = new Rng(seed);
        String proper = properName(rng);
        if (tier == Rarity.LEGENDARY) {
            Trait legend = traits.values().stream().filter(t -> t.rarity() == Rarity.LEGENDARY).findFirst().orElseThrow();
            return proper + ", the " + pickWord(legend.nouns(), rng.next());
        }
        String adjective = adjective(family, traits, context, rng);
        String noun = noun(family, traits, rng);
        if (adjective.equals(noun)) adjective = pickWord(family.adjectives(), rng.next()); // no "Sea Sea"
        double pattern = rng.next();
        if (pattern < 0.55) return "The " + adjective + " " + noun;
        if (pattern < 0.8) return "The " + noun + " of " + proper;
        return adjective + " " + noun;
    }

    /** Two or three invented syllables, e.g. "Kaldrun", "Vaelimar". */
    static String properName(Rng rng) {
        StringBuilder s = new StringBuilder(ONSETS[rng.nextInt(ONSETS.length)]);
        if (rng.next() < 0.4) s.append(VOWELS[rng.nextInt(VOWELS.length)]).append(ONSETS[rng.nextInt(ONSETS.length)].toLowerCase(Locale.ROOT));
        String coda = CODAS[rng.nextInt(CODAS.length)];
        // Avoid three consonants or two vowels piling up at the join.
        if (isVowel(s.charAt(s.length() - 1)) == isVowel(coda.charAt(0))) s.append(isVowel(coda.charAt(0)) ? "r" : VOWELS[rng.nextInt(3)]);
        return s.append(coda).toString();
    }

    private static String adjective(Family family, Map<Axis, Trait> traits, RegionContext context, Rng rng) {
        List<List<String>> sources = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        for (RegionContext.Tag tag : RegionContext.Tag.VALUES) {
            // A sea always borders the sea; that says nothing about it.
            if (tag == RegionContext.Tag.COASTAL && family.biome() == BiomeType.OCEAN) continue;
            double weight = switch (tag) {
                case BLIGHTED -> 4;
                case COASTAL -> 0.6; // many regions touch the sea; too common to name most of them after it
                default -> 1.5;
            };
            if (tag.applies(context)) add(sources, weights, tag.adjectives, weight);
        }
        for (Trait t : traits.values()) add(sources, weights, t.adjectives(), t.axis() == Axis.PALETTE ? 2 : 1);
        add(sources, weights, family.adjectives(), 0.5);
        double total = weights.stream().mapToDouble(Double::doubleValue).sum();
        double target = rng.next() * total;
        double wordRoll = rng.next();
        for (int i = 0; i < sources.size(); i++) {
            target -= weights.get(i);
            if (target < 0) return pickWord(sources.get(i), wordRoll);
        }
        return pickWord(sources.get(sources.size() - 1), wordRoll);
    }

    private static String noun(Family family, Map<Axis, Trait> traits, Rng rng) {
        Trait form = traits.get(Axis.FORM);
        double sourceRoll = rng.next(), wordRoll = rng.next();
        if (form != null && !form.nouns().isEmpty() && sourceRoll < 0.7) return pickWord(form.nouns(), wordRoll);
        return pickWord(family.nouns(), wordRoll);
    }

    private static void add(List<List<String>> sources, List<Double> weights, List<String> words, double weight) {
        if (words.isEmpty()) return;
        sources.add(words);
        weights.add(weight);
    }

    private static String pickWord(List<String> words, double roll) {
        return words.get(Math.min(words.size() - 1, (int) (roll * words.size())));
    }

    private static boolean isVowel(char c) {
        return "aeiouy".indexOf(c) >= 0;
    }
}
