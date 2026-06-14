package com.cinder.ctm;

import com.cinder.resource.NamespaceId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Phase 5 immutability tests for {@link CtmRuleSet}.
 *
 * <p>The class is documented as immutable: callers must not be
 * able to mutate the rule set through a returned list or map.
 * The tests verify that the returned collections are
 * unmodifiable.
 */
class Phase5RuleSetImmutabilityTest {

    @Test
    void all_returnsUnmodifiableList() {
        CtmRuleSet set = makeRuleSet();
        List<CtmRule> all = set.all();
        // Any modification must throw UnsupportedOperationException.
        assertThrows(UnsupportedOperationException.class, () -> all.clear());
        assertThrows(UnsupportedOperationException.class, () -> all.remove(0));
    }

    @Test
    void rulesForSprite_returnsUnmodifiableList() {
        CtmRuleSet set = makeRuleSet();
        List<CtmRule> rules = set.rulesForSprite(
                new NamespaceId("minecraft", "block/glass"));
        // rules is non-empty because we registered one rule for
        // this sprite in makeRuleSet().
        assertNotNull(rules);
        assertEquals(1, rules.size());
        assertThrows(UnsupportedOperationException.class, () -> rules.clear());
    }

    @Test
    void rulesForBlock_returnsUnmodifiableList() {
        CtmRuleSet set = makeRuleSet();
        List<CtmRule> rules = set.rulesForBlock("minecraft:glass");
        assertEquals(1, rules.size());
        assertThrows(UnsupportedOperationException.class, () -> rules.clear());
    }

    @Test
    void rulesForSprite_unknown_returnsEmpty() {
        CtmRuleSet set = makeRuleSet();
        List<CtmRule> rules = set.rulesForSprite(
                new NamespaceId("minecraft", "block/unknown"));
        assertNotNull(rules);
        assertEquals(0, rules.size());
    }

    @Test
    void rulesForBlock_unknown_returnsEmpty() {
        CtmRuleSet set = makeRuleSet();
        List<CtmRule> rules = set.rulesForBlock("minecraft:unknown");
        assertNotNull(rules);
        assertEquals(0, rules.size());
    }

    private static CtmRuleSet makeRuleSet() {
        CtmRule rule = new CtmRule.Builder()
                .method(CtmMethod.CTM)
                .addMatchTile(new NamespaceId("minecraft", "block/glass"))
                .addMatchBlock(new BlockSpec(
                        "minecraft", "glass", java.util.Map.of()))
                .addTile(CtmTileSpec.numeric(0))
                .sourceFile("test.properties")
                .sourceLine(1)
                .build();
        return new CtmRuleSet.Builder().add(rule).build();
    }
}
