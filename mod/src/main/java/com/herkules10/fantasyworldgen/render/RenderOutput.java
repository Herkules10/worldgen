package com.herkules10.fantasyworldgen.render;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.slf4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.herkules10.fantasyworldgen.FantasyWorldgen;
import com.herkules10.fantasyworldgen.engine.biome.BiomeType;
import com.herkules10.fantasyworldgen.engine.debug.HeightRamp;
import com.mojang.logging.LogUtils;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Turns {@link RenderData} into {@code <name>_map/height/biome/slice.png} plus {@code <name>.json}. */
final class RenderOutput {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Font FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    private static final int LEGEND_WIDTH = 280;

    private RenderOutput() {}

    static void write(RenderData d, ServerLevel level, double generateSeconds, double sampleSeconds) throws IOException {
        RenderSettings s = d.settings;
        Files.createDirectories(s.outputDir());
        Map<String, Path> files = new LinkedHashMap<>();
        files.put("map", save(map(d), s, "map"));
        files.put("height", save(height(d), s, "height"));
        files.put("biome", save(biome(d, level), s, "biome"));
        if (d.slice != null) files.put("slice", save(slice(d), s, "slice"));

        Path json = s.outputDir().resolve(s.name() + ".json");
        try (Writer w = Files.newBufferedWriter(json, StandardCharsets.UTF_8)) {
            GSON.toJson(summary(d, level, files, generateSeconds, sampleSeconds), w);
        }
        files.forEach((k, p) -> LOGGER.info("[render] wrote {}", p.toAbsolutePath()));
        LOGGER.info("[render] wrote {}", json.toAbsolutePath());
        LOGGER.info("[render] done: generation {}s, sampling {}s", round(generateSeconds), round(sampleSeconds));
    }

    private static Path save(BufferedImage image, RenderSettings s, String kind) throws IOException {
        Path path = s.outputDir().resolve(s.name() + "_" + kind + ".png");
        ImageIO.write(image, "png", path.toFile());
        return path;
    }

    // ---- images ----

    /** Map colors (like a vanilla map) with hillshading, water depth tint, structure boxes and a coordinate grid. */
    private static BufferedImage map(RenderData d) {
        int n = d.pixels;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        for (int pz = 0; pz < n; pz++) {
            for (int px = 0; px < n; px++) {
                int i = pz * n + px;
                int rgb = d.surfaceRgb[i];
                if (d.fluidDepth[i] > 0) {
                    rgb = scale(rgb, 1f - 0.5f * Math.min(d.fluidDepth[i], 30) / 30f);
                } else {
                    rgb = scale(rgb, shade(d.surfaceY, n, px, pz, d.settings.scale()));
                }
                img.setRGB(px, pz, rgb);
            }
        }
        Graphics2D g = graphics(img);
        drawGrid(g, d);
        drawStructures(g, d);
        g.dispose();
        return img;
    }

    /** Terrain height (water removed) on the shared height ramp, contour lines every 32 blocks (bold every 128). */
    private static BufferedImage height(RenderData d) {
        int n = d.pixels;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        for (int pz = 0; pz < n; pz++) {
            for (int px = 0; px < n; px++) {
                int y = d.groundY[pz * n + px];
                int rgb = scale(HeightRamp.rgb(y), 0.6f + 0.4f * shade(d.groundY, n, px, pz, d.settings.scale()));
                int right = px + 1 < n ? d.groundY[pz * n + px + 1] : y;
                int down = pz + 1 < n ? d.groundY[(pz + 1) * n + px] : y;
                if (crosses(y, right, 128) || crosses(y, down, 128)) rgb = scale(rgb, 0.45f);
                else if (crosses(y, right, 32) || crosses(y, down, 32)) rgb = scale(rgb, 0.75f);
                img.setRGB(px, pz, rgb);
            }
        }
        Graphics2D g = graphics(img);
        drawGrid(g, d);
        g.dispose();
        return img;
    }

