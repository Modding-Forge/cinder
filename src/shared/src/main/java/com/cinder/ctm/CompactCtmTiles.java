package com.cinder.ctm;

/**
 * Compact-CTM tile helpers shared by renderer adapters and fallback atlas
 * generation.
 *
 * <p>The public 47-tile CTM template is still the source of truth. Compact CTM
 * rules provide five source textures after the base texture; this helper maps
 * each quadrant of a 47-template result to one of those source tile indices.
 *
 * <h2>Threading</h2>
 *
 * <p>Stateless and thread-safe.
 *
 * <h2>Performance</h2>
 *
 * <p>Performance: HOT PATH. Allocation policy: none.
 */
public final class CompactCtmTiles {
    public static final int QUADRANT_TOP_LEFT = 0;
    public static final int QUADRANT_TOP_RIGHT = 1;
    public static final int QUADRANT_BOTTOM_LEFT = 2;
    public static final int QUADRANT_BOTTOM_RIGHT = 3;
    public static final int QUADRANT_COUNT = 4;

    private CompactCtmTiles() {
    }

    /**
     * Returns the compact rule tile index for one quadrant of a full 47-CTM
     * tile. Returned indices are rule tile indices {@code 1..5}; index
     * {@code 0} remains the rule's base/pass-through texture.
     */
    public static int sourceTileIndexForQuadrant(int fullTileIndex,
                                                 int quadrant) {
        int sideMask = TileIndexTable.sideMaskForTile(fullTileIndex);
        int diagMask = TileIndexTable.diagonalMaskForTile(fullTileIndex);
        boolean right = quadrant == QUADRANT_TOP_RIGHT
                || quadrant == QUADRANT_BOTTOM_RIGHT;
        boolean bottom = quadrant == QUADRANT_BOTTOM_LEFT
                || quadrant == QUADRANT_BOTTOM_RIGHT;
        int horizontalBit = right ? 0x2 : 0x1;
        int verticalBit = bottom ? 0x8 : 0x4;
        int diagonalBit = switch (quadrant) {
            case QUADRANT_TOP_LEFT -> 0x1;
            case QUADRANT_BOTTOM_LEFT -> 0x2;
            case QUADRANT_TOP_RIGHT -> 0x4;
            case QUADRANT_BOTTOM_RIGHT -> 0x8;
            default -> throw new IllegalArgumentException(
                    "bad quadrant: " + quadrant);
        };
        boolean horizontal = (sideMask & horizontalBit) != 0;
        boolean vertical = (sideMask & verticalBit) != 0;
        if (horizontal && vertical) {
            return (diagMask & diagonalBit) != 0 ? 2 : 5;
        }
        if (vertical) {
            return 3;
        }
        if (horizontal) {
            return 4;
        }
        return 1;
    }
}
