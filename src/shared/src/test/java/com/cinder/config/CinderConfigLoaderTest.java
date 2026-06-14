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
        assertEquals(CinderConfigDefaults.NATURAL_TEXTURES_ENABLED,
                cfg.naturalTexturesEnabled());
        assertEquals(CinderConfigDefaults.BETTER_SNOW_ENABLED,
                cfg.betterSnowEnabled());
        assertEquals(CinderConfigDefaults.CUSTOM_ANIMATIONS_ENABLED,
                cfg.customAnimationsEnabled());
        assertEquals(CinderConfigDefaults.CUSTOM_ANIMATION_MIPMAP_DISTANCE,
                cfg.customAnimationMipmapDistance());
        assertEquals(CinderConfigDefaults.DETAILS_SKY_ENABLED,
                cfg.detailsSkyEnabled());
        assertEquals(CinderConfigDefaults.DETAILS_SUN_ENABLED,
                cfg.detailsSunEnabled());
        assertEquals(CinderConfigDefaults.DETAILS_MOON_ENABLED,
                cfg.detailsMoonEnabled());
        assertEquals(CinderConfigDefaults.DETAILS_STARS_ENABLED,
                cfg.detailsStarsEnabled());
        assertEquals(CinderConfigDefaults.DETAILS_CLOUDS_ENABLED,
                cfg.detailsCloudsEnabled());
        assertEquals(CinderConfigDefaults.DETAILS_CLOUD_HEIGHT,
                cfg.detailsCloudHeight());
        assertEquals(CinderConfigDefaults.ANIMATIONS_ENABLED,
                cfg.animationsEnabled());
        assertEquals(CinderConfigDefaults.PARTICLES_ENABLED,
                cfg.particlesEnabled());
        assertEquals(CinderConfigDefaults.PARTICLE_RAIN_SPLASH,
                cfg.particleRainSplash());
        assertEquals(CinderConfigDefaults.PARTICLE_BLOCK_BREAK,
                cfg.particleBlockBreak());
        assertEquals(CinderConfigDefaults.PARTICLE_BLOCK_BREAKING,
                cfg.particleBlockBreaking());
        assertEquals(CinderConfigDefaults.PARTICLE_EXPLOSION,
                cfg.particleExplosion());
        assertEquals(CinderConfigDefaults.PARTICLE_WATER,
                cfg.particleWater());
        assertEquals(CinderConfigDefaults.PARTICLE_SMOKE,
                cfg.particleSmoke());
        assertEquals(CinderConfigDefaults.PARTICLE_POTION,
                cfg.particlePotion());
        assertEquals(CinderConfigDefaults.PARTICLE_PORTAL,
                cfg.particlePortal());
        assertEquals(CinderConfigDefaults.PARTICLE_FLAME,
                cfg.particleFlame());
        assertEquals(CinderConfigDefaults.PARTICLE_REDSTONE,
                cfg.particleRedstone());
        assertEquals(CinderConfigDefaults.PARTICLE_DRIPPING,
                cfg.particleDripping());
        assertEquals(CinderConfigDefaults.PARTICLE_FIREWORK,
                cfg.particleFirework());
        assertEquals(CinderConfigDefaults.FOG_ENABLED, cfg.fogEnabled());
        assertEquals(CinderConfigDefaults.FOG_WATER, cfg.fogWater());
        assertEquals(CinderConfigDefaults.FOG_LAVA, cfg.fogLava());
        assertEquals(CinderConfigDefaults.FOG_POWDER_SNOW,
                cfg.fogPowderSnow());
        assertEquals(CinderConfigDefaults.FOG_AIR, cfg.fogAir());
        assertEquals(CinderConfigDefaults.ENTITY_SHADOWS_ENABLED,
                cfg.entityShadowsEnabled());
        assertEquals(CinderConfigDefaults.ENTITY_PLAYER_NAME_TAGS,
                cfg.entityPlayerNameTags());
        assertEquals(CinderConfigDefaults.ENTITY_ITEM_FRAMES,
                cfg.entityItemFrames());
        assertEquals(CinderConfigDefaults.ENTITY_PAINTINGS,
                cfg.entityPaintings());
        assertEquals(CinderConfigDefaults.ENTITY_PISTON_ANIMATIONS,
                cfg.entityPistonAnimations());
        assertEquals(CinderConfigDefaults.ENTITY_BEACON_BEAM,
                cfg.entityBeaconBeam());
        assertEquals(CinderConfigDefaults.ENTITY_ENCHANTING_TABLE_BOOK,
                cfg.entityEnchantingTableBook());
        assertEquals(CinderConfigDefaults.SHOW_FPS, cfg.showFps());
        assertEquals(CinderConfigDefaults.OVERLAY_CORNER,
                cfg.overlayCorner());
        assertEquals(CinderConfigDefaults.TEXT_CONTRAST,
                cfg.textContrast());
        assertEquals(CinderConfigDefaults.TOAST_SYSTEM,
                cfg.toastSystem());
        assertEquals(CinderConfigDefaults.BIOME_COLORS_ENABLED,
                cfg.biomeColorsEnabled());
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
                + "cinder.better_grass.warped_nylium = false\n"
                + "cinder.natural_textures.enabled = false\n"
                + "cinder.better_snow.enabled = false\n"
                + "cinder.custom_animations.enabled = false\n"
                + "cinder.custom_animations.mipmap_distance = 2\n"
                + "cinder.details.sky.enabled = false\n"
                + "cinder.details.cloud_height = 240\n"
                + "cinder.animations.enabled = false\n"
                + "cinder.animations.water = false\n"
                + "cinder.particles.enabled = false\n"
                + "cinder.particles.rain_splash = false\n"
                + "cinder.particles.block_break = false\n"
                + "cinder.particles.block_breaking = false\n"
                + "cinder.particles.explosion = false\n"
                + "cinder.particles.water = false\n"
                + "cinder.particles.smoke = false\n"
                + "cinder.particles.potion = false\n"
                + "cinder.particles.portal = false\n"
                + "cinder.particles.flame = false\n"
                + "cinder.particles.redstone = false\n"
                + "cinder.particles.dripping = false\n"
                + "cinder.particles.firework = false\n"
                + "cinder.fog.enabled = false\n"
                + "cinder.fog.water = false\n"
                + "cinder.fog.lava = false\n"
                + "cinder.fog.powder_snow = false\n"
                + "cinder.fog.air = false\n"
                + "cinder.entities.shadows.enabled = false\n"
                + "cinder.entities.player_name_tags = false\n"
                + "cinder.entities.item_frames = false\n"
                + "cinder.entities.paintings = false\n"
                + "cinder.entities.piston_animations = false\n"
                + "cinder.entities.beacon_beam = false\n"
                + "cinder.entities.enchanting_table_book = false\n"
                + "cinder.hud.fps = true\n"
                + "cinder.hud.fps_extended = true\n"
                + "cinder.hud.coords = true\n"
                + "cinder.hud.corner = bottom_right\n"
                + "cinder.hud.text_contrast = backdrop\n"
                + "cinder.hud.steady_debug = true\n"
                + "cinder.hud.steady_debug_refresh_interval = 20\n"
                + "cinder.toasts.advancement = false\n"
                + "cinder.toasts.recipe = false\n"
                + "cinder.toasts.system = false\n"
                + "cinder.toasts.tutorial = false\n"
                + "cinder.extras.instant_sneak = true\n"
                + "cinder.extras.fullscreen_mode = borderless\n"
                + "cinder.colors.biome.enabled = false\n";
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
        assertFalse(cfg.naturalTexturesEnabled());
        assertFalse(cfg.betterSnowEnabled());
        assertFalse(cfg.customAnimationsEnabled());
        assertEquals(2, cfg.customAnimationMipmapDistance());
        assertFalse(cfg.detailsSkyEnabled());
        assertEquals(240, cfg.detailsCloudHeight());
        assertFalse(cfg.animationsEnabled());
        assertFalse(cfg.animationWater());
        assertFalse(cfg.particlesEnabled());
        assertFalse(cfg.particleRainSplash());
        assertFalse(cfg.particleBlockBreak());
        assertFalse(cfg.particleBlockBreaking());
        assertFalse(cfg.particleExplosion());
        assertFalse(cfg.particleWater());
        assertFalse(cfg.particleSmoke());
        assertFalse(cfg.particlePotion());
        assertFalse(cfg.particlePortal());
        assertFalse(cfg.particleFlame());
        assertFalse(cfg.particleRedstone());
        assertFalse(cfg.particleDripping());
        assertFalse(cfg.particleFirework());
        assertFalse(cfg.fogEnabled());
        assertFalse(cfg.fogWater());
        assertFalse(cfg.fogLava());
        assertFalse(cfg.fogPowderSnow());
        assertFalse(cfg.fogAir());
        assertFalse(cfg.entityShadowsEnabled());
        assertFalse(cfg.entityPlayerNameTags());
        assertFalse(cfg.entityItemFrames());
        assertFalse(cfg.entityPaintings());
        assertFalse(cfg.entityPistonAnimations());
        assertFalse(cfg.entityBeaconBeam());
        assertFalse(cfg.entityEnchantingTableBook());
        assertTrue(cfg.showFps());
        assertTrue(cfg.showFpsExtended());
        assertTrue(cfg.showCoords());
        assertEquals(OverlayCorner.BOTTOM_RIGHT, cfg.overlayCorner());
        assertEquals(TextContrast.BACKDROP, cfg.textContrast());
        assertTrue(cfg.steadyDebugHud());
        assertEquals(20, cfg.steadyDebugHudRefreshInterval());
        assertFalse(cfg.toastAdvancement());
        assertFalse(cfg.toastRecipe());
        assertFalse(cfg.toastSystem());
        assertFalse(cfg.toastTutorial());
        assertTrue(cfg.instantSneak());
        assertEquals(FullscreenMode.BORDERLESS, cfg.fullscreenMode());
        assertFalse(cfg.biomeColorsEnabled());
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
    void parse_malformedMipmapDistance_usesDefault() {
        String body = "cinder.custom_animations.mipmap_distance = 9\n";
        CinderConfig cfg = CinderConfigLoader.load(new StringReader(body));
        assertEquals(CinderConfigDefaults.CUSTOM_ANIMATION_MIPMAP_DISTANCE,
                cfg.customAnimationMipmapDistance());
    }

    @Test
    void parse_malformedCloudHeight_usesDefault() {
        String body = "cinder.details.cloud_height = 999\n";
        CinderConfig cfg = CinderConfigLoader.load(new StringReader(body));
        assertEquals(CinderConfigDefaults.DETAILS_CLOUD_HEIGHT,
                cfg.detailsCloudHeight());
    }

    @Test
    void builder_preservesUnchangedFields() {
        CinderConfig base = CinderConfigDefaults.defaults();
        CinderConfig changed = base.toBuilder()
                .detailsSkyEnabled(false)
                .animationWater(false)
                .particleFirework(false)
                .overlayCorner(OverlayCorner.BOTTOM_LEFT)
                .build();
        assertFalse(changed.detailsSkyEnabled());
        assertFalse(changed.animationWater());
        assertFalse(changed.particleFirework());
        assertEquals(OverlayCorner.BOTTOM_LEFT, changed.overlayCorner());
        assertEquals(base.ctmEnabled(), changed.ctmEnabled());
        assertEquals(base.betterGrassMode(), changed.betterGrassMode());
        assertEquals(base.customAnimationMipmapDistance(),
                changed.customAnimationMipmapDistance());
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
