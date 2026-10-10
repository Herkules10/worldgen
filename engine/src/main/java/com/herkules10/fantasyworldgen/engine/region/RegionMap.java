package com.herkules10.fantasyworldgen.engine.region;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.herkules10.fantasyworldgen.engine.Maths;
import com.herkules10.fantasyworldgen.engine.biome.BiomePlacement;
import com.herkules10.fantasyworldgen.engine.biome.BiomeType;
import com.herkules10.fantasyworldgen.engine.field.FieldSample;
import com.herkules10.fantasyworldgen.engine.field.WorldFields;
import com.herkules10.fantasyworldgen.engine.noise.Hashing;
import com.herkules10.fantasyworldgen.engine.variant.Families;
import com.herkules10.fantasyworldgen.engine.variant.Family;
import com.herkules10.fantasyworldgen.engine.variant.RegionContext;
import com.herkules10.fantasyworldgen.engine.variant.VariantRoller;

/**
 * Cuts the world into regions: contiguous areas of one biome family, each with its own variant.
 * <p>
 * <b>Design.</b> The dominant biome is sampled on a {@link #SPACING}-block lattice (the same lattice as
 * {@link WorldFields}, so field samples there are exact). Each family has its own jittered-grid Voronoi partition
 * whose spacing is the family's natural unit ({@link Family#regionSpacing()}: a range, a creature, a wound). A region
 * is a 4-connected component of the lattice nodes that carry the family's biome <i>and</i> fall into the same Voronoi
 * cell. So:
 * <ul>
 * <li>regions are contiguous by construction (connected components), and never mix biomes;</li>
 * <li>huge biome areas (the Wilds, the oceans, long ranges) are split into region-sized pieces with organic borders,
 * while a biome patch smaller than a cell usually stays one region;</li>
 * <li>everything is a pure function of the seed: a node's cell and label depend only on its coordinates, and a
 * component never leaves its cell, so computing one cell needs only that cell's window (3 x 3 grid squares).</li>
 * </ul>
 * Pure connected components of a biome would be unbounded (one ocean spans the world); a plain Voronoi partition
 * would cut one biome patch into unrelated fragments or glue separate patches together. Intersecting the two keeps
 * the work bounded and the regions meaningful. The one artefact, slivers that a cell edge cuts off a larger patch,
 * is removed by letting components under {@link #MIN_REGION_NODES} join the large region they touch across the edge
 * (they keep it contiguous; its statistics ignore them).
 * <p>
 * Columns near the edge between two regions of one biome prefer the cell they lie in, so such borders follow the
 * straight Voronoi edges rather than the lattice's staircase. Legendary regions of most families hold a legend site:
 * the first of a few dozen seeded points in a {@link Family#legendSpacing()} square that lies well inside the biome.
 * <p>
 * A region's id is a hash of the seed and its anchor node (its first node in scan order), so it is stable however
 * and wherever it is queried. Columns find their region as the nearest lattice node of their own biome; the palette
 * lookup adds a little per-column jitter so neighbouring regions dither into each other instead of meeting in a line.
 * <p>
 * All caches hold pure-function results (computed outside any lock; a race computes the same value twice).
 */
public final class RegionMap {
    public static final int SPACING = WorldFields.SPACING;
    private static final int TILE = 16; // nodes per lattice tile side: 512 blocks
    /**
     * Components with fewer nodes than this (a 128-block square) that touch a large region of their biome across a
     * cell border join that region instead of standing alone: slivers cut off by the partition are not places.
     */
    public static final int MIN_REGION_NODES = 16;
    /** Seeded tries per legend square to find a point well inside the family's biome. */
    private static final int LEGEND_TRIES = 64;
    /** A legend site needs its biome this many nodes away in all four directions, so it never lands on a sliver. */
    private static final int LEGEND_CORE = 4;
    /** Seed points sit within the middle {@code JITTER} of their grid square. */
    private static final double JITTER = 0.7;
    /** How far (in rings of nodes) a column looks for a node of its biome. */
    private static final int MAX_SEARCH_RING = 6;
    /** Width in blocks of the dithered band between two regions' palettes. */
    private static final double DITHER = 24;
    /** Extra distance in blocks for a node outside the column's own partition cell. */
    private static final double CELL_PENALTY = 3 * SPACING;
    private static final int TILE_CACHE_LIMIT = 8192;
    private static final int CELL_CACHE_LIMIT = 512;
    private static final int REGION_CACHE_LIMIT = 1024;
    private static final long NO_SITE = Long.MAX_VALUE;

