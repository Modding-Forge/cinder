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
                        + "safeMode={} verifyMode={} ctmEnabled={} "
                        + "ctmDebugLogging={} "
                        + "duplicateTranslucentBackfaces={} "
                        + "betterGrassMode={} "
                        + "betterGrassIgnoreResourcePack={} "
                        + "betterGrassBlocks=[grass={}, snowyGrass={}, "
                        + "dirtPath={}, farmland={}, mycelium={}, podzol={}, "
                        + "crimson={}, warped={}] citEnabled={} "
                        + "customGuiEnabled={} customColorsEnabled={} "
                        + "customAnimationsEnabled={} "
                        + "customAnimationMipmapDistance={}",
                Constants.MOD_NAME, file, cfg.enabled(),
                cfg.safeMode(), cfg.verifyMode(), cfg.ctmEnabled(),
                cfg.ctmDebugLogging(),
                cfg.duplicateTranslucentBackfaces(),
                cfg.betterGrassMode(),
                cfg.betterGrassIgnoreResourcePack(),
                cfg.betterGrassGrassBlock(),
                cfg.betterGrassSnowyGrassBlock(),
                cfg.betterGrassDirtPath(), cfg.betterGrassFarmland(),
                cfg.betterGrassMycelium(), cfg.betterGrassPodzol(),
                cfg.betterGrassCrimsonNylium(),
                cfg.betterGrassWarpedNylium(), cfg.citEnabled(),
                cfg.customGuiEnabled(), cfg.customColorsEnabled(),
                cfg.customAnimationsEnabled(),
                cfg.customAnimationMipmapDistance());
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
        props.setProperty("cinder.custom_animations.enabled",
                Boolean.toString(config.customAnimationsEnabled()));
        props.setProperty("cinder.custom_animations.mipmap_distance",
                Integer.toString(config.customAnimationMipmapDistance()));
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
