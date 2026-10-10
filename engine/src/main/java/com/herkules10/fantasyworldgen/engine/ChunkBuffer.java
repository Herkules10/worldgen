package com.herkules10.fantasyworldgen.engine;

import java.util.Arrays;

/**
 * The blocks of one 16 x {@link WorldShape#HEIGHT} x 16 chunk. Sections are allocated on first write, so the mostly
 * empty sky of a tall world costs nothing; an unallocated section is all air.
 */
public final class ChunkBuffer {
    private static final int SECTION_VOLUME = 16 * 16 * 16;

    private final byte[][] sections = new byte[WorldShape.SECTION_COUNT][];

    public Block get(int localX, int y, int localZ) {
        byte[] section = sections[sectionIndex(y)];
        return section == null ? Block.AIR : Block.byId(section[index(localX, y, localZ)]);
    }

    public void set(int localX, int y, int localZ, Block block) {
        int s = sectionIndex(y);
        byte[] section = sections[s];
        if (section == null) {
            if (block == Block.AIR) return;
            section = sections[s] = new byte[SECTION_VOLUME]; // zero is AIR
        }
        section[index(localX, y, localZ)] = (byte) block.id();
    }

    /** True if section {@code index} (0 = the section at {@link WorldShape#MIN_Y}) was never written. */
    public boolean isSectionEmpty(int index) {
        return sections[index] == null;
    }

    public void clear() {
        Arrays.fill(sections, null);
    }

    private static int sectionIndex(int y) {
        if (!WorldShape.contains(y)) throw new IllegalArgumentException("y out of world: " + y);
        return (y >> 4) - WorldShape.MIN_SECTION_Y;
    }

    private static int index(int localX, int y, int localZ) {
        if ((localX | localZ) >>> 4 != 0) throw new IllegalArgumentException("local coordinates out of chunk: " + localX + ", " + localZ);
        return ((y & 15) << 8) | (localZ << 4) | localX;
    }
}
