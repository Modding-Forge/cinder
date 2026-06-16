package com.cinder.customsky;

import com.cinder.resource.NamespaceId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CustomSkyPropertiesTest {

    @Test
    void parsesNumberedLayersWithoutSkyZero() {
        CustomSkyParseResult result = CustomSkyProperties.parseAll(List.of(
                new CustomSkyProperties.RuleSource("""
                        source=skybox:stars.png
                        blend=alpha
                        weather=clear
                        """, "minecraft:optifine/sky/world0/sky1.properties"),
                new CustomSkyProperties.RuleSource("""
                        source=skybox:sunflare.png
                        blend=add
                        """, "minecraft:optifine/sky/world0/sky8.properties")));

        assertTrue(result.errors().isEmpty());
        CustomSkyRuleSet ruleSet = CustomSkyRuleSet.of(result.layers());
        CustomSkyLayer[] layers = ruleSet.layersForWorld(0);
        assertEquals(2, layers.length);
        assertEquals(1, layers[0].layerIndex());
        assertEquals(8, layers[1].layerIndex());
    }

    @Test
    void resolvesSourcesWithOptifinePathRules() {
        CustomSkyLayer bare = CustomSkyProperties.parseString(
                "source=skybox/stars.png",
                "minecraft:optifine/sky/world0/sky1.properties");
        CustomSkyLayer home = CustomSkyProperties.parseString(
                "source=~/sky/world0/sky2.png",
                "minecraft:optifine/sky/world0/sky2.properties");
        CustomSkyLayer relative = CustomSkyProperties.parseString(
                "source=./local.png",
                "minecraft:optifine/sky/world0/sky3.properties");
        CustomSkyLayer namespaced = CustomSkyProperties.parseString(
                "source=skybox:stars.png",
                "minecraft:optifine/sky/world0/sky4.properties");
        CustomSkyLayer implicit = CustomSkyProperties.parseString(
                "",
                "minecraft:optifine/sky/world0/sky5.properties");

        assertEquals(new NamespaceId("minecraft", "skybox/stars.png"),
                bare.source());
        assertEquals(new NamespaceId("minecraft",
                        "optifine/sky/world0/sky2.png"),
                home.source());
        assertEquals(new NamespaceId("minecraft",
                        "optifine/sky/world0/local.png"),
                relative.source());
        assertEquals(new NamespaceId("skybox", "stars.png"),
                namespaced.source());
        assertEquals(new NamespaceId("minecraft",
                        "optifine/sky/world0/sky5.png"),
                implicit.source());
    }

    @Test
    void parsesFadeTimesAndComputesMissingStartFadeOut() {
        CustomSkyLayer layer = CustomSkyProperties.parseString("""
                startFadeIn=19\\:20
                endFadeIn=19\\:40
                endFadeOut=4\\:40
                """, "minecraft:optifine/sky/world0/sky1.properties");

        assertTrue(layer.hasFade());
        assertEquals(13333, layer.startFadeIn());
        assertEquals(13666, layer.endFadeIn());
        assertEquals(22666, layer.endFadeOut());
        assertEquals(22333, layer.startFadeOut());
        assertEquals(0.0F, layer.fadeAlpha(12000), 0.001F);
        assertEquals(1.0F, layer.fadeAlpha(18000), 0.001F);
    }

    @Test
    void pureBdCraftDayAndNightLayersDoNotOverlapAtNoon() {
        CustomSkyLayer day = CustomSkyProperties.parseString("""
                startFadeIn=5\\:00
                endFadeIn=7\\:00
                endFadeOut=18\\:00
                blend=add
                rotate=false
                source=./sky1.png
                """, "minecraft:optifine/sky/world0/sky1.properties");
        CustomSkyLayer night = CustomSkyProperties.parseString("""
                startFadeIn=19\\:00
                endFadeIn=22\\:00
                endFadeOut=4\\:00
                blend=add
                rotate=false
                source=./sky2.png
                """, "minecraft:optifine/sky/world0/sky2.properties");

        assertEquals(1.0F, day.fadeAlpha(6000), 0.001F);
        assertEquals(0.0F, night.fadeAlpha(6000), 0.001F);
        assertEquals(0.0F, day.fadeAlpha(18000), 0.001F);
        assertEquals(1.0F, night.fadeAlpha(18000), 0.001F);
    }

    @Test
    void parsesConditionsAndRotation() {
        CustomSkyLayer layer = CustomSkyProperties.parseString("""
                blend=screen
                rotate=false
                speed=2.5
                axis=1.0 0.0 0.0
                days=1-3
                daysLoop=8
                weather=clear rain
                biomes=plains minecraft:forest
                heights=64-80
                transition=2.5
                """, "minecraft:optifine/sky/world0/sky2.properties");

        assertEquals(CustomSkyBlendMode.SCREEN, layer.blend());
        assertFalse(layer.rotation().rotate());
        assertEquals(2.5F, layer.rotation().speed(), 0.001F);
        assertEquals(1.0F, layer.rotation().axisX(), 0.001F);
        assertEquals(50, layer.transitionTicks());
        assertArrayEquals(new NamespaceId[]{
                new NamespaceId("minecraft", "plains"),
                new NamespaceId("minecraft", "forest")
        }, layer.biomes());
        assertTrue(layer.matches(0, 2, CustomSkyLayer.WEATHER_CLEAR,
                new NamespaceId("minecraft", "plains"), 70));
        assertFalse(layer.matches(0, 4, CustomSkyLayer.WEATHER_CLEAR,
                new NamespaceId("minecraft", "plains"), 70));
        assertFalse(layer.matches(0, 2, CustomSkyLayer.WEATHER_THUNDER,
                new NamespaceId("minecraft", "plains"), 70));
        assertFalse(layer.matches(0, 2, CustomSkyLayer.WEATHER_CLEAR,
                new NamespaceId("minecraft", "desert"), 70));
        assertFalse(layer.matches(0, 2, CustomSkyLayer.WEATHER_CLEAR,
                new NamespaceId("minecraft", "plains"), 90));
    }

    @Test
    void weatherConditionsProduceContinuousTargetAlpha() {
        CustomSkyLayer clear = CustomSkyProperties.parseString("""
                weather=clear
                """, "minecraft:optifine/sky/world0/sky1.properties");
        CustomSkyLayer rainAndThunder = CustomSkyProperties.parseString("""
                weather=rain thunder
                """, "minecraft:optifine/sky/world0/sky2.properties");

        assertEquals(1.0F, clear.conditionTargetAlpha(0, 0,
                1.0F, 0.0F, 0.0F, null, 64), 0.001F);
        assertEquals(0.25F, clear.conditionTargetAlpha(0, 0,
                0.25F, 0.75F, 0.0F, null, 64), 0.001F);
        assertEquals(0.75F, rainAndThunder.conditionTargetAlpha(0, 0,
                0.25F, 0.50F, 0.25F, null, 64), 0.001F);
        assertEquals(0.0F, rainAndThunder.conditionTargetAlpha(1, 0,
                0.25F, 0.50F, 0.25F, null, 64), 0.001F);
    }

    @Test
    void isolatesMalformedLayer() {
        CustomSkyParseResult result = CustomSkyProperties.parseAll(List.of(
                new CustomSkyProperties.RuleSource("axis=broken",
                        "minecraft:optifine/sky/world0/sky1.properties"),
                new CustomSkyProperties.RuleSource("source=skybox:stars.png",
                        "minecraft:optifine/sky/world0/sky2.properties")));

        assertEquals(1, result.layers().size());
        assertEquals(1, result.errors().size());
        assertEquals(2, result.layers().getFirst().layerIndex());
    }
}
