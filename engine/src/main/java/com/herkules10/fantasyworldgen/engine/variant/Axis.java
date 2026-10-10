package com.herkules10.fantasyworldgen.engine.variant;

import java.util.Locale;

/**
 * The rolled axes along which a biome family varies (Artistic_Direction.md, "Variation"). The fifth axis, context, is
 * not rolled: it is the {@link RegionContext} every roll reads first.
 */
public enum Axis {
    /** Which colours and block families. */
    PALETTE,
    /** Which shapes, proportions and silhouettes. */
    FORM,
    /** How big, how dense, how extreme. */
    SCALE,
    /** How old, how worn, what happened since. */
    STATE;

    public static final Axis[] VALUES = values();

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
