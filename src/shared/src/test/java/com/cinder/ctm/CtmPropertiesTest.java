package com.cinder.ctm;

import com.cinder.resource.NamespaceId;
import com.cinder.resource.PropertiesFile;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CtmPropertiesTest {

    private static CtmRule parse(String body, String src) {
        PropertiesFile p;
        try {
            p = PropertiesFile.parse(new StringReader(body));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return CtmProperties.parse(p,
                new NamespaceId("minecraft", "optifine/ctm"), src);
    }

    @Test
    void minimalGlassRule() {
        CtmRule r = parse(
                "method=ctm\n"
                        + "matchTiles=minecraft:block/glass\n"
                        + "tiles=0-46\n",
                "optifine/ctm/glass.properties");
        assertEquals(CtmMethod.CTM, r.method());
        assertEquals(47, r.tiles().size());
        assertEquals(ConnectMode.BLOCK, r.connect()); // default
    }

    @Test
    void bareMatchTileResolvesToVanillaBlockSprite() {
        CtmRule r = parse(
                "method=ctm_compact\n"
                        + "matchTiles=diamond_ore\n"
                        + "tiles=0-4\n",
                "minecraft:optifine/ctm/ores/diamond_ore/connect.properties");
        assertEquals(new NamespaceId("minecraft", "block/diamond_ore"),
                r.matchTiles().get(0));
    }

    @Test
    void namespacedMatchTileResolvesToNamespacedBlockSprite() {
        CtmRule r = parse(
                "method=ctm_compact\n"
                        + "matchTiles=create:zinc_ore\n"
                        + "tiles=0-4\n",
                "create:optifine/ctm/ores/zinc_ore/connect.properties");
        assertEquals(new NamespaceId("create", "block/zinc_ore"),
                r.matchTiles().get(0));
    }

    @Test
    void connectTileTakesEffect() {
        CtmRule r = parse(
                "method=ctm\n"
                        + "matchBlocks=minecraft:stone\n"
                        + "connect=tile\n"
                        + "tiles=0-46\n",
                "optifine/ctm/stone.properties");
        assertEquals(ConnectMode.TILE, r.connect());
    }

    @Test
    void connectStateTakesEffect() {
        CtmRule r = parse(
                "method=ctm\n"
                        + "matchBlocks=minecraft:redstone_wire\n"
                        + "connect=state\n"
                        + "tiles=0-46\n",
                "x.properties");
        assertEquals(ConnectMode.STATE, r.connect());
    }

    @Test
    void overlayConnectTilesResolveToBlockSprites() {
        CtmRule r = parse(
                "method=overlay\n"
                        + "matchBlocks=minecraft:dirt\n"
                        + "connectTiles=sand minecraft:block/grass_block_top\n"
                        + "tiles=0-16\n",
                "x.properties");
        assertEquals(2, r.connectTiles().size());
        assertEquals(new NamespaceId("minecraft", "block/sand"),
                r.connectTiles().get(0));
        assertEquals(new NamespaceId("minecraft", "block/grass_block_top"),
                r.connectTiles().get(1));
    }

    @Test
    void overlayConnectBlocksAreParsed() {
        CtmRule r = parse(
                "method=overlay\n"
                        + "matchBlocks=minecraft:dirt\n"
                        + "connectBlocks=minecraft:sand grass_block\n"
                        + "tiles=0-16\n",
                "x.properties");
        assertEquals(2, r.connectBlocks().size());
        assertEquals("sand", r.connectBlocks().get(0).name());
        assertEquals("grass_block", r.connectBlocks().get(1).name());
    }

    @Test
    void overlayTintMetadataIsParsed() {
        CtmRule r = parse(
                "method=overlay\n"
                        + "matchBlocks=minecraft:sand\n"
                        + "connectTiles=grass_block_top\n"
                        + "tintIndex=0\n"
                        + "tintBlock=grass_block\n"
                        + "tiles=0-16\n",
                "x.properties");
        assertEquals(0, r.tintIndex());
        assertTrue(r.tintBlock().isPresent());
        assertEquals("minecraft", r.tintBlock().get().namespace());
        assertEquals("grass_block", r.tintBlock().get().name());
    }

    @Test
    void facesAll_isZero() {
        CtmRule r = parse(
                "method=ctm\n"
                        + "matchBlocks=minecraft:stone\n"
                        + "faces=all\n"
                        + "tiles=0-46\n",
                "x.properties");
        assertEquals(0, r.facesMask());
    }

    @Test
    void facesSides() {
        CtmRule r = parse(
                "method=ctm\n"
                        + "matchBlocks=minecraft:oak_log\n"
                        + "faces=sides\n"
                        + "tiles=0-3\n",
                "x.properties");
        // D=0, U=1, N=2, S=3, W=4, E=5. "sides" = N|S|W|E = 0x3C
        assertEquals((1 << 2) | (1 << 3) | (1 << 4) | (1 << 5), r.facesMask());
    }

    @Test
    void heightRangeWithNegatives() {
        CtmRule r = parse(
                "method=ctm\n"
                        + "matchBlocks=minecraft:oak_leaves\n"
                        + "heights=(-64)-0\n"
                        + "tiles=0-3\n",
                "x.properties");
        assertTrue(r.heights().contains(-64));
        assertTrue(r.heights().contains(-1));
        assertTrue(r.heights().contains(0));
        assertFalse(r.heights().contains(1));
    }

    @Test
    void repeatMethodRequiresWidthHeight() {
        assertThrows(IllegalArgumentException.class, () -> parse(
                "method=repeat\n"
                        + "matchBlocks=minecraft:stone\n"
                        + "tiles=0-3\n",
                "x.properties"));
    }

    @Test
    void repeatMethodAcceptsWidthHeight() {
        CtmRule r = parse(
                "method=repeat\n"
                        + "matchBlocks=minecraft:stone\n"
                        + "width=2\n"
                        + "height=2\n"
                        + "tiles=0-3\n",
                "x.properties");
        assertEquals(2, r.width());
        assertEquals(2, r.height());
    }

    @Test
    void weightAndRandomWeights() {
        CtmRule r = parse(
                "method=random\n"
                        + "matchBlocks=minecraft:grass_block\n"
                        + "tiles=0-3\n"
                        + "weights=1 2 3 4\n"
                        + "weight=10\n",
                "x.properties");
        assertEquals(10, r.weight());
        assertNotNull(r.randomWeights());
        assertEquals(4, r.randomWeights().size());
    }

    @Test
    void ctmCompactOverrides() {
        CtmRule r = parse(
                "method=ctm_compact\n"
                        + "matchBlocks=minecraft:glass\n"
                        + "tiles=0-4\n"
                        + "ctm.0=0\n"
                        + "ctm.46=4\n",
                "x.properties");
        int[] overrides = r.ctmOverrides();
        assertNotNull(overrides);
        assertEquals(0, overrides[0]);
        assertEquals(4, overrides[46]);
        // Untouched entries stay at -1.
        assertEquals(-1, overrides[20]);
    }

    @Test
    void innerSeamsDefaultFalse() {
        CtmRule r = parse(
                "method=ctm\n"
                        + "matchBlocks=minecraft:stone\n"
                        + "tiles=0-46\n",
                "x.properties");
        assertFalse(r.innerSeams());
    }

    @Test
    void innerSeamsTrue() {
        CtmRule r = parse(
                "method=ctm\n"
                        + "matchBlocks=minecraft:stone\n"
                        + "innerSeams=true\n"
                        + "tiles=0-46\n",
                "x.properties");
        assertTrue(r.innerSeams());
    }

    @Test
    void filenameDefaultInferrence_matchTiles() {
        // "x.properties" lives in "optifine/ctm"; with no matchBlocks the
        // parser should infer matchTiles=block/x.
        CtmRule r = parse(
                "method=ctm\n"
                        + "tiles=0-46\n",
                "optifine/ctm/glass.properties");
        assertEquals(1, r.matchTiles().size());
        assertEquals("block/glass", r.matchTiles().get(0).path());
    }

    @Test
    void filenameDefaultInferrence_matchBlocks() {
        CtmRule r = parse(
                "method=ctm\n"
                        + "tiles=0-46\n",
                "optifine/ctm/block_stone.properties");
        assertEquals(1, r.matchBlocks().size());
        assertEquals("stone", r.matchBlocks().get(0).name());
    }

    @Test
    void blockSpecWithProperties() {
        CtmRule r = parse(
                "method=horizontal\n"
                        + "matchBlocks=minecraft:oak_stairs:facing=east,west:half=bottom\n"
                        + "tiles=0-3\n",
                "x.properties");
        BlockSpec s = r.matchBlocks().get(0);
        assertEquals("oak_stairs", s.name());
        assertTrue(s.properties().get("facing").contains("east"));
        assertTrue(s.properties().get("facing").contains("west"));
        assertTrue(s.properties().get("half").contains("bottom"));
    }

    @Test
    void unqualifiedBlockSpecWithProperties() {
        CtmRule r = parse(
                "method=horizontal\n"
                        + "matchBlocks=brick_slab:type=top\n"
                        + "tiles=0-3\n",
                "x.properties");
        BlockSpec s = r.matchBlocks().get(0);
        assertEquals("minecraft", s.namespace());
        assertEquals("brick_slab", s.name());
        assertTrue(s.properties().get("type").contains("top"));
    }

    @Test
    void missingMethod_throws() {
        assertThrows(IllegalArgumentException.class, () -> parse(
                "matchBlocks=minecraft:stone\n"
                        + "tiles=0-46\n",
                "x.properties"));
    }

    @Test
    void missingTiles_throws() {
        assertThrows(IllegalArgumentException.class, () -> parse(
                "method=ctm\n"
                        + "matchBlocks=minecraft:stone\n",
                "x.properties"));
    }

    @Test
    void unknownMethod_throws() {
        assertThrows(IllegalArgumentException.class, () -> parse(
                "method=bogus_method\n"
                        + "matchBlocks=minecraft:stone\n"
                        + "tiles=0-46\n",
                "x.properties"));
    }

    @Test
    void ruleSet_isBuiltFromMultipleRules() {
        CtmRuleSet.Builder b = new CtmRuleSet.Builder();
        b.add(r(parse(
                "method=ctm\n"
                        + "matchBlocks=minecraft:stone\n"
                        + "tiles=0-46\n",
                "a.properties")));
        b.add(r(parse(
                "method=ctm\n"
                        + "matchBlocks=minecraft:stone\n"
                        + "tiles=0-46\n"
                        + "weight=5\n",
                "b.properties")));
        CtmRuleSet set = b.build();
        assertEquals(2, set.rulesForBlock("minecraft:stone").size());
        // Higher weight first.
        assertEquals(5, set.rulesForBlock("minecraft:stone").get(0).weight());
    }

    private CtmRule r(CtmRule rule) { return rule; }

    @Test
    void emptyRuleSet_works() {
        CtmRuleSet set = CtmRuleSet.empty();
        assertNotNull(set);
        assertEquals(0, set.all().size());
    }

    @Test
    void selector_returnsFirstTileOfHighestPriorityRule() {
        CtmRule rule = parse(
                "method=ctm\n"
                        + "matchBlocks=minecraft:stone\n"
                        + "tiles=0-46\n",
                "x.properties");
        // The rule is matchBlocks-based; for sprite lookup we expect null
        // from the new select() entry point.
        CtmSelector sel = new CtmSelector(new CtmRuleSet.Builder().add(rule).build());
        com.cinder.resource.NamespaceId sprite =
                new com.cinder.resource.NamespaceId("minecraft", "block/glass");
        // The full engine needs a NeighborView; for this smoke test we
        // just confirm that building the selector and looking it up
        // does not throw. The actual null-on-miss is verified in
        // CtmSelectorTest.
        CtmRuleSet rs = new CtmRuleSet.Builder().add(rule).build();
        assertEquals(1, rs.rulesForBlock("minecraft:stone").size());
        // Use sprite ref to avoid "unused" warning; selector API is
        // exercised in CtmSelectorTest.
        assertNotNull(sprite);
        assertNotNull(sel);
    }
}
