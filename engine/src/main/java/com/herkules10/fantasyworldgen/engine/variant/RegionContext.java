package com.herkules10.fantasyworldgen.engine.variant;

import static com.herkules10.fantasyworldgen.engine.Maths.smoothstep;

import java.util.ArrayList;
import java.util.List;

import com.herkules10.fantasyworldgen.engine.biome.BiomeType;

/**
 * What the world says about a region before chance gets a say: its shared fields, averaged over the region, and which
 * biomes it borders. Variant rolls read this first ("cold regions get icy variants").
 *
 * @param heat            mean heat in [0, 1]
 * @param moisture        mean moisture in [0, 1]
 * @param corruption      mean corruption in [0, 1]
 * @param maxCorruption   highest corruption anywhere in the region
 * @param uplift          mean uplift of the ranges, in blocks
 * @param maxUplift       highest uplift in the region, in blocks
 * @param continentalness mean continentalness
 * @param areaBlocks      area in square blocks
 * @param neighbours      share of the region's border shared with each biome, indexed by ordinal (sums to 1, or all 0
 *                        for a region without a border)
 */
public record RegionContext(double heat, double moisture, double corruption, double maxCorruption, double uplift,
                            double maxUplift, double continentalness, long areaBlocks, double[] neighbours) {
    /** 1 for clearly cold regions, 0 for temperate and warm ones. */
    public double cold() {
        return smoothstep(0.38, 0.15, heat);
    }

    public double hot() {
        return smoothstep(0.6, 0.8, heat);
    }

    public double dry() {
        return smoothstep(0.42, 0.22, moisture);
    }

    public double wet() {
        return smoothstep(0.6, 0.8, moisture);
    }

    /** 1 where a Scar's corruption reaches noticeably into the region. */
    public double blight() {
        return smoothstep(0.02, 0.25, maxCorruption);
    }

    /** Share of the border shared with {@code biome}. */
    public double neighbour(BiomeType biome) {
        return neighbours[biome.ordinal()];
    }

    /** 1 for regions with a long sea coast. */
    public double coast() {
        return smoothstep(0.08, 0.3, neighbour(BiomeType.OCEAN));
    }

    /** Readable summary of the context, for previews and JSON. */
    public List<String> tags() {
        List<String> tags = new ArrayList<>();
        for (Tag t : Tag.VALUES) if (t.applies(this)) tags.add(t.id);
        for (BiomeType b : BiomeType.VALUES) if (neighbour(b) >= 0.15) tags.add("borders_" + b.id());
        return tags;
    }

    /** Context traits that also colour place names. */
    enum Tag {
        BLIGHTED("blighted", "Ashen|Blighted|Withered|Hollow|Sickened"),
        FROZEN("frozen", "Frost|Rime|Winter|Hoarfrost|Frozen"),
        SCORCHED("scorched", "Sunburnt|Searing|Blazing|Sunscorched"),
        ARID("arid", "Thirsting|Dust|Barren|Parched"),
        LUSH("lush", "Verdant|Rain|Misted|Dripping"),
        COASTAL("coastal", "Salt|Tide|Sea|Gull");

        static final Tag[] VALUES = values();

        final String id;
        final List<String> adjectives;

        Tag(String id, String adjectives) {
            this.id = id;
            this.adjectives = List.of(adjectives.split("\\|"));
        }

        boolean applies(RegionContext c) {
            return switch (this) {
                case BLIGHTED -> c.blight() > 0.5;
                case FROZEN -> c.cold() > 0.5;
                case SCORCHED -> c.hot() > 0.5;
                case ARID -> c.dry() > 0.5;
                case LUSH -> c.wet() > 0.5;
                case COASTAL -> c.coast() > 0.5;
            };
        }
    }
}
