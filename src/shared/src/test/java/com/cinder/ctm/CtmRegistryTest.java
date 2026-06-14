package com.cinder.ctm;

import com.cinder.resource.NamespaceId;
import com.cinder.resource.PropertiesFile;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CtmRegistryTest {

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

    @Test
    void emptyRegistry_hasNoRules() {
        CtmRegistry r = new CtmRegistry("cinder");
        assertEquals(0, r.ruleSet().all().size());
        assertTrue(r.rulesForSprite(new NamespaceId("minecraft", "block/glass"))
                .isEmpty());
        assertTrue(r.rulesForBlock("minecraft:stone").isEmpty());
    }

    @Test
    void replace_swapsTheRuleSet() {
        CtmRegistry r = new CtmRegistry("cinder");
        CtmRule rule1 = parseRule(
                "method=ctm\n"
                        + "matchBlocks=minecraft:stone\n"
                        + "tiles=0-46\n");
        CtmRule rule2 = parseRule(
                "method=ctm\n"
                        + "matchBlocks=minecraft:dirt\n"
                        + "tiles=0-46\n");
        r.replace(new CtmRuleSet.Builder().add(rule1).build());
        assertEquals(1, r.ruleSet().rulesForBlock("minecraft:stone").size());
        assertEquals(0, r.ruleSet().rulesForBlock("minecraft:dirt").size());
        r.replace(new CtmRuleSet.Builder().add(rule2).build());
        assertEquals(0, r.ruleSet().rulesForBlock("minecraft:stone").size());
        assertEquals(1, r.ruleSet().rulesForBlock("minecraft:dirt").size());
    }

    @Test
    void firstRuleFor_findsBySprite() {
        CtmRule rule = parseRule(
                "method=ctm\n"
                        + "matchBlocks=minecraft:stone\n"
                        + "tiles=0-46\n");
        CtmRegistry r = new CtmRegistry("cinder");
        r.replace(new CtmRuleSet.Builder().add(rule).build());
        Optional<CtmRule> found = r.firstRuleFor(
                new NamespaceId("minecraft", "block/stone"),
                "minecraft:stone",
                CtmMethod.CTM);
        assertTrue(found.isPresent());
        assertEquals(CtmMethod.CTM, found.get().method());
    }

    @Test
    void firstRuleFor_returnsEmptyWhenNoMatch() {
        CtmRegistry r = new CtmRegistry("cinder");
        r.replace(new CtmRuleSet.Builder().add(parseRule(
                "method=ctm\n"
                        + "matchBlocks=minecraft:stone\n"
                        + "tiles=0-46\n")).build());
        Optional<CtmRule> found = r.firstRuleFor(
                new NamespaceId("minecraft", "block/dirt"),
                "minecraft:dirt",
                CtmMethod.CTM);
        assertTrue(found.isEmpty());
    }

    @Test
    void rulesWithMethod_filters() {
        CtmRule ctm = parseRule(
                "method=ctm\n"
                        + "matchBlocks=minecraft:stone\n"
                        + "tiles=0-46\n");
        CtmRule horiz = parseRule(
                "method=horizontal\n"
                        + "matchBlocks=minecraft:oak_log\n"
                        + "tiles=0-3\n");
        CtmRegistry r = new CtmRegistry("cinder");
        r.replace(new CtmRuleSet.Builder().add(ctm).add(horiz).build());
        List<CtmRule> ctms = r.rulesWithMethod(CtmMethod.CTM);
        assertEquals(1, ctms.size());
        List<CtmRule> horizontals = r.rulesWithMethod(CtmMethod.HORIZONTAL);
        assertEquals(1, horizontals.size());
        List<CtmRule> fixeds = r.rulesWithMethod(CtmMethod.FIXED);
        assertEquals(0, fixeds.size());
    }

    @Test
    void modId_returnsConstructorValue() {
        CtmRegistry r = new CtmRegistry("cinder");
        assertEquals("cinder", r.modId());
        assertNotNull(r.ruleSet());
    }
}
