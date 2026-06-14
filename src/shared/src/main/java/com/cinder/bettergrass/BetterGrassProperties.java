package com.cinder.bettergrass;

import com.cinder.resource.NamespaceId;
import com.cinder.resource.PropertiesFile;

import java.io.IOException;
import java.io.Reader;
import java.util.Objects;

/**
 * Parser for OptiFine {@code optifine/bettergrass.properties}.
 *
 * <p>The parser is intentionally forgiving: unknown keys are ignored, invalid
 * booleans keep their defaults, and invalid texture identifiers keep their
 * vanilla defaults. Resource reload code can therefore publish a safe snapshot
 * even when a pack contains partial or malformed Better Grass options.
 *
 * <p>Threading: stateless and thread-safe.
 */
public final class BetterGrassProperties {

    private BetterGrassProperties() {
    }

    /**
     * Parses one UTF-8 properties body from a reader.
     */
    public static BetterGrassRules parse(Reader reader) throws IOException {
        Objects.requireNonNull(reader, "reader");
        return fromProperties(PropertiesFile.parse(reader));
    }

    /**
     * Converts an already parsed {@link PropertiesFile} into a rules snapshot.
     */
    public static BetterGrassRules fromProperties(PropertiesFile props) {
        Objects.requireNonNull(props, "props");
        return new BetterGrassRules(
                readBool(props, "grass", true),
                readBool(props, "grass.snow", true),
                readBool(props, "dirt_path", true),
                readBool(props, "farmland", true),
                readBool(props, "mycelium", true),
                readBool(props, "mycelium.snow", true),
                readBool(props, "podzol", true),
                readBool(props, "podzol.snow", true),
                readBool(props, "crimson_nylium", true),
                readBool(props, "warped_nylium", true),
                readBool(props, "grass.multilayer", false),
                readTexture(props, "texture.grass",
                        BetterGrassRules.GRASS_TEXTURE),
                readTexture(props, "texture.grass_side",
                        BetterGrassRules.GRASS_SIDE_TEXTURE),
                readTexture(props, "texture.dirt_path",
                        BetterGrassRules.DIRT_PATH_TEXTURE),
                readTexture(props, "texture.dirt_path_side",
                        BetterGrassRules.DIRT_PATH_SIDE_TEXTURE),
                readTexture(props, "texture.farmland",
                        BetterGrassRules.FARMLAND_TEXTURE),
                readTexture(props, "texture.farmland_side",
                        BetterGrassRules.FARMLAND_SIDE_TEXTURE),
                readTexture(props, "texture.mycelium",
                        BetterGrassRules.MYCELIUM_TEXTURE),
                readTexture(props, "texture.podzol",
                        BetterGrassRules.PODZOL_TEXTURE),
                readTexture(props, "texture.crimson_nylium",
                        BetterGrassRules.CRIMSON_NYLIUM_TEXTURE),
                readTexture(props, "texture.warped_nylium",
                        BetterGrassRules.WARPED_NYLIUM_TEXTURE),
                readTexture(props, "texture.snow",
                        BetterGrassRules.SNOW_TEXTURE));
    }

    private static boolean readBool(PropertiesFile props, String key,
                                    boolean fallback) {
        String value = props.get(key);
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        if (trimmed.equalsIgnoreCase("true") || trimmed.equals("1")) {
            return true;
        }
        if (trimmed.equalsIgnoreCase("false") || trimmed.equals("0")) {
            return false;
        }
        return fallback;
    }

    private static NamespaceId readTexture(PropertiesFile props, String key,
                                           NamespaceId fallback) {
        String value = props.get(key);
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        try {
            return NamespaceId.parse(stripPng(value.trim()));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String stripPng(String value) {
        return value.endsWith(".png")
                ? value.substring(0, value.length() - 4)
                : value;
    }
}