    private final long cellSeed;
    private final long idSeed;
    private final long legendSeed;
    private final long ditherSeed;
    private final WorldFields fields;
    private final Map<Long, NodeTile> tiles = new ConcurrentHashMap<>();
    private final Map<CellKey, Cell> cells = new ConcurrentHashMap<>();
    private final Map<CellKey, CellRegions> regions = new ConcurrentHashMap<>();
    private final Map<CellKey, Long> legendSites = new ConcurrentHashMap<>();

    public RegionMap(long seed, WorldFields fields) {
        this.cellSeed = Hashing.derive(seed, "region_cells");
        this.idSeed = Hashing.derive(seed, "region_ids");
        this.legendSeed = Hashing.derive(seed, "legend_sites");
        this.ditherSeed = Hashing.derive(seed, "region_dither");
        this.fields = fields;
    }

    // ---------------------------------------------------------------- queries

    /**
     * The region of {@code biome} that column (x, z) belongs to: the one owning the nearest node of that biome. A
     * sliver of a biome too small to hold a lattice node nearby belongs to the region around it (the nearest node's).
     */
    public Region regionAt(int x, int z, BiomeType biome) {
        Region r = search(x, z, biome, false);
        if (r != null) return r;
        int gx = Math.floorDiv(x + SPACING / 2, SPACING), gz = Math.floorDiv(z + SPACING / 2, SPACING);
        return regionAtNode(gx, gz);
    }

    /**
     * Like {@link #regionAt}, but jittered per column so that near a border either region may win: palettes dither
     * across a band about {@link #DITHER} blocks wide. Null if no node of the biome is within reach.
     */
    public Region paletteRegionAt(int x, int z, BiomeType biome) {
        return search(x, z, biome, true);
    }

    /** Dominant biome at lattice node (gx, gz), i.e. at block (gx * SPACING, gz * SPACING). */
    public BiomeType biomeAtNode(int gx, int gz) {
        NodeTile t = tile(Math.floorDiv(gx, TILE), Math.floorDiv(gz, TILE));
        return BiomeType.VALUES[t.biome[tileIndex(gx, gz)]];
    }

    /** The region lattice node (gx, gz) belongs to. */
    public Region regionAtNode(int gx, int gz) {
        BiomeType biome = biomeAtNode(gx, gz);
        long owner = owner(Families.of(biome), gx, gz);
        int cx = unpackX(owner), cz = unpackZ(owner);
        int index = cell(biome, cx, cz).componentAt(gx, gz);
        CellRegions cr = regions(biome, cx, cz);
        if (cr.regions[index] != null) return cr.regions[index];
        // An absorbed fragment: the region it touches across the cell border, which is never itself absorbed.
        long link = cr.absorbedInto[index];
        BiomeType linked = biomeAtNode(unpackX(link), unpackZ(link));
        long linkOwner = owner(Families.of(linked), unpackX(link), unpackZ(link));
        int lcx = unpackX(linkOwner), lcz = unpackZ(linkOwner);
        return regions(linked, lcx, lcz).regions[cell(linked, lcx, lcz).componentAt(unpackX(link), unpackZ(link))];
    }

    /** The regions of {@code biome} in partition cell (cx, cz), in anchor order (absorbed fragments left out). */
    public List<Region> regionsInCell(BiomeType biome, int cx, int cz) {
        List<Region> out = new ArrayList<>();
        for (Region r : regions(biome, cx, cz).regions) if (r != null) out.add(r);
        return out;
    }

    /**
     * Every region with at least one lattice node in the block rectangle [minX, maxX] x [minZ, maxZ], in the order a
     * row-by-row scan of the lattice first meets them. Only the cells of biomes actually present get computed.
     */
    public List<Region> regionsIn(int minX, int minZ, int maxX, int maxZ) {
        Map<Long, Region> seen = new LinkedHashMap<>();
        for (int gz = Math.floorDiv(minZ + SPACING - 1, SPACING); gz <= Math.floorDiv(maxZ, SPACING); gz++) {
            Region last = null;
            for (int gx = Math.floorDiv(minX + SPACING - 1, SPACING); gx <= Math.floorDiv(maxX, SPACING); gx++) {
                Region r = regionAtNode(gx, gz);
                if (r != last) seen.putIfAbsent(r.id(), r);
                last = r;
            }
        }
        return new ArrayList<>(seen.values());
    }

