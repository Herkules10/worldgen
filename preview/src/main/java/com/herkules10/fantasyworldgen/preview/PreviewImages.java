package com.herkules10.fantasyworldgen.preview;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;

import com.herkules10.fantasyworldgen.engine.Block;
import com.herkules10.fantasyworldgen.engine.WorldShape;
import com.herkules10.fantasyworldgen.engine.biome.BiomeType;
import com.herkules10.fantasyworldgen.engine.debug.HeightRamp;
import com.herkules10.fantasyworldgen.engine.field.FieldSample;

/**
 * The preview images. They deliberately match the in-game {@code runRender} images (same layout, height ramp and
 * contour spacing), so the two can be compared side by side.
 */
final class PreviewImages {
    private static final Font FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    private static final int SLICE_SKY = 0xBFD9F2;
    private static final int SLICE_CAVE = 0x1E1E24;
    private static final int LEGEND_WIDTH = 280;

    private PreviewImages() {}

    static Path write(BufferedImage image, Path path) throws IOException {
        ImageIO.write(image, "png", path.toFile());
        return path;
    }

    /** Block colors (approximating vanilla map colors) with hillshading and water depth tint. */
    static BufferedImage map(PreviewData d) {
        int n = d.pixels;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        for (int pz = 0; pz < n; pz++) {
            for (int px = 0; px < n; px++) {
                int i = pz * n + px;
                int rgb = mapColor(d.topBlock[i]);
                if (d.topBlock[i] == Block.WATER) {
                    int depth = WorldShape.SEA_LEVEL - 1 - d.groundY[i];
                    rgb = scale(rgb, 1f - 0.5f * Math.min(depth, 30) / 30f);
                } else {
                    rgb = scale(rgb, shade(d.groundY, n, px, pz, d.options.scale));
                }
                img.setRGB(px, pz, rgb);
            }
        }
        Graphics2D g = graphics(img);
        drawGrid(g, d);
        g.dispose();
        return img;
    }

