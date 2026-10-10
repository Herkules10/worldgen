package com.herkules10.fantasyworldgen.engine.field;

import com.herkules10.fantasyworldgen.engine.Maths;
import com.herkules10.fantasyworldgen.engine.noise.FractalNoise;
import com.herkules10.fantasyworldgen.engine.noise.Hashing;

/**
 * Where the world was torn open. Scars are rare (at most one per {@link #CELL}-sized cell); each one spreads a
 * corruption field that fades over a couple of thousand blocks, and neighbouring biomes read that field.
 */
public final class ScarSites {
    static final int CELL = 12_288;
    private static final double CHANCE = 0.25;
    /** Corruption is 1 within this distance of a site... */
    private static final double CORE = 250;
    /** ...and fades to 0 at this distance (scaled per site). */
    private static final double REACH = 2400;

    private final long seed;
    /** Frays the edge of the corruption so a Scar is never a clean disc. */
    private final FractalNoise fray;

    public ScarSites(long seed) {
        this.seed = Hashing.derive(seed, "scars");
        this.fray = new FractalNoise(Hashing.derive(seed, "scar_fray"), 4, 900);
    }

    /** Corruption at (x, z), in [0, 1]. */
    public double corruption(double x, double z) {
        int cx = Math.floorDiv((int) Math.floor(x), CELL);
        int cz = Math.floorDiv((int) Math.floor(z), CELL);
        double frayed = 700 * fray.sample(x, z);
        double best = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                long h = Hashing.hash(seed, cx + dx, cz + dz);
                if (Maths.unit(h) >= CHANCE) continue;
                double sx = (cx + dx + 0.2 + 0.6 * Maths.unit(Hashing.mix(h + 1))) * CELL;
                double sz = (cz + dz + 0.2 + 0.6 * Maths.unit(Hashing.mix(h + 2))) * CELL;
                double size = 0.8 + 0.5 * Maths.unit(Hashing.mix(h + 3));
                double ddx = x - sx, ddz = z - sz;
                double d = (Math.sqrt(ddx * ddx + ddz * ddz) + frayed) / size;
                best = Math.max(best, 1 - Maths.smoothstep(CORE, REACH, d));
            }
        }
        return best;
    }
}
