package com.cinder.config;

import com.cinder.resource.OptifinePropertyParsers;
import com.cinder.resource.PropertiesFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Parses a {@code .properties}-style configuration file into a
 * {@link CinderConfig}. The format is intentionally minimal:
 * one key per line, value is a boolean. Unknown keys are
 * ignored; malformed values fall back to the default and the
 * loader does not throw.
 *
 * <p>File format example:
 * <pre>
 * # Cinder configuration
 * cinder.enabled = true
 * cinder.safe_mode = false
 * cinder.verify_mode = false
 * cinder.ctm.enabled = true
 * cinder.ctm.debug_logging = false
 * cinder.general.duplicate_translucent_backfaces = false
 * cinder.better_grass.mode = fast
 * cinder.better_grass.ignore_resource_pack = false
 * cinder.better_grass.grass_block = true
 * cinder.better_grass.snowy_grass_block = true
 * cinder.better_grass.dirt_path = true
 * cinder.better_grass.farmland = true
 * cinder.better_grass.mycelium = true
 * cinder.better_grass.podzol = true
 * cinder.better_grass.crimson_nylium = true
 * cinder.better_grass.warped_nylium = true
 * cinder.cit.enabled = true
 * cinder.custom_gui.enabled = true
 * cinder.custom_colors.enabled = true
 * cinder.custom_sky.enabled = true
 * cinder.natural_textures.enabled = true
 * cinder.better_snow.enabled = true
 * cinder.custom_animations.enabled = true
 * cinder.custom_animations.mipmap_distance = 4
 * </pre>
 *
 * <p>Performance: O(file size). Called once at config load and
 * once per reload; not in any hot path.
 */
public final class CinderConfigLoader {

    private CinderConfigLoader() {
    }

