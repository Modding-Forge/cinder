package com.cinder.customcolors;

import com.cinder.resource.NamespaceId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ColorPropertiesTest {

    @Test
    void colorPropertiesParsesRgbKeysAndAliases() {
        CustomColorParseResult result = ColorProperties.parseColorProperties("""
                text.xpbar=12abef
                dye.silver=#445566
                collar.silver=0x778899
                map.snow=aabbcc
                broken=not-a-color
                """, "minecraft:optifine/color.properties");

        assertEquals(0x12abef, result.overrides().colorOr("text.xpbar", 0));
        assertEquals(0x445566, result.overrides().colorOr("dye.light_gray", 0));
        assertEquals(0x778899, result.overrides().colorOr("collar.light_gray", 0));
        assertEquals(0xaabbcc, result.overrides().colorOr("map.white", 0));
        assertEquals(1, result.errors().size());
        assertEquals("broken", result.errors().getFirst().key());
    }

    @Test
    void paletteBlockBuildsBlockColormapRules() {
        CustomColorParseResult result = ColorProperties.parseColorProperties("""
                palette.format=grid
                palette.block.~/colormap/stone.png=minecraft:stone minecraft:redstone_ore:lit=true
                """, "minecraft:optifine/color.properties");

        assertEquals(1, result.blockRules().size());
        ColormapRule rule = result.blockRules().getFirst();
        assertEquals(ColormapFormat.GRID, rule.format());
        assertEquals(new NamespaceId("minecraft",
                "optifine/colormap/stone"), rule.source());
        assertEquals("minecraft:stone", rule.blocks()[0].toString());
        assertEquals("minecraft:redstone_ore:lit=true",
                rule.blocks()[1].toString());
    }

    @Test
    void colormapPropertiesParseFormatBlocksAndColor() {
        ColormapRule rule = ColorProperties.parseColormapProperties("""
                format=fixed
                blocks=minecraft:sand
                source=./sand.png
                color=336699
                yVariance=2
                yOffset=-4
                """, "minecraft:optifine/colormap/blocks/sand.properties");

        assertEquals(ColormapFormat.FIXED, rule.format());
        assertEquals(new NamespaceId("minecraft",
                "optifine/colormap/blocks/sand"), rule.source());
        assertEquals(0x336699, rule.fixedColor());
        assertEquals(2, rule.yVariance());
        assertEquals(-4, rule.yOffset());
        assertEquals("minecraft:sand", rule.blocks()[0].toString());
    }

    @Test
    void colormapImageSamplesVanillaGridAndFixed() {
        ColormapImage image = new ColormapImage(4, 4, new int[]{
                0, 1, 2, 3,
                4, 5, 6, 7,
                8, 9, 10, 11,
                12, 13, 14, 15
        });
        ColormapRule vanilla = new ColormapRule("v",
                new NamespaceId("minecraft", "v"), ColormapFormat.VANILLA,
                null, null, 0, 0, null);
        ColormapRule grid = new ColormapRule("g",
                new NamespaceId("minecraft", "g"), ColormapFormat.GRID,
                null, null, 0, 1, null);
        ColormapRule fixed = new ColormapRule("f",
                new NamespaceId("minecraft", "f"), ColormapFormat.FIXED,
                null, 0x123456, 0, 0, null);

        assertEquals(0, vanilla.sample(image, 1.0D, 1.0D,
                0, 0, 0, 0));
        assertEquals(9, grid.sample(image, 0.0D, 0.0D,
                1, 1, 0, 0));
        assertEquals(0x123456, fixed.sample(image, 0.0D, 0.0D,
                0, 0, 0, 0));
    }

    @Test
    void ruleSetIsImmutable() {
        CustomColorRuleSet set = new CustomColorRuleSet(
                new ColorOverrideTable(java.util.Map.of("text.xpbar", 1)),
                new ColormapRule[0],
                java.util.Map.of("redstone", new ColormapImage(1, 1,
                        new int[]{0xff0000})),
                java.util.Map.of());

        assertEquals(1, set.overrides().colorOr("text.xpbar", 0));
        assertThrows(UnsupportedOperationException.class,
                () -> set.specialColormaps().put("x",
                        new ColormapImage(1, 1, new int[]{0})));
    }
}
