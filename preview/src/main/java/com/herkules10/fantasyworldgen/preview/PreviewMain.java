package com.herkules10.fantasyworldgen.preview;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;

import com.herkules10.fantasyworldgen.engine.Block;
import com.herkules10.fantasyworldgen.engine.TerrainEngine;
import com.herkules10.fantasyworldgen.engine.WorldShape;
import com.herkules10.fantasyworldgen.engine.biome.BiomeType;

/**
 * Renders engine output straight to PNGs, without Minecraft: {@code ./gradlew :preview:run --args="--seed 42 --size 2048"}.
 * <p>
 * Options: {@code --seed N} (default 42), {@code --size N} blocks per side (default 1024), {@code --x N --z N} region
 * center (default 0, 0), {@code --scale N} blocks per pixel (default 1), {@code --sliceZ N} z of the cross-section
 * (default: center), {@code --name S}, {@code --out DIR}. Writes {@code <name>_map/height/slice.png} and
 * {@code <name>.json}. Same arguments always give identical files.
 */
public final class PreviewMain {
    private PreviewMain() {}

    public static void main(String[] args) throws IOException {
        Options o;
        try {
            o = Options.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("preview: " + e.getMessage());
            System.exit(2);
            return;
        }
        long start = System.nanoTime();
        TerrainEngine engine = new TerrainEngine(o.seed);
        PreviewData data = sample(engine, o);
        double seconds = (System.nanoTime() - start) / 1e9;

        Files.createDirectories(o.out);
        Map<String, Path> files = new LinkedHashMap<>();
        files.put("map", PreviewImages.write(PreviewImages.map(data), o.out.resolve(o.name + "_map.png")));
        files.put("height", PreviewImages.write(PreviewImages.height(data), o.out.resolve(o.name + "_height.png")));
        files.put("biome", PreviewImages.write(PreviewImages.biome(data), o.out.resolve(o.name + "_biome.png")));
        files.put("fields", PreviewImages.write(PreviewImages.fields(data), o.out.resolve(o.name + "_fields.png")));
        if (data.slice != null) files.put("slice", PreviewImages.write(PreviewImages.slice(data), o.out.resolve(o.name + "_slice.png")));
        Path json = o.out.resolve(o.name + ".json");
        writeSummary(data, files, seconds, json);

        files.values().forEach(p -> System.out.println("[preview] wrote " + p.toAbsolutePath()));
        System.out.println("[preview] wrote " + json.toAbsolutePath());
        System.out.printf(Locale.ROOT, "[preview] sampled %d columns in %.2fs%n", data.pixels * data.pixels, seconds);
    }

    private static PreviewData sample(TerrainEngine engine, Options o) {
        PreviewData d = new PreviewData(o);
        int n = d.pixels;
        d.windAngle = engine.fields().wind().angle(o.x, o.z);
        // Rows are independent and the engine is a pure function, so parallel sampling can't change the output.
        IntStream.range(0, n).parallel().forEach(pz -> {
            int z = o.minZ() + pz * o.scale;
            for (int px = 0; px < n; px++) {
                int x = o.minX() + px * o.scale;
                TerrainEngine.Column c = engine.column(x, z);
                int i = pz * n + px;
                d.groundY[i] = c.surfaceY();
                d.topBlock[i] = c.surfaceY() < WorldShape.SEA_LEVEL - 1 ? Block.WATER : TerrainEngine.block(c, c.surfaceY());
                d.biome[i] = c.biome();
                d.fields[i] = c.fields();
            }
        });
        if (d.slice != null) {
            IntStream.range(0, n).parallel().forEach(px -> {
                Block[] column = new Block[WorldShape.HEIGHT];
                engine.fillColumn(o.minX() + px * o.scale, o.sliceZ, column);
                for (int k = 0; k < WorldShape.HEIGHT; k++) d.slice[(WorldShape.HEIGHT - 1 - k) * n + px] = column[k];
            });
        }
        return d;
    }

