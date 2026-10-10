package com.herkules10.fantasyworldgen.preview;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.herkules10.fantasyworldgen.engine.biome.BiomeType;
import com.herkules10.fantasyworldgen.engine.biome.CuratedPalette;
import com.herkules10.fantasyworldgen.engine.region.Region;
import com.herkules10.fantasyworldgen.engine.variant.Axis;
import com.herkules10.fantasyworldgen.engine.variant.RegionContext;
import com.herkules10.fantasyworldgen.engine.variant.Trait;

/** The regions in view: which pixels each covers, and the JSON list of their traits. */
final class PreviewRegions {
    private PreviewRegions() {}

    /** A region as seen in the preview: its pixel count and the mean position of its pixels. */
    record Visible(Region region, int pixels, double meanPx, double meanPz) {
        double share(PreviewData d) {
            return pixels / (double) (d.pixels * d.pixels);
        }
    }

    /** Regions in view, largest first (ties by id, so the order is reproducible). */
    static List<Visible> visible(PreviewData d) {
        Map<Region, double[]> acc = new IdentityHashMap<>();
        int n = d.pixels;
        for (int pz = 0; pz < n; pz++) {
            for (int px = 0; px < n; px++) {
                double[] a = acc.computeIfAbsent(d.region[pz * n + px], r -> new double[3]);
                a[0]++;
                a[1] += px;
                a[2] += pz;
            }
        }
        // Regions are cached per engine, but the same region can be two instances if a cache was cleared in between;
        // merge by id.
        Map<Long, Visible> byId = new java.util.HashMap<>();
        for (Map.Entry<Region, double[]> e : acc.entrySet()) {
            double[] a = e.getValue();
            byId.merge(e.getKey().id(), new Visible(e.getKey(), (int) a[0], a[1] / a[0], a[2] / a[0]), (x, y) -> new Visible(x.region(),
                    x.pixels() + y.pixels(), (x.meanPx() * x.pixels() + y.meanPx() * y.pixels()) / (x.pixels() + y.pixels()),
                    (x.meanPz() * x.pixels() + y.meanPz() * y.pixels()) / (x.pixels() + y.pixels())));
        }
        List<Visible> out = new ArrayList<>(byId.values());
        out.sort(Comparator.comparingInt((Visible v) -> -v.pixels()).thenComparingLong(v -> v.region().id()));
        return out;
    }

    static void writeJson(PreviewData d, Path path) throws IOException {
        List<Visible> visible = visible(d);
        StringBuilder s = new StringBuilder("{\n");
        s.append(String.format(Locale.ROOT, "  \"seed\": %d,%n  \"count\": %d,%n  \"regions\": [%n", d.options.seed, visible.size()));
        for (int i = 0; i < visible.size(); i++) {
            Visible v = visible.get(i);
            Region r = v.region();
            RegionContext c = r.context();
            s.append("    {");
            s.append(String.format(Locale.ROOT, "\"name\": %s, \"key\": %s, \"id\": \"%016x\", \"biome\": %s, \"rarity\": %s,",
                    quote(r.name()), quote(r.key()), r.id(), quote(r.biome().id()), quote(r.rarity().id())));
            s.append("\n      \"traits\": {");
            String sep = "";
            for (Axis axis : Axis.VALUES) {
                Trait t = r.variant().trait(axis);
                if (t == null) continue;
                s.append(sep).append(quote(axis.id())).append(": {\"id\": ").append(quote(t.id())).append(", \"label\": ").append(quote(t.label()))
                        .append(", \"rarity\": ").append(quote(t.rarity().id())).append('}');
                sep = ", ";
            }
            s.append("},");
            CuratedPalette p = r.variant().palette();
            if (p != null) {
                s.append(String.format(Locale.ROOT, "%n      \"palette\": {\"base\": %s, \"accent\": %s, \"highlight\": %s},",
                        quote(p.base().name().toLowerCase(Locale.ROOT)), quote(p.accent().name().toLowerCase(Locale.ROOT)),
                        quote(p.highlight().name().toLowerCase(Locale.ROOT))));
            }
            s.append(String.format(Locale.ROOT,
                    "%n      \"context\": {\"heat\": %.3f, \"moisture\": %.3f, \"corruption\": %.3f, \"maxCorruption\": %.3f, \"uplift\": %.1f, "
                            + "\"maxUplift\": %.1f, \"tags\": [%s], \"neighbours\": {%s}},",
                    c.heat(), c.moisture(), c.corruption(), c.maxCorruption(), c.uplift(), c.maxUplift(),
                    String.join(", ", c.tags().stream().map(PreviewRegions::quote).toList()), neighbours(c)));
            s.append(String.format(Locale.ROOT,
                    "%n      \"areaBlocks\": %d, \"centroid\": [%.0f, %.0f], \"bounds\": [%d, %d, %d, %d], \"anchor\": [%d, %d], \"cell\": [%d, %d], "
                            + "\"visibleShare\": %.4f}%s%n",
                    r.areaBlocks(), r.centroidX(), r.centroidZ(), r.minX(), r.minZ(), r.maxX(), r.maxZ(), r.anchorX(), r.anchorZ(), r.cellX(), r.cellZ(),
                    v.share(d), i + 1 < visible.size() ? "," : ""));
        }
        s.append("  ]\n}\n");
        try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write(s.toString());
        }
    }

    private static String neighbours(RegionContext c) {
        List<String> parts = new ArrayList<>();
        for (BiomeType b : BiomeType.VALUES) {
            if (c.neighbour(b) >= 0.01) parts.add(String.format(Locale.ROOT, "%s: %.2f", quote(b.id()), c.neighbour(b)));
        }
        return String.join(", ", parts);
    }

    private static String quote(String s) {
        return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