    /** Surface biomes with a legend (share of the region) on the right. */
    private static BufferedImage biome(RenderData d, ServerLevel level) {
        int n = d.pixels;
        int[] colors = new int[d.biomes.size()];
        for (int b = 0; b < colors.length; b++) colors[b] = biomeColor(d.biomes.get(b));

        BufferedImage img = new BufferedImage(n + LEGEND_WIDTH, Math.max(n, 20 + 16 * colors.length), BufferedImage.TYPE_INT_RGB);
        for (int pz = 0; pz < n; pz++) {
            for (int px = 0; px < n; px++) {
                int i = pz * n + px;
                int rgb = colors[d.biome[i]];
                // thin dark border between biomes
                if ((px + 1 < n && d.biome[i + 1] != d.biome[i]) || (pz + 1 < n && d.biome[i + n] != d.biome[i])) rgb = scale(rgb, 0.55f);
                img.setRGB(px, pz, rgb);
            }
        }
        Graphics2D g = graphics(img);
        drawGrid(g, d);
        g.setColor(new Color(0x202020));
        g.fillRect(n, 0, LEGEND_WIDTH, img.getHeight());
        double[] shares = biomeShares(d);
        Integer[] order = sortedBiomes(shares);
        int y = 16;
        for (int b : order) {
            g.setColor(new Color(colors[b]));
            g.fillRect(n + 8, y - 10, 12, 12);
            g.setColor(Color.WHITE);
            g.drawString(String.format("%5.1f%%  %s", shares[b] * 100, RenderData.biomeId(d.biomes.get(b))), n + 26, y);
            y += 16;
        }
        g.dispose();
        return img;
    }

