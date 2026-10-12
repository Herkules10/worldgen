package com.herkules10.fantasyworldgen.engine.region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.herkules10.fantasyworldgen.engine.Block;
import com.herkules10.fantasyworldgen.engine.Determinism;
import com.herkules10.fantasyworldgen.engine.TerrainEngine;
import com.herkules10.fantasyworldgen.engine.WorldShape;
import com.herkules10.fantasyworldgen.engine.biome.BiomeType;
import com.herkules10.fantasyworldgen.engine.biome.CuratedPalette;
import com.herkules10.fantasyworldgen.engine.variant.Axis;
import com.herkules10.fantasyworldgen.engine.variant.Families;
import com.herkules10.fantasyworldgen.engine.variant.Family;
import com.herkules10.fantasyworldgen.engine.variant.Rarity;

/** Regions on a real world: partition, determinism, rarity and context, checked over a 16k x 16k area. */
class RegionMapTest {
    private static final long SEED = 42;
    private static final long SECOND_SEED = 1337;
    private static final int HALF = 8192;
    private static TerrainEngine engine;
    private static List<Region> regions;
    /**
     * Regions of two seeds, for the frequency checks: one 16k x 16k area holds only a handful of ranges, too few to
     * judge shares by, and a larger area would thrash the region caches.
     */
    private static List<Region> sample;

    @BeforeAll
    static void enumerate() {
        engine = new TerrainEngine(SEED);
        regions = engine.regions().regionsIn(-HALF, -HALF, HALF - 1, HALF - 1);
        sample = new ArrayList<>(regions);
        sample.addAll(new TerrainEngine(SECOND_SEED).regions().regionsIn(-HALF, -HALF, HALF - 1, HALF - 1));
    }

    private static List<Region> of(BiomeType biome) {
        return regions.stream().filter(r -> r.biome() == biome).toList();
    }

    private static String describe(Region r) {
        StringBuilder s = new StringBuilder(r.key()).append(' ').append(r.name()).append(' ').append(r.rarity());
        for (Axis a : Axis.VALUES) if (r.variant().trait(a) != null) s.append(' ').append(r.variant().trait(a).id());
        return s.toString();
    }

    @Test
    void sameSeedGivesSameRegionsInAnyOrderAndOnAnyThread() throws Exception {
        List<int[]> points = new ArrayList<>();
        for (int i = 0; i < 400; i++) points.add(new int[] {(i * 7919) % (2 * HALF) - HALF, (i * 104_729) % (2 * HALF) - HALF});
        points.add(new int[] {-1_000_003, 2_000_017});
        Map<Integer, String> expected = new HashMap<>();
        for (int i = 0; i < points.size(); i++) expected.put(i, describe(engine.regionAt(points.get(i)[0], points.get(i)[1])));

        TerrainEngine reversed = new TerrainEngine(SEED);
        for (int i = points.size() - 1; i >= 0; i--) {
            assertEquals(expected.get(i), describe(reversed.regionAt(points.get(i)[0], points.get(i)[1])), "order-dependent region");
        }
        TerrainEngine shared = new TerrainEngine(SEED);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int[] p : points) results.add(pool.submit(() -> describe(shared.regionAt(p[0], p[1]))));
            for (int i = 0; i < points.size(); i++) assertEquals(expected.get(i), results.get(i).get(), "thread-dependent region");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void chunksWithCuratedPalettesAreDeterministic() throws Exception {
        // Chunks in the Spine, where blocks come from the region's palette: the default samples miss them.
        List<int[]> chunks = new ArrayList<>();
        for (Region r : of(BiomeType.SPINE)) {
            if (r.nodes() >= 100 && chunks.size() < 6) chunks.add(new int[] {(int) r.centroidX() >> 4, (int) r.centroidZ() >> 4});
        }
        assertTrue(chunks.size() >= 3, "too few Spine regions in the sample area");
        Determinism.assertDeterministic(TerrainEngine::new, SEED, chunks.toArray(new int[0][]));
    }

