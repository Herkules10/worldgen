package com.herkules10.fantasyworldgen.engine.variant;

import static com.herkules10.fantasyworldgen.engine.Maths.band;
import static com.herkules10.fantasyworldgen.engine.Maths.smoothstep;
import static com.herkules10.fantasyworldgen.engine.variant.Axis.FORM;
import static com.herkules10.fantasyworldgen.engine.variant.Axis.PALETTE;
import static com.herkules10.fantasyworldgen.engine.variant.Axis.SCALE;
import static com.herkules10.fantasyworldgen.engine.variant.Axis.STATE;
import static com.herkules10.fantasyworldgen.engine.variant.Rarity.COMMON;
import static com.herkules10.fantasyworldgen.engine.variant.Rarity.LEGENDARY;
import static com.herkules10.fantasyworldgen.engine.variant.Rarity.RARE;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

import com.herkules10.fantasyworldgen.engine.WorldShape;
import com.herkules10.fantasyworldgen.engine.biome.BiomeType;
import com.herkules10.fantasyworldgen.engine.biome.CuratedPalette;

/**
 * The variant tables of every biome family, transcribed from the "Variation" lists of Artistic_Direction.md.
 * Affinities encode "context first": an option the world calls for (a frost hide beside the Spine, a corrupted marsh
 * near the Scar) gets a large weight; options without a context reason share a small, even weight.
 * <p>
 * Only the Spine has curated block palettes so far; the other families' palette options are named but still use the
 * placeholder blocks in {@link com.herkules10.fantasyworldgen.engine.biome.Palettes}.
 */
public final class Families {
    /**
     * Uplift at which a Spine peak breaks through the cloud layer (crest height is at most about 1.1 x uplift above
     * the sea, plus the Spine's 10-block base). Those peaks are the Spine's legendary variant.
     */
    public static final double SPINE_CLOUD_UPLIFT = (WorldShape.CLOUD_LEVEL - WorldShape.SEA_LEVEL - 10) / 1.1;

    /**
     * Each family has at most one legendary region per square of this side: about one per family in the area a
     * player explores in a long-lived world. Families that are rare themselves use larger squares.
     */
    static final int DEFAULT_LEGEND_SPACING = 24_576;

    private static final Family[] BY_BIOME = new Family[BiomeType.VALUES.length];

