package com.herkules10.fantasyworldgen.render;

import java.nio.file.Path;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Parameters of a headless terrain render, passed in as system properties by the {@code runRender} Gradle task.
 * The world seed and level type are not here: they go into server.properties, because they must be known before the
 * world is created.
 */
record RenderSettings(int centerX, int centerZ, int size, int scale, int sliceZ, ResourceKey<Level> dimension,
                      Path outputDir, String name) {
    static final String PREFIX = "fantasyworldgen.render";

    static boolean enabled() {
        return Boolean.getBoolean(PREFIX);
    }

    static RenderSettings fromSystemProperties() {
        int x = intProp("x", 0);
        int z = intProp("z", 0);
        int size = intProp("size", 512);
        int scale = intProp("scale", 1);
        if (size < 16 || size > 16384) throw new IllegalArgumentException("size must be in [16, 16384], got " + size);
        if (scale < 1 || scale > size) throw new IllegalArgumentException("scale must be in [1, size], got " + scale);
        int sliceZ = intProp("sliceZ", z);
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.parse(System.getProperty(PREFIX + ".dimension", "minecraft:overworld")));
        String out = System.getProperty(PREFIX + ".out");
        if (out == null || out.isBlank()) throw new IllegalArgumentException(PREFIX + ".out is not set");
        String name = System.getProperty(PREFIX + ".name", "");
        if (name.isBlank()) name = "render";
        if (!name.matches("[A-Za-z0-9_.-]+")) throw new IllegalArgumentException("name may only contain [A-Za-z0-9_.-], got " + name);
        return new RenderSettings(x, z, size, scale, sliceZ, dimension, Path.of(out), name);
    }

    private static int intProp(String key, int fallback) {
        String value = System.getProperty(PREFIX + "." + key, "");
        if (value.isBlank()) return fallback;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be an integer, got " + value);
        }
    }

    /** Inclusive minimum block coordinates; the region is [minX, minX + size) x [minZ, minZ + size). */
    int minX() {
        return centerX - size / 2;
    }

    int minZ() {
        return centerZ - size / 2;
    }

    /** Output image width/height in pixels (one pixel per {@code scale} blocks). */
    int pixels() {
        return (size + scale - 1) / scale;
    }

    boolean sliceInRegion() {
        return sliceZ >= minZ() && sliceZ < minZ() + size;
    }
}
