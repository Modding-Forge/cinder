package com.cinder.ctm;

import com.cinder.resource.NamespaceId;
import com.cinder.resource.PropertiesFile;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CtmSelectorTest {

    /**
     * Synthetic {@link NeighborView} that lets each test hand-author
     * which blocks/sprites are present in the 3x3x3 cube. Only the
     * centre cell is populated by the constructor; every other
     * neighbour starts as {@code null} and is opted-in by calling
     * {@link #set(int, int, int, String, NamespaceId)}. This matches
     * how the real engine treats air (no block, no rendered sprite).
     */
    private static final class Grid implements NeighborView {
        final String selfId;
        final NamespaceId selfSprite;
        final String[][][] blockIds = new String[3][3][3];
        final NamespaceId[][][][] sprites = new NamespaceId[3][3][3][6];
        final boolean[][][] full = new boolean[3][3][3];

        Grid(String selfId, NamespaceId selfSprite) {
            this.selfId = selfId;
            this.selfSprite = selfSprite;
            // Only the centre cell is populated. All neighbours start
            // empty (null) and "not full block"; tests opt in via
            // set() to indicate which neighbours actually exist.
            blockIds[1][1][1] = selfId;
            full[1][1][1] = true;
            for (int f = 0; f < 6; f++) {
                sprites[1][1][1][f] = selfSprite;
            }
        }

        Grid set(int dx, int dy, int dz, String blockId, NamespaceId sprite) {
            blockIds[dx + 1][dy + 1][dz + 1] = blockId;
            for (int f = 0; f < 6; f++) {
                sprites[dx + 1][dy + 1][dz + 1][f] = sprite;
            }
            full[dx + 1][dy + 1][dz + 1] = true;
            return this;
        }

        Grid setFace(int dx, int dy, int dz, int face, NamespaceId sprite) {
            sprites[dx + 1][dy + 1][dz + 1][face] = sprite;
            return this;
        }

        @Override public NamespaceId sprite(int dx, int dy, int dz, int face) {
            return sprites[dx + 1][dy + 1][dz + 1][face];
        }
        @Override public String blockId(int dx, int dy, int dz) {
            return blockIds[dx + 1][dy + 1][dz + 1];
        }
        @Override public boolean isFullBlock(int dx, int dy, int dz) {
            return full[dx + 1][dy + 1][dz + 1];
        }
    }

    private static CtmRule parseRule(String body) {
        PropertiesFile p;
        try {
            p = PropertiesFile.parse(new StringReader(body));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return CtmProperties.parse(p,
                new NamespaceId("minecraft", "optifine/ctm"), "x.properties");
    }

    private static CtmSelector selector(CtmRule rule) {
        return new CtmSelector(new CtmRuleSet.Builder().add(rule).build());
    }

    @Test
    void ctm_isolatedBlock_returnsTileZero() {
        CtmRule r = parseRule(
                "method=ctm\n"
                        + "matchBlocks=minecraft:stone\n"
                        + "tiles=0-46\n");
        Grid g = new Grid("minecraft:stone",
                new NamespaceId("minecraft", "block/stone"));
        CtmSelectionResult sel = selector(r).select(r, g, 0, 0, 0, Faces.UP);
        assertNotNull(sel);
        assertEquals(0, sel.tileIndex());
    }

    @Test
    void ctm_allSidesSameBlock_returnsAllConnected() {
        CtmRule r = parseRule(
                "method=ctm\n"
                        + "matchBlocks=minecraft:stone\n"
                        + "connect=block\n"
                        + "tiles=0-46\n");
        Grid g = new Grid("minecraft:stone",
                new NamespaceId("minecraft", "block/stone"));
        // Set every neighbour (6 ortho + 8 corner + 12 edge = 26
        // cells) to stone. The 4 ortho sides AND the 4 diagonals
        // for the UP face are therefore all stone, so the engine
        // should return the all-connected tile.
        NamespaceId stoneSprite = new NamespaceId("minecraft", "block/stone");
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    g.set(dx, dy, dz, "minecraft:stone", stoneSprite);
                }
            }
        }
        CtmSelectionResult sel = selector(r).select(r, g, 0, 0, 0, Faces.UP);
        assertEquals(TileIndexTable.TILE_ALL_CONNECTED, sel.tileIndex());
    }

    @Test
    void horizontal_bothSides_returnsMiddleTile() {
        CtmRule r = parseRule(
                "method=horizontal\n"
                        + "matchBlocks=minecraft:oak_log\n"
                        + "tiles=0-3\n");
        Grid g = new Grid("minecraft:oak_log",
                new NamespaceId("minecraft", "block/oak_log"));
        g.set(1, 0, 0, "minecraft:oak_log", new NamespaceId("minecraft", "block/oak_log"));
        g.set(-1, 0, 0, "minecraft:oak_log", new NamespaceId("minecraft", "block/oak_log"));
        CtmSelectionResult sel = selector(r).select(r, g, 0, 0, 0, Faces.UP);
        assertEquals(1, sel.tileIndex());
    }

    @Test
    void horizontal_leftOnly_returnsTile2() {
        CtmRule r = parseRule(
                "method=horizontal\n"
                        + "matchBlocks=minecraft:oak_log\n"
                        + "tiles=0-3\n");
        Grid g = new Grid("minecraft:oak_log",
                new NamespaceId("minecraft", "block/oak_log"));
        g.set(-1, 0, 0, "minecraft:oak_log", new NamespaceId("minecraft", "block/oak_log"));
        CtmSelectionResult sel = selector(r).select(r, g, 0, 0, 0, Faces.UP);
        assertEquals(2, sel.tileIndex());
    }

    @Test
    void horizontal_rightOnly_returnsTile0() {
        CtmRule r = parseRule(
                "method=horizontal\n"
                        + "matchBlocks=minecraft:oak_log\n"
                        + "tiles=0-3\n");
        Grid g = new Grid("minecraft:oak_log",
                new NamespaceId("minecraft", "block/oak_log"));
        g.set(1, 0, 0, "minecraft:oak_log", new NamespaceId("minecraft", "block/oak_log"));
        CtmSelectionResult sel = selector(r).select(r, g, 0, 0, 0, Faces.UP);
        assertEquals(0, sel.tileIndex());
    }

    @Test
    void horizontal_neither_returnsTile3() {
        CtmRule r = parseRule(
                "method=horizontal\n"
                        + "matchBlocks=minecraft:oak_log\n"
                        + "tiles=0-3\n");
        Grid g = new Grid("minecraft:oak_log",
                new NamespaceId("minecraft", "block/oak_log"));
        CtmSelectionResult sel = selector(r).select(r, g, 0, 0, 0, Faces.UP);
        assertEquals(3, sel.tileIndex());
    }

    @Test
    void vertical_neither_returnsTile3() {
        CtmRule r = parseRule(
                "method=vertical\n"
                        + "matchBlocks=minecraft:oak_log\n"
                        + "tiles=0-3\n");
        Grid g = new Grid("minecraft:oak_log",
                new NamespaceId("minecraft", "block/oak_log"));
        CtmSelectionResult sel = selector(r).select(r, g, 0, 0, 0, Faces.NORTH);
        assertEquals(3, sel.tileIndex());
    }

    @Test
    void fixed_isAlwaysZero() {
        CtmRule r = parseRule(
                "method=fixed\n"
                        + "matchBlocks=minecraft:glass\n"
                        + "tiles=0\n");
        Grid g = new Grid("minecraft:glass",
                new NamespaceId("minecraft", "block/glass"));
        CtmSelectionResult sel = selector(r).select(r, g, 5, 5, 5, Faces.UP);
        assertEquals(0, sel.tileIndex());
    }

    @Test
    void top_requiresBlockAbove() {
        CtmRule r = parseRule(
                "method=top\n"
                        + "matchBlocks=minecraft:sandstone\n"
                        + "connect=block\n"
                        + "tiles=0\n");
        Grid g = new Grid("minecraft:sandstone",
                new NamespaceId("minecraft", "block/sandstone"));
        CtmSelectionResult isolated = selector(r).select(r, g, 0, 0, 0, Faces.NORTH);
        assertNull(isolated);

        g.set(0, 1, 0, "minecraft:sandstone",
                new NamespaceId("minecraft", "block/sandstone"));
        CtmSelectionResult connected = selector(r).select(r, g, 0, 0, 0, Faces.NORTH);
        assertNotNull(connected);
        assertEquals(0, connected.tileIndex());
    }

    @Test
    void overlay_usesConnectTilesForNeighbourSelection() {
        CtmRule r = parseRule(
                "method=overlay\n"
                        + "matchBlocks=minecraft:dirt\n"
                        + "connectTiles=sand\n"
                        + "tiles=0-16\n");
        Grid g = new Grid("minecraft:dirt",
                new NamespaceId("minecraft", "block/dirt"));
        g.set(0, 0, -1, "minecraft:sand",
                new NamespaceId("minecraft", "block/sand"));

        CtmSelectionResult sel = selector(r).select(r, g, 0, 0, 0, Faces.UP);

        assertNotNull(sel);
        assertEquals(15, sel.tileIndex());
    }

    @Test
    void overlay_matchBlocksCanTargetFarmland() {
        CtmRule r = parseRule(
                "method=overlay\n"
                        + "matchBlocks=minecraft:farmland minecraft:dirt\n"
                        + "connectTiles=grass_block_top\n"
                        + "tiles=0-16\n");
        Grid g = new Grid("minecraft:farmland",
                new NamespaceId("minecraft", "block/farmland_moist"));
        g.set(0, 0, -1, "minecraft:grass_block",
                new NamespaceId("minecraft", "block/grass_block_top"));

        CtmRenderSelection selection = selector(r).selectRender(
                r, g, 0, 0, 0, Faces.UP,
                new NamespaceId("minecraft", "block/farmland_moist"));

        assertNotNull(selection);
        assertTrue(selection.isOverlay());
        assertEquals(java.util.List.of(15), selection.overlayTileIndices());
    }

    @Test
    void overlay_connectTilesDoesNotInferDirectionalTextureFromBlockName() {
        CtmRule r = parseRule(
                "method=overlay\n"
                        + "matchBlocks=minecraft:dirt\n"
                        + "connectTiles=grass_block_top\n"
                        + "tiles=0-16\n");
        Grid g = new Grid("minecraft:dirt",
                new NamespaceId("minecraft", "block/dirt"));
        g.set(0, 0, -1, "minecraft:grass_block", null);

        CtmSelectionResult sel = selector(r).select(r, g, 0, 0, 0, Faces.UP);

        assertNotNull(sel);
        assertTrue(sel.isSkip());
    }

    @Test
    void overlay_connectTilesFallsBackFromCompactBlockName() {
        CtmRule r = parseRule(
                "method=overlay\n"
                        + "matchBlocks=minecraft:dirt\n"
                        + "connectTiles=snow\n"
                        + "tiles=0-16\n");
        Grid g = new Grid("minecraft:dirt",
                new NamespaceId("minecraft", "block/dirt"));
        g.set(0, 0, -1, "minecraft:snow_block", null);

        CtmSelectionResult sel = selector(r).select(r, g, 0, 0, 0, Faces.UP);

        assertNotNull(sel);
        assertEquals(15, sel.tileIndex());
    }

    @Test
    void overlay_oppositeSidesExposeMultipleOverlayTilesForRender() {
        CtmRule r = parseRule(
                "method=overlay\n"
                        + "matchBlocks=minecraft:dirt\n"
                        + "connectTiles=grass_block_top\n"
                        + "tiles=0-16\n");
        Grid g = new Grid("minecraft:dirt",
                new NamespaceId("minecraft", "block/dirt"));
        NamespaceId grass = new NamespaceId("minecraft", "block/grass_block_top");
        g.set(-1, 0, 0, "minecraft:grass_block", grass);
        g.set(1, 0, 0, "minecraft:grass_block", grass);

        CtmRenderSelection selection = selector(r).selectRender(
                r, g, 0, 0, 0, Faces.UP,
                new NamespaceId("minecraft", "block/dirt"));

        assertNotNull(selection);
        assertEquals(java.util.List.of(9, 7), selection.overlayTileIndices());
    }

    @Test
    void overlay_diagonalOnlyWithoutBaseSideSkipsCornersForRender() {
        CtmRule r = parseRule(
                "method=overlay\n"
                        + "matchBlocks=minecraft:dirt\n"
                        + "connectTiles=grass_block_top\n"
                        + "tiles=0-16\n");
        Grid g = new Grid("minecraft:dirt",
                new NamespaceId("minecraft", "block/dirt"));
        g.set(-1, 0, -1, "minecraft:grass_block", null);
        g.set(1, 0, 1, "minecraft:grass_block", null);

        CtmRenderSelection selection = selector(r).selectRender(
                r, g, 0, 0, 0, Faces.UP,
                new NamespaceId("minecraft", "block/dirt"));

        assertNotNull(selection);
        assertEquals(java.util.List.of(), selection.overlayTileIndices());
    }

    @Test
    void overlay_diagonalCornerWithoutOverlaySideRendersForRender() {
        CtmRule r = parseRule(
                "method=overlay\n"
                        + "matchBlocks=minecraft:dirt\n"
                        + "connectTiles=grass_block_top\n"
                        + "tiles=0-16\n");
        Grid g = new Grid("minecraft:dirt",
                new NamespaceId("minecraft", "block/dirt"));
        NamespaceId grass = new NamespaceId("minecraft", "block/grass_block_top");
        g.set(1, 0, 1, "minecraft:grass_block", grass);
        g.set(1, 0, 0, "minecraft:dirt",
                new NamespaceId("minecraft", "block/dirt"));

        CtmRenderSelection selection = selector(r).selectRender(
                r, g, 0, 0, 0, Faces.UP,
                new NamespaceId("minecraft", "block/dirt"));

        assertNotNull(selection);
        assertEquals(java.util.List.of(0), selection.overlayTileIndices());
    }

    @Test
    void overlay_diagonalCornerWithAdjacentOverlaySideRendersForRender() {
        CtmRule r = parseRule(
                "method=overlay\n"
                        + "matchBlocks=minecraft:dirt\n"
                        + "connectTiles=grass_block_top\n"
                        + "tiles=0-16\n");
        Grid g = new Grid("minecraft:dirt",
                new NamespaceId("minecraft", "block/dirt"));
        NamespaceId grass = new NamespaceId("minecraft", "block/grass_block_top");
        g.set(1, 0, 0, "minecraft:grass_block", grass);
        g.set(1, 0, 1, "minecraft:grass_block", grass);

        CtmRenderSelection selection = selector(r).selectRender(
                r, g, 0, 0, 0, Faces.UP,
                new NamespaceId("minecraft", "block/dirt"));

        assertNotNull(selection);
        assertEquals(java.util.List.of(7, 0), selection.overlayTileIndices());
    }

    @Test
    void overlay_diagonalCornerSkipsWhenOnlyUnanchoredSideIsConnected() {
        CtmRule r = parseRule(
                "method=overlay\n"
                        + "matchBlocks=minecraft:gravel\n"
                        + "connectTiles=grass_block_top\n"
                        + "tiles=0-16\n");
        Grid g = new Grid("minecraft:gravel",
                new NamespaceId("minecraft", "block/gravel"));
        NamespaceId grass = new NamespaceId("minecraft", "block/grass_block_top");
        g.set(0, 0, 1, "minecraft:grass_block", grass);
        g.set(1, 0, -1, "minecraft:grass_block", grass);

        CtmRenderSelection selection = selector(r).selectRender(
                r, g, 0, 0, 0, Faces.UP,
                new NamespaceId("minecraft", "block/gravel"));

        assertNotNull(selection);
        assertEquals(java.util.List.of(1), selection.overlayTileIndices());
    }

    @Test
    void overlay_sideWithAdjacentBaseSideAddsCornerCapForRender() {
        CtmRule r = parseRule(
                "method=overlay\n"
                        + "matchBlocks=minecraft:dirt minecraft:sand\n"
                        + "connectTiles=snow\n"
                        + "tiles=0-16\n");
        Grid g = new Grid("minecraft:sand",
                new NamespaceId("minecraft", "block/sand"));
        g.set(-1, 0, 0, "minecraft:dirt",
                new NamespaceId("minecraft", "block/dirt"));
        g.set(0, 0, 1, "minecraft:snow_block",
                new NamespaceId("minecraft", "block/snow"));

        CtmRenderSelection selection = selector(r).selectRender(
                r, g, 0, 0, 0, Faces.UP,
                new NamespaceId("minecraft", "block/sand"));

        assertNotNull(selection);
        assertEquals(java.util.List.of(1, 2), selection.overlayTileIndices());
    }

    @Test
    void overlay_withoutConnectTileNeighboursSkips() {
        CtmRule r = parseRule(
                "method=overlay\n"
                        + "matchBlocks=minecraft:dirt\n"
                        + "connectTiles=sand\n"
                        + "tiles=0-16\n");
        Grid g = new Grid("minecraft:dirt",
                new NamespaceId("minecraft", "block/dirt"));

        CtmSelectionResult sel = selector(r).select(r, g, 0, 0, 0, Faces.UP);

        assertNotNull(sel);
        assertTrue(sel.isSkip());
    }

    @Test
    void repeat_isPositionDependent() {
        CtmRule r = parseRule(
                "method=repeat\n"
                        + "matchBlocks=minecraft:stone\n"
                        + "width=2\n"
                        + "height=2\n"
                        + "tiles=0-3\n");
        Grid g = new Grid("minecraft:stone",
                new NamespaceId("minecraft", "block/stone"));
        CtmSelectionResult at00 = selector(r).select(r, g, 0, 0, 0, Faces.UP);
        CtmSelectionResult at10 = selector(r).select(r, g, 1, 0, 0, Faces.UP);
        CtmSelectionResult at01 = selector(r).select(r, g, 0, 1, 0, Faces.UP);
        CtmSelectionResult at11 = selector(r).select(r, g, 1, 1, 0, Faces.UP);
        // All 4 should be in 0..3 and at least two should differ.
        assertTrue(at00.tileIndex() < 4);
        assertTrue(at10.tileIndex() < 4);
        assertTrue(at01.tileIndex() < 4);
        assertTrue(at11.tileIndex() < 4);
        // The 2x2 layout should produce 4 distinct tile indices in
        // total across the 4 cells.
        int distinct = (int) java.util.stream.Stream.of(at00, at10, at01, at11)
                .map(CtmSelectionResult::tileIndex)
                .distinct()
                .count();
        assertTrue(distinct >= 2, "2x2 repeat should produce at least 2 distinct tiles");
    }

    @Test
    void random_isDeterministic() {
        CtmRule r = parseRule(
                "method=random\n"
                        + "matchBlocks=minecraft:grass_block\n"
                        + "tiles=0-3\n");
        Grid g = new Grid("minecraft:grass_block",
                new NamespaceId("minecraft", "block/grass_block"));
        CtmSelectionResult a = selector(r).select(r, g, 10, 20, 30, Faces.UP);
        CtmSelectionResult b = selector(r).select(r, g, 10, 20, 30, Faces.UP);
        assertEquals(a.tileIndex(), b.tileIndex());
    }

    @Test
    void random_weightedDistribution() {
        // Build a 2-tile rule with weight 1:99. The second tile
        // should be sampled almost always.
        CtmRule r = parseRule(
                "method=random\n"
                        + "matchBlocks=minecraft:grass_block\n"
                        + "tiles=0-1\n"
                        + "weights=1 99\n");
        Grid g = new Grid("minecraft:grass_block",
                new NamespaceId("minecraft", "block/grass_block"));
        int second = 0;
        int n = 1000;
        for (int i = 0; i < n; i++) {
            CtmSelectionResult s = selector(r).select(r, g, i, 0, 0, Faces.UP);
            if (s.tileIndex() == 1) {
                second++;
            }
        }
        double ratio = second / (double) n;
        // Expected ~0.99, allow 5% slack.
        assertTrue(ratio > 0.95, "Second tile should dominate, got " + ratio);
    }

    @Test
    void ctmCompact_allConnected_returns4() {
        CtmRule r = parseRule(
                "method=ctm_compact\n"
                        + "matchBlocks=minecraft:glass\n"
                        + "tiles=0-4\n");
        Grid g = new Grid("minecraft:glass",
                new NamespaceId("minecraft", "block/glass"));
        // Fully surround with glass.
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    g.set(dx, dy, dz, "minecraft:glass",
                            new NamespaceId("minecraft", "block/glass"));
                }
        CtmSelectionResult sel = selector(r).select(r, g, 0, 0, 0, Faces.UP);
        assertEquals(4, sel.tileIndex());
    }

    @Test
    void ctmCompact_tileConnectUsesRenderedFaceSprite() {
        CtmRule r = parseRule(
                "method=ctm_compact\n"
                        + "matchBlocks=minecraft:iron_block\n"
                        + "connect=tile\n"
                        + "tiles=0-4\n");
        NamespaceId top = new NamespaceId("minecraft", "block/iron_block");
        NamespaceId side = new NamespaceId("minecraft", "block/iron_block_side");
        Grid g = new Grid("minecraft:iron_block", top);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                g.set(dx, 0, dz, "minecraft:iron_block", side);
                g.setFace(dx, 0, dz, Faces.UP, top);
            }
        }

        CtmSelectionResult sel = selector(r).select(r, g, 0, 0, 0, Faces.UP);

        assertEquals(4, sel.tileIndex());
    }

    @Test
    void ctm_isolatedReturnsZero() {
        CtmRule r = parseRule(
                "method=ctm\n"
                        + "matchBlocks=minecraft:stone\n"
                        + "tiles=0-46\n");
        Grid g = new Grid("minecraft:stone",
                new NamespaceId("minecraft", "block/stone"));
        CtmSelectionResult sel = selector(r).select(r, g, 0, 0, 0, Faces.UP);
        assertEquals(0, sel.tileIndex());
    }

    @Test
    void faceMask_isRespected() {
        CtmRule r = parseRule(
                "method=ctm\n"
                        + "matchBlocks=minecraft:stone\n"
                        + "faces=top\n"
                        + "tiles=0-46\n");
        Grid g = new Grid("minecraft:stone",
                new NamespaceId("minecraft", "block/stone"));
        // Should be ignored on a non-top face even though the rule
        // would otherwise match.
        CtmSelectionResult sel = selector(r).select(r, g, 0, 0, 0, Faces.NORTH);
        assertNull(sel);
    }

    @Test
    void overlay_twoSidePairsUseOverlayTileLayoutForRender() {
        CtmRule r = parseRule(
                "method=overlay\n"
                        + "matchBlocks=minecraft:dirt\n"
                        + "connectTiles=grass_block_top\n"
                        + "tiles=0-16\n");
        assertOverlayTiles(r, Faces.UP,
                new int[][] { { -1, 0, 0 }, { 0, 0, 1 } },
                java.util.List.of(4));
        assertOverlayTiles(r, Faces.UP,
                new int[][] { { -1, 0, 0 }, { 0, 0, -1 } },
                java.util.List.of(11));
    }

    @Test
    void select_returnsNullForNullRule() {
        Grid g = new Grid("minecraft:stone",
                new NamespaceId("minecraft", "block/stone"));
        CtmSelector s = new CtmSelector(CtmRuleSet.empty());
        assertNull(s.select(null, g, 0, 0, 0, Faces.UP));
    }

    private static void assertOverlayTiles(CtmRule rule, int face,
                                           int[][] offsets,
                                           java.util.List<Integer> expected) {
        Grid g = new Grid("minecraft:dirt",
                new NamespaceId("minecraft", "block/dirt"));
        NamespaceId grass = new NamespaceId("minecraft", "block/grass_block_top");
        for (int[] offset : offsets) {
            g.set(offset[0], offset[1], offset[2],
                    "minecraft:grass_block", grass);
        }
        CtmRenderSelection selection = selector(rule).selectRender(
                rule, g, 0, 0, 0, face,
                new NamespaceId("minecraft", "block/dirt"));
        assertNotNull(selection);
        assertEquals(expected, selection.overlayTileIndices());
    }
}