    static {
        register(new Builder(BiomeType.OCEAN, 4096)
                .names("Sea|Deep|Sound|Gulf|Expanse|Reach|Waters|Strait|Bight|Shallows|Main",
                        "Grey|Restless|Silver|Cold|Long|Whispering|Pale|Dark|Wide|Wandering|Quiet|Iron|Sunken"));
        register(new Builder(BiomeType.WILDS, 2048)
                .names("Reach|Downs|Vale|Moors|Fells|Weald|Hollows|Marches|Heath|Leas|Barrows|Wolds|Meads",
                        "Green|Quiet|Wandering|Grey|Long|Fair|Low|Windy|Golden|Old|Lonely|Bright|Hidden"));

        register(new Builder(BiomeType.SPINE, 4096)
                .names("Range|Peaks|Spine|Heights|Mountains", "High|Old|Great")
                .contextLegend(c -> c.maxUplift() >= SPINE_CLOUD_UPLIFT)
                .palette(COMMON, CuratedPalette.SPINE_GREY_GRANITE, c -> 1.0, "Grey|Granite|Iron|Slate")
                .palette(COMMON, CuratedPalette.SPINE_RED_ROCK,
                        c -> 0.25 + 2 * c.dry() + 1.5 * c.hot() + 4 * c.neighbour(BiomeType.GLASS_WASTE), "Red|Rust|Ochre|Ember")
                .palette(COMMON, CuratedPalette.SPINE_BLACK_VOLCANIC, c -> 0.25 + 4 * c.blight() + 0.5 * c.hot(),
                        "Black|Cinder|Obsidian|Smoke")
                .palette(RARE, CuratedPalette.SPINE_WHITE_MARBLE, c -> 1.0, "White|Pale|Marble|Ivory")
                .trait(FORM, COMMON, "horn_peaks", "Jagged horn peaks", c -> 0.8 + c.cold(), "Jagged|Horned", "Horns|Teeth|Fangs|Pinnacles")
                .trait(FORM, COMMON, "table_mountains", "Flat-topped table mountains", c -> 0.4 + 1.5 * c.dry(), "Flat", "Tables|Anvils|Battlements|Altars")
                .trait(FORM, COMMON, "massif", "A single huge massif", c -> 0.3 + 2 * smoothstep(550, 800, c.maxUplift()), "Mighty",
                        "Throne|Bulwark|Crown|Citadel")
                .trait(FORM, RARE, "needle_spires", "A forest of needle spires", c -> 1.0, "Needled", "Needles|Spires|Thorns|Lances")
                .trait(SCALE, COMMON, "low_range", "Low range", c -> 0.1 + 3 * smoothstep(450, 300, c.maxUplift()), "Low|Lesser", "")
                .trait(SCALE, COMMON, "high_range", "High range", c -> 0.3 + 2 * band(c.maxUplift(), 300, 450, 600, 750), "High|Great", "")
                .trait(SCALE, COMMON, "towering_range", "Towering range", c -> 0.1 + 3 * smoothstep(600, 850, c.maxUplift()),
                        "Towering|Titan|Sky", "")
                .trait(SCALE, LEGENDARY, "cloudpiercer", "A peak above the clouds", c -> 1.0, "", "Cloudpiercer|Skyreacher|Stormcrown")
                .trait(STATE, COMMON, "glaciated", "Heavily glaciated", c -> 0.2 + 3 * c.cold(), "Frozen|Glacier|Rime|Ice", "")
                .trait(STATE, COMMON, "bare", "Dry and bare", c -> 0.2 + 3 * c.dry(), "Bare|Bleak|Barren|Windswept", "")
                .trait(STATE, COMMON, "young", "Young and sharp", c -> 0.6, "Sharp|Broken|Raw", "")
                .trait(STATE, COMMON, "old", "Old and rounded", c -> 0.6, "Old|Worn|Elder|Weathered", ""));

        register(new Builder(BiomeType.GLASS_WASTE, 3072)
                .names("Waste|Sands|Glass|Expanse|Barrens", "Silent|Burning|Endless")
                .trait(PALETTE, COMMON, "amber_glass", "Amber glass over gold sand", c -> 1.0, "Amber|Golden|Honeyed", "")
                .trait(PALETTE, COMMON, "emerald_glass", "Emerald glass over white sand", c -> 0.8, "Emerald|Green", "")
                .trait(PALETTE, RARE, "violet_glass", "Violet glass", c -> 1.0, "Violet|Twilight", "")
                .trait(PALETTE, RARE, "blood_glass", "Blood-red glass over red sand", c -> 1 + 3 * c.blight(), "Blood|Crimson", "")
                .trait(FORM, COMMON, "crescent_dunes", "Crescent dunes", c -> 1.0, "", "Crescents|Dunes|Sickles")
                .trait(FORM, COMMON, "star_dunes", "Star dunes", c -> 0.7, "", "Stars|Drifts")
                .trait(FORM, COMMON, "parallel_ridges", "Long parallel ridges", c -> 0.7, "", "Ridges|Furrows|Combs")
                .trait(FORM, RARE, "dune_sea", "A sea of dunes taller than hills", c -> 1.0, "", "Sea|Swell|Breakers")
                .trait(FORM, LEGENDARY, "impact_crater", "The impact crater", c -> 1.0, "", "Sunfall|Crater|Furnace")
                .trait(STATE, COMMON, "buried", "A few glints in the troughs", c -> 1.0, "Buried|Glinting", "")
                .trait(STATE, COMMON, "glass_plains", "Whole glass plains uncovered", c -> 0.4 + c.dry(), "Glass|Shining|Glazed", ""));

        register(new Builder(BiomeType.DRAGONSCALE, 4096)
                .names("Hide|Coil|Scales|Sleeper|Body", "Sleeping|Silent|Dreaming")
                .trait(PALETTE, COMMON, "ash", "Ash hide", c -> 0.4 + 2 * c.hot(), "Ashen|Cinder|Ash", "")
                .trait(PALETTE, COMMON, "verdigris", "Verdigris hide", c -> 0.5 + c.wet(), "Verdigris|Copper", "")
                .trait(PALETTE, COMMON, "gilded", "Gilded hide", c -> 0.2 + 4 * c.neighbour(BiomeType.GLASS_WASTE), "Gilded|Golden|Sunlit", "")
                .trait(PALETTE, COMMON, "frost", "Frost hide", c -> 0.1 + 3 * c.cold() + 4 * c.neighbour(BiomeType.SPINE), "Frost|Rime|White", "")
                .trait(PALETTE, RARE, "amethyst", "Amethyst hide", c -> 1.0, "Amethyst|Violet|Singing", "")
                .trait(PALETTE, COMMON, "abyssal", "Abyssal hide", c -> 0.05 + 4 * c.coast(), "Abyssal|Drowned|Deep", "")
                .trait(PALETTE, COMMON, "bone", "Bone hide", c -> 0.3 + c.dry(), "Bone|Bleached|Pale", "")
                .trait(FORM, COMMON, "wyrm", "Wyrm", c -> 1.0, "Coiled", "Coil|Ring|Serpent")
                .trait(FORM, COMMON, "drake", "Drake", c -> 1.0, "Winged", "Wings|Drake")
                .trait(FORM, COMMON, "leviathan", "Leviathan", c -> 0.15 + 3 * c.coast(), "Finned", "Leviathan|Back|Fins")
                .trait(FORM, RARE, "hydra", "Hydra", c -> 1.0, "Many-Headed", "Necks|Hydra|Heads")
                .trait(FORM, LEGENDARY, "elder", "Elder", c -> 1.0, "", "Elder|Horned One|Firstborn")
                .trait(SCALE, COMMON, "wyrmling", "A wyrmling covering a few hills", c -> 1.0, "Little|Lesser", "")
                .trait(SCALE, COMMON, "great_beast", "A great beast spanning the region", c -> 0.8, "Great|Vast", "")
                .trait(STATE, COMMON, "fresh", "Freshly fallen and sharp", c -> 0.8, "Fallen|Sharp", "")
                .trait(STATE, COMMON, "fossilized", "Fossilized and weathered", c -> 0.6 + c.dry(), "Fossil|Petrified|Stone", "")
                .trait(STATE, COMMON, "overgrown", "Overgrown by forest", c -> 0.3 + 2 * c.wet(), "Mossy|Overgrown|Green", "")
                .trait(STATE, RARE, "crystallized", "Crystallized", c -> 1.0, "Crystal|Glass", ""));

        register(new Builder(BiomeType.SCAR, 8192).legendSpacing(65_536)
                .names("Scar|Wound|Rift|Breach", "Open|Burning|Hungry")
                .trait(PALETTE, COMMON, "crimson", "Crimson and lava", c -> 1.0, "Crimson|Bleeding|Red", "")
                .trait(PALETTE, COMMON, "warped", "Warped teal and soul fire", c -> 1.0, "Teal|Soulfire|Warped", "")
                .trait(PALETTE, RARE, "void", "Void-black, purple and obsidian", c -> 1.0, "Void|Black|Starless", "")
                .trait(FORM, COMMON, "maw", "One deep maw", c -> 1.0, "", "Maw|Throat|Pit")
                .trait(FORM, COMMON, "rift", "A long straight rift", c -> 1.0, "", "Cut|Rift|Gash")
                .trait(FORM, COMMON, "ring", "A ring of wounds around a raised centre", c -> 0.7, "", "Ring|Circle|Wounds")
                .trait(STATE, COMMON, "spreading", "Fresh and spreading", c -> 1.0, "Weeping|Spreading|Raw", "")
                .trait(STATE, COMMON, "scabbed", "Old and scabbed over", c -> 0.7, "Scabbed|Old|Closing", "")
                .trait(STATE, LEGENDARY, "sealed", "Sealed, a glassy silent crater", c -> 1.0, "", "Seal|Silence|Closed Wound"));

        register(new Builder(BiomeType.STILLED_COLOSSI, 3072).legendSpacing(49_152)
                .names("Colossi|Giants|Lake|Mirror", "Stilled|Silent|Sleeping")
                .trait(PALETTE, COMMON, "brass", "Brass and copper", c -> 1.0, "Brass|Copper|Verdigris", "")
                .trait(PALETTE, COMMON, "white_gold", "White stone and gold", c -> 1.0, "White|Gilded", "")
                .trait(PALETTE, COMMON, "dark_iron", "Dark iron and blackstone", c -> 0.8 + 2 * c.blight(), "Iron|Dark", "")
                .trait(PALETTE, RARE, "crystal", "Crystal-bodied", c -> 1.0, "Crystal|Glass|Amethyst", "")
                .trait(FORM, COMMON, "humanoid", "Humanoid giants", c -> 1.0, "", "Giants|Sentinels|Watchers")
                .trait(FORM, COMMON, "walkers", "Four-legged walkers", c -> 0.7, "", "Walkers|Striders")
                .trait(FORM, RARE, "serpentine", "Serpentine constructs", c -> 1.0, "", "Coils|Serpents")
                .trait(FORM, COMMON, "engines", "Towering engines", c -> 0.6, "", "Engines|Towers")
                .trait(SCALE, COMMON, "few_islands", "A few huge islands", c -> 1.0, "Lone|Few", "")
                .trait(SCALE, COMMON, "archipelago", "A whole sky archipelago", c -> 1.0, "Scattered|Many", "")
                .trait(STATE, COMMON, "intact", "Nearly intact and gleaming", c -> 0.7, "Gleaming|Bright", "")
                .trait(STATE, COMMON, "worn", "Worn to their posture", c -> 1.0, "Worn|Mossbound", "")
                .trait(STATE, LEGENDARY, "pulsing_core", "A core that still pulses", c -> 1.0, "", "Heart|Waking One|Pulse"));

        register(new Builder(BiomeType.CANOPY_CATHEDRAL, 2048)
                .names("Cathedral|Wood|Canopy|Forest|Grove", "Deep|Green|Hushed")
                .trait(PALETTE, COMMON, "evergreen", "Evergreen giants", c -> 1.0, "Evergreen|Dark|Pine", "")
                .trait(PALETTE, COMMON, "golden_autumn", "Golden autumn giants", c -> 0.7, "Golden|Amber|Autumn", "")
                .trait(PALETTE, RARE, "pale", "Pale white-barked giants", c -> 1.0, "Pale|White|Ghost", "")
                .trait(PALETTE, COMMON, "crimson", "Crimson-leaved giants", c -> 6 * c.blight(), "Crimson|Bloodleaf", "")
                .trait(FORM, COMMON, "columns", "Straight columns", c -> 1.0, "", "Columns|Pillars|Nave")
                .trait(FORM, COMMON, "banyan", "Twisted banyan trunks", c -> 0.8, "", "Roots|Tangles|Arches")
                .trait(FORM, COMMON, "many_crowns", "Trees with several crowns", c -> 0.6, "", "Crowns|Spires")
                .trait(SCALE, LEGENDARY, "world_tree", "The world tree", c -> 1.0, "", "World Tree|Eldest|Mother of Trees")
                .trait(STATE, COMMON, "healthy", "Healthy and dense", c -> 1.0, "Deep|Green", "")
                .trait(STATE, COMMON, "burned", "Burned, new green growth", c -> 0.15 + c.dry() + 2 * c.blight(), "Burned|Charred", "")
                .trait(STATE, RARE, "flooded", "Flooded, trunks in still water", c -> 1 + c.wet(), "Drowned|Flooded", ""));

        register(new Builder(BiomeType.FROZEN_WAVE, 2048)
                .names("Wave|Breaker|Crest|Wall", "Frozen|Still|Halted")
                .trait(PALETTE, COMMON, "glacier_blue", "Glacier blue", c -> 1.0, "Blue|Glacier", "")
                .trait(PALETTE, RARE, "pale_green", "Pale green ice", c -> 1.0, "Jade|Green", "")
                .trait(PALETTE, COMMON, "storm", "Dark stormy ice", c -> 0.6, "Storm|Grey|Dark", "")
                .trait(FORM, COMMON, "single_curl", "A single towering curl", c -> 1.0, "", "Curl|Wave|Crest")
                .trait(FORM, COMMON, "wave_train", "A row of smaller waves", c -> 0.8, "", "Waves|Breakers|Rollers")
                .trait(FORM, COMMON, "broken", "A broken, half-collapsed wave", c -> 0.5, "", "Collapse|Fallen Wave")
                .trait(FORM, LEGENDARY, "open_sea_wave", "A wave frozen far out at sea", c -> 1.0, "", "Lone Wave|Sea Crown|Isle")
                .trait(STATE, COMMON, "sharp", "Sharp and fresh", c -> 1.0, "Still|Sharp", "")
                .trait(STATE, COMMON, "melting", "Melting, waterfalls on its face", c -> 0.2 + 2 * smoothstep(0.15, 0.3, c.heat()),
                        "Weeping|Melting", ""));

        register(new Builder(BiomeType.THOUSAND_STAIRS, 2048)
                .names("Stairs|Falls|Terraces|Steps", "Thousand|Singing|White")
                .trait(PALETTE, COMMON, "white_travertine", "White travertine, turquoise water", c -> 1.0, "White|Turquoise", "")
                .trait(PALETTE, COMMON, "rust", "Rust-red iron terraces", c -> 0.4 + c.hot(), "Rust|Red|Iron", "")
                .trait(PALETTE, COMMON, "mossy_gold", "Green-gold mossy terraces", c -> 0.4 + c.wet(), "Mossy|Golden|Green", "")
                .trait(FORM, COMMON, "narrow", "A tall narrow staircase", c -> 1.0, "", "Stair|Ladder|Steps")
                .trait(FORM, COMMON, "amphitheatre", "A wide amphitheatre", c -> 1.0, "", "Amphitheatre|Bowl|Tiers")
                .trait(FORM, RARE, "sinkhole_spiral", "Terraces spiralling into a sinkhole", c -> 1.0, "", "Spiral|Well|Sinkhole")
                .trait(STATE, COMMON, "overflowing", "Active and overflowing", c -> 0.5 + c.wet(), "Brimming|Falling|Singing", "")
                .trait(STATE, COMMON, "dried", "Dried out, stained rims", c -> 0.2 + 2 * c.dry(), "Dry|Silent|Empty", "")
                .trait(STATE, LEGENDARY, "hot_spring", "Fed by a glowing hot spring", c -> 1.0, "", "Cauldron|Steaming Stairs|Springs"));

        register(new Builder(BiomeType.LANTERN_MARSH, 2048)
                .names("Marsh|Fen|Mire|Lanterns", "Glowing|Warm|Gentle")
                .trait(PALETTE, COMMON, "warm_gold", "Warm gold glow", c -> 1.0, "Golden|Lantern|Amber", "")
                .trait(PALETTE, COMMON, "blue_green", "Cool blue-green glow", c -> 1.0, "Glimmer|Teal|Moonlit", "")
                .trait(PALETTE, RARE, "pink_violet", "Pink-violet glow", c -> 1.0, "Rose|Violet", "")
                .trait(PALETTE, COMMON, "mixed", "Mixed glow", c -> 0.6, "Many-Lit|Motley", "")
                .trait(FORM, COMMON, "lagoons", "Open lagoons with giant pads", c -> 1.0, "", "Lagoons|Pools|Mere")
                .trait(FORM, COMMON, "root_forest", "Dense root forest", c -> 1.0, "", "Roots|Halls|Tangle")
                .trait(FORM, COMMON, "flooded_plain", "Flooded plain with tree islands", c -> 0.8, "", "Flats|Water-Meadows")
                .trait(SCALE, LEGENDARY, "great_bloom", "A bloom the size of a hill", c -> 1.0, "", "Great Bloom|Night Flower|Great Lantern")
                .trait(STATE, COMMON, "healthy", "Healthy", c -> 1.0, "Glowing|Bright", "")
                .trait(STATE, COMMON, "corrupted", "Corrupted, glow turned red", c -> 6 * c.blight(), "Sickly|Red-Lit|Fevered", ""));

        for (BiomeType b : BiomeType.VALUES) {
            if (BY_BIOME[b.ordinal()] == null) throw new IllegalStateException("no variant family for " + b);
        }
    }

