package com.cinder.ctm;

import com.cinder.resource.NamespaceId;

/**
 * Loader-agnostic view of a 3x3x3 block neighbourhood centred at one
 * position.
 *
 * <p>The CTM engine does not need to know that the underlying
 * representation is a Minecraft {@code BlockState}; it only needs to
 * know:
 * <ul>
 *   <li>the rendered sprite of the centre block face,</li>
 *   <li>the rendered sprite and block identity of every neighbour in
 *       the 3x3x3 cube,</li>
 *   <li>and, for some methods, the block state in full.</li>
 * </ul>
 *
 * <p>Implementations are expected to be:
 * <ul>
 *   <li><b>Pre-cached.</b> The renderer builds one of these per
 *       section and reuses it for every block in the section. The
 *       engine never re-queries neighbour data outside this view.</li>
 *   <li><b>Position-relative.</b> The methods take a {@code (dx, dy, dz)}
 *       offset within the 3x3x3 cube, not a world position. This keeps
 *       the engine branchless and allocation-free.</li>
 *   <li><b>Per-face.</b> Different faces of the same block can have
 *       different rendered sprites (e.g. logs, grass, vanilla piston
 *       sides); the {@link #sprite(int, int, int, int)} method takes
 *       a face parameter.</li>
 * </ul>
 *
 * <p>Performance: every method is O(1). The interface is intentionally
 * a flat list of methods (not a richer object) to keep the JIT happy.
 */
public interface NeighborView {

    /**
     * Returns the rendered sprite of the neighbour at offset
     * {@code (dx, dy, dz)} for the given face, or {@code null} if
     * the neighbour is air (or otherwise has no rendered sprite).
     *
     * <p>Valid offsets: {@code -1 <= dx,dy,dz <= 1}.
     */
    NamespaceId sprite(int dx, int dy, int dz, int face);

    /**
     * Returns a stable identifier for the neighbour block (the
     * "block ID" used in {@code matchBlocks}). Used by the
     * {@code connect=block} mode to compare neighbours. Two
     * neighbours "match" under {@code block} mode iff their block
     * IDs are equal.
     *
     * <p>For the centre block (offsets 0,0,0), the result should be
     * the centre block's ID.
     */
    String blockId(int dx, int dy, int dz);

    /**
     * Returns {@code true} if the neighbour is opaque enough to
     * "connect" with the centre block under {@code connect=state}
     * mode. The exact definition is loader-dependent; the
     * adapter supplies a definition consistent with how OptiFine
     * resource packs expect it.
     */
    boolean isFullBlock(int dx, int dy, int dz);

    /**
     * Phase 4.3: returns the biome id of the neighbour at the
     * given offset, or {@code null} if the neighbour's biome is
     * not known (e.g. in a unit test that does not model biomes).
     *
     * <p>Only the centre cell (offsets 0,0,0) is consulted by the
     * selector for the {@code biomes=} filter; the other 26 cells
     * are not queried. The method is therefore usually
     * implemented as a constant-returning function in tests.
     */
    default String biomeId(int dx, int dy, int dz) {
        return null;
    }
}