    /** Vertical cross-section along X at sliceZ; one pixel per block vertically. Sea level dashed, Y labels every 128. */
    private static BufferedImage slice(RenderData d) {
        int n = d.pixels;
        BufferedImage img = new BufferedImage(n, d.worldHeight, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, n, d.worldHeight, d.slice, 0, n);
        Graphics2D g = graphics(img);
        int maxY = d.minY + d.worldHeight - 1;
        g.setColor(new Color(255, 255, 255, 90));
        for (int y = Math.floorDiv(d.minY, 128) * 128; y <= maxY; y += 128) {
            if (y < d.minY) continue;
            int row = maxY - y;
            g.drawLine(0, row, n, row);
            label(g, "y=" + y, 3, row - 2);
        }
        g.setColor(new Color(0, 220, 255, 200));
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[] {4f, 4f}, 0f));
        int seaRow = maxY - d.seaLevel;
        g.drawLine(0, seaRow, n, seaRow);
        g.setStroke(new BasicStroke(1f));
        int step = gridStep(d.settings);
        for (int x = ceilTo(d.settings.minX(), step); x < d.settings.minX() + d.settings.size(); x += step) {
            int col = (x - d.settings.minX()) / d.settings.scale();
            g.setColor(new Color(255, 255, 255, 60));
            g.drawLine(col, 0, col, d.worldHeight);
            label(g, "x=" + x, col + 3, 12);
        }
        label(g, "z=" + d.settings.sliceZ(), n - 60, d.worldHeight - 4);
        g.dispose();
        return img;
    }

    // ---- overlays ----

    private static void drawGrid(Graphics2D g, RenderData d) {
        RenderSettings s = d.settings;
        int step = gridStep(s);
        int n = d.pixels;
        g.setColor(new Color(255, 255, 255, 55));
        for (int x = ceilTo(s.minX(), step); x < s.minX() + s.size(); x += step) {
            int px = (x - s.minX()) / s.scale();
            g.drawLine(px, 0, px, n);
        }
        for (int z = ceilTo(s.minZ(), step); z < s.minZ() + s.size(); z += step) {
            int pz = (z - s.minZ()) / s.scale();
            g.drawLine(0, pz, n, pz);
        }
        for (int x = ceilTo(s.minX(), step); x < s.minX() + s.size(); x += step) label(g, "x=" + x, (x - s.minX()) / s.scale() + 3, 12);
        for (int z = ceilTo(s.minZ(), step); z < s.minZ() + s.size(); z += step) label(g, "z=" + z, 3, (z - s.minZ()) / s.scale() - 3);
    }

    private static void drawStructures(Graphics2D g, RenderData d) {
        RenderSettings s = d.settings;
        BasicStroke surface = new BasicStroke(2f);
        BasicStroke underground = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[] {3f, 3f}, 0f);
        for (RenderData.StructureInfo info : d.structures) {
            BoundingBox b = info.box();
            g.setStroke(b.maxY() < d.seaLevel - 8 ? underground : surface);
            int x0 = Math.floorDiv(b.minX() - s.minX(), s.scale());
            int z0 = Math.floorDiv(b.minZ() - s.minZ(), s.scale());
            int w = Math.max(1, (b.maxX() - b.minX() + 1) / s.scale());
            int h = Math.max(1, (b.maxZ() - b.minZ() + 1) / s.scale());
            g.setColor(new Color(255, 0, 220));
            g.drawRect(x0, z0, w, h);
            String name = info.id().contains(":") ? info.id().substring(info.id().indexOf(':') + 1) : info.id();
            label(g, name, x0 + 2, z0 - 3);
        }
        g.setStroke(new BasicStroke(1f));
    }

    private static void label(Graphics2D g, String text, int x, int y) {
        Color c = g.getColor();
        g.setColor(Color.BLACK);
        g.drawString(text, x + 1, y + 1);
        g.setColor(Color.WHITE);
        g.drawString(text, x, y);
        g.setColor(c);
    }

    private static Graphics2D graphics(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setFont(FONT);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }

    /** Grid spacing in blocks: a power of two, at most 8 lines across and at least 64 px apart so labels don't collide. */
    private static int gridStep(RenderSettings s) {
        int step = 16;
        while (s.size() / step > 8 || step / s.scale() < 64) step *= 2;
        return step;
    }

    private static int ceilTo(int value, int step) {
        return Math.floorDiv(value + step - 1, step) * step;
    }

    // ---- colors ----

    /** Brightness factor from the height gradient, lit from the north-west. ~1 on flat ground. */
    private static float shade(int[] heights, int n, int px, int pz, int scale) {
        int i = pz * n + px;
        int l = heights[px > 0 ? i - 1 : i], r = heights[px + 1 < n ? i + 1 : i];
        int u = heights[pz > 0 ? i - n : i], dn = heights[pz + 1 < n ? i + n : i];
        // positive when the ground rises towards the south-east, i.e. the slope faces the light
        double slope = ((r - l) + (dn - u)) / (2.0 * scale);
        return (float) (1.0 + 0.35 * Math.tanh(slope / 1.5));
    }

    private static boolean crosses(int a, int b, int interval) {
        return Math.floorDiv(a, interval) != Math.floorDiv(b, interval);
    }

    /**
     * Our biomes use the engine's debug colors (the same as the preview). Others get a stable color: water biomes in
     * blues, beaches sandy, everything else hashed onto non-blue hues.
     */
    private static int biomeColor(Holder<Biome> b) {
        String id = RenderData.biomeId(b);
        if (id.startsWith(FantasyWorldgen.MODID + ":")) return BiomeType.byId(id.substring(FantasyWorldgen.MODID.length() + 1)).debugColor();
        int hash = RenderData.biomeId(b).hashCode() * 0x9E3779B1;
        float jitter = ((hash >>> 8) & 0xFF) / 255f;
        if (b.is(BiomeTags.IS_DEEP_OCEAN)) return Color.HSBtoRGB(0.63f, 0.8f, 0.35f + 0.15f * jitter) & 0xFFFFFF;
        if (b.is(BiomeTags.IS_OCEAN)) return Color.HSBtoRGB(0.6f, 0.65f, 0.6f + 0.2f * jitter) & 0xFFFFFF;
        if (b.is(BiomeTags.IS_RIVER)) return Color.HSBtoRGB(0.53f, 0.55f, 0.9f) & 0xFFFFFF;
        if (b.is(BiomeTags.IS_BEACH)) return Color.HSBtoRGB(0.13f, 0.35f, 0.93f) & 0xFFFFFF;
        float hue = ((hash >>> 16) & 0xFFFF) / 65535f * 0.7f; // 0..0.7, then skip the cyan..violet band used by water
        if (hue > 0.45f) hue += 0.3f;
        return Color.HSBtoRGB(hue % 1f, 0.45f + 0.4f * jitter, 0.65f + 0.3f * (((hash >>> 4) & 0xF) / 15f)) & 0xFFFFFF;
    }

    private static int scale(int rgb, float f) {
        int r = Math.min(255, Math.round(((rgb >> 16) & 0xFF) * f));
        int g = Math.min(255, Math.round(((rgb >> 8) & 0xFF) * f));
        int b = Math.min(255, Math.round((rgb & 0xFF) * f));
        return (r << 16) | (g << 8) | b;
    }


    // ---- summary ----

    private static double[] biomeShares(RenderData d) {
        double[] shares = new double[d.biomes.size()];
        for (int b : d.biome) shares[b]++;
        for (int k = 0; k < shares.length; k++) shares[k] /= d.biome.length;
        return shares;
    }

    private static Integer[] sortedBiomes(double[] shares) {
        Integer[] order = new Integer[shares.length];
        for (int k = 0; k < order.length; k++) order[k] = k;
        Arrays.sort(order, Comparator.comparingDouble((Integer k) -> -shares[k]));
        return order;
    }

    private static JsonObject summary(RenderData d, ServerLevel level, Map<String, Path> files, double gen, double sample) {
        RenderSettings s = d.settings;
        JsonObject o = new JsonObject();
        o.addProperty("seed", level.getSeed());
        o.addProperty("dimension", s.dimension().location().toString());
        o.addProperty("minecraftVersion", SharedConstants.getCurrentVersion().getName());
        o.addProperty("centerX", s.centerX());
        o.addProperty("centerZ", s.centerZ());
        o.addProperty("size", s.size());
        o.addProperty("scale", s.scale());
        o.addProperty("minX", s.minX());
        o.addProperty("minZ", s.minZ());
        o.addProperty("maxXExclusive", s.minX() + s.size());
        o.addProperty("maxZExclusive", s.minZ() + s.size());
        o.addProperty("imagePixels", d.pixels);
        o.addProperty("pixelToBlock", "block = (minX + px * scale, minZ + pz * scale)");
        if (d.slice != null) o.addProperty("sliceZ", s.sliceZ());
        o.addProperty("seaLevel", d.seaLevel);
        o.addProperty("worldMinY", d.minY);
        o.addProperty("worldMaxY", d.minY + d.worldHeight - 1);

        int[] ground = d.groundY.clone();
        Arrays.sort(ground);
        long sum = 0;
        for (int y : ground) sum += y;
        JsonObject h = new JsonObject();
        h.addProperty("min", ground[0]);
        h.addProperty("p05", ground[ground.length * 5 / 100]);
        h.addProperty("median", ground[ground.length / 2]);
        h.addProperty("p95", ground[Math.min(ground.length - 1, ground.length * 95 / 100)]);
        h.addProperty("max", ground[ground.length - 1]);
        h.addProperty("mean", round((double) sum / ground.length));
        o.add("terrainHeight", h);

        int wet = 0;
        for (int depth : d.fluidDepth) if (depth > 0) wet++;
        o.addProperty("fluidSurfaceFraction", round((double) wet / d.fluidDepth.length));

        double[] shares = biomeShares(d);
        JsonArray biomes = new JsonArray();
        for (int b : sortedBiomes(shares)) {
            JsonObject e = new JsonObject();
            e.addProperty("id", RenderData.biomeId(d.biomes.get(b)));
            e.addProperty("fraction", round(shares[b]));
            biomes.add(e);
        }
        o.add("biomes", biomes);

        List<RenderData.StructureInfo> sorted = new ArrayList<>(d.structures);
        sorted.sort(Comparator.comparing(RenderData.StructureInfo::id).thenComparingInt(i -> i.box().minX()).thenComparingInt(i -> i.box().minZ()));
        JsonArray structures = new JsonArray();
        for (RenderData.StructureInfo info : sorted) {
            BoundingBox b = info.box();
            JsonObject e = new JsonObject();
            e.addProperty("id", info.id());
            e.addProperty("centerX", (b.minX() + b.maxX()) / 2);
            e.addProperty("centerZ", (b.minZ() + b.maxZ()) / 2);
            e.addProperty("box", String.format("[%d %d %d] .. [%d %d %d]", b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()));
            structures.add(e);
        }
        o.addProperty("structuresNote", "structures whose start chunk lies inside the region");
        o.add("structures", structures);

        JsonObject images = new JsonObject();
        files.forEach((k, p) -> images.addProperty(k, p.getFileName().toString()));
        o.add("images", images);
        o.addProperty("generationSeconds", round(gen));
        o.addProperty("samplingSeconds", round(sample));
        return o;
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}
