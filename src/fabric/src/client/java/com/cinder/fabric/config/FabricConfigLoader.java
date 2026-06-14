package com.cinder.fabric.config;

import com.cinder.Constants;
import com.cinder.config.CinderConfig;
import com.cinder.config.CinderConfigDefaults;
import com.cinder.config.CinderConfigHolder;
import com.cinder.config.CinderConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Fabric-side config loader. Reads
 * {@code <config-dir>/cinder.properties} at startup and on
 * every config reload.
 *
 * <p>The config directory is the Fabric-config standard path
 * (typically {@code .minecraft/config/} for the client). If
 * the file does not exist, the defaults are used and a debug
 * message is logged. Parse errors fall back to the defaults
 * with a warning.
 *
 * <p>This class lives in the {@code client} source set because
 * the config is only consulted by client-side features (the
 * CTM engine, the renderer). The dedicated server does not
 * need it.
 */
public final class FabricConfigLoader {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(Constants.MOD_ID + "/config");

    private static final String FILE_NAME = "cinder.properties";

    private FabricConfigLoader() {
    }

    /**
     * Loads the config from the given directory and installs it
     * in the {@link CinderConfigHolder}. Returns the loaded
     * config (also returned by {@link CinderConfigHolder#get()}
     * after the call).
     */
    public static CinderConfig loadAndInstall(Path configDir) {
        CinderConfig cfg = load(configDir);
        CinderConfigHolder.replace(cfg);
        return cfg;
    }

