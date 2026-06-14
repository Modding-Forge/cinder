package com.cinder.quad;

import com.cinder.ctm.BlockSpec;
import com.cinder.ctm.CtmMethod;
import com.cinder.ctm.CtmRegistry;
import com.cinder.ctm.CtmRule;
import com.cinder.ctm.CtmRuleSet;
import com.cinder.ctm.CtmTileAtlas;
import com.cinder.ctm.CtmTileAtlasEntry;
import com.cinder.ctm.CtmTileResolver;
import com.cinder.ctm.CtmTileSpec;
import com.cinder.ctm.NeighborView;
import com.cinder.platform.Platform;
import com.cinder.platform.Platforms;
import com.cinder.resource.NamespaceId;
import com.cinder.verify.VerifyRecorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link CtmQuadDecorator} - Cinder's default
 * decorator that consults the {@link CtmRegistry}.
 *
 * <p>The tests use a hand-built registry and neighbour view;
 * the renderer integration (Fabric adapter) is not in
 * scope. The tests verify the in-process logic only.
 */
class CtmQuadDecoratorTest {

    /** Test platform with a configurable CTM registry. */
    private static final class TestPlatform implements Platform {
        final CtmRegistry registry;
        TestPlatform(CtmRegistry registry) { this.registry = registry; }
        @Override public String id() { return "test"; }
        @Override public String modId() { return "test"; }
        @Override public boolean isClient() { return true; }
        @Override public CtmRegistry ctmRegistry() { return registry; }
    }

    /** A minimal neighbour view that reports "no neighbours". */
    private static final class EmptyView implements NeighborView {
        @Override public NamespaceId sprite(int dx, int dy, int dz, int face) {
            return null;
        }
        @Override public String blockId(int dx, int dy, int dz) { return null; }
        @Override public boolean isFullBlock(int dx, int dy, int dz) { return false; }
    }

    /** A populated neighbour view where the centre and all 26
     *  neighbours are glass. */
    private static final class ConnectedView implements NeighborView {
        final NamespaceId glass = new NamespaceId("minecraft", "block/glass");
        @Override public NamespaceId sprite(int dx, int dy, int dz, int face) {
            return glass;
        }
        @Override public String blockId(int dx, int dy, int dz) {
            return "minecraft:glass";
        }
        @Override public boolean isFullBlock(int dx, int dy, int dz) {
            return true;
        }
    }

    /** Test quad stub. */
    private static final class StubQuad implements QuadRef {
        private final NamespaceId sprite;
        private final String blockId;
        StubQuad(NamespaceId sprite, String blockId) {
            this.sprite = sprite;
            this.blockId = blockId;
        }
        @Override public NamespaceId sprite() { return sprite; }
        @Override public String blockId() { return blockId; }
        @Override public int lightEmission() { return 0; }
        @Override public int tintIndex() { return -1; }
        @Override public float aoShade() { return 1.0f; }
    }

    /** Test quad stub that honours {@link #withSprite}
     *  by recording the requested sprite id. */
    private static final class SwappableQuad implements QuadRef {
        private final NamespaceId sprite;
        private final String blockId;
        NamespaceId lastSwapRequest;
        SwappableQuad(NamespaceId sprite, String blockId) {
            this.sprite = sprite;
            this.blockId = blockId;
        }
        @Override public NamespaceId sprite() { return sprite; }
        @Override public String blockId() { return blockId; }
        @Override public int lightEmission() { return 0; }
        @Override public int tintIndex() { return -1; }
        @Override public float aoShade() { return 1.0f; }
        @Override public QuadRef withSprite(NamespaceId newSprite) {
            lastSwapRequest = newSprite;
            // Return a fresh quad with the new sprite so the
            // decorator sees a non-null replacement.
            return new SwappableQuad(newSprite, blockId);
        }
    }

    private final CtmRegistry registry = new CtmRegistry("test");

    @BeforeEach
    void installPlatform() {
        Platforms.setForTest(new TestPlatform(registry));
        registry.replace(CtmRuleSet.empty());
        VerifyRecorder.get().reset();
    }

    @AfterEach
    void restorePlatform() {
        Platforms.setForTest(null);
        VerifyRecorder.get().reset();
        CtmTileAtlas.resetForTest();
    }

