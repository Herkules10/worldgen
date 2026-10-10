package com.herkules10.fantasyworldgen.engine.field;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.herkules10.fantasyworldgen.engine.Maths;
import com.herkules10.fantasyworldgen.engine.noise.FractalNoise;
import com.herkules10.fantasyworldgen.engine.noise.Hashing;

/**
 * The global fields every biome reads: continents, mountain uplift, wind-borne moisture with rain shadows, heat
 * and corruption. They vary over thousands of blocks, so they are computed exactly on a {@link #SPACING}-block
 * lattice and interpolated in between. Lattice values are pure functions of the seed, so the tile cache only saves
 * time and never changes a result.
 */
public final class WorldFields {
    public static final int SPACING = 32;
    private static final int TILE = 16; // lattice points per tile side: 512 blocks
    private static final int CACHE_LIMIT = 4096;

    /** How far upwind the rain-shadow check looks, as steps of {@link #UPWIND_STEP} blocks. */
    private static final int UPWIND_STEPS = 36;
    private static final double UPWIND_STEP = 130;
    private static final int DOWNWIND_STEPS = 4;
    private static final double DOWNWIND_STEP = 200;

    private final FractalNoise continents;
    private final FractalNoise moistureNoise;
    private final FractalNoise heatNoise;
    private final FractalNoise dragonNoise;
    private final FractalNoise basinNoise;
    private final Wind wind;
    private final Ranges ranges;
    private final ScarSites scars;
    private final Map<Long, float[]> tiles = new ConcurrentHashMap<>();

    public WorldFields(long seed) {
        this.continents = new FractalNoise(Hashing.derive(seed, "continents"), 5, 2048);
        this.moistureNoise = new FractalNoise(Hashing.derive(seed, "moisture"), 3, 5000);
        this.heatNoise = new FractalNoise(Hashing.derive(seed, "heat"), 3, 9000);
        this.dragonNoise = new FractalNoise(Hashing.derive(seed, "dragon_mask"), 3, 2500);
        this.basinNoise = new FractalNoise(Hashing.derive(seed, "basin_mask"), 3, 2000);
        this.wind = new Wind(seed);
        this.ranges = new Ranges(seed, wind);
        this.scars = new ScarSites(seed);
    }

    public Wind wind() {
        return wind;
    }

    public Ranges ranges() {
        return ranges;
    }

    /** Fields at (x, z), bilinearly interpolated from the lattice. This is what generation uses. */
    public FieldSample sample(int x, int z) {
        int gx = Math.floorDiv(x, SPACING), gz = Math.floorDiv(z, SPACING);
        double fx = (x - gx * SPACING) / (double) SPACING;
        double fz = (z - gz * SPACING) / (double) SPACING;
        double[] v = new double[FieldSample.COUNT];
        accumulate(v, gx, gz, (1 - fx) * (1 - fz));
        accumulate(v, gx + 1, gz, fx * (1 - fz));
        accumulate(v, gx, gz + 1, (1 - fx) * fz);
        accumulate(v, gx + 1, gz + 1, fx * fz);
        return FieldSample.of(v);
    }

    private void accumulate(double[] v, int gx, int gz, double weight) {
        if (weight == 0) return;
        int tx = Math.floorDiv(gx, TILE), tz = Math.floorDiv(gz, TILE);
        float[] tile = tile(tx, tz);
        int base = ((gz - tz * TILE) * TILE + (gx - tx * TILE)) * FieldSample.COUNT;
        for (int i = 0; i < FieldSample.COUNT; i++) v[i] += weight * tile[base + i];
    }

    private float[] tile(int tx, int tz) {
        long key = ((long) tx << 32) ^ (tz & 0xFFFFFFFFL);
        float[] tile = tiles.get(key);
        if (tile == null) {
            tile = new float[TILE * TILE * FieldSample.COUNT];
            for (int lz = 0; lz < TILE; lz++) {
                for (int lx = 0; lx < TILE; lx++) {
                    FieldSample s = exact((tx * TILE + lx) * SPACING, (tz * TILE + lz) * SPACING);
                    int base = (lz * TILE + lx) * FieldSample.COUNT;
                    for (int i = 0; i < FieldSample.COUNT; i++) tile[base + i] = (float) s.get(i);
                }
            }
            if (tiles.size() > CACHE_LIMIT) tiles.clear();
            tiles.put(key, tile);
        }
        return tile;
    }

    /** Fields computed directly at (x, z), without the lattice. */
    public FieldSample exact(double x, double z) {
        double c = continents.sample(x, z);
        double uplift = ranges.uplift(x, z);
        double angle = wind.angle(x, z);
        double wx = Maths.cos(angle), wz = Maths.sin(angle);

        // Rain shadow: air that crossed high ground upwind has rained out. Nearer barriers count more.
        double barrier = 0;
        for (int k = 1; k <= UPWIND_STEPS; k++) {
            double d = k * UPWIND_STEP;
            double u = ranges.barrier(x - wx * d, z - wz * d);
            barrier = Math.max(barrier, u * (1 - d / (UPWIND_STEPS * UPWIND_STEP * 1.2)));
        }
        // Windward side: air forced up a range ahead of us rains here.
        double ahead = 0;
        for (int k = 1; k <= DOWNWIND_STEPS; k++) {
            double d = k * DOWNWIND_STEP;
            ahead = Math.max(ahead, ranges.barrier(x + wx * d, z + wz * d));
        }
        double moisture = 0.5 + 0.3 * moistureNoise.sample(x, z)
                + 0.15 * (1 - Maths.smoothstep(0.0, 0.35, c))     // coasts are wet
                - 0.15 * Maths.smoothstep(0.2, 0.6, c)              // deep interiors are drier
                - 0.8 * Maths.smoothstep(80, 350, barrier - 0.8 * uplift)
                + 0.25 * Maths.smoothstep(60, 300, ahead - uplift);

        double heat = 0.5 + 0.75 * heatNoise.sample(x, z) - uplift / 1000;

        return new FieldSample(c, uplift, Maths.clamp(moisture, 0, 1), Maths.clamp(heat, 0, 1), scars.corruption(x, z), barrier,
                Maths.smoothstep(0.15, 0.4, dragonNoise.sample(x, z)), Maths.smoothstep(0.1, 0.3, basinNoise.sample(x, z)));
    }
}