    /** Lattice node of the family's legend site in legend square (lx, lz), or null if that square has none. */
    public int[] legendSite(BiomeType biome, int lx, int lz) {
        long site = legendSiteNode(biome, lx, lz);
        return site == NO_SITE ? null : new int[] {unpackX(site), unpackZ(site)};
    }

    private Region search(int x, int z, BiomeType biome, boolean dither) {
        int gx0 = Math.floorDiv(x, SPACING), gz0 = Math.floorDiv(z, SPACING);
        long columnHash = dither ? Hashing.hash(ditherSeed, x, z) : 0;
        Family family = Families.of(biome);
        long columnCell = ownerAt(family, x, z);
        int bestX = 0, bestZ = 0, foundRing = -1;
        double best = Double.MAX_VALUE;
        for (int r = 1; r <= MAX_SEARCH_RING; r++) {
            // Ring r: the square [g0 - r + 1, g0 + r] minus the square of ring r - 1.
            for (int gz = gz0 - r + 1; gz <= gz0 + r; gz++) {
                for (int gx = gx0 - r + 1; gx <= gx0 + r; gx++) {
                    boolean inner = gx > gx0 - r + 1 && gx < gx0 + r && gz > gz0 - r + 1 && gz < gz0 + r;
                    if (inner || biomeAtNode(gx, gz) != biome) continue;
                    double dx = (double) gx * SPACING - x, dz = (double) gz * SPACING - z;
                    double d = Math.sqrt(dx * dx + dz * dz);
                    // Prefer nodes of the partition cell the column itself lies in: borders between two regions of
                    // one biome then follow the straight cell edge instead of the lattice's staircase.
                    if (owner(family, gx, gz) != columnCell) d += CELL_PENALTY;
                    if (dither) d += DITHER * Maths.unit(Hashing.mix(columnHash ^ Hashing.hash(ditherSeed, gx, gz)));
                    if (d < best) {
                        best = d;
                        bestX = gx;
                        bestZ = gz;
                    }
                }
            }
            if (foundRing >= 0) break;            // looked one ring further than the first hit
            if (best < Double.MAX_VALUE) foundRing = r;
        }
        return foundRing < 0 ? null : regionAtNode(bestX, bestZ);
    }

    // ---------------------------------------------------------------- lattice labels

    private record NodeTile(byte[] biome, float[] uplift) {}

    private NodeTile tile(int tx, int tz) {
        long key = pack(tx, tz);
        NodeTile tile = tiles.get(key);
        if (tile == null) {
            byte[] biome = new byte[TILE * TILE];
            float[] uplift = new float[TILE * TILE];
            double[] weights = new double[BiomeType.VALUES.length];
            for (int lz = 0; lz < TILE; lz++) {
                for (int lx = 0; lx < TILE; lx++) {
                    int x = (tx * TILE + lx) * SPACING, z = (tz * TILE + lz) * SPACING;
                    FieldSample f = fields.sample(x, z);
                    double u = fields.ranges().uplift(x, z);
                    biome[lz * TILE + lx] = (byte) BiomePlacement.biomeAt(f, u, weights).ordinal();
                    uplift[lz * TILE + lx] = (float) u;
                }
            }
            tile = new NodeTile(biome, uplift);
            if (tiles.size() > TILE_CACHE_LIMIT) tiles.clear();
            tiles.put(key, tile);
        }
        return tile;
    }

    /** Biome at a node computed without the tile caches (for sparse probes). */
    private BiomeType biomeDirect(int gx, int gz) {
        double x = (double) gx * SPACING, z = (double) gz * SPACING;
        return BiomePlacement.biomeAt(fields.exact(x, z), fields.ranges().uplift(x, z), new double[BiomeType.VALUES.length]);
    }

    private static int tileIndex(int gx, int gz) {
        return Math.floorMod(gz, TILE) * TILE + Math.floorMod(gx, TILE);
    }

    private double upliftAtNode(int gx, int gz) {
        return tile(Math.floorDiv(gx, TILE), Math.floorDiv(gz, TILE)).uplift[tileIndex(gx, gz)];
    }

    // ---------------------------------------------------------------- partition cells

