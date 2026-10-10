package com.herkules10.fantasyworldgen.preview;

import com.herkules10.fantasyworldgen.engine.Block;
import com.herkules10.fantasyworldgen.engine.WorldShape;
import com.herkules10.fantasyworldgen.engine.biome.BiomeType;
import com.herkules10.fantasyworldgen.engine.field.FieldSample;
import com.herkules10.fantasyworldgen.engine.region.Region;

/** Per-column samples of the preview region, indexed {@code pz * pixels + px}, plus the vertical slice. */
final class PreviewData {
    final PreviewMain.Options options;
    final int pixels;
    /** Y of the highest solid block. */
    final int[] groundY;
    /** Block seen from above: water over oceans and lakes, else the ground block. */
    final Block[] topBlock;
    /** Dominant biome (what Minecraft is told). */
    final BiomeType[] biome;
    final FieldSample[] fields;
    /** Region of the dominant biome; neighbouring pixels share the same instance. */
    final Region[] region;
    /** Prevailing wind direction at the region center, radians. */
    double windAngle;
    /** Cross-section along X at {@code options.sliceZ}, row 0 = top of the world; null if outside the region. */
    final Block[] slice;

    PreviewData(PreviewMain.Options options) {
        this.options = options;
        this.pixels = (options.size + options.scale - 1) / options.scale;
        int n = pixels * pixels;
        groundY = new int[n];
        topBlock = new Block[n];
        biome = new BiomeType[n];
        fields = new FieldSample[n];
        region = new Region[n];
        boolean sliceInRegion = options.sliceZ >= options.minZ() && options.sliceZ < options.minZ() + options.size;
        slice = sliceInRegion ? new Block[pixels * WorldShape.HEIGHT] : null;
    }
}