    private Families() {}

    public static Family of(BiomeType biome) {
        return BY_BIOME[biome.ordinal()];
    }

    private static void register(Builder b) {
        BY_BIOME[b.biome.ordinal()] = new Family(b.biome, b.spacing, b.legendSpacing, List.copyOf(b.traits), b.contextLegend,
                b.defaultPalette, b.nouns, b.adjectives);
    }

    private static List<String> words(String s) {
        return s.isEmpty() ? List.of() : List.of(s.split("\\|"));
    }

    private static final class Builder {
        final BiomeType biome;
        final int spacing;
        int legendSpacing = DEFAULT_LEGEND_SPACING;
        final List<Trait> traits = new ArrayList<>();
        Predicate<RegionContext> contextLegend;
        CuratedPalette defaultPalette;
        List<String> nouns = List.of();
        List<String> adjectives = List.of();

        Builder(BiomeType biome, int spacing) {
            this.biome = biome;
            this.spacing = spacing;
        }

        Builder names(String nouns, String adjectives) {
            this.nouns = words(nouns);
            this.adjectives = words(adjectives);
            return this;
        }

        Builder legendSpacing(int blocks) {
            this.legendSpacing = blocks;
            return this;
        }

        Builder contextLegend(Predicate<RegionContext> test) {
            this.contextLegend = test;
            return this;
        }

        Builder trait(Axis axis, Rarity rarity, String id, String label, ToDoubleFunction<RegionContext> affinity,
                      String adjectives, String nouns) {
            traits.add(new Trait(axis, id, label, rarity, affinity, words(adjectives), words(nouns), null));
            return this;
        }

        /** A curated palette option; the first one added is the family's default. */
        Builder palette(Rarity rarity, CuratedPalette palette, ToDoubleFunction<RegionContext> affinity, String adjectives) {
            if (defaultPalette == null) defaultPalette = palette;
            traits.add(new Trait(PALETTE, palette.id(), palette.label(), rarity, affinity, words(adjectives), List.of(), palette));
            return this;
        }
    }
}
