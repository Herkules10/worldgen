package com.herkules10.fantasyworldgen.engine.biome;

import java.util.Locale;

/**
 * The biome families from Artistic_Direction.md, plus the ocean and a calm filler. The mod registers one Minecraft
 * biome per entry as {@code fantasyworldgen:<id>}.
 */
public enum BiomeType {
    OCEAN("Ocean", 0x2F5D9E),
    /** Calm, unremarkable land between the set pieces. Not in the art direction yet; a placeholder to decide on. */
    WILDS("Wilds", 0x8DB360),
    SPINE("The Spine of the World", 0x8E8E9A),
    GLASS_WASTE("The Glass Waste", 0xE8C77A),
    DRAGONSCALE("The Sleeping Hides", 0xB0413E),
    SCAR("The Scar", 0x3B0A2A),
    STILLED_COLOSSI("The Stilled Colossi", 0x7FD6D0),
    CANOPY_CATHEDRAL("The Canopy Cathedral", 0x1F6B2E),
    FROZEN_WAVE("The Frozen Wave", 0xCFEFFF),
    THOUSAND_STAIRS("The Thousand Stairs", 0xF2F0E6),
    LANTERN_MARSH("The Lantern Marsh", 0xC9A227);

    public static final BiomeType[] VALUES = values();

    private final String displayName;
    private final int debugColor;

    BiomeType(String displayName, int debugColor) {
        this.displayName = displayName;
        this.debugColor = debugColor;
    }

    /** Lower-case id, the path of the Minecraft biome. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String displayName() {
        return displayName;
    }

    /** RGB for debug maps (preview and in-game render). */
    public int debugColor() {
        return debugColor;
    }

    public static BiomeType byId(String id) {
        return valueOf(id.toUpperCase(Locale.ROOT));
    }
}
