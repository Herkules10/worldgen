package com.herkules10.fantasyworldgen.engine.biome;

import com.herkules10.fantasyworldgen.engine.Block;

/**
 * One hand-designed block set of a biome family (Artistic_Direction.md: "palettes are curated, not random"). A family
 * lists a handful of these; a region's variant picks one, and neighbouring regions dither into each other at their
 * borders instead of mixing blocks at random.
 *
 * @param id        stable lower-case id, used in previews and JSON
 * @param label     human-readable name
 * @param base      the bulk of the exposed material
 * @param accent    secondary bands and patches, roughly a third
 * @param highlight rare, thin, bright streaks that catch the eye
 */
public record CuratedPalette(String id, String label, Block base, Block accent, Block highlight) {
    /** The Spine's ranges, by the rock they are made of. */
    public static final CuratedPalette SPINE_GREY_GRANITE =
            new CuratedPalette("grey_granite", "Grey granite", Block.STONE, Block.ANDESITE, Block.CALCITE);
    public static final CuratedPalette SPINE_RED_ROCK =
            new CuratedPalette("red_rock", "Red rock", Block.GRANITE, Block.TERRACOTTA, Block.RED_SANDSTONE);
    public static final CuratedPalette SPINE_BLACK_VOLCANIC =
            new CuratedPalette("black_volcanic", "Black volcanic", Block.BASALT, Block.BLACKSTONE, Block.TUFF);
    public static final CuratedPalette SPINE_WHITE_MARBLE =
            new CuratedPalette("white_marble", "White marble", Block.CALCITE, Block.DIORITE, Block.QUARTZ_BLOCK);
}