    /**
     * Parses a configuration from an input stream of UTF-8 text.
     * The stream is consumed but not closed by this method.
     */
    public static CinderConfig load(InputStream stream) {
        return load(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    /**
     * Parses a configuration from a {@link Reader}. The reader
     * is consumed but not closed by this method.
     */
    public static CinderConfig load(Reader reader) {
        Objects.requireNonNull(reader, "reader");
        PropertiesFile props;
        try {
            props = PropertiesFile.parse(reader);
        } catch (IOException e) {
            return CinderConfigDefaults.defaults();
        }
        return fromProperties(props);
    }

    /**
     * Builds a {@link CinderConfig} from an already-parsed
     * {@link PropertiesFile}. Used by tests and by adapters
     * that have their own property-parsing layer.
     */
    public static CinderConfig fromProperties(PropertiesFile props) {
        boolean enabled = readBool(props, "cinder.enabled",
                CinderConfigDefaults.ENABLED);
        boolean safeMode = readBool(props, "cinder.safe_mode",
                CinderConfigDefaults.SAFE_MODE);
        boolean verifyMode = readBool(props, "cinder.verify_mode",
                CinderConfigDefaults.VERIFY_MODE);
        boolean ctmEnabled = readBool(props, "cinder.ctm.enabled",
                CinderConfigDefaults.CTM_ENABLED);
        boolean ctmDebugLogging = readBool(props, "cinder.ctm.debug_logging",
                CinderConfigDefaults.CTM_DEBUG_LOGGING);
        boolean duplicateTranslucentBackfaces = readBool(props,
                "cinder.general.duplicate_translucent_backfaces",
                CinderConfigDefaults.DUPLICATE_TRANSLUCENT_BACKFACES);
        BetterGrassMode betterGrassMode = BetterGrassMode.parse(
                props.get("cinder.better_grass.mode"),
                CinderConfigDefaults.BETTER_GRASS_MODE);
        boolean betterGrassIgnoreResourcePack = readBool(props,
                "cinder.better_grass.ignore_resource_pack",
                CinderConfigDefaults.BETTER_GRASS_IGNORE_RESOURCE_PACK);
        boolean betterGrassGrassBlock = readBool(props,
                "cinder.better_grass.grass_block",
                CinderConfigDefaults.BETTER_GRASS_GRASS_BLOCK);
        boolean betterGrassSnowyGrassBlock = readBool(props,
                "cinder.better_grass.snowy_grass_block",
                CinderConfigDefaults.BETTER_GRASS_SNOWY_GRASS_BLOCK);
        boolean betterGrassDirtPath = readBool(props,
                "cinder.better_grass.dirt_path",
                CinderConfigDefaults.BETTER_GRASS_DIRT_PATH);
        boolean betterGrassFarmland = readBool(props,
                "cinder.better_grass.farmland",
                CinderConfigDefaults.BETTER_GRASS_FARMLAND);
        boolean betterGrassMycelium = readBool(props,
                "cinder.better_grass.mycelium",
                CinderConfigDefaults.BETTER_GRASS_MYCELIUM);
        boolean betterGrassPodzol = readBool(props,
                "cinder.better_grass.podzol",
                CinderConfigDefaults.BETTER_GRASS_PODZOL);
        boolean betterGrassCrimsonNylium = readBool(props,
                "cinder.better_grass.crimson_nylium",
                CinderConfigDefaults.BETTER_GRASS_CRIMSON_NYLIUM);
        boolean betterGrassWarpedNylium = readBool(props,
                "cinder.better_grass.warped_nylium",
                CinderConfigDefaults.BETTER_GRASS_WARPED_NYLIUM);
        boolean citEnabled = readBool(props, "cinder.cit.enabled",
                CinderConfigDefaults.CIT_ENABLED);
        boolean customGuiEnabled = readBool(props, "cinder.custom_gui.enabled",
                CinderConfigDefaults.CUSTOM_GUI_ENABLED);
        boolean customColorsEnabled = readBool(props,
                "cinder.custom_colors.enabled",
                CinderConfigDefaults.CUSTOM_COLORS_ENABLED);
        boolean customSkyEnabled = readBool(props,
                "cinder.custom_sky.enabled",
                CinderConfigDefaults.CUSTOM_SKY_ENABLED);
        boolean naturalTexturesEnabled = readBool(props,
                "cinder.natural_textures.enabled",
                CinderConfigDefaults.NATURAL_TEXTURES_ENABLED);
        boolean betterSnowEnabled = readBool(props,
                "cinder.better_snow.enabled",
                CinderConfigDefaults.BETTER_SNOW_ENABLED);
        boolean customAnimationsEnabled = readBool(props,
                "cinder.custom_animations.enabled",
                CinderConfigDefaults.CUSTOM_ANIMATIONS_ENABLED);
        boolean randomEntitiesEnabled = readBool(props,
                "cinder.random_entities.enabled",
                CinderConfigDefaults.RANDOM_ENTITIES_ENABLED);
        boolean customEntityModelsEnabled = readBool(props,
                "cinder.custom_entity_models.enabled",
                CinderConfigDefaults.CUSTOM_ENTITY_MODELS_ENABLED);
        int customAnimationMipmapDistance = readInt(props,
                "cinder.custom_animations.mipmap_distance",
                CinderConfigDefaults.CUSTOM_ANIMATION_MIPMAP_DISTANCE,
                0, 4);
        boolean detailsSkyEnabled = readBool(props,
                "cinder.details.sky.enabled",
                CinderConfigDefaults.DETAILS_SKY_ENABLED);
        boolean detailsSunEnabled = readBool(props,
                "cinder.details.sun.enabled",
                CinderConfigDefaults.DETAILS_SUN_ENABLED);
        boolean detailsMoonEnabled = readBool(props,
                "cinder.details.moon.enabled",
                CinderConfigDefaults.DETAILS_MOON_ENABLED);
        boolean detailsStarsEnabled = readBool(props,
                "cinder.details.stars.enabled",
                CinderConfigDefaults.DETAILS_STARS_ENABLED);
        boolean detailsCloudsEnabled = readBool(props,
                "cinder.details.clouds.enabled",
                CinderConfigDefaults.DETAILS_CLOUDS_ENABLED);
        int detailsCloudHeight = readInt(props,
                "cinder.details.cloud_height",
                CinderConfigDefaults.DETAILS_CLOUD_HEIGHT,
                0, 512);
        boolean detailsRainSnowEnabled = readBool(props,
                "cinder.details.rain_snow.enabled",
                CinderConfigDefaults.DETAILS_RAIN_SNOW_ENABLED);
        boolean detailsVignetteEnabled = readBool(props,
                "cinder.details.vignette.enabled",
                CinderConfigDefaults.DETAILS_VIGNETTE_ENABLED);
        boolean animationsEnabled = readBool(props,
                "cinder.animations.enabled",
                CinderConfigDefaults.ANIMATIONS_ENABLED);
        boolean animationWater = readBool(props,
                "cinder.animations.water",
                CinderConfigDefaults.ANIMATION_WATER);
        boolean animationLava = readBool(props,
                "cinder.animations.lava",
                CinderConfigDefaults.ANIMATION_LAVA);
        boolean animationFire = readBool(props,
                "cinder.animations.fire",
                CinderConfigDefaults.ANIMATION_FIRE);
        boolean animationPortal = readBool(props,
                "cinder.animations.portal",
                CinderConfigDefaults.ANIMATION_PORTAL);
        boolean animationSculkSensor = readBool(props,
                "cinder.animations.sculk_sensor",
                CinderConfigDefaults.ANIMATION_SCULK_SENSOR);
        boolean animationBlocks = readBool(props,
                "cinder.animations.blocks",
                CinderConfigDefaults.ANIMATION_BLOCKS);
        boolean particlesEnabled = readBool(props,
                "cinder.particles.enabled",
                CinderConfigDefaults.PARTICLES_ENABLED);
        boolean particleRainSplash = readBool(props,
                "cinder.particles.rain_splash",
                CinderConfigDefaults.PARTICLE_RAIN_SPLASH);
        boolean particleBlockBreak = readBool(props,
                "cinder.particles.block_break",
                CinderConfigDefaults.PARTICLE_BLOCK_BREAK);
        boolean particleBlockBreaking = readBool(props,
                "cinder.particles.block_breaking",
                CinderConfigDefaults.PARTICLE_BLOCK_BREAKING);
        boolean particleExplosion = readBool(props,
                "cinder.particles.explosion",
                CinderConfigDefaults.PARTICLE_EXPLOSION);
        boolean particleWater = readBool(props,
                "cinder.particles.water",
                CinderConfigDefaults.PARTICLE_WATER);
        boolean particleSmoke = readBool(props,
                "cinder.particles.smoke",
                CinderConfigDefaults.PARTICLE_SMOKE);
        boolean particlePotion = readBool(props,
                "cinder.particles.potion",
                CinderConfigDefaults.PARTICLE_POTION);
        boolean particlePortal = readBool(props,
                "cinder.particles.portal",
                CinderConfigDefaults.PARTICLE_PORTAL);
        boolean particleFlame = readBool(props,
                "cinder.particles.flame",
                CinderConfigDefaults.PARTICLE_FLAME);
        boolean particleRedstone = readBool(props,
                "cinder.particles.redstone",
                CinderConfigDefaults.PARTICLE_REDSTONE);
        boolean particleDripping = readBool(props,
                "cinder.particles.dripping",
                CinderConfigDefaults.PARTICLE_DRIPPING);
        boolean particleFirework = readBool(props,
                "cinder.particles.firework",
                CinderConfigDefaults.PARTICLE_FIREWORK);
        boolean fogEnabled = readBool(props,
                "cinder.fog.enabled",
                CinderConfigDefaults.FOG_ENABLED);
        boolean fogWater = readBool(props,
                "cinder.fog.water",
                CinderConfigDefaults.FOG_WATER);
        boolean fogLava = readBool(props,
                "cinder.fog.lava",
                CinderConfigDefaults.FOG_LAVA);
        boolean fogPowderSnow = readBool(props,
                "cinder.fog.powder_snow",
                CinderConfigDefaults.FOG_POWDER_SNOW);
        boolean fogAir = readBool(props,
                "cinder.fog.air",
                CinderConfigDefaults.FOG_AIR);
        boolean entityShadowsEnabled = readBool(props,
                "cinder.entities.shadows.enabled",
                CinderConfigDefaults.ENTITY_SHADOWS_ENABLED);
        boolean entityNameTagsEnabled = readBool(props,
                "cinder.entities.name_tags.enabled",
                CinderConfigDefaults.ENTITY_NAME_TAGS_ENABLED);
        boolean entityPlayerNameTags = readBool(props,
                "cinder.entities.player_name_tags",
                CinderConfigDefaults.ENTITY_PLAYER_NAME_TAGS);
        boolean entityItemFrames = readBool(props,
                "cinder.entities.item_frames",
                CinderConfigDefaults.ENTITY_ITEM_FRAMES);
        boolean entityPaintings = readBool(props,
                "cinder.entities.paintings",
                CinderConfigDefaults.ENTITY_PAINTINGS);
        boolean entityPistonAnimations = readBool(props,
                "cinder.entities.piston_animations",
                CinderConfigDefaults.ENTITY_PISTON_ANIMATIONS);
        boolean entityBeaconBeam = readBool(props,
                "cinder.entities.beacon_beam",
                CinderConfigDefaults.ENTITY_BEACON_BEAM);
        boolean entityLimitBeaconBeamHeight = readBool(props,
                "cinder.entities.limit_beacon_beam_height",
                CinderConfigDefaults.ENTITY_LIMIT_BEACON_BEAM_HEIGHT);
        boolean entityEnchantingTableBook = readBool(props,
                "cinder.entities.enchanting_table_book",
                CinderConfigDefaults.ENTITY_ENCHANTING_TABLE_BOOK);
        boolean showFps = readBool(props,
                "cinder.hud.fps",
                CinderConfigDefaults.SHOW_FPS);
        boolean showFpsExtended = readBool(props,
                "cinder.hud.fps_extended",
                CinderConfigDefaults.SHOW_FPS_EXTENDED);
        boolean showCoords = readBool(props,
                "cinder.hud.coords",
                CinderConfigDefaults.SHOW_COORDS);
        OverlayCorner overlayCorner = OverlayCorner.parse(
                props.get("cinder.hud.corner"),
                CinderConfigDefaults.OVERLAY_CORNER);
        TextContrast textContrast = TextContrast.parse(
                props.get("cinder.hud.text_contrast"),
                CinderConfigDefaults.TEXT_CONTRAST);
        boolean steadyDebugHud = readBool(props,
                "cinder.hud.steady_debug",
                CinderConfigDefaults.STEADY_DEBUG_HUD);
        int steadyDebugHudRefreshInterval = readInt(props,
                "cinder.hud.steady_debug_refresh_interval",
                CinderConfigDefaults.STEADY_DEBUG_HUD_REFRESH_INTERVAL,
                1, 200);
        boolean toastAdvancement = readBool(props,
                "cinder.toasts.advancement",
                CinderConfigDefaults.TOAST_ADVANCEMENT);
        boolean toastRecipe = readBool(props,
                "cinder.toasts.recipe",
                CinderConfigDefaults.TOAST_RECIPE);
        boolean toastSystem = readBool(props,
                "cinder.toasts.system",
                CinderConfigDefaults.TOAST_SYSTEM);
        boolean toastTutorial = readBool(props,
                "cinder.toasts.tutorial",
                CinderConfigDefaults.TOAST_TUTORIAL);
        boolean instantSneak = readBool(props,
                "cinder.extras.instant_sneak",
                CinderConfigDefaults.INSTANT_SNEAK);
        FullscreenMode fullscreenMode = FullscreenMode.parse(
                props.get("cinder.extras.fullscreen_mode"),
                CinderConfigDefaults.FULLSCREEN_MODE);
        boolean biomeColorsEnabled = readBool(props,
                "cinder.colors.biome.enabled",
                CinderConfigDefaults.BIOME_COLORS_ENABLED);
        boolean skyColorsEnabled = readBool(props,
                "cinder.colors.sky.enabled",
                CinderConfigDefaults.SKY_COLORS_ENABLED);
        return new CinderConfig(enabled, safeMode, verifyMode, ctmEnabled,
                ctmDebugLogging, duplicateTranslucentBackfaces,
                betterGrassMode,
                betterGrassIgnoreResourcePack,
                betterGrassGrassBlock, betterGrassSnowyGrassBlock,
                betterGrassDirtPath,
                betterGrassFarmland, betterGrassMycelium, betterGrassPodzol,
                betterGrassCrimsonNylium, betterGrassWarpedNylium,
                citEnabled, customGuiEnabled, customColorsEnabled,
                customSkyEnabled,
                naturalTexturesEnabled,
                betterSnowEnabled,
                customAnimationsEnabled,
                randomEntitiesEnabled,
                customEntityModelsEnabled,
                customAnimationMipmapDistance,
                detailsSkyEnabled,
                detailsSunEnabled,
                detailsMoonEnabled,
                detailsStarsEnabled,
                detailsCloudsEnabled,
                detailsCloudHeight,
                detailsRainSnowEnabled,
                detailsVignetteEnabled,
                animationsEnabled,
                animationWater,
                animationLava,
                animationFire,
                animationPortal,
                animationSculkSensor,
                animationBlocks,
                particlesEnabled,
                particleRainSplash,
                particleBlockBreak,
                particleBlockBreaking,
                particleExplosion,
                particleWater,
                particleSmoke,
                particlePotion,
                particlePortal,
                particleFlame,
                particleRedstone,
                particleDripping,
                particleFirework,
                fogEnabled,
                fogWater,
                fogLava,
                fogPowderSnow,
                fogAir,
                entityShadowsEnabled,
                entityNameTagsEnabled,
                entityPlayerNameTags,
                entityItemFrames,
                entityPaintings,
                entityPistonAnimations,
                entityBeaconBeam,
                entityLimitBeaconBeamHeight,
                entityEnchantingTableBook,
                showFps,
                showFpsExtended,
                showCoords,
                overlayCorner,
                textContrast,
                steadyDebugHud,
                steadyDebugHudRefreshInterval,
                toastAdvancement,
                toastRecipe,
                toastSystem,
                toastTutorial,
                instantSneak,
                fullscreenMode,
                biomeColorsEnabled,
                skyColorsEnabled);
    }

    private static boolean readBool(PropertiesFile props, String key, boolean fallback) {
        return OptifinePropertyParsers.parseBoolean(props.get(key),
                fallback);
    }

    private static int readInt(PropertiesFile props,
                               String key,
                               int fallback,
                               int min,
                               int max) {
        return OptifinePropertyParsers.parseIntOrDefault(props.get(key),
                fallback, min, max);
    }
}
