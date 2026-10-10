package com.herkules10.fantasyworldgen.engine;

import com.herkules10.fantasyworldgen.engine.biome.BiomePlacement;
import com.herkules10.fantasyworldgen.engine.biome.BiomeType;
import com.herkules10.fantasyworldgen.engine.biome.Landforms;
import com.herkules10.fantasyworldgen.engine.biome.Palettes;
import com.herkules10.fantasyworldgen.engine.field.FieldSample;
import com.herkules10.fantasyworldgen.engine.field.WorldFields;
import com.herkules10.fantasyworldgen.engine.noise.Hashing;
import com.herkules10.fantasyworldgen.engine.region.Region;
import com.herkules10.fantasyworldgen.engine.region.RegionMap;
import com.herkules10.fantasyworldgen.engine.variant.Families;

/**
 * Entry point of the terrain engine: everything about the world is a pure function of the seed and the coordinates.
 * Instances are thread-safe; chunks can be generated in any order, on any thread.
 * <p>
 * Pipeline per column: shared {@link WorldFields} (continents, ranges, wind and rain shadow, heat, corruption) →
 * biome blend weights ({@link BiomePlacement}) → blended landform height ({@link Landforms}) → region and variant
 * ({@link RegionMap}) → surface blocks ({@link Palettes}). Landforms and most palettes are still placeholders.
 */
public final class TerrainEngine {
    private final long seed;
    private final long paletteSeed;
    private final WorldFields fields;
    private final Landforms landforms;
    private final RegionMap regions;

    /**
     * One column of the world.
     *
     * @param surfaceY     y of the highest solid block (water above it, if any, is not counted)
     * @param biome        dominant biome; what Minecraft is told
     * @param paletteBiome  biome whose blocks the surface uses; dithered across transitions so palettes don't meet in
     *                      a hard line
     * @param paletteRegion region whose curated palette the surface uses, also dithered across region borders; null
     *                      if the palette biome has no curated palettes yet
     * @param land          share of land vs ocean in [0, 1]
     */
    public record Column(int x, int z, int surfaceY, BiomeType biome, BiomeType paletteBiome, Region paletteRegion, double land,
                         FieldSample fields) {}

    /**
     * Where new worlds put the player.
     *
     * @param y     feet height: one above the surface block
     * @param biome dominant biome of the spawn column
     */
    public record Spawn(int x, int y, int z, BiomeType biome) {}

    public TerrainEngine(long seed) {
        this.seed = seed;
        this.paletteSeed = Hashing.derive(seed, "palette_dither");
        this.fields = new WorldFields(seed);
        this.landforms = new Landforms(seed);
        this.regions = new RegionMap(seed, fields);
    }

    public long seed() {
        return seed;
    }

    public WorldFields fields() {
        return fields;
    }

    public Column column(int x, int z) {
        FieldSample f = fields.sample(x, z);
        double uplift = fields.ranges().uplift(x, z);
        double windAngle = fields.wind().angle(x, z);
        double land = BiomePlacement.land(f, uplift);
        double[] weights = new double[BiomeType.VALUES.length];
        BiomePlacement.landWeights(f, uplift, weights);

        double landHeight = 0;
        for (BiomeType b : BiomeType.VALUES) {
            double w = weights[b.ordinal()];
            if (w > 1e-4) landHeight += w * landforms.land(b, x, z, f, uplift, windAngle);
        }
        double height = WorldShape.SEA_LEVEL + land * landHeight + (1 - land) * landforms.ocean(x, z, f);
        int surface = (int) Math.max(WorldShape.MIN_Y + 1, Math.min(WorldShape.MAX_Y - 1, Math.floor(height)));

        BiomeType biome = land < 0.5 ? BiomeType.OCEAN : BiomePlacement.dominant(weights);
        BiomeType palette = land < 0.5 ? BiomeType.OCEAN : pick(weights, Maths.unit(Hashing.hash(paletteSeed, x, z)));
        // Only families with curated palettes need their region per column; the rest skip the lookup.
        Region paletteRegion = Families.of(palette).hasCuratedPalettes() ? regions.paletteRegionAt(x, z, palette) : null;
        return new Column(x, z, surface, biome, palette, paletteRegion, land, f);
    }

    /** The region partition and variants of this world. */
    public RegionMap regions() {
        return regions;
    }

    /** The region of the dominant biome at (x, z): its variant, traits and place name. */
    public Region regionAt(int x, int z) {
        return regions.regionAt(x, z, biomeAt(x, z));
    }

    /** Y of the highest solid block in the column (water above it, if any, is not counted). */
    public int surfaceY(int x, int z) {
        return column(x, z).surfaceY();
    }

    /** The dominant biome at (x, z). */
    public BiomeType biomeAt(int x, int z) {
        return column(x, z).biome();
    }

    /**
     * The world spawn: dry, gently sloped land near the origin, preferably in the calm Wilds. Searches a fixed spiral
     * outward from 0, 0, so it is a pure function of the seed. Costs up to some tens of thousands of column samples;
     * call once per world.
     */
    public Spawn findSpawn() {
        return SpawnFinder.find(this);
    }

    public Block blockAt(int x, int y, int z) {
        return block(column(x, z), y);
    }

    /** Block at height {@code y} of an already sampled column. */
    public static Block block(Column c, int y) {
        return columnBlock(c, y);
    }

    /**
     * Writes column (x, z) into {@code out}, indexed {@code y - WorldShape.MIN_Y}.
     *
     * @return the surface y, as {@link #surfaceY}
     */
    public int fillColumn(int x, int z, Block[] out) {
        if (out.length != WorldShape.HEIGHT) throw new IllegalArgumentException("column must have " + WorldShape.HEIGHT + " entries");
        Column c = column(x, z);
        for (int y = WorldShape.MIN_Y; y <= WorldShape.MAX_Y; y++) out[y - WorldShape.MIN_Y] = columnBlock(c, y);
        return c.surfaceY();
    }

    /** Generates chunk (chunkX, chunkZ), i.e. blocks x in [16 chunkX, 16 chunkX + 16), into a cleared {@code out}. */
    public void fillChunk(int chunkX, int chunkZ, ChunkBuffer out) {
        out.clear();
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                Column c = column((chunkX << 4) + lx, (chunkZ << 4) + lz);
                for (int y = WorldShape.MIN_Y, end = Math.max(c.surfaceY(), WorldShape.SEA_LEVEL - 1); y <= end; y++) {
                    out.set(lx, y, lz, columnBlock(c, y));
                }
            }
        }
    }

    private static Block columnBlock(Column c, int y) {
        int surface = c.surfaceY();
        if (y > surface) return y < WorldShape.SEA_LEVEL ? Block.WATER : Block.AIR;
        if (y == WorldShape.MIN_Y) return Block.BEDROCK;
        Block b = Palettes.block(c.paletteBiome(), c.paletteRegion(), c.x(), y, c.z(), surface);
        if (b != null) return b;
        return y < 0 ? Block.DEEPSLATE : Block.STONE;
    }

    /** Weighted pick: biome i with probability weights[i] (they sum to 1). */
    private static BiomeType pick(double[] weights, double r) {
        double acc = 0;
        for (int i = 0; i < weights.length; i++) {
            acc += weights[i];
            if (r < acc) return BiomeType.VALUES[i];
        }
        return BiomePlacement.dominant(weights);
    }
}
