package com.cinder.ctm;

import com.cinder.resource.NamespaceId;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link CtmRenderResolver}, the shared rule lookup and selection
 * entry point used by renderer integrations.
 */
class CtmRenderResolverTest {

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

    private static final class OverlayView implements NeighborView {
        @Override public NamespaceId sprite(int dx, int dy, int dz, int face) {
            if (dx == 0 && dy == 0 && dz == -1) {
                return new NamespaceId("minecraft", "block/sand");
            }
            return new NamespaceId("minecraft", "block/dirt");
        }

        @Override public String blockId(int dx, int dy, int dz) {
            if (dx == 0 && dy == 0 && dz == -1) {
                return "minecraft:sand";
            }
            return "minecraft:dirt";
        }

        @Override public boolean isFullBlock(int dx, int dy, int dz) {
            if (dy == 1) {
                return false;
            }
            return true;
        }
    }

    private static final class MultiOverlayView implements NeighborView {
        @Override public NamespaceId sprite(int dx, int dy, int dz, int face) {
            if (dx == 0 && dy == 0 && dz == -1) {
                return new NamespaceId("minecraft", "block/sand");
            }
            if (dx == -1 && dy == 0 && dz == 0) {
                return new NamespaceId("minecraft", "block/gravel");
            }
            return new NamespaceId("minecraft", "block/dirt");
        }

        @Override public String blockId(int dx, int dy, int dz) {
            if (dx == 0 && dy == 0 && dz == -1) {
                return "minecraft:sand";
            }
            if (dx == -1 && dy == 0 && dz == 0) {
                return "minecraft:gravel";
            }
            return "minecraft:dirt";
        }

        @Override public boolean isFullBlock(int dx, int dy, int dz) {
            return dy != 1;
        }
    }

    private static final class ThrowingView implements NeighborView {
        @Override public NamespaceId sprite(int dx, int dy, int dz, int face) {
            throw new AssertionError("prefilter should skip selector");
        }

        @Override public String blockId(int dx, int dy, int dz) {
            throw new AssertionError("prefilter should skip selector");
        }

        @Override public boolean isFullBlock(int dx, int dy, int dz) {
            throw new AssertionError("prefilter should skip selector");
        }
    }

    @Test
    void resolve_prefersSpriteRuleBeforeBlockRule() {
        CtmRule spriteRule = rule(CtmMethod.FIXED, 1, true, false, 10);
        CtmRule blockRule = rule(CtmMethod.HORIZONTAL, 4, false, true, 0);
        CtmRegistry registry = new CtmRegistry("test");
        registry.replace(new CtmRuleSet.Builder()
                .add(blockRule)
                .add(spriteRule)
                .build());

        CtmRenderSelection selection = new CtmRenderResolver(registry)
                .resolve("minecraft:glass", GLASS, new ConnectedView(),
                        0, 64, 0, Faces.UP);

        assertEquals(spriteRule, selection.rule());
        assertEquals(CtmMethod.FIXED, selection.method());
    }

    @Test
    void resolvePlan_replacementWithoutOverlay() {
        CtmRule rule = rule(CtmMethod.FIXED, 1, true, false, 0);
        CtmRegistry registry = new CtmRegistry("test");
        registry.replace(new CtmRuleSet.Builder().add(rule).build());

        CtmRenderPlan plan = new CtmRenderResolver(registry)
                .resolvePlan("minecraft:glass", GLASS, new ConnectedView(),
                        0, 64, 0, Faces.UP);

        assertNotNull(plan);
        assertTrue(plan.hasReplacement());
        assertFalse(plan.hasOverlays());
        assertEquals(rule, plan.replacement().rule());
    }

    @Test
    void resolvePlan_overlayWithoutReplacement() {
        CtmRule sand = overlayRule("sand", "b_sand.properties");
        CtmRegistry registry = new CtmRegistry("test");
        registry.replace(new CtmRuleSet.Builder().add(sand).build());

        CtmRenderPlan plan = new CtmRenderResolver(registry)
                .resolvePlan("minecraft:dirt",
                        new NamespaceId("minecraft", "block/dirt"),
                        new OverlayView(), 0, 64, 0, Faces.UP);

        assertNotNull(plan);
        assertFalse(plan.hasReplacement());
        assertTrue(plan.hasOverlays());
        assertEquals(sand, plan.overlays().getFirst().rule());
    }

