package com.herkules10.fantasyworldgen.engine;

/**
 * The blocks the engine can place. The mod maps each one to a Minecraft block state, so the engine never sees
 * Minecraft types. Add entries as the engine needs them; the order is irrelevant (nothing persists ordinals).
 */
public enum Block {
    AIR,
    WATER,
    BEDROCK,
    STONE,
    DEEPSLATE,
    DIRT,
    GRASS_BLOCK,
    SAND,
    GRAVEL,
    SNOW_BLOCK,
    SANDSTONE,
    TUFF,
    BLACKSTONE,
    CALCITE,
    DIORITE,
    PODZOL,
    PACKED_ICE,
    MUD;

    private static final Block[] VALUES = values();

    public static Block byId(int id) {
        return VALUES[id];
    }

    public int id() {
        return ordinal();
    }

    public boolean isAir() {
        return this == AIR;
    }

    public boolean isFluid() {
        return this == WATER;
    }
}