    @Test
    void decorate_noRuleMatch_returnsEmpty() {
        CtmQuadDecorator d = new CtmQuadDecorator();
        StubQuad q = new StubQuad(
                new NamespaceId("minecraft", "block/glass"),
                "minecraft:glass");
        QuadContext ctx = new QuadContext(0, 0, 0, 1,
                "minecraft:glass", q.sprite(), new EmptyView());
        Optional<QuadRef> out = d.decorate(q, ctx);
        assertTrue(out.isEmpty(),
                "no rule match must leave the quad unchanged");
    }

    @Test
    void decorate_ruleMatches_consultsView() {
        CtmRule rule = new CtmRule.Builder()
                .method(CtmMethod.CTM)
                .addMatchTile(new NamespaceId("minecraft", "block/glass"))
                .addMatchBlock(new BlockSpec(
                        "minecraft", "glass", Map.of()))
                .addTile(CtmTileSpec.numeric(0))
                .sourceFile("test.properties")
                .sourceLine(1)
                .build();
        registry.replace(new CtmRuleSet.Builder().add(rule).build());

        CtmQuadDecorator d = new CtmQuadDecorator();
        StubQuad q = new StubQuad(glassSprite(), "minecraft:glass");
        QuadContext ctx = new QuadContext(0, 0, 0, 1,
                "minecraft:glass", q.sprite(), new ConnectedView());
        long matchesBefore = VerifyRecorder.get().snapshot().matches();
        Optional<QuadRef> out = d.decorate(q, ctx);
        long matchesAfter = VerifyRecorder.get().snapshot().matches();
        // Phase 5: the sprite swap is deferred to the
        // renderer-side adapter. The decorator records the
        // match but does not modify the quad.
        assertTrue(out.isEmpty(),
                "Phase 5 does not yet perform the sprite swap");
        assertEquals(matchesBefore + 1, matchesAfter,
                "match must be recorded for verify mode");
    }

    @Test
    void decorate_nullView_returnsEmpty() {
        CtmRule rule = new CtmRule.Builder()
                .method(CtmMethod.CTM)
                .addMatchTile(glassSprite())
                .addMatchBlock(new BlockSpec(
                        "minecraft", "glass", Map.of()))
                .addTile(CtmTileSpec.numeric(0))
                .sourceFile("test.properties")
                .sourceLine(1)
                .build();
        registry.replace(new CtmRuleSet.Builder().add(rule).build());

        CtmQuadDecorator d = new CtmQuadDecorator();
        StubQuad q = new StubQuad(glassSprite(), "minecraft:glass");
        QuadContext ctx = new QuadContext(0, 0, 0, 1,
                "minecraft:glass", q.sprite());
        Optional<QuadRef> out = d.decorate(q, ctx);
        assertTrue(out.isEmpty());
    }

    @Test
    void decorate_ruleMatchesWithAtlasEntry_returnsSwap() {
        // Phase 7: when the CtmTileAtlas has an entry for
        // the matched rule, the decorator should call
        // withSprite on the quad and return the replacement.
        // The rule has 47 tiles (0-46) so the selector can
        // return any tile index up to 46 - this matches the
        // OptiFine default CTM rule shape.
        CtmRule.Builder builder = new CtmRule.Builder()
                .method(CtmMethod.CTM)
                .addMatchTile(glassSprite())
                .addMatchBlock(new BlockSpec(
                        "minecraft", "glass", Map.of()))
                .sourceFile("test.properties")
                .sourceLine(1);
        for (int i = 0; i < 47; i++) {
            builder.addTile(CtmTileSpec.numeric(i));
        }
        CtmRule built = builder.build();
        registry.replace(new CtmRuleSet.Builder().add(built).build());

        // Build a tile atlas entry with 47 resolutions.
        List<CtmTileResolver.Resolution> resolutions =
                new java.util.ArrayList<>();
        for (int i = 0; i < 47; i++) {
            resolutions.add(new CtmTileResolver.Resolution(
                    i,
                    new NamespaceId("cinder", "optifine/ctm/glass/" + i),
                    "minecraft:optifine/ctm/glass/" + i + ".png",
                    true));
        }
        CtmTileAtlasEntry entry =
                new CtmTileAtlasEntry(built, resolutions);
        CtmTileAtlas.replace(CtmTileAtlas.of(List.of(entry)));

        try {
            CtmQuadDecorator d = new CtmQuadDecorator();
            SwappableQuad q =
                    new SwappableQuad(glassSprite(), "minecraft:glass");
            QuadContext ctx = new QuadContext(0, 0, 0, 1,
                    "minecraft:glass", q.sprite(), new ConnectedView());
            Optional<QuadRef> out = d.decorate(q, ctx);
            assertTrue(out.isPresent(),
                    "Phase 7 must return a swap when the "
                            + "atlas has an entry for the rule");
            // The selector returns some tile index in [0, 46].
            // The corresponding sprite should have been
            // requested.
            assertNotNull(q.lastSwapRequest,
                    "withSprite must be called with the "
                            + "resolved sprite id");
            assertTrue(q.lastSwapRequest.toString()
                            .startsWith("cinder:optifine/ctm/glass/"),
                    "sprite id must be from the tile atlas");
        } finally {
            CtmTileAtlas.resetForTest();
        }
    }