    @Test
    void resolvePlan_preservesOverlayBeforeReplacement() {
        CtmRule overlay = overlayRule("sand", "a_overlay.properties");
        CtmRule replacement = fixedBlockRule("dirt", "z_replacement.properties");
        CtmRegistry registry = new CtmRegistry("test");
        registry.replace(new CtmRuleSet.Builder()
                .add(replacement)
                .add(overlay)
                .build());

        CtmRenderPlan plan = new CtmRenderResolver(registry)
                .resolvePlan("minecraft:dirt",
                        new NamespaceId("minecraft", "block/dirt"),
                        new OverlayView(), 0, 64, 0, Faces.UP);

        assertNotNull(plan);
        assertTrue(plan.hasReplacement());
        assertTrue(plan.hasOverlays());
        assertEquals(replacement, plan.replacement().rule());
        assertEquals(overlay, plan.overlays().getFirst().rule());
    }

    @Test
    void renderIndex_prefilterSkipsUnknownSpriteAndBlock() {
        CtmRule rule = rule(CtmMethod.FIXED, 1, true, false, 0);
        CtmRegistry registry = new CtmRegistry("test");
        registry.replace(new CtmRuleSet.Builder().add(rule).build());
        NamespaceId unknown = new NamespaceId("minecraft", "block/stone");

        CtmRenderResolver resolver = new CtmRenderResolver(registry);
        assertFalse(resolver.hasCandidates(
                "minecraft:stone", unknown, Faces.UP));
        assertNull(resolver.resolvePlan(
                "minecraft:stone", unknown, new ThrowingView(),
                0, 64, 0, Faces.UP));
    }

    @Test
    void renderIndex_tracksFaceCandidatesAndOverlayPresence() {
        CtmRule topOnly = new CtmRule.Builder()
                .method(CtmMethod.FIXED)
                .facesMask(1 << Faces.UP)
                .addMatchTile(GLASS)
                .addTile(CtmTileSpec.numeric(0))
                .sourceFile("fixed.properties")
                .sourceLine(1)
                .build();
        CtmRule overlay = overlayRule("sand", "overlay.properties");
        CtmRuleSet set = new CtmRuleSet.Builder()
                .add(topOnly)
                .add(overlay)
                .build();

        assertTrue(set.renderIndex().hasSpriteCandidate(GLASS, Faces.UP));
        assertFalse(set.renderIndex().hasSpriteCandidate(GLASS, Faces.NORTH));
        assertTrue(set.renderIndex().hasOverlayCandidates());
    }

    @Test
    void resolve_returnsNullWhenNoRuleAppliesToFace() {
        CtmRule rule = new CtmRule.Builder()
                .method(CtmMethod.FIXED)
                .facesMask(1 << Faces.NORTH)
                .addMatchTile(GLASS)
                .addTile(CtmTileSpec.numeric(0))
                .sourceFile("fixed.properties")
                .sourceLine(1)
                .build();
        CtmRegistry registry = new CtmRegistry("test");
        registry.replace(new CtmRuleSet.Builder().add(rule).build());

        CtmRenderSelection selection = new CtmRenderResolver(registry)
                .resolve("minecraft:glass", GLASS, new ConnectedView(),
                        0, 64, 0, Faces.UP);

        assertNull(selection);
    }

    @Test
    void resolve_continuesAfterOverlaySkip() {
        CtmRule redSand = overlayRule("red_sand", "a_red_sand.properties");
        CtmRule sand = overlayRule("sand", "b_sand.properties");
        CtmRegistry registry = new CtmRegistry("test");
        registry.replace(new CtmRuleSet.Builder()
                .add(redSand)
                .add(sand)
                .build());

        CtmRenderSelection selection = new CtmRenderResolver(registry)
                .resolve("minecraft:dirt",
                        new NamespaceId("minecraft", "block/dirt"),
                        new OverlayView(), 0, 64, 0, Faces.UP);

        assertNotNull(selection);
        assertEquals(sand, selection.rule());
        assertEquals(15, selection.primaryTileIndex());
    }

