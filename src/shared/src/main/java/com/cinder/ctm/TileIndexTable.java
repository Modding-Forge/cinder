package com.cinder.ctm;

import java.util.Arrays;

/**
 * Maps CTM neighbour signatures to the public OptiFine 47-tile template
 * indices.
 *
 * <p>The template uses green edge markers for missing side neighbours and
 * green corner markers for missing diagonal neighbours. Diagonals only matter
 * when both adjacent side neighbours are present, so callers may pass arbitrary
 * diagonal bits for open corners and they will be ignored before lookup.</p>
 */
public final class TileIndexTable {
    public static final int TILE_NONE = 0;
    public static final int TILE_FULLY_CONNECTED = 26;
    public static final int TILE_ALL_CONNECTED = TILE_FULLY_CONNECTED;
    public static final int TILE_COUNT = 47;

    private static final int UNMAPPED = -1;
    private static final int[][] INDEX_BY_SIGNATURE = new int[16][16];
    private static final int[] SIDE_MASK_BY_TILE = new int[TILE_COUNT];
    private static final int[] DIAGONAL_MASK_BY_TILE = new int[TILE_COUNT];

    static {
        for (int[] row : INDEX_BY_SIGNATURE) {
            Arrays.fill(row, UNMAPPED);
        }
        Arrays.fill(SIDE_MASK_BY_TILE, UNMAPPED);
        Arrays.fill(DIAGONAL_MASK_BY_TILE, UNMAPPED);

        register(0, 0x0, 0x0);
        register(1, 0x2, 0x0);
        register(2, 0x3, 0x0);
        register(3, 0x1, 0x0);
        register(4, 0xA, 0x0);
        register(5, 0x9, 0x0);
        register(6, 0xE, 0x0);
        register(7, 0xB, 0x0);
        register(8, 0xF, 0x4);
        register(9, 0xF, 0x8);
        register(10, 0xF, 0x3);
        register(11, 0xF, 0x5);
        register(12, 0x8, 0x0);
        register(13, 0xA, 0x8);
        register(14, 0xB, 0xA);
        register(15, 0x9, 0x2);
        register(16, 0x6, 0x0);
        register(17, 0x5, 0x0);
        register(18, 0x7, 0x0);
        register(19, 0xD, 0x0);
        register(20, 0xF, 0x1);
        register(21, 0xF, 0x2);
        register(22, 0xF, 0xA);
        register(23, 0xF, 0xC);
        register(24, 0xC, 0x0);
        register(25, 0xE, 0xC);
        register(26, 0xF, 0xF);
        register(27, 0xD, 0x3);
        register(28, 0xE, 0x8);
        register(29, 0xB, 0x2);
        register(30, 0xE, 0x4);
        register(31, 0xB, 0x8);
        register(32, 0xF, 0x7);
        register(33, 0xF, 0xD);
        register(34, 0xF, 0x6);
        register(35, 0xF, 0x9);
        register(36, 0x4, 0x0);
        register(37, 0x6, 0x4);
        register(38, 0x7, 0x5);
        register(39, 0x5, 0x1);
        register(40, 0x7, 0x4);
        register(41, 0xD, 0x1);
        register(42, 0x7, 0x1);
        register(43, 0xD, 0x2);
        register(44, 0xF, 0xB);
        register(45, 0xF, 0xE);
        register(46, 0xF, 0x0);
    }

    private TileIndexTable() {
    }

    public static int indexFor(int face, int sideMask, int diagonalMask) {
        int normalizedSideMask = sideMask & 0xF;
        int normalizedDiagonalMask = normalizeDiagonalMask(normalizedSideMask, diagonalMask);
        int tile = INDEX_BY_SIGNATURE[normalizedSideMask][normalizedDiagonalMask];
        if (tile == UNMAPPED) {
            throw new IllegalArgumentException(
                    "Unmapped CTM signature sideMask=" + normalizedSideMask
                            + " diagonalMask=" + normalizedDiagonalMask
                            + " face=" + face);
        }
        return tile;
    }

    public static int sideMaskForTile(int tile) {
        validateTile(tile);
        return SIDE_MASK_BY_TILE[tile];
    }

    public static int diagonalMaskForTile(int tile) {
        validateTile(tile);
        return DIAGONAL_MASK_BY_TILE[tile];
    }

    public static int sideCountForTile(int tile) {
        return Integer.bitCount(sideMaskForTile(tile));
    }

    public static int normalizeDiagonalMask(int sideMask, int diagonalMask) {
        int normalized = 0;
        int sides = sideMask & 0xF;
        int diagonals = diagonalMask & 0xF;

        if ((diagonals & 0x1) != 0 && hasBoth(sides, 0x1, 0x4)) {
            normalized |= 0x1;
        }
        if ((diagonals & 0x2) != 0 && hasBoth(sides, 0x1, 0x8)) {
            normalized |= 0x2;
        }
        if ((diagonals & 0x4) != 0 && hasBoth(sides, 0x2, 0x4)) {
            normalized |= 0x4;
        }
        if ((diagonals & 0x8) != 0 && hasBoth(sides, 0x2, 0x8)) {
            normalized |= 0x8;
        }

        return normalized;
    }

    public static int sideBase(int sideMask) {
        int normalizedSideMask = sideMask & 0xF;
        for (int diagonalMask = 0; diagonalMask < 16; diagonalMask++) {
            int tile = INDEX_BY_SIGNATURE[normalizedSideMask][diagonalMask];
            if (tile != UNMAPPED) {
                return tile;
            }
        }
        return TILE_NONE;
    }

    private static void register(int tile, int sideMask, int diagonalMask) {
        validateTile(tile);
        int normalizedSideMask = sideMask & 0xF;
        int normalizedDiagonalMask = normalizeDiagonalMask(normalizedSideMask, diagonalMask);

        if (INDEX_BY_SIGNATURE[normalizedSideMask][normalizedDiagonalMask] != UNMAPPED) {
            throw new IllegalStateException("Duplicate CTM signature for tile " + tile);
        }
        if (SIDE_MASK_BY_TILE[tile] != UNMAPPED) {
            throw new IllegalStateException("Duplicate CTM tile " + tile);
        }

        INDEX_BY_SIGNATURE[normalizedSideMask][normalizedDiagonalMask] = tile;
        SIDE_MASK_BY_TILE[tile] = normalizedSideMask;
        DIAGONAL_MASK_BY_TILE[tile] = normalizedDiagonalMask;
    }

    private static boolean hasBoth(int mask, int first, int second) {
        return (mask & first) != 0 && (mask & second) != 0;
    }

    private static void validateTile(int tile) {
        if (tile < 0 || tile >= TILE_COUNT) {
            throw new IllegalArgumentException("tile must be in range 0.." + (TILE_COUNT - 1) + ": " + tile);
        }
    }
}