    @Test
    void decorate_ruleMatchesButAtlasEmpty_returnsEmpty() {
        // Phase 7: when no tile atlas entry exists for
        // the matched rule, the decorator should still
        // record the match but pass through (no swap).
        CtmRule rule = new CtmRule.Builder()
                .method(CtmMethod.CTM)
                .addMatchTile(glassSprite())
                .addMatchBlock(new BlockSpec(
                        "minecraft", "glass", Map.of()))
                .addTile(CtmTileSpec.numeric(0))
                .sourceFile("test.properties")
                .sourceLine(1)
                .build();
        registry.replace(new CtmRuleSet.Builder().add(rule).build());
        // Atlas is the default empty.

        CtmQuadDecorator d = new CtmQuadDecorator();
        SwappableQuad q =
                new SwappableQuad(glassSprite(), "minecraft:glass");
        QuadContext ctx = new QuadContext(0, 0, 0, 1,
                "minecraft:glass", q.sprite(), new ConnectedView());
        Optional<QuadRef> out = d.decorate(q, ctx);
        assertTrue(out.isEmpty(),
                "empty atlas must yield no swap");
        assertNull(q.lastSwapRequest,
                "withSprite must NOT be called when atlas is empty");
    }

    @Test
    void decorate_horizontalRuleMatchesWithoutCtmMethodFilter() {
        CtmRule.Builder builder = new CtmRule.Builder()
                .method(CtmMethod.HORIZONTAL)
                .addMatchTile(glassSprite())
                .addMatchBlock(new BlockSpec(
                        "minecraft", "glass", Map.of()))
                .sourceFile("horizontal.properties")
                .sourceLine(1);
        for (int i = 0; i < 4; i++) {
            builder.addTile(CtmTileSpec.numeric(i));
        }
        CtmRule built = builder.build();
        registry.replace(new CtmRuleSet.Builder().add(built).build());

        List<CtmTileResolver.Resolution> resolutions =
                new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            resolutions.add(new CtmTileResolver.Resolution(
                    i,
                    new NamespaceId("cinder", "optifine/ctm/glass/h" + i),
                    "minecraft:optifine/ctm/glass/h" + i + ".png",
                    true));
        }
        CtmTileAtlas.replace(CtmTileAtlas.of(List.of(
                new CtmTileAtlasEntry(built, resolutions))));

        CtmQuadDecorator d = new CtmQuadDecorator();
        SwappableQuad q =
                new SwappableQuad(glassSprite(), "minecraft:glass");
        QuadContext ctx = new QuadContext(0, 0, 0, 1,
                "minecraft:glass", q.sprite(), new ConnectedView());
        Optional<QuadRef> out = d.decorate(q, ctx);
        assertTrue(out.isPresent(),
                "non-CTM methods must be eligible in the live decorator");
        assertEquals("cinder:optifine/ctm/glass/h1",
                q.lastSwapRequest.toString());
    }

    @Test
    void priority_is100() {
        CtmQuadDecorator d = new CtmQuadDecorator();
        assertEquals(100, d.priority());
    }

    @Test
    void id_isCinderCtm() {
        CtmQuadDecorator d = new CtmQuadDecorator();
        assertEquals("cinder:ctm", d.id());
    }

    private static NamespaceId glassSprite() {
        return new NamespaceId("minecraft", "block/glass");
    }
}
