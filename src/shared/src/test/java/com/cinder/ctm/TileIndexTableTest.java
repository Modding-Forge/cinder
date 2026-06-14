package com.cinder.ctm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TileIndexTableTest {

    @Test
    void noSides_isAlwaysZero() {
        for (int face = 0; face < 6; face++) {
            for (int d = 0; d < 16; d++) {
                assertEquals(0, TileIndexTable.indexFor(face, 0, d),
                        "no sides must be tile 0, face=" + face + " diag=" + d);
            }
        }
    }

    @Test
    void allSidesAndAllDiagonals_isAllConnected() {
        // 4 sides + 4 diagonals.
        int tile = TileIndexTable.indexFor(Faces.UP, 0xF, 0xF);
        assertEquals(TileIndexTable.TILE_ALL_CONNECTED, tile);
    }

    @Test
    void allSidesZeroDiagonals_isNotAllConnected() {
        int tile = TileIndexTable.indexFor(Faces.UP, 0xF, 0x0);
        // Not the full "all connected" tile (which requires corners).
        assertEquals(46, tile);
    }

    @Test
    void templateOracle_matchesPublic47TileLayout() {
        int[][] cases = new int[][] {
                {0, 0x0, 0x0}, {1, 0x2, 0x0}, {2, 0x3, 0x0},
                {3, 0x1, 0x0}, {4, 0xA, 0x0}, {5, 0x9, 0x0},
                {6, 0xE, 0x0}, {7, 0xB, 0x0}, {8, 0xF, 0x4},
                {9, 0xF, 0x8}, {10, 0xF, 0x3}, {11, 0xF, 0x5},
                {12, 0x8, 0x0}, {13, 0xA, 0x8}, {14, 0xB, 0xA},
                {15, 0x9, 0x2}, {16, 0x6, 0x0}, {17, 0x5, 0x0},
                {18, 0x7, 0x0}, {19, 0xD, 0x0}, {20, 0xF, 0x1},
                {21, 0xF, 0x2}, {22, 0xF, 0xA}, {23, 0xF, 0xC},
                {24, 0xC, 0x0}, {25, 0xE, 0xC}, {26, 0xF, 0xF},
                {27, 0xD, 0x3}, {28, 0xE, 0x8}, {29, 0xB, 0x2},
                {30, 0xE, 0x4}, {31, 0xB, 0x8}, {32, 0xF, 0x7},
                {33, 0xF, 0xD}, {34, 0xF, 0x6}, {35, 0xF, 0x9},
                {36, 0x4, 0x0}, {37, 0x6, 0x4}, {38, 0x7, 0x5},
                {39, 0x5, 0x1}, {40, 0x7, 0x4}, {41, 0xD, 0x1},
                {42, 0x7, 0x1}, {43, 0xD, 0x2}, {44, 0xF, 0xB},
                {45, 0xF, 0xE}, {46, 0xF, 0x0}
        };
        for (int[] c : cases) {
            assertEquals(c[0], TileIndexTable.indexFor(Faces.UP, c[1], c[2]),
                    "tile for sideMask=" + Integer.toHexString(c[1])
                            + " diagMask=" + Integer.toHexString(c[2]));
        }
    }

    @Test
    void namedNeighbourPatterns_matchPublic47TileLayout() {
        assertTile("isolated", 0, 0x0, 0x0);
        assertTile("one north edge", 1, 0x2, 0x0);
        assertTile("opposite north/south edges", 4, 0xA, 0x0);
        assertTile("north west corner", 2, 0x3, 0x0);
        assertTile("t shape missing west", 6, 0xE, 0x0);
        assertTile("o shape without diagonals", 46, 0xF, 0x0);
        assertTile("fully connected", 26, 0xF, 0xF);
    }

    @Test
    void eachMissingCornerWithAllSidesConnected_matchesPublic47TileLayout() {
        assertTile("missing diagonal 0", 45, 0xF, 0xE);
        assertTile("missing diagonal 1", 33, 0xF, 0xD);
        assertTile("missing diagonal 2", 44, 0xF, 0xB);
        assertTile("missing diagonal 3", 32, 0xF, 0x7);
    }

    @Test
    void sameInput_sameOutput() {
        int a = TileIndexTable.indexFor(Faces.NORTH, 0b1010, 0b0101);
        int b = TileIndexTable.indexFor(Faces.NORTH, 0b1010, 0b0101);
        assertEquals(a, b);
    }

    @Test
    void diagonalsAreIgnoredWhenAdjacentSidesAreOpen() {
        assertEquals(1, TileIndexTable.indexFor(Faces.UP, 0x2, 0xF));
        assertEquals(0, TileIndexTable.indexFor(Faces.UP, 0x0, 0xF));
    }

    @Test
    void allMasks_coverAllTiles() {
        // Every (sideMask, diagMask) pair with sideMask != 0 should
        // produce a tile in 1..46.
        for (int face = 0; face < 6; face++) {
            for (int s = 1; s < 16; s++) {
                for (int d = 0; d < 16; d++) {
                    int tile = TileIndexTable.indexFor(face, s, d);
                    assertTrue(tile >= 1 && tile < TileIndexTable.TILE_COUNT,
                            "tile out of range: " + tile);
                }
            }
        }
    }

    private static void assertTile(String name, int expected, int sideMask,
                                   int diagonalMask) {
        assertEquals(expected,
                TileIndexTable.indexFor(Faces.UP, sideMask, diagonalMask),
                name + " sideMask=" + Integer.toHexString(sideMask)
                        + " diagMask=" + Integer.toHexString(diagonalMask));
    }
}