    /** Terrain height on the shared ramp, contour lines every 32 blocks (bold every 128). */
    static BufferedImage height(PreviewData d) {
        int n = d.pixels;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        for (int pz = 0; pz < n; pz++) {
            for (int px = 0; px < n; px++) {
                int y = d.groundY[pz * n + px];
                int rgb = scale(HeightRamp.rgb(y), 0.6f + 0.4f * shade(d.groundY, n, px, pz, d.options.scale));
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

    /** Vertical cross-section along X at sliceZ, one pixel per block vertically. Sea level dashed, Y labels every 128. */
    static BufferedImage slice(PreviewData d) {
        int n = d.pixels;
        int h = WorldShape.HEIGHT;
        BufferedImage img = new BufferedImage(n, h, BufferedImage.TYPE_INT_RGB);
        for (int px = 0; px < n; px++) {
            boolean belowGround = false;
            for (int row = 0; row < h; row++) {
                Block b = d.slice[row * n + px];
                int rgb;
                if (b.isAir()) {
                    rgb = belowGround ? SLICE_CAVE : SLICE_SKY;
                } else {
                    if (!b.isFluid()) belowGround = true;
                    rgb = mapColor(b);
                }
                img.setRGB(px, row, rgb);
            }
        }
        Graphics2D g = graphics(img);
        g.setColor(new Color(255, 255, 255, 90));
        for (int y = Math.floorDiv(WorldShape.MIN_Y, 128) * 128; y <= WorldShape.MAX_Y; y += 128) {
            if (y < WorldShape.MIN_Y) continue;
            int row = WorldShape.MAX_Y - y;
            g.drawLine(0, row, n, row);
            label(g, "y=" + y, 3, row - 2);
        }
        g.setColor(new Color(0, 220, 255, 200));
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[] {4f, 4f}, 0f));
        int seaRow = WorldShape.MAX_Y - WorldShape.SEA_LEVEL;
        g.drawLine(0, seaRow, n, seaRow);
        g.setStroke(new BasicStroke(1f));
        PreviewMain.Options o = d.options;
        int step = gridStep(o);
        for (int x = ceilTo(o.minX(), step); x < o.minX() + o.size; x += step) {
            int col = (x - o.minX()) / o.scale;
            g.setColor(new Color(255, 255, 255, 60));
            g.drawLine(col, 0, col, h);
            label(g, "x=" + x, col + 3, 12);
        }
        label(g, "z=" + o.sliceZ, n - 60, h - 4);
        g.dispose();
        return img;
    }

    /** Dominant biomes, lightly hillshaded, with a legend (share of the region) on the right. */
    static BufferedImage biome(PreviewData d) {
        int n = d.pixels;
        Map<BiomeType, Double> shares = biomeShares(d);
        BufferedImage img = new BufferedImage(n + LEGEND_WIDTH, Math.max(n, 20 + 16 * shares.size()), BufferedImage.TYPE_INT_RGB);
        for (int pz = 0; pz < n; pz++) {
            for (int px = 0; px < n; px++) {
                int i = pz * n + px;
                int rgb = scale(d.biome[i].debugColor(), 0.75f + 0.25f * shade(d.groundY, n, px, pz, d.options.scale));
                if ((px + 1 < n && d.biome[i + 1] != d.biome[i]) || (pz + 1 < n && d.biome[i + n] != d.biome[i])) rgb = scale(rgb, 0.55f);
                img.setRGB(px, pz, rgb);
            }
        }
        Graphics2D g = graphics(img);
        drawGrid(g, d);
        g.setColor(new Color(0x202020));
        g.fillRect(n, 0, LEGEND_WIDTH, img.getHeight());
        int y = 16;
        for (Map.Entry<BiomeType, Double> e : shares.entrySet()) {
            g.setColor(new Color(e.getKey().debugColor()));
            g.fillRect(n + 8, y - 10, 12, 12);
            g.setColor(Color.WHITE);
            g.drawString(String.format(Locale.ROOT, "%5.1f%%  %s", e.getValue() * 100, e.getKey().id()), n + 26, y);
            y += 16;
        }
        g.dispose();
        return img;
    }

    /**
     * The shared world fields as four half-resolution panels: uplift, moisture (with the wind direction), heat and
     * corruption. Water is darkened so coastlines stay readable.
     */
    static BufferedImage fields(PreviewData d) {
        int n = d.pixels;
        int half = (n + 1) / 2;
        BufferedImage img = new BufferedImage(half * 2, half * 2, BufferedImage.TYPE_INT_RGB);
        String[] titles = {"uplift (0-900 blocks)", "moisture (wind ->)", "heat", "corruption"};
        for (int panel = 0; panel < 4; panel++) {
            int ox = (panel % 2) * half, oy = (panel / 2) * half;
            for (int qz = 0; qz < half; qz++) {
                for (int qx = 0; qx < half; qx++) {
                    int i = Math.min(n - 1, qz * 2) * n + Math.min(n - 1, qx * 2);
                    FieldSample f = d.fields[i];
                    int rgb = switch (panel) {
                        case 0 -> HeightRamp.lerp(0x101010, 0xFFFFFF, (float) Math.min(1, f.uplift() / 900));
                        case 1 -> HeightRamp.lerp(0xA0632B, 0x1E5BD6, (float) f.moisture());
                        case 2 -> HeightRamp.lerp(0x2B6CD6, 0xE0402A, (float) f.heat());
                        default -> HeightRamp.lerp(0x202020, 0xE020C0, (float) f.corruption());
                    };
                    if (d.groundY[i] < WorldShape.SEA_LEVEL - 1) rgb = scale(rgb, 0.55f);
                    img.setRGB(ox + qx, oy + qz, rgb);
                }
            }
        }
        Graphics2D g = graphics(img);
        g.setColor(new Color(255, 255, 255, 160));
        g.drawLine(half, 0, half, half * 2);
        g.drawLine(0, half, half * 2, half);
        for (int panel = 0; panel < 4; panel++) label(g, titles[panel], (panel % 2) * half + 6, (panel / 2) * half + 16);
        // wind arrow in the moisture panel
        int cx = half + half / 2, cz = half / 2, len = Math.max(20, half / 8);
        int tx = cx + (int) Math.round(Math.cos(d.windAngle) * len), tz = cz + (int) Math.round(Math.sin(d.windAngle) * len);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(3f));
        g.drawLine(cx, cz, tx, tz);
        g.fillOval(tx - 5, tz - 5, 10, 10);
        g.dispose();
        return img;
    }