    private static void writeSummary(PreviewData d, Map<String, Path> files, double seconds, Path json) throws IOException {
        Options o = d.options;
        int[] sorted = d.groundY.clone();
        Arrays.sort(sorted);
        long sum = 0;
        int wet = 0;
        for (int i = 0; i < sorted.length; i++) {
            sum += sorted[i];
            if (d.topBlock[i] == Block.WATER) wet++;
        }
        StringBuilder s = new StringBuilder("{\n");
        field(s, "seed", o.seed);
        field(s, "centerX", o.x);
        field(s, "centerZ", o.z);
        field(s, "size", o.size);
        field(s, "scale", o.scale);
        field(s, "minX", o.minX());
        field(s, "minZ", o.minZ());
        field(s, "maxXExclusive", o.minX() + o.size);
        field(s, "maxZExclusive", o.minZ() + o.size);
        field(s, "imagePixels", d.pixels);
        field(s, "pixelToBlock", "\"block = (minX + px * scale, minZ + pz * scale)\"");
        if (d.slice != null) field(s, "sliceZ", o.sliceZ);
        field(s, "seaLevel", WorldShape.SEA_LEVEL);
        field(s, "worldMinY", WorldShape.MIN_Y);
        field(s, "worldMaxY", WorldShape.MAX_Y);
        s.append("  \"terrainHeight\": {");
        s.append(String.format(Locale.ROOT, "\"min\": %d, \"p05\": %d, \"median\": %d, \"p95\": %d, \"max\": %d, \"mean\": %.3f},%n",
                sorted[0], sorted[sorted.length * 5 / 100], sorted[sorted.length / 2],
                sorted[Math.min(sorted.length - 1, sorted.length * 95 / 100)], sorted[sorted.length - 1], (double) sum / sorted.length));
        field(s, "fluidSurfaceFraction", String.format(Locale.ROOT, "%.3f", (double) wet / sorted.length));
        s.append("  \"biomes\": {");
        String biomeSep = "";
        for (Map.Entry<BiomeType, Double> e : PreviewImages.biomeShares(d).entrySet()) {
            s.append(biomeSep).append(String.format(Locale.ROOT, "\"%s\": %.4f", e.getKey().id(), e.getValue()));
            biomeSep = ", ";
        }
        s.append("},\n");
        s.append("  \"images\": {");
        String sep = "";
        for (Map.Entry<String, Path> e : files.entrySet()) {
            s.append(sep).append('"').append(e.getKey()).append("\": \"").append(e.getValue().getFileName()).append('"');
            sep = ", ";
        }
        s.append("},\n");
        s.append(String.format(Locale.ROOT, "  \"samplingSeconds\": %.3f%n}%n", seconds));
        try (Writer w = Files.newBufferedWriter(json, StandardCharsets.UTF_8)) {
            w.write(s.toString());
        }
    }

    private static void field(StringBuilder s, String key, Object value) {
        s.append("  \"").append(key).append("\": ").append(value).append(",\n");
    }

    static final class Options {
        long seed = 42;
        int size = 1024;
        int x;
        int z;
        int scale = 1;
        int sliceZ;
        String name;
        Path out;

        int minX() {
            return x - size / 2;
        }

        int minZ() {
            return z - size / 2;
        }

        static Options parse(String[] args) {
            Options o = new Options();
            Integer sliceZ = null;
            String out = System.getProperty("fantasyworldgen.preview.out", "build/previews");
            for (int i = 0; i < args.length; i++) {
                String key = args[i];
                if (i + 1 >= args.length) throw new IllegalArgumentException("missing value for " + key);
                String value = args[++i];
                switch (key) {
                    case "--seed" -> o.seed = parseLong(key, value);
                    case "--size" -> o.size = (int) parseLong(key, value);
                    case "--x" -> o.x = (int) parseLong(key, value);
                    case "--z" -> o.z = (int) parseLong(key, value);
                    case "--scale" -> o.scale = (int) parseLong(key, value);
                    case "--sliceZ" -> sliceZ = (int) parseLong(key, value);
                    case "--name" -> o.name = value;
                    case "--out" -> out = value;
                    default -> throw new IllegalArgumentException("unknown option " + key);
                }
            }
            if (o.size < 16 || o.size > 16384) throw new IllegalArgumentException("--size must be in [16, 16384], got " + o.size);
            if (o.scale < 1 || o.scale > o.size) throw new IllegalArgumentException("--scale must be in [1, size], got " + o.scale);
            o.sliceZ = sliceZ != null ? sliceZ : o.z;
            if (o.name == null) {
                o.name = "s" + o.seed + "_x" + o.x + "_z" + o.z + "_" + o.size + (o.scale == 1 ? "" : "_d" + o.scale);
            }
            if (!o.name.matches("[A-Za-z0-9_.-]+")) throw new IllegalArgumentException("--name may only contain [A-Za-z0-9_.-], got " + o.name);
            o.out = Path.of(out);
            return o;
        }

        private static long parseLong(String key, String value) {
            try {
                return Long.parseLong(value.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(key + " must be an integer, got " + value);
            }
        }
    }
}
