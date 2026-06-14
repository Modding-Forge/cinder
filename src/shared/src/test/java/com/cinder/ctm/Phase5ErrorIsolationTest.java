package com.cinder.ctm;

import com.cinder.resource.NamespaceId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 5 tests for the {@link CtmRuleParser} error-isolation
 * contract.
 *
 * <p>A single malformed {@code .properties} file in a resource
 * pack must not crash the entire resource reload: a player who
 * installs an experimental resource pack should still get CTM
 * for the other rules. The parser must therefore collect parse
 * errors per source via the {@link CtmRuleParser.ParseErrorListener}
 * and continue with the remaining sources.
 */
class Phase5ErrorIsolationTest {

    @Test
    void tryParseString_invalidBody_returnsEmpty() {
        // "method=" with no value is an invalid CTM properties
        // body; tryParseString should report the error and
        // return Optional.empty.
        List<String> errors = new ArrayList<>();
        var opt = CtmRuleParser.tryParseString(
                "method=\n",  // no method value
                new NamespaceId("minecraft", "optifine/ctm"),
                "bad.properties",
                (label, message, cause) -> errors.add(label + ": " + message));
        assertTrue(opt.isEmpty(), "expected empty Optional for bad body");
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).startsWith("bad.properties:"));
    }

    @Test
    void buildRuleSet_isolatesPerFileErrors() {
        // Mixed list: one good rule, one bad rule, another good
        // rule. The build must return a set with the two good
        // rules and report exactly one error.
        List<String> errors = new ArrayList<>();
        List<CtmRuleParser.RuleSource> sources = List.of(
                new CtmRuleParser.RuleSource(
                        "method=ctm\n"
                                + "matchBlocks=minecraft:stone\n"
                                + "tiles=0-46\n",
                        "good-1.properties"),
                new CtmRuleParser.RuleSource(
                        "method=\n",  // missing method
                        "bad.properties"),
                new CtmRuleParser.RuleSource(
                        "method=horizontal\n"
                                + "matchBlocks=minecraft:oak_log\n"
                                + "tiles=0-3\n",
                        "good-2.properties"));
        CtmRuleSet set = CtmRuleParser.buildRuleSet(
                sources,
                new NamespaceId("minecraft", "optifine/ctm"),
                (label, message, cause) -> errors.add(label));
        assertEquals(2, set.all().size(),
                "two good rules must be in the set");
        assertEquals(1, errors.size());
        assertEquals("bad.properties", errors.get(0));
    }

    @Test
    void buildRuleSet_allGood_noErrors() {
        List<String> errors = new ArrayList<>();
        List<CtmRuleParser.RuleSource> sources = List.of(
                new CtmRuleParser.RuleSource(
                        "method=ctm\n"
                                + "matchBlocks=minecraft:stone\n"
                                + "tiles=0-46\n",
                        "stone.properties"),
                new CtmRuleParser.RuleSource(
                        "method=horizontal\n"
                                + "matchBlocks=minecraft:oak_log\n"
                                + "tiles=0-3\n",
                        "log.properties"));
        CtmRuleSet set = CtmRuleParser.buildRuleSet(
                sources,
                new NamespaceId("minecraft", "optifine/ctm"),
                (label, message, cause) -> errors.add(label));
        assertEquals(2, set.all().size());
        assertTrue(errors.isEmpty());
    }

    @Test
    void buildRuleSet_strictOverload_propagatesErrors() {
        // The backwards-compatible overload must still throw on
        // the first error (its documented contract is "any
        // failure propagates").
        List<CtmRuleParser.RuleSource> sources = List.of(
                new CtmRuleParser.RuleSource(
                        "method=\n",
                        "bad.properties"));
        try {
            CtmRuleParser.buildRuleSet(
                    sources,
                    new NamespaceId("minecraft", "optifine/ctm"));
            // Should not reach here.
            throw new AssertionError("expected RuntimeException");
        } catch (RuntimeException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    void noopListener_silentlyDiscardsErrors() {
        // A test that does not care about errors uses the
        // no-op listener.
        var opt = CtmRuleParser.tryParseString(
                "method=\n",
                new NamespaceId("minecraft", "optifine/ctm"),
                "bad.properties",
                CtmRuleParser.NOOP_LISTENER);
        assertTrue(opt.isEmpty());
        // No assertion on error messages; the no-op listener
        // discards them.
    }
}
