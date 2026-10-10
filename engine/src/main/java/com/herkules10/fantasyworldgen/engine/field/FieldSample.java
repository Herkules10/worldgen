package com.herkules10.fantasyworldgen.engine.field;

/**
 * The shared world fields at one point. Every biome reads the same fields, so neighbouring regions agree on the
 * same story.
 *
 * @param continentalness below about 0 is ocean, above is land; roughly [-1, 1]
 * @param uplift          height the mountain ranges add, in blocks (0 away from ranges)
 * @param moisture        [0, 1]; low in the rain shadow of ranges, high on their windward side and near coasts
 * @param heat            [0, 1]; falls with altitude
 * @param corruption      [0, 1]; 1 at a Scar, fading outwards
 * @param upwindBarrier   highest uplift the wind crossed on its way here (distance-weighted), in blocks
 * @param dragonMask      [0, 1] rarity mask: where a Dragonscale Hide may lie
 * @param basinMask       [0, 1] rarity mask: where a Stilled Colossi basin may lie
 */
public record FieldSample(double continentalness, double uplift, double moisture, double heat, double corruption,
                          double upwindBarrier, double dragonMask, double basinMask) {
    static final int COUNT = 8;

    double get(int i) {
        return switch (i) {
            case 0 -> continentalness;
            case 1 -> uplift;
            case 2 -> moisture;
            case 3 -> heat;
            case 4 -> corruption;
            case 5 -> upwindBarrier;
            case 6 -> dragonMask;
            case 7 -> basinMask;
            default -> throw new IndexOutOfBoundsException(i);
        };
    }

    static FieldSample of(double[] v) {
        return new FieldSample(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7]);
    }
}