    @Test
    void regionsAreContiguousSingleBiomeAndUniquelyIdentified() {
        RegionMap map = engine.regions();
        int half = 4096 / RegionMap.SPACING;
        Map<Long, List<long[]>> nodes = new HashMap<>();
        Map<Long, Region> byId = new HashMap<>();
        Set<String> keys = new HashSet<>();
        for (int gz = -half; gz < half; gz++) {
            for (int gx = -half; gx < half; gx++) {
                Region r = map.regionAtNode(gx, gz);
                assertEquals(r.biome(), map.biomeAtNode(gx, gz), "node " + gx + ", " + gz + " is in a region of another biome");
                nodes.computeIfAbsent(r.id(), k -> new ArrayList<>()).add(new long[] {gx, gz});
                if (byId.putIfAbsent(r.id(), r) == null) assertTrue(keys.add(r.key()), "two regions share key " + r.key());
            }
        }
        // Only regions well inside the window can be checked whole (absorbed fragments reach a little past the bounds).
        int margin = RegionMap.MIN_REGION_NODES + 1, checked = 0;
        for (Map.Entry<Long, List<long[]>> e : nodes.entrySet()) {
            Region r = byId.get(e.getKey());
            if (r.minX() / RegionMap.SPACING - margin < -half || r.maxX() / RegionMap.SPACING + margin >= half
                    || r.minZ() / RegionMap.SPACING - margin < -half || r.maxZ() / RegionMap.SPACING + margin >= half) continue;
            checked++;
            assertTrue(e.getValue().size() >= r.nodes(), r.key() + " lost nodes");
            assertTrue(isConnected(e.getValue()), r.key() + " (" + r.name() + ") is not contiguous");
        }
        assertTrue(checked > 50, "only " + checked + " regions checked");
    }