    /** Share of the region per dominant biome, largest first. */
    static Map<BiomeType, Double> biomeShares(PreviewData d) {
        double[] counts = new double[BiomeType.VALUES.length];
        for (BiomeType b : d.biome) counts[b.ordinal()]++;
        Map<BiomeType, Double> shares = new LinkedHashMap<>();
        Arrays.stream(BiomeType.VALUES)
                .filter(b -> counts[b.ordinal()] > 0)
                .sorted(Comparator.comparingDouble((BiomeType b) -> -counts[b.ordinal()]).thenComparing(Enum::ordinal))
                .forEach(b -> shares.put(b, counts[b.ordinal()] / d.biome.length));
        return shares;
    }

    /** Vanilla MapColor RGB of the block, so the preview map looks like the in-game render's map. */
    static int mapColor(Block b) {
        return switch (b) {
            case AIR -> 0;
            case WATER -> 0x4040FF;
            case GRASS_BLOCK -> 0x7FB238;
            case DIRT -> 0x976D4D;
            case SAND -> 0xF7E9A3;
            case SNOW_BLOCK -> 0xFFFFFF;
            case DEEPSLATE -> 0x646464;
            case STONE, GRAVEL, BEDROCK -> 0x707070;
            case SANDSTONE -> 0xF7E9A3;
            case TUFF -> 0x392923;
            case BLACKSTONE -> 0x191919;
            case CALCITE -> 0xD1B1A1;
            case DIORITE -> 0xFFFCF5;
            case PODZOL -> 0x815631;
            case PACKED_ICE -> 0xA0A0FF;
            case MUD -> 0x575C5C;
        };
    }

    private static void drawGrid(Graphics2D g, PreviewData d) {
        PreviewMain.Options o = d.options;
        int step = gridStep(o);
        int n = d.pixels;
        g.setColor(new Color(255, 255, 255, 55));
        for (int x = ceilTo(o.minX(), step); x < o.minX() + o.size; x += step) g.drawLine((x - o.minX()) / o.scale, 0, (x - o.minX()) / o.scale, n);
        for (int z = ceilTo(o.minZ(), step); z < o.minZ() + o.size; z += step) g.drawLine(0, (z - o.minZ()) / o.scale, n, (z - o.minZ()) / o.scale);
        for (int x = ceilTo(o.minX(), step); x < o.minX() + o.size; x += step) label(g, "x=" + x, (x - o.minX()) / o.scale + 3, 12);
        for (int z = ceilTo(o.minZ(), step); z < o.minZ() + o.size; z += step) label(g, "z=" + z, 3, (z - o.minZ()) / o.scale - 3);
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

    /** Grid spacing in blocks: a power of two, at most 8 lines across and at least 64 px apart. */
    private static int gridStep(PreviewMain.Options o) {
        int step = 16;
        while (o.size / step > 8 || step / o.scale < 64) step *= 2;
        return step;
    }

    private static int ceilTo(int value, int step) {
        return Math.floorDiv(value + step - 1, step) * step;
    }

    /** Brightness factor from the height gradient, lit from the north-west. ~1 on flat ground. */
    private static float shade(int[] heights, int n, int px, int pz, int scale) {
        int i = pz * n + px;
        int l = heights[px > 0 ? i - 1 : i], r = heights[px + 1 < n ? i + 1 : i];
        int u = heights[pz > 0 ? i - n : i], dn = heights[pz + 1 < n ? i + n : i];
        double slope = ((r - l) + (dn - u)) / (2.0 * scale);
        return (float) (1.0 + 0.35 * Math.tanh(slope / 1.5));
    }

    private static boolean crosses(int a, int b, int interval) {
        return Math.floorDiv(a, interval) != Math.floorDiv(b, interval);
    }

    private static int scale(int rgb, float f) {
        int r = Math.min(255, Math.round(((rgb >> 16) & 0xFF) * f));
        int g = Math.min(255, Math.round(((rgb >> 8) & 0xFF) * f));
        int b = Math.min(255, Math.round((rgb & 0xFF) * f));
        return (r << 16) | (g << 8) | b;
    }
}
