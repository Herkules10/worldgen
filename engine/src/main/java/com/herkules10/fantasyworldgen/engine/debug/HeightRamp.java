package com.herkules10.fantasyworldgen.engine.debug;

import com.herkules10.fantasyworldgen.engine.WorldShape;

/**
 * Fixed height-to-color ramp shared by the preview and the in-game render, so their height maps are directly
 * comparable across seeds, regions and tools.
 */
public final class HeightRamp {
    /** Heights above sea level at which each color is reached exactly. Spans the full world height. */
    private static final int[] STOPS = {0, 48, 128, 256, 512, 1024, WorldShape.MAX_Y - WorldShape.SEA_LEVEL};
    private static final int[] COLORS = {0x3E7D33, 0x86A94A, 0xD6C27F, 0x9C6B45, 0x8C8C8C, 0xC8C8D2, 0xFFFFFF};

    private HeightRamp() {}

    /** RGB for terrain at {@code y}; below sea level, light to dark blue over 64 blocks of depth. */
    public static int rgb(int y) {
        int above = y - WorldShape.SEA_LEVEL;
        if (above < 0) return lerp(0x6FA8DC, 0x0B2550, Math.min(1f, -above / 64f));
        for (int k = 1; k < STOPS.length; k++) {
            if (above <= STOPS[k]) return lerp(COLORS[k - 1], COLORS[k], (above - STOPS[k - 1]) / (float) (STOPS[k] - STOPS[k - 1]));
        }
        return COLORS[COLORS.length - 1];
    }

    public static int lerp(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
