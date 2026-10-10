package com.herkules10.fantasyworldgen.engine;

/**
 * Vertical extent of the world the engine generates for. The mod's dimension type must use exactly these values
 * (a GameTest checks it).
 * <p>
 * The top is Minecraft's hard limit (y 2031): the mod targets Distant Horizons, where very tall terrain stays visible.
 * The bottom stays at vanilla's -64 because underground volume is solid and costly, and players expect deepslate
 * below 0 and bedrock at -64.
 */
public final class WorldShape {
    public static final int MIN_Y = -64;
    public static final int MAX_Y = 2031;
    /** Number of block layers, {@code MAX_Y - MIN_Y + 1}. A multiple of 16. */
    public static final int HEIGHT = MAX_Y - MIN_Y + 1;
    /** Water fills every y below this, like vanilla's sea level. */
    public static final int SEA_LEVEL = 63;
    /**
     * Height of the cloud layer (vanilla: 192). Above ordinary Spine peaks (up to about y 950) so that clouds sit over
     * the ranges, not through them; only landmark and legendary peaks break through.
     */
    public static final int CLOUD_LEVEL = 1100;

    public static final int SECTION_SIZE = 16;
    public static final int SECTION_COUNT = HEIGHT / SECTION_SIZE;
    /** Section y coordinate (y >> 4) of the lowest section. */
    public static final int MIN_SECTION_Y = MIN_Y >> 4;

    private WorldShape() {}

    public static boolean contains(int y) {
        return y >= MIN_Y && y <= MAX_Y;
    }
}