    /** Seed point of the family's grid square (cx, cz), in blocks: {x, z}. */
    private double[] center(Family family, int cx, int cz) {
        long h = Hashing.hash(cellSeed ^ Hashing.mix(family.biome().ordinal() + 1L), cx, cz);
        double margin = (1 - JITTER) / 2;
        return new double[] {
                (cx + margin + JITTER * Maths.unit(Hashing.mix(h + 1))) * family.regionSpacing(),
                (cz + margin + JITTER * Maths.unit(Hashing.mix(h + 2))) * family.regionSpacing()};
    }

    /** The family's cell owning node (gx, gz): the nearest seed point among the 3 x 3 grid squares around it. */
    private long owner(Family family, int gx, int gz) {
        return ownerAt(family, gx * SPACING, gz * SPACING);
    }

    /** The family's cell owning block (x, z); {@link #owner} for any position, not only lattice nodes. */
    private long ownerAt(Family family, int x, int z) {
        int g = family.regionSpacing();
        int sx = Math.floorDiv(x, g), sz = Math.floorDiv(z, g);
        double best = Double.MAX_VALUE;
        long owner = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                double[] c = center(family, sx + dx, sz + dz);
                double d = (c[0] - x) * (c[0] - x) + (c[1] - z) * (c[1] - z);
                if (d < best) {
                    best = d;
                    owner = pack(sx + dx, sz + dz);
                }
            }
        }
        return owner;
    }

    private record CellKey(int biome, int cx, int cz) {}

    /** Phase one of a cell: its components and their statistics (no variants, so it never needs another cell). */
    private static final class Cell {
        final int originX, originZ, width, height;
        final short[] component; // over the members' bounding box; -1 = not in this cell
        final Component[] components;

        Cell(int originX, int originZ, int width, int height, short[] component, Component[] components) {
            this.originX = originX;
            this.originZ = originZ;
            this.width = width;
            this.height = height;
            this.component = component;
            this.components = components;
        }

        int componentAt(int gx, int gz) {
            int lx = gx - originX, lz = gz - originZ;
            if (lx < 0 || lz < 0 || lx >= width || lz >= height) return -1;
            return component[lz * width + lx];
        }
    }

    /** Running statistics of one component; sums are accumulated in a fixed (BFS) order, so they are reproducible. */
    private static final class Component {
        final int anchorX, anchorZ;
        int nodes;
        double sumX, sumZ;
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        double heat, moisture, corruption, maxCorruption, uplift, maxUplift, continentalness;
        final int[] contacts = new int[BiomeType.VALUES.length];
        int contactTotal;
        /** First node of the same biome across the cell border (in BFS order), or {@link #NO_SITE}. */
        long link = NO_SITE;

        Component(int anchorX, int anchorZ) {
            this.anchorX = anchorX;
            this.anchorZ = anchorZ;
        }

        RegionContext context() {
            double[] neighbours = new double[contacts.length];
            for (int i = 0; i < contacts.length; i++) neighbours[i] = contactTotal == 0 ? 0 : contacts[i] / (double) contactTotal;
            return new RegionContext(heat / nodes, moisture / nodes, corruption / nodes, maxCorruption, uplift / nodes, maxUplift,
                    continentalness / nodes, (long) nodes * SPACING * SPACING, neighbours);
        }
    }

    private Cell cell(BiomeType biome, int cx, int cz) {
        CellKey key = new CellKey(biome.ordinal(), cx, cz);
        Cell cell = cells.get(key);
        if (cell == null) {
            cell = computeCell(biome, cx, cz);
            if (cells.size() > CELL_CACHE_LIMIT) cells.clear();
            cells.put(key, cell);
        }
        return cell;
    }

    private Cell computeCell(BiomeType biome, int cx, int cz) {
        Family family = Families.of(biome);
        int g = family.regionSpacing();
        // Nodes whose 3 x 3 neighbourhood of grid squares includes (cx, cz): the only ones this cell can own.
        int gx0 = Math.floorDiv((cx - 1) * g + SPACING - 1, SPACING), gx1 = Math.floorDiv((cx + 2) * g - 1, SPACING);
        int gz0 = Math.floorDiv((cz - 1) * g + SPACING - 1, SPACING), gz1 = Math.floorDiv((cz + 2) * g - 1, SPACING);
        int w = gx1 - gx0 + 1, h = gz1 - gz0 + 1;
        double[][] centers = new double[25][];
        for (int i = 0; i < 25; i++) centers[i] = center(family, cx - 2 + i % 5, cz - 2 + i / 5);
        long self = pack(cx, cz);

        boolean[] member = new boolean[w * h];
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int gz = gz0; gz <= gz1; gz++) {
            for (int gx = gx0; gx <= gx1; gx++) {
                if (ownerFrom(centers, cx, cz, g, gx, gz) != self || biomeAtNode(gx, gz) != biome) continue;
                member[(gz - gz0) * w + gx - gx0] = true;
                minX = Math.min(minX, gx);
                maxX = Math.max(maxX, gx);
                minZ = Math.min(minZ, gz);
                maxZ = Math.max(maxZ, gz);
            }
        }
        if (minX > maxX) return new Cell(0, 0, 0, 0, new short[0], new Component[0]);

        int bw = maxX - minX + 1, bh = maxZ - minZ + 1;
        short[] comp = new short[bw * bh];
        java.util.Arrays.fill(comp, (short) -1);
        List<Component> components = new ArrayList<>();
        int[] queue = new int[bw * bh];
        int[] dirX = {1, -1, 0, 0}, dirZ = {0, 0, 1, -1};
        for (int lz = 0; lz < bh; lz++) {
            for (int lx = 0; lx < bw; lx++) {
                int start = lz * bw + lx;
                if (comp[start] != -1 || !member[(lz + minZ - gz0) * w + lx + minX - gx0]) continue;
                if (components.size() >= Short.MAX_VALUE) throw new IllegalStateException("too many components in one cell");
                short id = (short) components.size();
                Component c = new Component(lx + minX, lz + minZ);
                components.add(c);
                int head = 0, tail = 0;
                queue[tail++] = start;
                comp[start] = id;
                while (head < tail) {
                    int i = queue[head++];
                    int nx = i % bw + minX, nz = i / bw + minZ;
                    accumulate(c, nx, nz);
                    for (int d = 0; d < 4; d++) {
                        int ax = nx + dirX[d], az = nz + dirZ[d];
                        int bx = ax - minX, bz = az - minZ;
                        boolean inBox = bx >= 0 && bz >= 0 && bx < bw && bz < bh;
                        if (inBox && member[(az - gz0) * w + ax - gx0]) {
                            int j = bz * bw + bx;
                            if (comp[j] == -1) {
                                comp[j] = id;
                                queue[tail++] = j;
                            }
                        } else {
                            // A border: count which biome lies across it (the same biome means another cell's region).
                            BiomeType across = biomeAtNode(ax, az);
                            c.contacts[across.ordinal()]++;
                            c.contactTotal++;
                            if (across == biome && c.link == NO_SITE) c.link = pack(ax, az);
                        }
                    }
                }
            }
        }
        return new Cell(minX, minZ, bw, bh, comp, components.toArray(new Component[0]));
    }

    /** {@link #owner} with the seed points of the 5 x 5 squares around (cx, cz) precomputed; same result. */
    private static long ownerFrom(double[][] centers, int cx, int cz, int g, int gx, int gz) {
        double x = (double) gx * SPACING, z = (double) gz * SPACING;
        int sx = Math.floorDiv(gx * SPACING, g), sz = Math.floorDiv(gz * SPACING, g);
        double best = Double.MAX_VALUE;
        long owner = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                double[] c = centers[(sz + dz - cz + 2) * 5 + (sx + dx - cx + 2)];
                double d = (c[0] - x) * (c[0] - x) + (c[1] - z) * (c[1] - z);
                if (d < best) {
                    best = d;
                    owner = pack(sx + dx, sz + dz);
                }
            }
        }
        return owner;
    }

    private void accumulate(Component c, int gx, int gz) {
        FieldSample f = fields.sample(gx * SPACING, gz * SPACING);
        double uplift = upliftAtNode(gx, gz);
        c.nodes++;
        c.sumX += gx * SPACING;
        c.sumZ += gz * SPACING;
        c.minX = Math.min(c.minX, gx);
        c.maxX = Math.max(c.maxX, gx);
        c.minZ = Math.min(c.minZ, gz);
        c.maxZ = Math.max(c.maxZ, gz);
        c.heat += f.heat();
        c.moisture += f.moisture();
        c.corruption += f.corruption();
        c.maxCorruption = Math.max(c.maxCorruption, f.corruption());
        c.uplift += uplift;
        c.maxUplift = Math.max(c.maxUplift, uplift);
        c.continentalness += f.continentalness();
    }

    // ---------------------------------------------------------------- regions (phase two: variants)

    /**
     * Phase two of a cell: a region per component, or for an absorbed fragment null and the node it joins through.
     * Reads only this cell and the phase one of neighbouring cells, so there is no recursion.
     */
    private record CellRegions(Region[] regions, long[] absorbedInto) {}

    private CellRegions regions(BiomeType biome, int cx, int cz) {
        CellKey key = new CellKey(biome.ordinal(), cx, cz);
        CellRegions out = regions.get(key);
        if (out == null) {
            Cell cell = cell(biome, cx, cz);
            Family family = Families.of(biome);
            Region[] list = new Region[cell.components.length];
            long[] absorbedInto = new long[list.length];
            for (int i = 0; i < list.length; i++) {
                Component c = cell.components[i];
                absorbedInto[i] = NO_SITE;
                if (c.nodes < MIN_REGION_NODES && c.link != NO_SITE && joinsLargeRegion(biome, c.link)) {
                    absorbedInto[i] = c.link;
                    continue;
                }
                RegionContext context = c.context();
                long id = Hashing.hash(idSeed, c.anchorX, c.anchorZ);
                boolean site = family.usesLegendSites() && holdsLegendSite(family, cell, i, c);
                list[i] = new Region(id, biome, cx, cz, c.anchorX * SPACING, c.anchorZ * SPACING, c.nodes,
                        c.sumX / c.nodes, c.sumZ / c.nodes, c.minX * SPACING, c.minZ * SPACING, c.maxX * SPACING, c.maxZ * SPACING,
                        context, VariantRoller.roll(family, context, id, site));
            }
            out = new CellRegions(list, absorbedInto);
            if (regions.size() > REGION_CACHE_LIMIT) regions.clear();
            regions.put(key, out);
        }
        return out;
    }

    /** True if node {@code link} (of {@code biome}, in another cell) belongs to a component that stands as a region. */
    private boolean joinsLargeRegion(BiomeType biome, long link) {
        int gx = unpackX(link), gz = unpackZ(link);
        long owner = owner(Families.of(biome), gx, gz);
        Cell other = cell(biome, unpackX(owner), unpackZ(owner));
        return other.components[other.componentAt(gx, gz)].nodes >= MIN_REGION_NODES;
    }

    private boolean holdsLegendSite(Family family, Cell cell, int index, Component c) {
        int size = family.legendSpacing();
        for (int lz = Math.floorDiv(c.minZ * SPACING, size); lz <= Math.floorDiv(c.maxZ * SPACING, size); lz++) {
            for (int lx = Math.floorDiv(c.minX * SPACING, size); lx <= Math.floorDiv(c.maxX * SPACING, size); lx++) {
                long site = legendSiteNode(family.biome(), lx, lz);
                if (site != NO_SITE && cell.componentAt(unpackX(site), unpackZ(site)) == index) return true;
            }
        }
        return false;
    }

    /**
     * The family's legend site in legend square (lx, lz): the first of a fixed list of seeded points that lies well
     * inside the biome. Only reads lattice labels, never another cell, so there is no recursion.
     */
    private long legendSiteNode(BiomeType biome, int lx, int lz) {
        CellKey key = new CellKey(biome.ordinal(), lx, lz);
        Long cached = legendSites.get(key);
        if (cached != null) return cached;
        long site = NO_SITE;
        int size = Families.of(biome).legendSpacing();
        long h = Hashing.hash(legendSeed ^ Hashing.mix(biome.ordinal() + 1L), lx, lz);
        for (int k = 0; k < LEGEND_TRIES && site == NO_SITE; k++) {
            int gx = Math.floorDiv((int) Math.floor((lx + Maths.unit(Hashing.mix(h + 2L * k))) * size), SPACING);
            int gz = Math.floorDiv((int) Math.floor((lz + Maths.unit(Hashing.mix(h + 2L * k + 1))) * size), SPACING);
            // Direct samples: far cheaper than computing whole lattice tiles at scattered points. They are not rounded
            // like the tiles, so the site node may rarely carry another label; then the square just has no legend.
            if (biomeDirect(gx, gz) == biome && biomeDirect(gx + LEGEND_CORE, gz) == biome && biomeDirect(gx - LEGEND_CORE, gz) == biome
                    && biomeDirect(gx, gz + LEGEND_CORE) == biome && biomeDirect(gx, gz - LEGEND_CORE) == biome) {
                site = pack(gx, gz);
            }
        }
        if (legendSites.size() > REGION_CACHE_LIMIT) legendSites.clear();
        legendSites.put(key, site);
        return site;
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    private static int unpackZ(long packed) {
        return (int) packed;
    }
}
