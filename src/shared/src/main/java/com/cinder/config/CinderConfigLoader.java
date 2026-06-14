package com.cinder.config;

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
        boolean customAnimationsEnabled = readBool(props,
                "cinder.custom_animations.enabled",
                CinderConfigDefaults.CUSTOM_ANIMATIONS_ENABLED);
        int customAnimationMipmapDistance = readInt(props,
                "cinder.custom_animations.mipmap_distance",
                CinderConfigDefaults.CUSTOM_ANIMATION_MIPMAP_DISTANCE,
                0, 4);
        return new CinderConfig(enabled, safeMode, verifyMode, ctmEnabled,
                ctmDebugLogging, duplicateTranslucentBackfaces,
                betterGrassMode,
                betterGrassIgnoreResourcePack,
                betterGrassGrassBlock, betterGrassSnowyGrassBlock,
                betterGrassDirtPath,
                betterGrassFarmland, betterGrassMycelium, betterGrassPodzol,
                betterGrassCrimsonNylium, betterGrassWarpedNylium,
                citEnabled, customGuiEnabled, customColorsEnabled,
                customAnimationsEnabled,
                customAnimationMipmapDistance);
    }

    private static boolean readBool(PropertiesFile props, String key, boolean fallback) {
        String v = props.get(key);
        if (v == null) {
            return fallback;
        }
        String trimmed = v.trim();
        if (trimmed.equalsIgnoreCase("true") || trimmed.equals("1")) {
            return true;
        }
        if (trimmed.equalsIgnoreCase("false") || trimmed.equals("0")) {
            return false;
        }
        return fallback;
    }

    private static int readInt(PropertiesFile props,
                               String key,
                               int fallback,
                               int min,
                               int max) {
        String v = props.get(key);
        if (v == null) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(v.trim());
            if (parsed < min || parsed > max) {
                return fallback;
            }
            return parsed;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
