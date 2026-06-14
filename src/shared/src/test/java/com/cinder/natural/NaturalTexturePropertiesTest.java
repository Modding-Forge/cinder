package com.cinder.natural;

import com.cinder.resource.NamespaceId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NaturalTexturePropertiesTest {

    @Test
    void parsesRotationsAndFlip() {
        NaturalTextureParseResult result = NaturalTextureProperties.parse("""
                grass_top=4F
                stone=2
                minecraft:block/dirt=F
                """, "minecraft:optifine/natural.properties");

        assertTrue(result.errors().isEmpty());
        NaturalTextureRuleSet rules = NaturalTextureRuleSet.of(
                result.rules());
        NaturalTextureRule grass = rules.ruleFor(new NamespaceId(
                "minecraft", "block/grass_top"));
        assertEquals(4, grass.rotations());
        assertTrue(grass.flip());
        assertEquals(2, rules.ruleFor(new NamespaceId(
                "minecraft", "block/stone")).rotations());
        assertEquals(1, rules.ruleFor(new NamespaceId(
                "minecraft", "block/dirt")).rotations());
        assertTrue(rules.ruleFor(new NamespaceId(
                "minecraft", "block/dirt")).flip());
    }

    @Test
    void isolatesMalformedEntries() {
        NaturalTextureParseResult result = NaturalTextureProperties.parse("""
                stone=bad
                dirt=4
                """, "minecraft:optifine/natural.properties");

        assertEquals(1, result.errors().size());
        assertEquals(1, result.rules().size());
        assertFalse(NaturalTextureRuleSet.of(result.rules()).isEmpty());
    }
}