    @Test
    void resolve_stacksMultipleOverlayRules() {
        CtmRule gravel = overlayRule("gravel", "a_gravel.properties");
        CtmRule sand = overlayRule("sand", "b_sand.properties");
        CtmRegistry registry = new CtmRegistry("test");
        registry.replace(new CtmRuleSet.Builder()
                .add(gravel)
                .add(sand)
                .build());

        CtmRenderSelection selection = new CtmRenderResolver(registry)
                .resolve("minecraft:dirt",
                        new NamespaceId("minecraft", "block/dirt"),
                        new MultiOverlayView(), 0, 64, 0, Faces.UP);

        assertNotNull(selection);
        assertTrue(selection.isOverlay());
        assertEquals(4, selection.overlayTiles().size());
        assertEquals(gravel, selection.overlayTiles().get(0).rule());
        assertEquals(gravel, selection.overlayTiles().get(1).rule());
        assertEquals(sand, selection.overlayTiles().get(2).rule());
        assertEquals(sand, selection.overlayTiles().get(3).rule());
    }

    @Test
    void resolvePlan_stacksMultipleOverlayRulesInPriorityOrder() {
        CtmRule gravel = overlayRule("gravel", "a_gravel.properties");
        CtmRule sand = overlayRule("sand", "b_sand.properties");
        CtmRegistry registry = new CtmRegistry("test");
        registry.replace(new CtmRuleSet.Builder()
                .add(sand)
                .add(gravel)
                .build());

        CtmRenderPlan plan = new CtmRenderResolver(registry)
                .resolvePlan("minecraft:dirt",
                        new NamespaceId("minecraft", "block/dirt"),
                        new MultiOverlayView(), 0, 64, 0, Faces.UP);

        assertNotNull(plan);
        assertFalse(plan.hasReplacement());
        assertEquals(4, plan.overlays().size());
        assertEquals(gravel, plan.overlays().get(0).rule());
        assertEquals(gravel, plan.overlays().get(1).rule());
        assertEquals(sand, plan.overlays().get(2).rule());
        assertEquals(sand, plan.overlays().get(3).rule());
    }

    private static CtmRule rule(CtmMethod method, int tileCount,
                                boolean matchTile, boolean matchBlock,
                                int weight) {
        CtmRule.Builder builder = new CtmRule.Builder()
                .method(method)
                .weight(weight)
                .sourceFile(method.name().toLowerCase() + ".properties")
                .sourceLine(1);
        if (matchTile) {
            builder.addMatchTile(GLASS);
        }
        if (matchBlock) {
            builder.addMatchBlock(
                    new BlockSpec("minecraft", "glass", Map.of()));
        }
        for (int i = 0; i < tileCount; i++) {
            builder.addTile(CtmTileSpec.numeric(i));
        }
        return builder.build();
    }

    private static CtmRule overlayRule(String connectTile, String sourceFile) {
        CtmRule.Builder builder = new CtmRule.Builder()
                .method(CtmMethod.OVERLAY)
                .addMatchBlock(new BlockSpec("minecraft", "dirt", Map.of()))
                .addConnectTile(new NamespaceId("minecraft",
                        "block/" + connectTile))
                .sourceFile(sourceFile)
                .sourceLine(1);
        for (int i = 0; i <= 16; i++) {
            builder.addTile(CtmTileSpec.numeric(i));
        }
        return builder.build();
    }

    private static CtmRule fixedBlockRule(String block, String sourceFile) {
        return new CtmRule.Builder()
                .method(CtmMethod.FIXED)
                .addMatchBlock(new BlockSpec("minecraft", block, Map.of()))
                .addTile(CtmTileSpec.numeric(0))
                .sourceFile(sourceFile)
                .sourceLine(1)
                .build();
    }
}