    /**
     * Loads the config from the given directory. Returns the
     * defaults if the file is missing or malformed.
     */
    public static CinderConfig load(Path configDir) {
        if (configDir == null) {
            return CinderConfigDefaults.defaults();
        }
        Path file = configDir.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            LOGGER.debug("[{}] no {} found, using defaults",
                    Constants.MOD_NAME, file);
            return CinderConfigDefaults.defaults();
        }
        try (InputStream in = Files.newInputStream(file)) {
            CinderConfig cfg = CinderConfigLoader.load(in);
            LOGGER.info("[{}] loaded config from {}: enabled={} "
                        + "ctm={} betterGrass={} cit={} customGui={} "
                        + "customColors={} customSky={} naturalTextures={} "
                        + "betterSnow={} customAnimations={} "
                        + "randomEntities={} cem={} detailsSky={}",
                Constants.MOD_NAME, file, cfg.enabled(), cfg.ctmEnabled(),
                cfg.betterGrassMode(), cfg.citEnabled(),
                cfg.customGuiEnabled(), cfg.customColorsEnabled(),
                cfg.customSkyEnabled(), cfg.naturalTexturesEnabled(),
                cfg.betterSnowEnabled(), cfg.customAnimationsEnabled(),
                cfg.randomEntitiesEnabled(), cfg.customEntityModelsEnabled(),
                cfg.detailsSkyEnabled());
            return cfg;
        } catch (Exception e) {
            LOGGER.warn("[{}] failed to read config from {}; "
                            + "falling back to defaults: {}",
                    Constants.MOD_NAME, file, e.getMessage());
            return CinderConfigDefaults.defaults();
        }
    }

    /**
     * Writes the given config to the Fabric config directory.
     *
     * <p>Thread expectations: called from client UI/config code,
     * not from the renderer hot path. The active holder is not
     * changed here; callers should install the same immutable
     * snapshot before or after saving.
     */
    public static void save(Path configDir, CinderConfig config) {
        if (configDir == null || config == null) {
            return;
        }
        Path file = configDir.resolve(FILE_NAME);
        Properties props = new Properties();
        props.setProperty("cinder.enabled",
                Boolean.toString(config.enabled()));
        props.setProperty("cinder.safe_mode",
                Boolean.toString(config.safeMode()));
        props.setProperty("cinder.verify_mode",
                Boolean.toString(config.verifyMode()));
        props.setProperty("cinder.ctm.enabled",
                Boolean.toString(config.ctmEnabled()));
        props.setProperty("cinder.ctm.debug_logging",
                Boolean.toString(config.ctmDebugLogging()));
        props.setProperty("cinder.general.duplicate_translucent_backfaces",
                Boolean.toString(config.duplicateTranslucentBackfaces()));
        props.setProperty("cinder.better_grass.mode",
                config.betterGrassMode().name().toLowerCase());
        props.setProperty("cinder.better_grass.ignore_resource_pack",
                Boolean.toString(config.betterGrassIgnoreResourcePack()));
        props.setProperty("cinder.better_grass.grass_block",
                Boolean.toString(config.betterGrassGrassBlock()));
        props.setProperty("cinder.better_grass.snowy_grass_block",
                Boolean.toString(config.betterGrassSnowyGrassBlock()));
        props.setProperty("cinder.better_grass.dirt_path",
                Boolean.toString(config.betterGrassDirtPath()));
        props.setProperty("cinder.better_grass.farmland",
                Boolean.toString(config.betterGrassFarmland()));
        props.setProperty("cinder.better_grass.mycelium",
                Boolean.toString(config.betterGrassMycelium()));
        props.setProperty("cinder.better_grass.podzol",
                Boolean.toString(config.betterGrassPodzol()));
        props.setProperty("cinder.better_grass.crimson_nylium",
                Boolean.toString(config.betterGrassCrimsonNylium()));
        props.setProperty("cinder.better_grass.warped_nylium",
                Boolean.toString(config.betterGrassWarpedNylium()));
        props.setProperty("cinder.cit.enabled",
                Boolean.toString(config.citEnabled()));
        props.setProperty("cinder.custom_gui.enabled",
                Boolean.toString(config.customGuiEnabled()));
        props.setProperty("cinder.custom_colors.enabled",
                Boolean.toString(config.customColorsEnabled()));
        props.setProperty("cinder.custom_sky.enabled",
                Boolean.toString(config.customSkyEnabled()));
        props.setProperty("cinder.natural_textures.enabled",
                Boolean.toString(config.naturalTexturesEnabled()));
        props.setProperty("cinder.better_snow.enabled",
                Boolean.toString(config.betterSnowEnabled()));
        props.setProperty("cinder.custom_animations.enabled",
                Boolean.toString(config.customAnimationsEnabled()));
        props.setProperty("cinder.random_entities.enabled",
                Boolean.toString(config.randomEntitiesEnabled()));
        props.setProperty("cinder.custom_entity_models.enabled",
                Boolean.toString(config.customEntityModelsEnabled()));
        props.setProperty("cinder.custom_animations.mipmap_distance",
                Integer.toString(config.customAnimationMipmapDistance()));
        props.setProperty("cinder.details.sky.enabled",
                Boolean.toString(config.detailsSkyEnabled()));
        props.setProperty("cinder.details.sun.enabled",
                Boolean.toString(config.detailsSunEnabled()));
        props.setProperty("cinder.details.moon.enabled",
                Boolean.toString(config.detailsMoonEnabled()));
        props.setProperty("cinder.details.stars.enabled",
                Boolean.toString(config.detailsStarsEnabled()));
        props.setProperty("cinder.details.clouds.enabled",
                Boolean.toString(config.detailsCloudsEnabled()));
        props.setProperty("cinder.details.cloud_height",
                Integer.toString(config.detailsCloudHeight()));
        props.setProperty("cinder.details.rain_snow.enabled",
                Boolean.toString(config.detailsRainSnowEnabled()));
        props.setProperty("cinder.details.vignette.enabled",
                Boolean.toString(config.detailsVignetteEnabled()));
        props.setProperty("cinder.animations.enabled",
                Boolean.toString(config.animationsEnabled()));
        props.setProperty("cinder.animations.water",
                Boolean.toString(config.animationWater()));
        props.setProperty("cinder.animations.lava",
                Boolean.toString(config.animationLava()));
        props.setProperty("cinder.animations.fire",
                Boolean.toString(config.animationFire()));
        props.setProperty("cinder.animations.portal",
                Boolean.toString(config.animationPortal()));
        props.setProperty("cinder.animations.sculk_sensor",
                Boolean.toString(config.animationSculkSensor()));
        props.setProperty("cinder.animations.blocks",
                Boolean.toString(config.animationBlocks()));
        props.setProperty("cinder.particles.enabled",
                Boolean.toString(config.particlesEnabled()));
        props.setProperty("cinder.particles.rain_splash",
                Boolean.toString(config.particleRainSplash()));
        props.setProperty("cinder.particles.block_break",
                Boolean.toString(config.particleBlockBreak()));
        props.setProperty("cinder.particles.block_breaking",
                Boolean.toString(config.particleBlockBreaking()));
        props.setProperty("cinder.particles.explosion",
                Boolean.toString(config.particleExplosion()));
        props.setProperty("cinder.particles.water",
                Boolean.toString(config.particleWater()));
        props.setProperty("cinder.particles.smoke",
                Boolean.toString(config.particleSmoke()));
        props.setProperty("cinder.particles.potion",
                Boolean.toString(config.particlePotion()));
        props.setProperty("cinder.particles.portal",
                Boolean.toString(config.particlePortal()));
        props.setProperty("cinder.particles.flame",
                Boolean.toString(config.particleFlame()));
        props.setProperty("cinder.particles.redstone",
                Boolean.toString(config.particleRedstone()));
        props.setProperty("cinder.particles.dripping",
                Boolean.toString(config.particleDripping()));
        props.setProperty("cinder.particles.firework",
                Boolean.toString(config.particleFirework()));
        props.setProperty("cinder.fog.enabled",
                Boolean.toString(config.fogEnabled()));
        props.setProperty("cinder.fog.water",
                Boolean.toString(config.fogWater()));
        props.setProperty("cinder.fog.lava",
                Boolean.toString(config.fogLava()));
        props.setProperty("cinder.fog.powder_snow",
                Boolean.toString(config.fogPowderSnow()));
        props.setProperty("cinder.fog.air",
                Boolean.toString(config.fogAir()));
        props.setProperty("cinder.entities.shadows.enabled",
                Boolean.toString(config.entityShadowsEnabled()));
        props.setProperty("cinder.entities.name_tags.enabled",
                Boolean.toString(config.entityNameTagsEnabled()));
        props.setProperty("cinder.entities.player_name_tags",
                Boolean.toString(config.entityPlayerNameTags()));
        props.setProperty("cinder.entities.item_frames",
                Boolean.toString(config.entityItemFrames()));
        props.setProperty("cinder.entities.paintings",
                Boolean.toString(config.entityPaintings()));
        props.setProperty("cinder.entities.piston_animations",
                Boolean.toString(config.entityPistonAnimations()));
        props.setProperty("cinder.entities.beacon_beam",
                Boolean.toString(config.entityBeaconBeam()));
        props.setProperty("cinder.entities.limit_beacon_beam_height",
                Boolean.toString(config.entityLimitBeaconBeamHeight()));
        props.setProperty("cinder.entities.enchanting_table_book",
                Boolean.toString(config.entityEnchantingTableBook()));
        props.setProperty("cinder.hud.fps",
                Boolean.toString(config.showFps()));
        props.setProperty("cinder.hud.fps_extended",
                Boolean.toString(config.showFpsExtended()));
        props.setProperty("cinder.hud.coords",
                Boolean.toString(config.showCoords()));
        props.setProperty("cinder.hud.corner",
                config.overlayCorner().name().toLowerCase());
        props.setProperty("cinder.hud.text_contrast",
                config.textContrast().name().toLowerCase());
        props.setProperty("cinder.hud.steady_debug",
                Boolean.toString(config.steadyDebugHud()));
        props.setProperty("cinder.hud.steady_debug_refresh_interval",
                Integer.toString(config.steadyDebugHudRefreshInterval()));
        props.setProperty("cinder.toasts.advancement",
                Boolean.toString(config.toastAdvancement()));
        props.setProperty("cinder.toasts.recipe",
                Boolean.toString(config.toastRecipe()));
        props.setProperty("cinder.toasts.system",
                Boolean.toString(config.toastSystem()));
        props.setProperty("cinder.toasts.tutorial",
                Boolean.toString(config.toastTutorial()));
        props.setProperty("cinder.extras.instant_sneak",
                Boolean.toString(config.instantSneak()));
        props.setProperty("cinder.extras.fullscreen_mode",
                config.fullscreenMode().name().toLowerCase());
        props.setProperty("cinder.colors.biome.enabled",
                Boolean.toString(config.biomeColorsEnabled()));
        props.setProperty("cinder.colors.sky.enabled",
                Boolean.toString(config.skyColorsEnabled()));
        try {
            Files.createDirectories(configDir);
            try (OutputStream out = Files.newOutputStream(file)) {
                props.store(out, "Cinder configuration");
            }
            LOGGER.info("[{}] saved config to {}", Constants.MOD_NAME, file);
        } catch (Exception e) {
            LOGGER.warn("[{}] failed to save config to {}: {}",
                    Constants.MOD_NAME, file, e.getMessage());
        }
    }
}
