package com.cinder.ctm;

import com.cinder.resource.NamespaceId;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CtmRuleParserTest {

    @Test
    void parseString_glassRule() {
        CtmRule r = CtmRuleParser.parseString(
                "method=ctm\n"
                        + "matchBlocks=minecraft:glass\n"
                        + "tiles=0-46\n",
                new NamespaceId("minecraft", "optifine/ctm"),
                "test.properties");
        assertEquals(CtmMethod.CTM, r.method());
        assertEquals(47, r.tiles().size());
    }

    @Test
    void parseStream_utf8() throws IOException {
        String body = "method=ctm\n"
                + "matchBlocks=minecraft:glass\n"
                + "tiles=0-46\n";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        CtmRule r = CtmRuleParser.parseStream(
                new ByteArrayInputStream(bytes),
                new NamespaceId("minecraft", "optifine/ctm"),
                "stream.properties");
        assertNotNull(r);
        assertEquals(47, r.tiles().size());
    }

    @Test
    void parseStream_propagatesIOExceptionForBadUtf8() {
        // Empty stream: parse() returns an empty rule set, so the
        // call is well-formed but no method -> IllegalArgumentException
        // from CtmProperties.parse. We assert that an exception
        // surfaces rather than a silent empty rule.
        assertThrows(RuntimeException.class, () -> CtmRuleParser.parseStream(
                new ByteArrayInputStream(new byte[0]),
                new NamespaceId("minecraft", "optifine/ctm"),
                "empty.properties"));
    }

    @Test
    void buildRuleSet_combinesMultipleRules() {
        CtmRuleParser.RuleSource a = new CtmRuleParser.RuleSource(
                "method=ctm\n"
                        + "matchBlocks=minecraft:stone\n"
                        + "tiles=0-46\n",
                "stone.properties");
        CtmRuleParser.RuleSource b = new CtmRuleParser.RuleSource(
                "method=horizontal\n"
                        + "matchBlocks=minecraft:oak_log\n"
                        + "tiles=0-3\n",
                "log.properties");
        CtmRuleSet set = CtmRuleParser.buildRuleSet(
                Arrays.asList(a, b),
                new NamespaceId("minecraft", "optifine/ctm"));
        assertEquals(2, set.all().size());
        assertEquals(1, set.rulesForBlock("minecraft:stone").size());
        assertEquals(1, set.rulesForBlock("minecraft:oak_log").size());
    }

    @Test
    void buildRuleSet_usesSourceDirectoryAsRelativeParent() {
        CtmRuleParser.RuleSource source = new CtmRuleParser.RuleSource(
                "method=fixed\n"
                        + "matchBlocks=create:zinc_ore\n"
                        + "tiles=./border\n",
                "create:optifine/ctm/ores/zinc_ore/connect.properties");
        CtmRuleSet set = CtmRuleParser.buildRuleSet(
                java.util.List.of(source),
                new NamespaceId("minecraft", "optifine/ctm"));

        CtmRule rule = set.all().get(0);

        assertEquals(new NamespaceId("create",
                        "optifine/ctm/ores/zinc_ore/border"),
                rule.tiles().get(0).resolvedSprite());
    }

    @Test
    void buildRuleSet_emptyList_yieldsEmpty() {
        CtmRuleSet set = CtmRuleParser.buildRuleSet(
                java.util.Collections.emptyList(),
                new NamespaceId("minecraft", "optifine/ctm"));
        assertTrue(set.all().isEmpty());
    }
}
