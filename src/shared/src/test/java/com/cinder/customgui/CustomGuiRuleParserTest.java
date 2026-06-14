package com.cinder.customgui;

import com.cinder.condition.ConditionKey;
import com.cinder.resource.NamespaceId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CustomGuiRuleParserTest {

    @Test
    void minimalChestRuleParsesTexture() {
        CustomGuiRule rule = CustomGuiRuleParser.parseString("""
                container=chest
                texture=custom/chest.png
                """, "minecraft:optifine/gui/container/chest.properties");

        assertEquals("chest", rule.container());
        assertEquals(new NamespaceId("minecraft", "custom/chest.png"),
                rule.replacement().defaultTexture());
    }

    @Test
    void relativeAndNamedTexturePathsAreResolved() {
        CustomGuiRule rule = CustomGuiRuleParser.parseString("""
                container=chest
                texture=./treasure.png
                texture.large=./large_treasure.png
                texture.minecraft\\:textures/gui/container/generic_54.png=other:gui/large.png
                """, "minecraft:optifine/gui/container/chest/gold.properties");

        assertEquals(new NamespaceId("minecraft",
                        "optifine/gui/container/chest/treasure.png"),
                rule.replacement().defaultTexture());
        assertEquals(new NamespaceId("minecraft",
                        "optifine/gui/container/chest/large_treasure.png"),
                rule.replacement().namedTextures().get("large"));
        assertEquals(new NamespaceId("other", "gui/large.png"),
                rule.replacement().namedTextures().get(
                        "minecraft\\:textures/gui/container/generic_54.png"));
    }

    @Test
    void namespacedTexturePathIsPreserved() {
        CustomGuiRule rule = CustomGuiRuleParser.parseString("""
                container=anvil
                texture=example:gui/anvil
                """, "minecraft:optifine/gui/container/anvil.properties");

        assertEquals(new NamespaceId("example", "gui/anvil.png"),
                rule.replacement().defaultTexture());
    }

    @Test
    void ruleSetOrdersByWeightThenSourcePath() {
        CustomGuiRule low = CustomGuiRuleParser.parseString("""
                container=chest
                texture=low.png
                weight=1
                """, "minecraft:optifine/gui/container/b.properties");
        CustomGuiRule highA = CustomGuiRuleParser.parseString("""
                container=chest
                texture=high_a.png
                weight=10
                """, "minecraft:optifine/gui/container/a.properties");
        CustomGuiRule highB = CustomGuiRuleParser.parseString("""
                container=chest
                texture=high_b.png
                weight=10
                """, "minecraft:optifine/gui/container/c.properties");

        CustomGuiRuleSet set = CustomGuiRuleSet.of(
                List.of(low, highB, highA));

        assertEquals(List.of(highA, highB, low), set.all());
        assertEquals(highA, set.candidates("chest")[0]);
    }

    @Test
    void parseAllIsolatesMalformedFiles() {
        CustomGuiParseResult result = CustomGuiRuleParser.parseAll(List.of(
                new CustomGuiRuleParser.RuleSource("""
                        container=chest
                        texture=good.png
                        """, "minecraft:optifine/gui/container/good.properties"),
                new CustomGuiRuleParser.RuleSource("""
                        container=chest
                        texture=bad.png
                        weight=nope
                        """, "minecraft:optifine/gui/container/bad.properties")));

        assertEquals(1, result.rules().size());
        assertEquals(1, result.errors().size());
        assertEquals("minecraft:optifine/gui/container/bad.properties",
                result.errors().getFirst().sourceFile());
        assertTrue(result.errors().getFirst().message().contains("weight"));
    }

    @Test
    void guiConditionsAreAttachedToRule() {
        CustomGuiRule rule = CustomGuiRuleParser.parseString("""
                container=chest
                texture=treasure.png
                name=pattern:Treasure*
                biomes=minecraft:plains
                heights=1-80
                large=true
                colors=red
                """, "minecraft:optifine/gui/container/treasure.properties");

        assertTrue(rule.conditions().conditions().stream()
                .anyMatch(c -> c.key() == ConditionKey.CUSTOM_NAME));
        assertTrue(rule.conditions().conditions().stream()
                .anyMatch(c -> c.key() == ConditionKey.BIOME));
        assertTrue(rule.conditions().conditions().stream()
                .anyMatch(c -> c.key() == ConditionKey.CONTAINER_FLAG
                        && "large".equals(c.qualifier())));
    }
}
