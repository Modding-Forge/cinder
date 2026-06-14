package com.cinder.ctm;

import com.cinder.resource.NamespaceId;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link CtmRenderSelection}, the backend-neutral renderer-facing
 * metadata produced by {@link CtmSelector}.
 */
class CtmRenderSelectionTest {

    private static final NamespaceId GLASS =
            new NamespaceId("minecraft", "block/glass");

    private static final class ConnectedView implements NeighborView {
        @Override public NamespaceId sprite(int dx, int dy, int dz, int face) {
            return GLASS;
        }

        @Override public String blockId(int dx, int dy, int dz) {
            return "minecraft:glass";
        }

        @Override public boolean isFullBlock(int dx, int dy, int dz) {
            return true;
        }
    }

    private static final class IsolatedView implements NeighborView {
        @Override public NamespaceId sprite(int dx, int dy, int dz, int face) {
            return dx == 0 && dy == 0 && dz == 0 ? GLASS : null;
        }

        @Override public String blockId(int dx, int dy, int dz) {
            return dx == 0 && dy == 0 && dz == 0 ? "minecraft:glass" : null;
        }

        @Override public boolean isFullBlock(int dx, int dy, int dz) {
            return dx == 0 && dy == 0 && dz == 0;
        }
    }

    @Test
    void selectRender_returnsBackendNeutralPrimarySelection() {
        CtmRule rule = rule(CtmMethod.CTM, 47);
        CtmSelector selector = new CtmSelector(
                new CtmRuleSet.Builder().add(rule).build());

        CtmRenderSelection selection = selector.selectRender(
                rule, new ConnectedView(), 0, 64, 0, Faces.UP, GLASS);

        assertNotNull(selection);
        assertSame(rule, selection.rule());
        assertEquals(CtmMethod.CTM, selection.method());
        assertEquals(Faces.UP, selection.face());
        assertEquals(TileIndexTable.TILE_ALL_CONNECTED,
                selection.primaryTileIndex());
        assertEquals(CtmRenderSelection.NO_TILE,
                selection.secondaryTileIndex());
        assertEquals(GLASS, selection.baseSprite());
        assertTrue(selection.hasPrimaryTile());
        assertFalse(selection.hasSecondaryTile());
        assertFalse(selection.isLayered());
        assertFalse(selection.isOverlay());
        assertEquals(Integer.toString(TileIndexTable.TILE_ALL_CONNECTED),
                selection.primaryTile().rawSpec());
        assertNull(selection.secondaryTile());
    }

    @Test
    void selectRender_preservesLayeredMethodSelections() {
        CtmRule rule = rule(CtmMethod.HORIZONTAL_VERTICAL, 4);
        CtmSelector selector = new CtmSelector(
                new CtmRuleSet.Builder().add(rule).build());

        CtmRenderSelection selection = selector.selectRender(
                rule, new ConnectedView(), 0, 64, 0, Faces.NORTH, GLASS);

        assertNotNull(selection);
        assertEquals(CtmMethod.HORIZONTAL_VERTICAL, selection.method());
        assertTrue(selection.isLayered());
        assertEquals(1, selection.primaryTileIndex());
        assertEquals(1, selection.secondaryTileIndex());
        assertTrue(selection.hasPrimaryTile());
        assertTrue(selection.hasSecondaryTile());
        assertEquals("1", selection.primaryTile().rawSpec());
        assertEquals("1", selection.secondaryTile().rawSpec());
    }

    @Test
    void selectRender_marksOverlayMethods() {
        CtmRule rule = rule(CtmMethod.OVERLAY_FIXED, 1);
        CtmSelector selector = new CtmSelector(
                new CtmRuleSet.Builder().add(rule).build());

        CtmRenderSelection selection = selector.selectRender(
                rule, new IsolatedView(), 0, 64, 0, Faces.UP, GLASS);

        assertNotNull(selection);
        assertTrue(selection.isOverlay());
        assertFalse(selection.isLayered());
        assertEquals(0, selection.primaryTileIndex());
        assertEquals("0", selection.primaryTile().rawSpec());
    }

    @Test
    void selectRender_usesSyntheticFullTilesForCompactFallback() {
        CtmRule rule = rule(CtmMethod.CTM_COMPACT, 5);
        CtmSelector selector = new CtmSelector(
                new CtmRuleSet.Builder().add(rule).build());

        CtmRenderSelection selection = selector.selectRender(
                rule, new ConnectedView(), 0, 64, 0, Faces.UP, GLASS);

        assertNotNull(selection);
        assertEquals(CtmTileResolver.COMPACT_FULL_TILE_OFFSET
                        + Faces.UP * CtmTileResolver.COMPACT_FULL_TILE_COUNT
                        + TileIndexTable.TILE_ALL_CONNECTED,
                selection.primaryTileIndex());
        assertTrue(selection.hasPrimaryTile());
        assertNull(selection.primaryTile());
    }

    @Test
    void selectRender_usesRuleTileZeroForIsolatedCompact() {
        CtmRule rule = rule(CtmMethod.CTM_COMPACT, 5);
        CtmSelector selector = new CtmSelector(
                new CtmRuleSet.Builder().add(rule).build());

        CtmRenderSelection selection = selector.selectRender(
                rule, new IsolatedView(), 0, 64, 0, Faces.UP, GLASS);

        assertNotNull(selection);
        assertEquals(0, selection.primaryTileIndex());
        assertEquals("0", selection.primaryTile().rawSpec());
    }

    @Test
    void selectRender_returnsNullWhenRuleDoesNotApplyToFace() {
        CtmRule rule = new CtmRule.Builder()
                .method(CtmMethod.FIXED)
                .facesMask(1 << Faces.NORTH)
                .addMatchTile(GLASS)
                .addMatchBlock(new BlockSpec("minecraft", "glass", Map.of()))
                .addTile(CtmTileSpec.numeric(0))
                .sourceFile("fixed.properties")
                .sourceLine(1)
                .build();
        CtmSelector selector = new CtmSelector(
                new CtmRuleSet.Builder().add(rule).build());

        CtmRenderSelection selection = selector.selectRender(
                rule, new IsolatedView(), 0, 64, 0, Faces.UP, GLASS);

        assertNull(selection);
    }

    private static CtmRule rule(CtmMethod method, int tileCount) {
        CtmRule.Builder builder = new CtmRule.Builder()
                .method(method)
                .addMatchTile(GLASS)
                .addMatchBlock(new BlockSpec("minecraft", "glass", Map.of()))
                .sourceFile(method.name().toLowerCase() + ".properties")
                .sourceLine(1);
        for (int i = 0; i < tileCount; i++) {
            builder.addTile(CtmTileSpec.numeric(i));
        }
        return builder.build();
    }
}
