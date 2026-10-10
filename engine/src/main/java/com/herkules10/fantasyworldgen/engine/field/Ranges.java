package com.herkules10.fantasyworldgen.engine.field;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.herkules10.fantasyworldgen.engine.Maths;
import com.herkules10.fantasyworldgen.engine.noise.Hashing;
import com.herkules10.fantasyworldgen.engine.noise.Rng;

/**
 * The Spine's mountain ranges as explicit ridgelines rather than noise: each range is a gently curving polyline
 * with a crest height that swells in the middle and tapers at both ends. Ranges tend to run across the prevailing
 * wind, so they cast clear rain shadows. A few vertices are landmark peaks; rarely one is legendary and rises
 * above the cloud layer.
 * <p>
 * The world is cut into {@link #CELL}-sized cells; each cell may start one range. A range never reaches further
 * than one cell from its own, so a query only looks at the 3 x 3 cells around it.
 */
public final class Ranges {
    static final int CELL = 5120;
    private static final double CHANCE = 0.6;
    private static final int VERTICES = 12;
    private static final double LANDMARK_CHANCE = 0.35;
    private static final double LEGENDARY_CHANCE = 0.06;
    private static final int CACHE_LIMIT = 65_536;

    private final long seed;
    private final double windAngle;
    private final Map<Long, Range[]> cells = new ConcurrentHashMap<>();

    /** One ridgeline. Arrays are indexed by vertex; the bounding box includes the range's flanks. */
    record Range(double[] x, double[] z, double[] crest, double halfWidth, double minX, double maxX, double minZ, double maxZ) {}

    public Ranges(long seed, Wind wind) {
        this.seed = Hashing.derive(seed, "ranges");
        this.windAngle = wind.baseAngle();
    }

    /** Height in blocks that the ranges add at (x, z): 0 away from any range, the crest height on a ridgeline. */
    public double uplift(double x, double z) {
        return query(x, z, false);
    }

    /**
     * How much a range blocks the wind at (x, z): the crest height across most of the range's width. Much flatter
     * than {@link #uplift} near the crest, so sparse samples along the wind can't slip past a sharp ridge.
     */
    public double barrier(double x, double z) {
        return query(x, z, true);
    }

    private double query(double x, double z, boolean barrier) {
        int cx = Math.floorDiv((int) Math.floor(x), CELL);
        int cz = Math.floorDiv((int) Math.floor(z), CELL);
        double best = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (Range r : rangesIn(cx + dx, cz + dz)) best = Math.max(best, height(r, x, z, barrier));
            }
        }
        return best;
    }

    /** Ranges starting in cell (cx, cz); for tests and debugging. */
    Range[] rangesIn(int cx, int cz) {
        long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
        Range[] ranges = cells.get(key);
        if (ranges == null) {
            // Pure function of the cell, so a race only computes the same value twice.
            ranges = generate(cx, cz);
            if (cells.size() > CACHE_LIMIT) cells.clear();
            cells.put(key, ranges);
        }
        return ranges;
    }

    private static double height(Range r, double x, double z, boolean barrier) {
        if (x < r.minX || x > r.maxX || z < r.minZ || z > r.maxZ) return 0;
        // Each segment raises its own ridge; the range is their maximum. Taking the crest of only the nearest segment
        // would jump where the nearest segment changes at a bend.
        double best = 0;
        for (int i = 0; i + 1 < r.x.length; i++) {
            double ax = r.x[i], az = r.z[i];
            double sx = r.x[i + 1] - ax, sz = r.z[i + 1] - az;
            double t = Maths.clamp(((x - ax) * sx + (z - az) * sz) / (sx * sx + sz * sz), 0, 1);
            double px = ax + t * sx - x, pz = az + t * sz - z;
            double s = Math.sqrt(px * px + pz * pz) / r.halfWidth;
            if (s >= 1) continue;
            double crest = Maths.lerp(r.crest[i], r.crest[i + 1], t);
            best = Math.max(best, barrier ? crest * (1 - s * s) : crest * profile(s));
        }
        return best;
    }

    /** Sharp at the crest (s = 0), easing out into the foothills (s = 1). */
    private static double profile(double s) {
        double flank = 1 - s;
        return flank * flank * (1 + s) * (1 - 0.35 * s);
    }

    private Range[] generate(int cx, int cz) {
        long h = Hashing.hash(seed, cx, cz);
        if (Maths.unit(h) >= CHANCE) return new Range[0];
        Rng rng = new Rng(h);

        double x = (cx + 0.15 + 0.7 * rng.next()) * CELL;
        double z = (cz + 0.15 + 0.7 * rng.next()) * CELL;
        double heading = rng.next() < 0.25
                ? rng.next() * 2 * Math.PI
                : windAngle + Math.PI / 2 + (rng.next() - 0.5) * 1.2; // roughly across the wind
        double step = 280 + 60 * rng.next();
        double baseCrest = 280 + 360 * rng.next();
        double halfWidth = 500 + 350 * rng.next();

        double[] xs = new double[VERTICES], zs = new double[VERTICES], crest = new double[VERTICES];
        for (int i = 0; i < VERTICES; i++) {
            xs[i] = x;
            zs[i] = z;
            double along = i / (double) (VERTICES - 1);
            double taper = Maths.pow(Maths.sin(Math.PI * along), 0.6);
            crest[i] = baseCrest * taper * (0.75 + 0.5 * rng.next());
            heading += (rng.next() - 0.5) * 0.5;
            x += Maths.cos(heading) * step;
            z += Maths.sin(heading) * step;
        }
        if (rng.next() < LANDMARK_CHANCE) crest[2 + (int) (rng.next() * (VERTICES - 4))] *= 1.35;
        if (rng.next() < LEGENDARY_CHANCE) crest[2 + (int) (rng.next() * (VERTICES - 4))] *= 1.9;

        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (int i = 0; i < VERTICES; i++) {
            minX = Math.min(minX, xs[i]);
            maxX = Math.max(maxX, xs[i]);
            minZ = Math.min(minZ, zs[i]);
            maxZ = Math.max(maxZ, zs[i]);
        }
        return new Range[] {new Range(xs, zs, crest, halfWidth, minX - halfWidth, maxX + halfWidth, minZ - halfWidth, maxZ + halfWidth)};
    }
}
