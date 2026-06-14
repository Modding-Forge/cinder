package com.cinder.ctm;

import com.cinder.resource.DeterministicRandom;
import com.cinder.resource.NamespaceId;
import com.cinder.resource.WeightedSelector;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-face CTM tile-selection engine.
 *
 * <p>Given a {@link CtmRule}, a {@link NeighborView}, the centre
 * position, and the face being rendered, the selector returns a
 * {@link CtmSelectionResult} indicating which tile in the rule's
 * {@code tiles} list should be used (or {@code <skip>} /
 * {@code <default>}).
 *
 * <h2>Why this is a clean-room implementation</h2>
 *
 * <p>The OptiFine counterpart uses a procedural set of helper
 * functions in {@code ConnectedTextures.java} to compute the same
 * selection. We re-derive the algorithm from the documentation
 * in {@code optifine/OptiFineDoc/doc/ctm.properties}; we do not
 * copy or translate OptiFine's code. The result is the same
 * behaviour with a different code structure, different data
 * layouts ({@link TileIndexTable}), and a different neighbour
 * abstraction ({@link NeighborView}).
 *
 * <h2>Methods implemented</h2>
 *
 * <ul>
 *   <li>{@link CtmMethod#CTM} - 47-tile connection table</li>
 *   <li>{@link CtmMethod#HORIZONTAL} - 4-tile horizontal connections</li>
 *   <li>{@link CtmMethod#VERTICAL} - 4-tile vertical connections</li>
 *   <li>{@link CtmMethod#HORIZONTAL_VERTICAL} - layered</li>
 *   <li>{@link CtmMethod#VERTICAL_HORIZONTAL} - layered</li>
 *   <li>{@link CtmMethod#TOP} - 1-tile, top neighbour only</li>
 *   <li>{@link CtmMethod#FIXED} - 1-tile</li>
 *   <li>{@link CtmMethod#RANDOM} - per-face weighted random</li>
 *   <li>{@link CtmMethod#REPEAT} - per-block position-modulo</li>
 *   <li>Overlay variants of all of the above.</li>
 * </ul>
 *
 * <p>Performance: every {@code select} call is O(1) - at most 8
 * neighbour lookups (4 sides + 4 diagonals). No allocation in the
 * hot path.
 */
public final class CtmSelector {

    private final CtmRuleSet ruleSet;

    public CtmSelector(CtmRuleSet ruleSet) {
        this.ruleSet = ruleSet;
    }

    public CtmRuleSet ruleSet() {
        return ruleSet;
    }

    /**
     * Selects a tile for the face of the centre block. Returns
     * {@code null} if no rule applies (e.g. faces-mask excludes
     * this face, or {@code biomes}/{@code heights} filters
     * exclude this position).
     *
     * <p>The {@code biomes}/{@code heights} filter is applied
     * here for all methods: a rule whose biome does not match
     * the position's biome, or whose Y is outside the rule's
     * height range, is treated as if it had not matched at all.
     */
    public CtmSelectionResult select(CtmRule rule, NeighborView view,
                                    int x, int y, int z, int face) {
        if (rule == null) {
            return null;
        }
        if (rule.facesMask() != 0 && (rule.facesMask() & (1 << face)) == 0) {
            return null;
        }
        // Phase 4.3: heights filter. y is the centre position's
        // Y; a rule with a non-trivial heights range must include
        // y to be eligible.
        if (!rule.heights().contains(y)) {
            return null;
        }
        // Phase 4.3: biomes filter. The view is expected to
        // provide the centre-block biome ID; if the rule lists
        // biomes and the centre's biome is not in the list, the
        // rule does not apply.
        if (!rule.biomes().isEmpty()) {
            String centerBiome = view.biomeId(0, 0, 0);
            if (centerBiome == null || !rule.biomes().contains(centerBiome)) {
                return null;
            }
        }
        return switch (rule.method()) {
            case CTM               -> selectCtm(rule, view, x, y, z, face);
            case CTM_COMPACT       -> selectCtmCompact(rule, view, x, y, z, face);
            case HORIZONTAL        -> selectHorizontal(rule, view, x, y, z, face);
            case VERTICAL          -> selectVertical(rule, view, x, y, z, face);
            case HORIZONTAL_VERTICAL -> selectLayeredFirst(rule, view, x, y, z, face, true);
            case VERTICAL_HORIZONTAL -> selectLayeredFirst(rule, view, x, y, z, face, false);
            case TOP               -> selectTop(rule, view, x, y, z, face);
            case FIXED             -> CtmSelectionResult.ofTile(0);
            case RANDOM            -> selectRandom(rule, x, y, z, face);
            case REPEAT            -> selectRepeat(rule, x, y, z, face);
            case OVERLAY           -> selectOverlayCtm(rule, view, x, y, z, face);
            case OVERLAY_CTM       -> selectOverlayCtm(rule, view, x, y, z, face);
            case OVERLAY_RANDOM    -> selectOverlayRandom(rule, x, y, z, face);
            case OVERLAY_REPEAT    -> selectOverlayRepeat(rule, x, y, z, face);
            case OVERLAY_FIXED     -> CtmSelectionResult.ofTile(0);
        };
    }

    /**
     * Phase 4.5: layered result for {@code horizontal+vertical}
     * and {@code vertical+horizontal}. Returns a two-element
     * array: index 0 is the first method's result, index 1 is
     * the second. Either entry can be {@code null} if the
     * underlying method returned {@code null}.
     *
     * <p>The renderer is expected to consult both entries: the
     * first entry decides the base tile, the second entry may
     * emit an extra quad if non-null and not {@code <skip>}.
     */
    public CtmSelectionResult[] selectLayered(CtmRule rule, NeighborView view,
                                              int x, int y, int z, int face) {
        if (rule == null) {
            return new CtmSelectionResult[] { null, null };
        }
        if (rule.facesMask() != 0 && (rule.facesMask() & (1 << face)) == 0) {
            return new CtmSelectionResult[] { null, null };
        }
        if (!rule.heights().contains(y)) {
            return new CtmSelectionResult[] { null, null };
        }
        boolean horizontalFirst =
                rule.method() == CtmMethod.HORIZONTAL_VERTICAL;
        CtmSelectionResult first = horizontalFirst
                ? selectHorizontal(rule, view, x, y, z, face)
                : selectVertical(rule, view, x, y, z, face);
        CtmSelectionResult second = horizontalFirst
                ? selectVertical(rule, view, x, y, z, face)
                : selectHorizontal(rule, view, x, y, z, face);
        return new CtmSelectionResult[] { first, second };
    }

    /**
     * Selects renderer-facing CTM metadata for one face.
     *
     * <p>This method is the Vulkan-ready entry point: it returns immutable
     * metadata describing the selected rule, method, tile indices, layer flags,
     * and base sprite. It does not mutate quads, rewrite UVs, or require a
     * Minecraft/Fabric renderer type.
     *
     * <p>Performance: HOT PATH. Allocation policy: one
     * {@link CtmRenderSelection} per matching rule. Callers that only need the
     * primitive tile index can continue to use {@link #select}.
     */
    public CtmRenderSelection selectRender(CtmRule rule, NeighborView view,
                                           int x, int y, int z, int face,
                                           NamespaceId baseSprite) {
        if (isLayered(rule)) {
            CtmSelectionResult[] layers =
                    selectLayered(rule, view, x, y, z, face);
            if (layers[0] == null && layers[1] == null) {
                return null;
            }
            CtmSelectionResult primary = layers[0] != null
                    ? layers[0]
                    : CtmSelectionResult.useDefault();
            return CtmRenderSelection.from(
                    rule, face, baseSprite, primary, layers[1]);
        }
        if (rule != null && rule.method() == CtmMethod.CTM_COMPACT) {
            CtmSelectionResult primary =
                    selectCtmCompactForRender(rule, view, x, y, z, face);
            if (primary == null) {
                return null;
            }
            return CtmRenderSelection.from(rule, face, baseSprite, primary, null);
        }
        if (rule != null && isOverlay(rule.method())) {
            List<Integer> overlays = selectOverlayTiles(rule, view, face);
            return CtmRenderSelection.overlay(rule, face, baseSprite, overlays);
        }
        CtmSelectionResult primary = select(rule, view, x, y, z, face);
        if (primary == null) {
            return null;
        }
        return CtmRenderSelection.from(rule, face, baseSprite, primary, null);
    }

    // --- per-method selectors -----------------------------------------

    private static boolean isLayered(CtmRule rule) {
        if (rule == null) {
            return false;
        }
        return rule.method() == CtmMethod.HORIZONTAL_VERTICAL
                || rule.method() == CtmMethod.VERTICAL_HORIZONTAL;
    }

    private static boolean isOverlay(CtmMethod method) {
        return method == CtmMethod.OVERLAY
                || method == CtmMethod.OVERLAY_CTM
                || method == CtmMethod.OVERLAY_RANDOM
                || method == CtmMethod.OVERLAY_REPEAT
                || method == CtmMethod.OVERLAY_FIXED;
    }

    private CtmSelectionResult selectCtm(CtmRule rule, NeighborView view,
                                         int x, int y, int z, int face) {
        int sideMask = sideMask(rule, view, face);
        int diagMask = diagMask(rule, view, face, sideMask, rule.innerSeams());
        int effectiveSide = applyInnerSeams(sideMask, diagMask, rule.innerSeams());
        int textureSide = textureSideMask(face, effectiveSide);
        int textureDiag = textureDiagonalMask(face, diagMask);
        int tile = TileIndexTable.indexFor(face, textureSide, textureDiag);
        return CtmSelectionResult.ofTile(tile);
    }

    private CtmSelectionResult selectCtmCompact(CtmRule rule, NeighborView view,
                                                int x, int y, int z, int face) {
        int full = fullCtmTileIndex(rule, view, face);
        int[] overrides = rule.ctmOverrides();
        int resolved;
        if (overrides != null && full < overrides.length && overrides[full] >= 0) {
            resolved = overrides[full];
        } else {
            resolved = mapCompact(full);
        }
        return CtmSelectionResult.ofTile(resolved);
    }

    private CtmSelectionResult selectCtmCompactForRender(
            CtmRule rule, NeighborView view, int x, int y, int z, int face) {
        int full = fullCtmTileIndex(rule, view, face);
        if (full == 0) {
            return CtmSelectionResult.ofTile(0);
        }
        int[] overrides = rule.ctmOverrides();
        if (overrides != null && full < overrides.length && overrides[full] >= 0) {
            return CtmSelectionResult.ofTile(overrides[full]);
        }
        return CtmSelectionResult.ofTile(
                CtmTileResolver.COMPACT_FULL_TILE_OFFSET
                        + face * CtmTileResolver.COMPACT_FULL_TILE_COUNT
                        + full);
    }

    private int fullCtmTileIndex(CtmRule rule, NeighborView view, int face) {
        int sideMask = sideMask(rule, view, face);
        int diagMask = diagMask(rule, view, face, sideMask, rule.innerSeams());
        int effectiveSide = applyInnerSeams(sideMask, diagMask, rule.innerSeams());
        int textureSide = textureSideMask(face, effectiveSide);
        int textureDiag = textureDiagonalMask(face, diagMask);
        return TileIndexTable.indexFor(face, textureSide, textureDiag);
    }

    private static int textureSideMask(int face, int sideMask) {
        int mask = sideMask & 0xF;
        if (flipsTextureHorizontal(face)) {
            mask = ((mask & 0x1) << 1)
                    | ((mask & 0x2) >>> 1)
                    | (mask & 0xC);
        }
        if (flipsTextureVertical(face)) {
            mask = (mask & 0x3)
                    | ((mask & 0x4) << 1)
                    | ((mask & 0x8) >>> 1);
        }
        return mask;
    }

    private static int textureDiagonalMask(int face, int diagonalMask) {
        int mask = diagonalMask & 0xF;
        if (flipsTextureHorizontal(face)) {
            mask = ((mask & 0x1) << 2)
                    | ((mask & 0x2) << 2)
                    | ((mask & 0x4) >>> 2)
                    | ((mask & 0x8) >>> 2);
        }
        if (flipsTextureVertical(face)) {
            mask = ((mask & 0x1) << 1)
                    | ((mask & 0x2) >>> 1)
                    | ((mask & 0x4) << 1)
                    | ((mask & 0x8) >>> 1);
        }
        return mask;
    }

    private static boolean flipsTextureHorizontal(int face) {
        return face == Faces.NORTH || face == Faces.EAST;
    }

    private static boolean flipsTextureVertical(int face) {
        return face == Faces.DOWN;
    }

    /**
     * Maps a 47-tile full-CTM index to the compact subset. The
     * compact layout collapses intermediate variants to a small
     * set of representative tiles; the exact boundary values are
     * tested explicitly in {@code CtmSelectorTest}.
     */
    private static int mapCompact(int fullTile) {
        if (fullTile == 0) return 0;
        if (fullTile == TileIndexTable.TILE_ALL_CONNECTED) return 4;
        int sides = TileIndexTable.sideCountForTile(fullTile);
        if (sides == 1) return 1;
        if (sides == 2) return 2;
        if (sides == 3) return 3;
        return 4;
    }

    private CtmSelectionResult selectHorizontal(CtmRule rule, NeighborView view,
                                               int x, int y, int z, int face) {
        int[] w = Faces.delta(Faces.WEST);
        int[] e = Faces.delta(Faces.EAST);
        boolean left  = sideConnect(rule, view, w[0], w[1], w[2], face);
        boolean right = sideConnect(rule, view, e[0], e[1], e[2], face);
        int idx;
        if (left && right)      idx = 1;
        else if (left)          idx = 2;
        else if (right)         idx = 0;
        else                   idx = 3;
        return CtmSelectionResult.ofTile(idx);
    }

    private CtmSelectionResult selectVertical(CtmRule rule, NeighborView view,
                                             int x, int y, int z, int face) {
        int[] d = Faces.delta(Faces.DOWN);
        int[] u = Faces.delta(Faces.UP);
        boolean down = sideConnect(rule, view, d[0], d[1], d[2], face);
        boolean up   = sideConnect(rule, view, u[0], u[1], u[2], face);
        int idx;
        if (down && up)         idx = 1;
        else if (down)          idx = 2;
        else if (up)            idx = 0;
        else                   idx = 3;
        return CtmSelectionResult.ofTile(idx);
    }

    private CtmSelectionResult selectTop(CtmRule rule, NeighborView view,
                                         int x, int y, int z, int face) {
        int[] u = Faces.delta(Faces.UP);
        boolean top = sideConnect(rule, view, u[0], u[1], u[2], face);
        return top ? CtmSelectionResult.ofTile(0) : null;
    }

    private CtmSelectionResult selectLayeredFirst(CtmRule rule, NeighborView view,
                                                   int x, int y, int z, int face,
                                                   boolean horizontalFirst) {
        // Returns the first layer only; the second layer is
        // available via selectLayered() for renderers that can
        // emit two quads per face.
        return horizontalFirst
                ? selectHorizontal(rule, view, x, y, z, face)
                : selectVertical(rule, view, x, y, z, face);
    }

    private CtmSelectionResult selectRandom(CtmRule rule,
                                            int x, int y, int z, int face) {
        int n = rule.tiles().size();
        if (n <= 0) return CtmSelectionResult.ofTile(0);
        if (rule.randomWeights() != null && rule.randomWeights().size() == n) {
            Integer[] ws = rule.randomWeights().toArray(new Integer[0]);
            WeightedSelector sel = new WeightedSelector(ws);
            return CtmSelectionResult.ofTile(sel.sample(
                    DeterministicRandom.hash(x, y, z, face)));
        }
        int seed = DeterministicRandom.hash(x, y, z, face) & 0x7FFFFFFF;
        return CtmSelectionResult.ofTile(seed % n);
    }

    private CtmSelectionResult selectRepeat(CtmRule rule,
                                            int x, int y, int z, int face) {
        int w = Math.max(1, rule.width());
        int h = Math.max(1, rule.height());
        int idx = (Math.floorMod(x, w)) + (Math.floorMod(y, h) * w);
        if (idx >= rule.tiles().size()) {
            idx = idx % rule.tiles().size();
        }
        return CtmSelectionResult.ofTile(idx);
    }

    private CtmSelectionResult selectOverlayCtm(CtmRule rule, NeighborView view,
                                                int x, int y, int z, int face) {
        List<Integer> tiles = selectOverlayTiles(rule, view, face);
        return tiles.isEmpty()
                ? CtmSelectionResult.skip()
                : CtmSelectionResult.ofTile(tiles.getFirst());
    }

    private CtmSelectionResult selectOverlayRandom(CtmRule rule,
                                                   int x, int y, int z, int face) {
        return selectRandom(rule, x, y, z, face);
    }

    private CtmSelectionResult selectOverlayRepeat(CtmRule rule,
                                                   int x, int y, int z, int face) {
        return selectRepeat(rule, x, y, z, face);
    }

    // --- neighbour helpers --------------------------------------------

    private int sideMask(CtmRule rule, NeighborView view, int face) {
        int[] sides = Faces.orthogonalSides(face);
        int mask = 0;
        for (int i = 0; i < sides.length; i++) {
            // The neighbour offset for the i-th side of the face:
            // Faces.delta(side) gives the (dx, dy, dz) from the centre
            // to the neighbour on that side.
            int[] d = Faces.delta(sides[i]);
            if (sideConnect(rule, view, d[0], d[1], d[2], face)) {
                mask |= (1 << i);
            }
        }
        return mask;
    }

    private List<Integer> selectOverlayTiles(CtmRule rule, NeighborView view,
                                             int face) {
        if (rule.method() != CtmMethod.OVERLAY
                && rule.method() != CtmMethod.OVERLAY_CTM) {
            CtmSelectionResult result = select(rule, view, 0, 0, 0, face);
            if (result == null || !result.isConcrete()) {
                return List.of();
            }
            return List.of(result.tileIndex());
        }
        int sideMask = overlaySideMask(rule, view, face);
        int edgeMask = overlayEdgeMask(rule, view, face);
        int baseSideMask = overlayBaseSideMask(rule, view, face);
        return overlayTileIndices(sideMask, edgeMask, baseSideMask);
    }

    /**
     * Maps overlay neighbour masks to OptiFine's 17-tile overlay layout.
     *
     * <p>Performance: HOT PATH. Allocation policy: none. The side bits use the
     * canonical face-local order returned by {@link Faces#orthogonalSides(int)}
     * (left, right, top, bottom). The table was derived from the public
     * 17-tile overlay contract and the visible edge/corner semantics of real
     * resource-pack overlay sheets.
     */
    private static List<Integer> overlayTileIndices(int sideMask,
                                                    int edgeMask,
                                                    int baseSideMask) {
        int sides = sideMask & 0xF;
        if (sides == 0) {
            return overlayDiagonalOnlyTiles(edgeMask, baseSideMask);
        }
        if (sides == 0x3) {
            return List.of(9, 7);
        }
        if (sides == 0xC) {
            return List.of(1, 15);
        }
        int primary = overlaySingleTile(sides);
        if (primary < 0) {
            return List.of();
        }
        ArrayList<Integer> tiles = new ArrayList<>(2);
        tiles.add(primary);
        addOverlayEdgeCorners(tiles, edgeMask, sides | baseSideMask);
        addOverlayBaseCapCorners(tiles, sides, baseSideMask);
        return tiles;
    }

    private static List<Integer> overlayDiagonalOnlyTiles(int edgeMask,
                                                          int baseSideMask) {
        int edges = edgeMask & 0xF;
        if (edges == 0) {
            return List.of();
        }
        ArrayList<Integer> tiles = new ArrayList<>(4);
        addOverlayEdgeTile(tiles, edges, baseSideMask, 0x1, 0);
        addOverlayEdgeTile(tiles, edges, baseSideMask, 0x2, 2);
        addOverlayEdgeTile(tiles, edges, baseSideMask, 0x4, 14);
        addOverlayEdgeTile(tiles, edges, baseSideMask, 0x8, 16);
        return tiles;
    }

    private static void addOverlayEdgeTile(List<Integer> out,
                                           int edgeMask,
                                           int adjacencyMask,
                                           int edgeBit,
                                           int tile) {
        if ((edgeMask & edgeBit) != 0
                && hasAdjacentOverlayBase(edgeBit, adjacencyMask)) {
            out.add(tile);
        }
    }

    private int overlaySideMask(CtmRule rule, NeighborView view, int face) {
        int[][] sides = overlaySideOffsets(face);
        int mask = 0;
        for (int i = 0; i < sides.length; i++) {
            int[] d = sides[i];
            if (overlayConnect(rule, view, d[0], d[1], d[2], face)) {
                mask |= 1 << i;
            }
        }
        return mask;
    }

    private int overlayEdgeMask(CtmRule rule, NeighborView view, int face) {
        int[][] edges = overlayEdgeOffsets(face);
        int mask = 0;
        for (int i = 0; i < edges.length; i++) {
            int[] d = edges[i];
            if (overlayConnect(rule, view, d[0], d[1], d[2], face)) {
                mask |= 1 << i;
            }
        }
        return mask;
    }

    private int overlayBaseSideMask(CtmRule rule, NeighborView view, int face) {
        int[][] sides = overlaySideOffsets(face);
        int mask = 0;
        for (int i = 0; i < sides.length; i++) {
            int[] d = sides[i];
            if (overlayBaseMatches(rule, view, d[0], d[1], d[2], face)) {
                mask |= 1 << i;
            }
        }
        return mask;
    }

    private static int overlaySingleTile(int sideMask) {
        return switch (sideMask & 0xF) {
            case 0x1 -> 9;   // left
            case 0x2 -> 7;   // right
            case 0x3 -> 8;   // left + right
            case 0x4 -> 1;   // side index 2
            case 0x5 -> 4;   // side 0 + side 2
            case 0x6 -> 3;   // right + top
            case 0x7 -> 5;   // left + right + top
            case 0x8 -> 15;  // side index 3
            case 0x9 -> 11;  // side 0 + side 3
            case 0xA -> 10;  // right + bottom
            case 0xB -> 13;  // left + right + bottom
            case 0xC -> 8;   // top + bottom
            case 0xD -> 6;   // left + top + bottom
            case 0xE -> 12;  // right + top + bottom
            case 0xF -> 8;   // all sides
            default -> -1;
        };
    }

    private static void addOverlayEdgeCorners(List<Integer> out,
                                              int edgeMask,
                                              int adjacencyMask) {
        int edges = edgeMask & 0xF;
        if ((edges & 0x1) != 0
                && hasAdjacentOverlayBase(0x1, adjacencyMask)) {
            addOverlayCornerIfAbsent(out, 0);
        }
        if ((edges & 0x2) != 0
                && hasAdjacentOverlayBase(0x2, adjacencyMask)) {
            addOverlayCornerIfAbsent(out, 2);
        }
        if ((edges & 0x4) != 0
                && hasAdjacentOverlayBase(0x4, adjacencyMask)) {
            addOverlayCornerIfAbsent(out, 14);
        }
        if ((edges & 0x8) != 0
                && hasAdjacentOverlayBase(0x8, adjacencyMask)) {
            addOverlayCornerIfAbsent(out, 16);
        }
    }

    private static boolean hasAdjacentOverlayBase(int edgeBit,
                                                  int adjacencyMask) {
        int mask = adjacencyMask & 0xF;
        return switch (edgeBit) {
            case 0x1 -> (mask & 0x6) != 0;
            case 0x2 -> (mask & 0x5) != 0;
            case 0x4 -> (mask & 0xA) != 0;
            case 0x8 -> (mask & 0x9) != 0;
            default -> false;
        };
    }

    private static void addOverlayBaseCapCorners(List<Integer> out,
                                                 int sideMask,
                                                 int baseSideMask) {
        int sides = sideMask & 0xF;
        int base = baseSideMask & 0xF;
        if ((sides & 0x2) != 0 && (base & 0x4) != 0
                || (sides & 0x4) != 0 && (base & 0x2) != 0) {
            addOverlayCornerIfAbsent(out, 0);
        }
        if ((sides & 0x1) != 0 && (base & 0x4) != 0
                || (sides & 0x4) != 0 && (base & 0x1) != 0) {
            addOverlayCornerIfAbsent(out, 2);
        }
        if ((sides & 0x2) != 0 && (base & 0x8) != 0
                || (sides & 0x8) != 0 && (base & 0x2) != 0) {
            addOverlayCornerIfAbsent(out, 14);
        }
        if ((sides & 0x1) != 0 && (base & 0x8) != 0
                || (sides & 0x8) != 0 && (base & 0x1) != 0) {
            addOverlayCornerIfAbsent(out, 16);
        }
    }

    private static void addOverlayCornerIfAbsent(List<Integer> out,
                                                 int tile) {
        if (!out.contains(tile)) {
            out.add(tile);
        }
    }

    private int diagMask(CtmRule rule, NeighborView view, int face,
                        int sideMask, boolean innerSeams) {
        // Phase 4.2: with innerSeams=true, the diagonal mask
        // ignores the side-mask gate: every diagonal that
        // matches the centre block counts. This is the OF
        // semantics used by stained-glass panes and similar
        // "edge connection" textures. Without innerSeams (the
        // default), diagonals only count when their
        // corresponding sides are connected (sideMask != 0),
        // because a "diagonal" without any connecting side is
        // usually a coincidence (two unrelated blocks meeting
        // at a corner).
        if (!innerSeams && sideMask == 0) {
            return 0;
        }
        int[][] diags = Faces.diagonals(face);
        int mask = 0;
        for (int i = 0; i < diags.length; i++) {
            int[] d = diags[i];
            if (sideConnect(rule, view, d[0], d[1], d[2], face)) {
                mask |= (1 << i);
            }
        }
        return mask;
    }

    /**
     * Applies the {@code innerSeams} semantics: when enabled and
     * there is at least one matching diagonal but no matching
     * side, the function lifts the lowest diagonal bit into the
     * side mask. The resulting side+diag masks then drive a
     * single-side tile (e.g. tile 1), matching the
     * "edge connection" visual that OptiFine's innerSeams mode
     * produces on stained glass panes.
     *
     * <p>This is a no-op when innerSeams is false or when the
     * side mask is already non-zero.
     */
    private static int applyInnerSeams(int sideMask, int diagMask,
                                       boolean innerSeams) {
        if (!innerSeams) {
            return sideMask;
        }
        if (sideMask != 0 || diagMask == 0) {
            return sideMask;
        }
        // Lift the lowest diagonal bit into bit 0 of the side
        // mask. The exact bit is irrelevant; the side mask
        // being non-zero is what changes the tile index from 0
        // to a single-side tile.
        return 1 << Integer.numberOfTrailingZeros(diagMask);
    }

    /**
     * Returns {@code true} if the neighbour at offset
     * {@code (dx, dy, dz)} is "the same" as the centre block under
     * the rule's {@code connect} mode.
     *
     * <p>Tile connectivity compares the neighbour sprite on the same rendered
     * face as the centre quad. For example, while rendering a floor's top
     * face, the west neighbour is compared by its top sprite, not by its east
     * side sprite.
     */
    private boolean sideConnect(CtmRule rule, NeighborView view,
                                int dx, int dy, int dz, int face) {
        String neighbourBlockId = view.blockId(dx, dy, dz);
        String centreBlockId = view.blockId(0, 0, 0);
        NamespaceId neighbourSprite = view.sprite(dx, dy, dz, face);
        NamespaceId centreSprite = view.sprite(0, 0, 0, face);

        if (!rule.connectTiles().isEmpty()) {
            return matchesAnyConnectTile(rule, neighbourSprite,
                    neighbourBlockId);
        }
        if (!rule.connectBlocks().isEmpty()) {
            return neighbourBlockId != null
                    && matchesAnyConnectBlock(rule, neighbourBlockId);
        }

        return switch (rule.connect()) {
            case BLOCK -> neighbourBlockId != null
                    && neighbourBlockId.equals(centreBlockId);
            case STATE -> neighbourBlockId != null
                    && neighbourBlockId.equals(centreBlockId)
                    && view.isFullBlock(dx, dy, dz) == view.isFullBlock(0, 0, 0);
            case TILE  -> neighbourSprite != null && neighbourSprite.equals(centreSprite);
        };
    }

    private boolean overlayConnect(CtmRule rule, NeighborView view,
                                   int dx, int dy, int dz, int face) {
        if (!view.isFullBlock(dx, dy, dz)) {
            return false;
        }
        if (!rule.connectTiles().isEmpty()
                && !matchesAnyConnectTile(rule, view.sprite(dx, dy, dz, face),
                view.blockId(dx, dy, dz))) {
            return false;
        }
        if (!rule.connectBlocks().isEmpty()
                && !matchesAnyConnectBlock(rule, view.blockId(dx, dy, dz))) {
            return false;
        }
        if (rule.connectTiles().isEmpty()
                && rule.connectBlocks().isEmpty()
                && !matchesAnyConnectBlockId(view.blockId(dx, dy, dz),
                view.blockId(0, 0, 0))) {
            return false;
        }
        int[] normal = Faces.delta(face);
        if (view.isFullBlock(dx + normal[0], dy + normal[1],
                dz + normal[2])) {
            return false;
        }
        return !sameAsBaseUnderRule(rule, view, dx, dy, dz, face);
    }

    private boolean overlayBaseMatches(CtmRule rule, NeighborView view,
                                       int dx, int dy, int dz, int face) {
        String blockId = view.blockId(dx, dy, dz);
        if (blockId == null) {
            return false;
        }
        if (!rule.matchBlocks().isEmpty()) {
            if (!matchesAnyMatchBlock(rule, blockId)) {
                return false;
            }
        }
        if (!rule.matchTiles().isEmpty()) {
            NamespaceId sprite = view.sprite(dx, dy, dz, face);
            if (sprite == null || !rule.matchTiles().contains(sprite)) {
                return false;
            }
        }
        int[] normal = Faces.delta(face);
        return !view.isFullBlock(dx + normal[0], dy + normal[1],
                dz + normal[2]);
    }

    private static boolean sameAsBaseUnderRule(CtmRule rule,
                                               NeighborView view,
                                               int dx, int dy, int dz,
                                               int face) {
        String neighbourBlockId = view.blockId(dx, dy, dz);
        String centreBlockId = view.blockId(0, 0, 0);
        return switch (rule.connect()) {
            case BLOCK -> matchesAnyConnectBlockId(neighbourBlockId,
                    centreBlockId);
            case STATE -> matchesAnyConnectBlockId(neighbourBlockId,
                    centreBlockId)
                    && view.isFullBlock(dx, dy, dz)
                    == view.isFullBlock(0, 0, 0);
            case TILE -> {
                NamespaceId neighbourSprite = view.sprite(dx, dy, dz, face);
                NamespaceId centreSprite = view.sprite(0, 0, 0, face);
                yield neighbourSprite != null
                        && neighbourSprite.equals(centreSprite);
            }
        };
    }

    private static boolean matchesAnyConnectBlockId(String left,
                                                    String right) {
        return left != null && left.equals(right);
    }

    private static boolean matchesAnyMatchBlock(CtmRule rule,
                                                String neighbourBlockId) {
        int colon = neighbourBlockId.indexOf(':');
        String namespace = colon < 0
                ? NamespaceId.DEFAULT_NAMESPACE
                : neighbourBlockId.substring(0, colon);
        String name = colon < 0
                ? neighbourBlockId
                : neighbourBlockId.substring(colon + 1);
        for (BlockSpec block : rule.matchBlocks()) {
            if (block.namespace().equals(namespace)
                    && block.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static int[][] overlaySideOffsets(int face) {
        return switch (face) {
            case Faces.DOWN -> new int[][] {
                    { -1, 0, 0 }, { 1, 0, 0 },
                    { 0, 0, -1 }, { 0, 0, 1 }
            };
            case Faces.UP -> new int[][] {
                    { -1, 0, 0 }, { 1, 0, 0 },
                    { 0, 0, 1 }, { 0, 0, -1 }
            };
            case Faces.NORTH -> new int[][] {
                    { 1, 0, 0 }, { -1, 0, 0 },
                    { 0, -1, 0 }, { 0, 1, 0 }
            };
            case Faces.SOUTH -> new int[][] {
                    { -1, 0, 0 }, { 1, 0, 0 },
                    { 0, -1, 0 }, { 0, 1, 0 }
            };
            case Faces.WEST -> new int[][] {
                    { 0, 0, -1 }, { 0, 0, 1 },
                    { 0, -1, 0 }, { 0, 1, 0 }
            };
            case Faces.EAST -> new int[][] {
                    { 0, 0, 1 }, { 0, 0, -1 },
                    { 0, -1, 0 }, { 0, 1, 0 }
            };
            default -> throw new IllegalArgumentException("bad face: " + face);
        };
    }

    private static int[][] overlayEdgeOffsets(int face) {
        return switch (face) {
            case Faces.DOWN -> new int[][] {
                    { 1, 0, -1 }, { -1, 0, -1 },
                    { 1, 0, 1 }, { -1, 0, 1 }
            };
            case Faces.UP -> new int[][] {
                    { 1, 0, 1 }, { -1, 0, 1 },
                    { 1, 0, -1 }, { -1, 0, -1 }
            };
            case Faces.NORTH -> new int[][] {
                    { -1, -1, 0 }, { 1, -1, 0 },
                    { -1, 1, 0 }, { 1, 1, 0 }
            };
            case Faces.SOUTH -> new int[][] {
                    { 1, -1, 0 }, { -1, -1, 0 },
                    { 1, 1, 0 }, { -1, 1, 0 }
            };
            case Faces.WEST -> new int[][] {
                    { 0, -1, 1 }, { 0, -1, -1 },
                    { 0, 1, 1 }, { 0, 1, -1 }
            };
            case Faces.EAST -> new int[][] {
                    { 0, -1, -1 }, { 0, -1, 1 },
                    { 0, 1, -1 }, { 0, 1, 1 }
            };
            default -> throw new IllegalArgumentException("bad face: " + face);
        };
    }

    private static boolean matchesAnyConnectBlock(CtmRule rule,
                                                  String neighbourBlockId) {
        int colon = neighbourBlockId.indexOf(':');
        String namespace = colon < 0
                ? NamespaceId.DEFAULT_NAMESPACE
                : neighbourBlockId.substring(0, colon);
        String name = colon < 0
                ? neighbourBlockId
                : neighbourBlockId.substring(colon + 1);
        for (BlockSpec block : rule.connectBlocks()) {
            if (block.namespace().equals(namespace)
                    && block.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesAnyConnectTile(CtmRule rule,
                                                 NamespaceId neighbourSprite,
                                                 String neighbourBlockId) {
        if (neighbourSprite != null
                && rule.connectTiles().contains(neighbourSprite)) {
            return true;
        }
        if (neighbourBlockId == null) {
            return false;
        }
        int colon = neighbourBlockId.indexOf(':');
        String namespace = colon < 0
                ? NamespaceId.DEFAULT_NAMESPACE
                : neighbourBlockId.substring(0, colon);
        String name = colon < 0
                ? neighbourBlockId
                : neighbourBlockId.substring(colon + 1);
        String basePath = "block/" + name;
        String compactBlockPath = name.endsWith("_block")
                ? "block/" + name.substring(0, name.length() - 6)
                : null;
        for (NamespaceId tile : rule.connectTiles()) {
            if (!tile.namespace().equals(namespace)) {
                continue;
            }
            String path = tile.path();
            if (path.equals(basePath)
                    || path.equals(compactBlockPath)) {
                return true;
            }
        }
        return false;
    }
}
