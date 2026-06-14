package com.cinder.config;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CinderConfigLoaderTest {

    @Test
    void parse_emptyFile_returnsDefaults() {
        CinderConfig cfg = CinderConfigLoader.load(new StringReader(""));
        assertEquals(CinderConfigDefaults.ENABLED, cfg.enabled());
        assertEquals(CinderConfigDefaults.SAFE_MODE, cfg.safeMode());
        assertEquals(CinderConfigDefaults.VERIFY_MODE, cfg.verifyMode());
        assertEquals(CinderConfigDefaults.CTM_ENABLED, cfg.ctmEnabled());
        assertEquals(CinderConfigDefaults.CTM_DEBUG_LOGGING,
                cfg.ctmDebugLogging());
        assertEquals(CinderConfigDefaults.DUPLICATE_TRANSLUCENT_BACKFACES,
                cfg.duplicateTranslucentBackfaces());
        assertEquals(CinderConfigDefaults.BETTER_GRASS_MODE,
                cfg.betterGrassMode());
        assertEquals(CinderConfigDefaults.BETTER_GRASS_IGNORE_RESOURCE_PACK,
                cfg.betterGrassIgnoreResourcePack());
        assertEquals(CinderConfigDefaults.BETTER_GRASS_GRASS_BLOCK,
                cfg.betterGrassGrassBlock());
        assertEquals(CinderConfigDefaults.BETTER_GRASS_SNOWY_GRASS_BLOCK,
                cfg.betterGrassSnowyGrassBlock());
        assertEquals(CinderConfigDefaults.BETTER_GRASS_DIRT_PATH,
                cfg.betterGrassDirtPath());
        assertEquals(CinderConfigDefaults.BETTER_GRASS_FARMLAND,
                cfg.betterGrassFarmland());
        assertEquals(CinderConfigDefaults.BETTER_GRASS_MYCELIUM,
                cfg.betterGrassMycelium());
        assertEquals(CinderConfigDefaults.BETTER_GRASS_PODZOL,
                cfg.betterGrassPodzol());
        assertEquals(CinderConfigDefaults.BETTER_GRASS_CRIMSON_NYLIUM,
                cfg.betterGrassCrimsonNylium());
        assertEquals(CinderConfigDefaults.BETTER_GRASS_WARPED_NYLIUM,
                cfg.betterGrassWarpedNylium());
    }

    @Test
    void parse_booleanTrue_overridesDefault() {
        String body = "cinder.enabled = true\n"
                + "cinder.safe_mode = true\n"
                + "cinder.verify_mode = true\n"
                + "cinder.ctm.enabled = false\n"
                + "cinder.ctm.debug_logging = true\n"
                + "cinder.general.duplicate_translucent_backfaces = true\n"
                + "cinder.better_grass.mode = fancy\n"
                + "cinder.better_grass.ignore_resource_pack = true\n"
                + "cinder.better_grass.grass_block = false\n"
                + "cinder.better_grass.snowy_grass_block = false\n"
                + "cinder.better_grass.dirt_path = false\n"
                + "cinder.better_grass.farmland = false\n"
                + "cinder.better_grass.mycelium = false\n"
                + "cinder.better_grass.podzol = false\n"
                + "cinder.better_grass.crimson_nylium = false\n"
                + "cinder.better_grass.warped_nylium = false\n";
        CinderConfig cfg = CinderConfigLoader.load(new StringReader(body));
        assertTrue(cfg.enabled());
        assertTrue(cfg.safeMode());
        assertTrue(cfg.verifyMode());
        assertFalse(cfg.ctmEnabled());
        assertTrue(cfg.ctmDebugLogging());
        assertTrue(cfg.duplicateTranslucentBackfaces());
        assertEquals(BetterGrassMode.FANCY, cfg.betterGrassMode());
        assertTrue(cfg.betterGrassIgnoreResourcePack());
        assertFalse(cfg.betterGrassGrassBlock());
        assertFalse(cfg.betterGrassSnowyGrassBlock());
        assertFalse(cfg.betterGrassDirtPath());
        assertFalse(cfg.betterGrassFarmland());
        assertFalse(cfg.betterGrassMycelium());
        assertFalse(cfg.betterGrassPodzol());
        assertFalse(cfg.betterGrassCrimsonNylium());
        assertFalse(cfg.betterGrassWarpedNylium());
    }

    @Test
    void parse_booleanFalse_overridesDefault() {
        String body = "cinder.enabled = false\n"
                + "cinder.safe_mode = true\n";
        CinderConfig cfg = CinderConfigLoader.load(new StringReader(body));
        assertFalse(cfg.enabled());
        assertTrue(cfg.safeMode());
        // Unset keys keep their defaults.
        assertEquals(CinderConfigDefaults.VERIFY_MODE, cfg.verifyMode());
        assertEquals(CinderConfigDefaults.CTM_ENABLED, cfg.ctmEnabled());
        assertEquals(CinderConfigDefaults.CTM_DEBUG_LOGGING,
                cfg.ctmDebugLogging());
        assertEquals(CinderConfigDefaults.DUPLICATE_TRANSLUCENT_BACKFACES,
                cfg.duplicateTranslucentBackfaces());
        assertEquals(CinderConfigDefaults.BETTER_GRASS_MODE,
                cfg.betterGrassMode());
    }

    @Test
    void parse_unknownKey_isIgnored() {
        String body = "cinder.unknown = true\n"
                + "cinder.enabled = false\n";
        CinderConfig cfg = CinderConfigLoader.load(new StringReader(body));
        assertFalse(cfg.enabled());
    }

    @Test
    void parse_malformedBoolean_usesDefault() {
        // "yes" is not recognised; the loader falls back to the
        // default for the cinder.enabled key, and the other
        // keys (missing) keep their defaults too.
        String body = "cinder.enabled = yes\n";
        CinderConfig cfg = CinderConfigLoader.load(new StringReader(body));
        assertEquals(CinderConfigDefaults.ENABLED, cfg.enabled());
    }

    @Test
    void parse_numericBooleans_areAccepted() {
        // "0" and "1" are common synonyms for false and true.
        String body = "cinder.enabled = 0\n"
                + "cinder.safe_mode = 1\n";
        CinderConfig cfg = CinderConfigLoader.load(new StringReader(body));
        assertFalse(cfg.enabled());
        assertTrue(cfg.safeMode());
    }

    @Test
    void parse_stream_utf8() {
        // Test the InputStream overload, which is the path the
        // Fabric adapter uses for file I/O.
        String body = "cinder.enabled = false\n"
                + "cinder.verify_mode = true\n";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        CinderConfig cfg = CinderConfigLoader.load(
                new ByteArrayInputStream(bytes));
        assertFalse(cfg.enabled());
        assertTrue(cfg.verifyMode());
    }

    @Test
    void ctmActive_requiresBothMasterAndFeature() {
        CinderConfig on = new CinderConfig(true, false, false, true, false,
                BetterGrassMode.FAST);
        assertTrue(on.ctmActive());
        CinderConfig featureOff = new CinderConfig(true, false, false, false,
                false, BetterGrassMode.FAST);
        assertFalse(featureOff.ctmActive());
        CinderConfig masterOff = new CinderConfig(false, false, false, true,
                false, BetterGrassMode.FAST);
        assertFalse(masterOff.ctmActive());
    }

    @Test
    void betterGrassActive_requiresMasterAndNonOffMode() {
        CinderConfig fast = new CinderConfig(true, false, false, true, false,
                BetterGrassMode.FAST);
        assertTrue(fast.betterGrassActive());
        CinderConfig off = new CinderConfig(true, false, false, true, false,
                BetterGrassMode.OFF);
        assertFalse(off.betterGrassActive());
        CinderConfig masterOff = new CinderConfig(false, false, false, true,
                false, BetterGrassMode.FAST);
        assertFalse(masterOff.betterGrassActive());
    }

    @Test
    void anyBetterGrassBlockEnabled_tracksFamilyToggles() {
        CinderConfig allOff = new CinderConfig(true, false, false, true,
                false, BetterGrassMode.FAST, false, false, false, false,
                false, false, false, false);
        assertFalse(allOff.anyBetterGrassBlockEnabled());
        CinderConfig oneOn = new CinderConfig(true, false, false, true,
                false, BetterGrassMode.FAST, false, false, false, true,
                false, false, false, false);
        assertTrue(oneOn.anyBetterGrassBlockEnabled());
    }
}
