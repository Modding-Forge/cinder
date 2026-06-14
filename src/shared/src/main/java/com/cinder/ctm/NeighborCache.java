package com.cinder.ctm;

import com.cinder.resource.NamespaceId;

/**
 * Per-section neighbour cache used by the renderer to back a
 * {@link NeighborView}.
 *
 * <p>For each block in a 16x16x16 chunk section, the renderer
 * needs to look up the rendered sprite and block id of itself and
 * all 26 neighbours (6 ortho + 8 corner + 12 edge, all of which
 * fit in a 3x3x3 cube minus the centre; for a given face the
 * engine only queries a 4-ortho + 4-diag subset, so up to 9
 * lookups per face). Naively that is up to 48 lookups per block;
 * this cache reuses lookups across the 6 faces of the same block
 * to reduce that to 26 unique lookups per block.
 *
 * <p>Implementation: a packed 3x3x3 array per block position,
 * stored as a flat {@code Object[]} of length 27. The centre
 * block's entry is the centre cell of the cube. Each cell holds
 * a {@link Cached} value that is computed once and shared by all
 * faces.
 *
 * <p>Mutability: instances are intended to be created per block,
 * filled in by the renderer, then discarded. They are not
 * thread-safe; use one per section-build worker.
 */
public final class NeighborCache implements NeighborView {

    private static final int CUBE_SIZE = 27; // 3x3x3
    private final Cached[] cube = new Cached[CUBE_SIZE];
    private boolean filled;

    public NeighborCache() {
        for (int i = 0; i < CUBE_SIZE; i++) {
            cube[i] = new Cached();
        }
    }

    /**
     * Marks the cache as not yet filled, allowing the renderer to
     * repopulate it for a new block.
     */
    public void reset() {
        for (Cached c : cube) {
            c.blockId = null;
            c.fullBlock = false;
            for (int i = 0; i < c.sprite.length; i++) {
                c.sprite[i] = null;
            }
        }
        filled = false;
    }

    /**
     * Stores the centre-block sprite for the given face. The other
     * 26 cells are populated by the renderer in advance; see
     * {@link #set(int, int, int, String, boolean)} and
     * {@link #setSprite(int, int, int, int, NamespaceId)}.
     */
    public void setCenterSprite(int face, NamespaceId sprite) {
        cube[indexOf(1, 1, 1)].sprite[face] = sprite;
    }

    public void set(int dx, int dy, int dz, String blockId, boolean fullBlock) {
        Cached c = cube[indexOf(dx + 1, dy + 1, dz + 1)];
        c.blockId = blockId;
        c.fullBlock = fullBlock;
    }

    public void setSprite(int dx, int dy, int dz, int face, NamespaceId sprite) {
        cube[indexOf(dx + 1, dy + 1, dz + 1)].sprite[face] = sprite;
    }

    public void markFilled() {
        filled = true;
    }

    public boolean isFilled() {
        return filled;
    }

    @Override
    public NamespaceId sprite(int dx, int dy, int dz, int face) {
        return cube[indexOf(dx + 1, dy + 1, dz + 1)].sprite[face];
    }

    @Override
    public String blockId(int dx, int dy, int dz) {
        return cube[indexOf(dx + 1, dy + 1, dz + 1)].blockId;
    }

    @Override
    public boolean isFullBlock(int dx, int dy, int dz) {
        return cube[indexOf(dx + 1, dy + 1, dz + 1)].fullBlock;
    }

    private static int indexOf(int x, int y, int z) {
        return (x * 3 + y) * 3 + z;
    }

    private static final class Cached {
        String blockId;
        boolean fullBlock;
        final NamespaceId[] sprite = new NamespaceId[6];
    }
}
