package com.cinder.bettergrass;

import com.cinder.config.BetterGrassMode;
import com.cinder.config.CinderConfig;
import com.cinder.resource.NamespaceId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BetterGrassPropertiesTest {

    @AfterEach
    void clearPackRules() {
        BetterGrassRules.replaceResourcePackRules(null);
    }

    @Test
    void emptyProperties_useVanillaDefaults() throws Exception {
        BetterGrassRules rules = BetterGrassProperties.parse(
                new StringReader(""));

        assertTrue(rules.enabled(BetterGrassFamily.GRASS));
        assertTrue(rules.enabled(BetterGrassFamily.GRASS_SNOW));
        assertTrue(rules.enabled(BetterGrassFamily.DIRT_PATH));
        assertTrue(rules.enabled(BetterGrassFamily.FARMLAND));
        assertFalse(rules.grassMultilayer());
        assertEquals(BetterGrassRules.GRASS_TEXTURE,
                rules.texture(BetterGrassFamily.GRASS));
        assertEquals(BetterGrassRules.SNOW_TEXTURE,
                rules.texture(BetterGrassFamily.GRASS_SNOW));
    }

    @Test
    void booleans_areParsedForAllFamilies() throws Exception {
        BetterGrassRules rules = BetterGrassProperties.parse(new StringReader(
                "grass=false\n"
                        + "grass.snow=false\n"
                        + "dirt_path=false\n"
                        + "farmland=false\n"
                        + "mycelium=false\n"
                        + "mycelium.snow=false\n"
                        + "podzol=false\n"
                        + "podzol.snow=false\n"
                        + "crimson_nylium=false\n"
                        + "warped_nylium=false\n"));

        for (BetterGrassFamily family : BetterGrassFamily.values()) {
            assertFalse(rules.enabled(family), family.name());
        }
    }

    @Test
    void textures_acceptNamespaceBarePathAndInvalidFallback() throws Exception {
        BetterGrassRules rules = BetterGrassProperties.parse(new StringReader(
                "texture.grass=custom:block/green\n"
                        + "texture.snow=block/custom_snow.png\n"
                        + "texture.podzol=Bad Path!\n"));

        assertEquals(new NamespaceId("custom", "block/green"),
                rules.texture(BetterGrassFamily.GRASS));
        assertEquals(new NamespaceId("minecraft", "block/custom_snow"),
                rules.texture(BetterGrassFamily.GRASS_SNOW));
        assertEquals(BetterGrassRules.PODZOL_TEXTURE,
                rules.texture(BetterGrassFamily.PODZOL));
    }

    @Test
    void grassMultilayer_isParsed() throws Exception {
        BetterGrassRules rules = BetterGrassProperties.parse(
                new StringReader("grass.multilayer=true\n"));

        assertTrue(rules.grassMultilayer());
    }

    @Test
    void packRulesWinOverConfigTogglesWhenPublished() throws Exception {
        CinderConfig allOff = new CinderConfig(true, false, false, true,
                false, BetterGrassMode.FAST, false, false, false, false,
                false, false, false, false);
        BetterGrassRules pack = BetterGrassProperties.parse(
                new StringReader("grass=true\n"));

        assertFalse(BetterGrassRules.current(allOff)
                .enabled(BetterGrassFamily.GRASS));
        BetterGrassRules.replaceResourcePackRules(pack);

        assertSame(pack, BetterGrassRules.current(allOff));
        assertTrue(BetterGrassRules.current(allOff)
                .enabled(BetterGrassFamily.GRASS));
    }

    @Test
    void ignoredPackRulesFallBackToConfigToggles() throws Exception {
        CinderConfig allOffIgnoringPack = new CinderConfig(true, false, false,
                true, false, BetterGrassMode.FAST, true, false, false, false,
                false, false, false, false, false);
        BetterGrassRules pack = BetterGrassProperties.parse(
                new StringReader("grass=true\n"));
        BetterGrassRules.replaceResourcePackRules(pack);

        assertFalse(BetterGrassRules.current(allOffIgnoringPack)
                .enabled(BetterGrassFamily.GRASS));
    }
}
