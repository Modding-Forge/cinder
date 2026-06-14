package com.cinder.ctm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompactCtmTilesTest {

    @Test
    void isolatedTile_usesFirstCompactSourceEverywhere() {
        for (int quadrant = 0;
             quadrant < CompactCtmTiles.QUADRANT_COUNT;
             quadrant++) {
            assertEquals(1, CompactCtmTiles.sourceTileIndexForQuadrant(
                    TileIndexTable.TILE_NONE, quadrant));
        }
    }

    @Test
    void fullyConnectedTile_usesSecondCompactSourceEverywhere() {
        for (int quadrant = 0;
             quadrant < CompactCtmTiles.QUADRANT_COUNT;
             quadrant++) {
            assertEquals(2, CompactCtmTiles.sourceTileIndexForQuadrant(
                    TileIndexTable.TILE_FULLY_CONNECTED, quadrant));
        }
    }

    @Test
    void missingDiagonal_usesInnerCornerSourceForThatQuadrant() {
        int topLeftMissingDiagonal = TileIndexTable.indexFor(
                Faces.UP, 0xF, 0xE);
        assertEquals(5, CompactCtmTiles.sourceTileIndexForQuadrant(
                topLeftMissingDiagonal,
                CompactCtmTiles.QUADRANT_TOP_LEFT));
        assertEquals(2, CompactCtmTiles.sourceTileIndexForQuadrant(
                topLeftMissingDiagonal,
                CompactCtmTiles.QUADRANT_TOP_RIGHT));
    }

    @Test
    void oneSideConnection_usesHorizontalOrVerticalSourceByQuadrant() {
        int rightOnly = TileIndexTable.indexFor(Faces.UP, 0x2, 0x0);
        assertEquals(1, CompactCtmTiles.sourceTileIndexForQuadrant(
                rightOnly, CompactCtmTiles.QUADRANT_TOP_LEFT));
        assertEquals(4, CompactCtmTiles.sourceTileIndexForQuadrant(
                rightOnly, CompactCtmTiles.QUADRANT_TOP_RIGHT));
        assertEquals(4, CompactCtmTiles.sourceTileIndexForQuadrant(
                rightOnly, CompactCtmTiles.QUADRANT_BOTTOM_RIGHT));

        int topOnly = TileIndexTable.indexFor(Faces.UP, 0x4, 0x0);
        assertEquals(3, CompactCtmTiles.sourceTileIndexForQuadrant(
                topOnly, CompactCtmTiles.QUADRANT_TOP_LEFT));
        assertEquals(1, CompactCtmTiles.sourceTileIndexForQuadrant(
                topOnly, CompactCtmTiles.QUADRANT_BOTTOM_LEFT));
    }
}