    private static boolean isConnected(List<long[]> nodes) {
        Set<Long> set = new HashSet<>();
        for (long[] n : nodes) set.add(n[0] << 32 ^ (n[1] & 0xFFFFFFFFL));
        ArrayDeque<long[]> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        queue.add(nodes.get(0));
        seen.add(nodes.get(0)[0] << 32 ^ (nodes.get(0)[1] & 0xFFFFFFFFL));
        while (!queue.isEmpty()) {
            long[] n = queue.poll();
            for (long[] d : new long[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                long x = n[0] + d[0], z = n[1] + d[1], key = x << 32 ^ (z & 0xFFFFFFFFL);
                if (set.contains(key) && seen.add(key)) queue.add(new long[] {x, z});
            }
        }
        return seen.size() == set.size();
    }

    @Test
    void columnsBelongToARegionOfTheirOwnBiome() {
        int own = 0, total = 0;
        for (int i = 0; i < 4000; i++) {
            int x = (i * 7_919) % (2 * HALF) - HALF, z = (i * 3_571 * 17) % (2 * HALF) - HALF;
            TerrainEngine.Column c = engine.column(x, z);
            Region r = engine.regionAt(x, z);
            assertNotNull(r);
            total++;
            if (r.biome() == c.biome()) own++;
        }
        // Only slivers of a biome smaller than the lattice fall back to the region around them.
        assertTrue(own > 0.99 * total, own + " of " + total + " columns found a region of their own biome");
        for (int gz = -40; gz < 40; gz += 3) {
            for (int gx = -40; gx < 40; gx += 3) {
                assertEquals(engine.column(gx * RegionMap.SPACING, gz * RegionMap.SPACING).biome(), engine.regions().biomeAtNode(gx, gz));
            }
        }
    }

    @Test
    void rarityTiersHaveTheirFrequencies() {
        int withTraits = 0, rare = 0, legendary = 0;
        for (Region r : sample) {
            if (r.variant().traits().isEmpty()) continue;
            withTraits++;
            if (r.rarity() == Rarity.RARE) rare++;
            if (r.rarity() == Rarity.LEGENDARY) legendary++;
        }
        double rareShare = rare / (double) withTraits;
        assertTrue(withTraits > 300, "too few regions with traits: " + withTraits);
        assertTrue(rareShare > 1 / 60.0 && rareShare < 1 / 12.0, "rare share " + rareShare + " of " + withTraits);
        assertTrue(legendary < rare / 3.0, legendary + " legendary vs " + rare + " rare");

        // Legendary regions sit exactly on their family's legend sites: at most one per family per legend square.
        for (Region r : regions) {
            Family f = Families.of(r.biome());
            if (r.rarity() != Rarity.LEGENDARY || !f.usesLegendSites()) continue;
            int lx = Math.floorDiv(r.anchorX(), f.legendSpacing()), lz = Math.floorDiv(r.anchorZ(), f.legendSpacing());
            boolean holds = false;
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int[] site = engine.regions().legendSite(r.biome(), lx + dx, lz + dz);
                    if (site != null && engine.regions().regionAtNode(site[0], site[1]).id() == r.id()) holds = true;
                }
            }
            assertTrue(holds, describe(r) + " is legendary without holding a legend site");
        }
    }

    @Test
    void legendSitesBecomeLegendaryRegions() {
        int found = 0;
        for (BiomeType b : BiomeType.VALUES) {
            if (!Families.of(b).usesLegendSites()) continue;
            for (int lz = -2; lz < 2; lz++) {
                for (int lx = -2; lx < 2; lx++) {
                    int[] site = engine.regions().legendSite(b, lx, lz);
                    if (site == null || engine.regions().biomeAtNode(site[0], site[1]) != b) continue;
                    Region r = engine.regions().regionAtNode(site[0], site[1]);
                    assertEquals(Rarity.LEGENDARY, r.rarity(), "legend site of " + b + " in " + describe(r));
                    found++;
                }
            }
        }
        assertTrue(found >= 5, "only " + found + " legend sites found");
    }

    @Test
    void spineLegendsArePeaksAboveTheClouds() {
        for (Region r : of(BiomeType.SPINE)) {
            assertEquals(r.context().maxUplift() >= Families.SPINE_CLOUD_UPLIFT, r.rarity() == Rarity.LEGENDARY, describe(r));
        }
    }

    @Test
    void contextComesFirst() {
        // Where the Scar's corruption reaches, marshes and forests turn; nowhere else.
        for (Region r : regions) {
            if (r.variant().has("corrupted") || (r.biome() == BiomeType.CANOPY_CATHEDRAL && r.variant().has("crimson"))) {
                assertTrue(r.context().maxCorruption() > 0.02, describe(r) + " is corrupted far from any Scar");
            }
        }
        // Cold ranges are mostly glaciated, mild ones mostly not.
        int coldGlaciated = 0, cold = 0, mildGlaciated = 0, mild = 0;
        for (Region r : sample) {
            if (r.biome() != BiomeType.SPINE) continue;
            if (r.context().cold() > 0.8) {
                cold++;
                if (r.variant().has("glaciated")) coldGlaciated++;
            } else if (r.context().cold() < 0.2) {
                mild++;
                if (r.variant().has("glaciated")) mildGlaciated++;
            }
        }
        assertTrue(cold >= 3 && mild >= 3, "cold " + cold + ", mild " + mild);
        assertTrue(coldGlaciated / (double) cold > mildGlaciated / (double) mild,
                "glaciated: " + coldGlaciated + "/" + cold + " cold vs " + mildGlaciated + "/" + mild + " mild ranges");
    }

    @Test
    void spineRockUsesTheRegionPalette() {
        Block[] column = new Block[WorldShape.HEIGHT];
        Set<CuratedPalette> palettes = new HashSet<>();
        int checked = 0;
        for (int z = -HALF; z < HALF; z += 61) {
            for (int x = -HALF; x < HALF; x += 61) {
                TerrainEngine.Column c = engine.column(x, z);
                // High enough for the rock band under any state, and below any snow line.
                if (c.paletteBiome() != BiomeType.SPINE || c.surfaceY() < WorldShape.SEA_LEVEL + 210 || c.surfaceY() > WorldShape.SEA_LEVEL + 220) continue;
                assertNotNull(c.paletteRegion(), "Spine column without a palette region at " + x + ", " + z);
                CuratedPalette p = c.paletteRegion().variant().palette();
                palettes.add(p);
                engine.fillColumn(x, z, column);
                for (int depth = 1; depth < 20; depth++) {
                    Block b = column[c.surfaceY() - depth - WorldShape.MIN_Y];
                    assertTrue(b == p.base() || b == p.accent() || b == p.highlight(), b + " in a " + p.id() + " range at " + x + ", " + z);
                }
                checked++;
            }
        }
        assertTrue(checked > 20, "only " + checked + " rock-band columns found");
        assertTrue(palettes.size() >= 2, "only one Spine palette in a 16k area: " + palettes);
    }

    @Test
    void namesAreVaried() {
        Set<String> names = new HashSet<>();
        for (Region r : regions) {
            assertTrue(!r.name().isBlank());
            names.add(r.name());
        }
        assertTrue(names.size() > 0.7 * regions.size(), names.size() + " distinct names for " + regions.size() + " regions");
    }
}
