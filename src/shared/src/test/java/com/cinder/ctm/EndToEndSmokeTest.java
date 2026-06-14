package com.cinder.ctm;

import com.cinder.resource.NamespaceId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end smoke test: an entire OptiFine-style CTM property
 * file is parsed into a {@link CtmRule}, the rule is installed
 * into a {@link CtmRegistry}, the engine looks the rule up by
 * sprite and block id, and the selector runs against a
 * hand-built 3x3x3 {@link NeighborView}.
 *
 * <p>This is the minimum-loop integration test for Phase 3:
 * it exercises every layer we built (parser, rule, registry,
 * selector, selection result) without needing Minecraft on the
 * classpath.
 *
 * <p>What it does <b>not</b> cover: the actual BakedQuad
 * rewrite in the renderer (Phase 4), the ResourceManager
 * scan (Phase 4), and the Fabric/Mojang adapter (Phase 4).
 * Those are deferred to a later phase because they require
 * Minecraft on the test classpath, which the {@code shared}
 * module does not have.
 */
class EndToEndSmokeTest {

    @Test
    void glassCtm_parsedInstalledQueriedAndSelected() {
        // 1) Parse a property file.
        String body = "method=ctm\n"
                + "matchBlocks=minecraft:glass\n"
                + "matchTiles=minecraft:block/glass\n"
                + "tiles=0-46\n";
        CtmRule rule = CtmRuleParser.parseString(
                body,
                new NamespaceId("minecraft", "optifine/ctm/glass"),
                "test/glass.properties");
        assertEquals(CtmMethod.CTM, rule.method());
        assertEquals(47, rule.tiles().size());

        // 2) Install into a registry.
        CtmRegistry reg = new CtmRegistry("cinder");
        CtmRuleSet.Builder b = new CtmRuleSet.Builder();
        b.add(rule);
        reg.replace(b.build());

        // 3) Look up by sprite + block.
        Optional<CtmRule> match = reg.firstRuleFor(
                new NamespaceId("minecraft", "block/glass"),
                "minecraft:glass",
                CtmMethod.CTM);
        assertTrue(match.isPresent());
        assertEquals(rule, match.get());

        // 4) Run the selector on a view with no neighbours of the
        // same type - we expect tile 0 (no sides connected).
        NamespaceId glassSprite = new NamespaceId("minecraft", "block/glass");
        NeighborCache empty = new NeighborCache();
        empty.set(0, 0, 0, "minecraft:glass", true);
        empty.setCenterSprite(Faces.DOWN, glassSprite);
        empty.markFilled();
        CtmSelector selector = new CtmSelector(reg.ruleSet());
        CtmSelectionResult sel = selector.select(
                match.get(), empty, 0, 0, 0, Faces.DOWN);
        assertNotNull(sel);
        assertEquals(0, sel.tileIndex());

        // 5) With all 6 sides matching, expect tile 46
        // (TILE_ALL_CONNECTED). Build a view where every
        // neighbour is the same sprite/block.
        NeighborCache connectedView = new NeighborCache();
        connectedView.set(0, 0, 0, "minecraft:glass", true);
        for (int face = 0; face < 6; face++) {
            connectedView.setCenterSprite(face, glassSprite);
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    connectedView.set(dx, dy, dz, "minecraft:glass", true);
                }
            }
        }
        connectedView.markFilled();
        CtmSelectionResult selAll = selector.select(
                match.get(), connectedView, 0, 0, 0, Faces.UP);
        assertEquals(TileIndexTable.TILE_ALL_CONNECTED, selAll.tileIndex());
    }

    @Test
    void emptyRegistry_firstRuleFor_isEmpty() {
        CtmRegistry reg = CtmRegistry.empty("cinder");
        Optional<CtmRule> match = reg.firstRuleFor(
                new NamespaceId("minecraft", "block/glass"),
                "minecraft:glass",
                CtmMethod.CTM);
        assertTrue(match.isEmpty());
    }

    @Test
    void multipleRules_priorityOrder() {
        // Two rules on the same block but with different methods.
        // firstRuleFor should prefer CTM over HORIZONTAL.
        String ctmBody = "method=ctm\n"
                + "matchBlocks=minecraft:stone\n"
                + "tiles=0-46\n";
        String horizontalBody = "method=horizontal\n"
                + "matchBlocks=minecraft:stone\n"
                + "tiles=0-3\n";
        CtmRule ctm = CtmRuleParser.parseString(
                ctmBody, new NamespaceId("minecraft", "optifine/ctm/stone"),
                "stone-ctm.properties");
        CtmRule horizontal = CtmRuleParser.parseString(
                horizontalBody,
                new NamespaceId("minecraft", "optifine/ctm/stone-h"),
                "stone-h.properties");
        CtmRuleSet.Builder b = new CtmRuleSet.Builder();
        b.add(ctm);
        b.add(horizontal);
        CtmRuleSet set = b.build();
        CtmRegistry reg = new CtmRegistry("cinder");
        reg.replace(set);
        // Lookup returns the first matching rule in priority order
        // (insertion order is the priority order in this builder).
        Optional<CtmRule> ctmMatch = reg.firstRuleFor(
                null, "minecraft:stone", CtmMethod.CTM);
        assertTrue(ctmMatch.isPresent());
        assertEquals(CtmMethod.CTM, ctmMatch.get().method());
        Optional<CtmRule> hMatch = reg.firstRuleFor(
                null, "minecraft:stone", CtmMethod.HORIZONTAL);
        assertTrue(hMatch.isPresent());
        assertEquals(CtmMethod.HORIZONTAL, hMatch.get().method());
    }

    @Test
    void rulesWithMethod_filterReturnsOnlyMatching() {
        CtmRuleParser.RuleSource a = new CtmRuleParser.RuleSource(
                "method=ctm\nmatchBlocks=minecraft:stone\ntiles=0-46\n",
                "a.properties");
        CtmRuleParser.RuleSource b = new CtmRuleParser.RuleSource(
                "method=horizontal\nmatchBlocks=minecraft:oak_log\ntiles=0-3\n",
                "b.properties");
        CtmRuleParser.RuleSource c = new CtmRuleParser.RuleSource(
                "method=ctm\nmatchBlocks=minecraft:dirt\ntiles=0-46\n",
                "c.properties");
        CtmRuleSet set = CtmRuleParser.buildRuleSet(
                List.of(a, b, c), new NamespaceId("minecraft", "optifine/ctm"));
        CtmRegistry reg = new CtmRegistry("cinder");
        reg.replace(set);
        List<CtmRule> ctms = reg.rulesWithMethod(CtmMethod.CTM);
        assertEquals(2, ctms.size());
        List<CtmRule> horizontals = reg.rulesWithMethod(CtmMethod.HORIZONTAL);
        assertEquals(1, horizontals.size());
    }
}
