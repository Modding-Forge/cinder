package com.cinder.cit;

import com.cinder.condition.ConditionKey;
import com.cinder.resource.NamespaceId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CitRuleParserTest {

    @Test
    void minimalItemRule_usesFileTextureByDefault() {
        CitRule rule = CitRuleParser.parseString(
                "items=minecraft:diamond_sword\n",
                "minecraft:optifine/cit/swords/excalibur.properties");

        assertEquals(CitRuleType.ITEM, rule.type());
        assertArrayEquals(new NamespaceId[]{
                new NamespaceId("minecraft", "diamond_sword")
        }, rule.items());
        assertEquals(new NamespaceId("minecraft",
                        "optifine/cit/swords/excalibur"),
                rule.replacement().texture());
    }

    @Test
    void relativeAndNamedTexturePathsAreResolved() {
        CitRule rule = CitRuleParser.parseString("""
                items=diamond_sword
                texture=./excalibur.png
                texture.bow_pulling_0=./pull0
                model=custom/sword.json
                """, "minecraft:optifine/cit/swords/excalibur.properties");

        assertEquals(new NamespaceId("minecraft",
                        "optifine/cit/swords/excalibur"),
                rule.replacement().texture());
        assertEquals(new NamespaceId("minecraft",
                        "optifine/cit/swords/pull0"),
                rule.replacement().namedTextures().get("bow_pulling_0"));
        assertEquals(new NamespaceId("minecraft", "custom/sword"),
                rule.replacement().model());
    }

    @Test
    void namespacedTexturePathIsPreserved() {
        CitRule rule = CitRuleParser.parseString("""
                items=minecraft:stick
                texture=example:item/wand
                """, "minecraft:optifine/cit/wand.properties");

        assertEquals(new NamespaceId("example", "item/wand"),
                rule.replacement().texture());
    }

    @Test
    void ruleSetOrdersByWeightThenSourcePath() {
        CitRule low = CitRuleParser.parseString("""
                items=stick
                weight=1
                """, "minecraft:optifine/cit/b.properties");
        CitRule highA = CitRuleParser.parseString("""
                items=stick
                weight=10
                """, "minecraft:optifine/cit/a.properties");
        CitRule highB = CitRuleParser.parseString("""
                items=stick
                weight=10
                """, "minecraft:optifine/cit/c.properties");

        CitRuleSet set = CitRuleSet.of(List.of(low, highB, highA));

        assertEquals(List.of(highA, highB, low), set.all());
        assertEquals(highA, set.candidates(
                new NamespaceId("minecraft", "stick"))[0]);
    }

    @Test
    void parseAllIsolatesMalformedFiles() {
        CitParseResult result = CitRuleParser.parseAll(List.of(
                new CitRuleParser.RuleSource("items=stick\n",
                        "minecraft:optifine/cit/good.properties"),
                new CitRuleParser.RuleSource("items=stick\nweight=nope\n",
                        "minecraft:optifine/cit/bad.properties")));

        assertEquals(1, result.rules().size());
        assertEquals(1, result.errors().size());
        assertEquals("minecraft:optifine/cit/bad.properties",
                result.errors().getFirst().sourceFile());
        assertTrue(result.errors().getFirst().message().contains("weight"));
    }

    @Test
    void citConditionsIncludeModernAndLegacyKeys() {
        CitRule rule = CitRuleParser.parseString("""
                items=stick
                damage=1-5
                damageMask=7
                damagePercent=1-99
                stackSize=1-64
                enchantments=minecraft:sharpness
                enchantmentLevels=2-5
                hand=main
                components.minecraft\\:custom_name=pattern:Blade*
                nbt.display.Name=pattern:Blade*
                """, "minecraft:optifine/cit/blade.properties");

        assertTrue(rule.conditions().conditions().stream()
                .anyMatch(c -> c.key() == ConditionKey.COMPONENT
                        && "minecraft:custom_name".equals(c.qualifier())));
        assertTrue(rule.conditions().conditions().stream()
                .anyMatch(c -> c.key() == ConditionKey.NBT_RAW
                        && "display.Name".equals(c.qualifier())));
        assertTrue(rule.conditions().conditions().stream()
                .anyMatch(c -> c.key() == ConditionKey.DAMAGE_MASK));
    }
}
