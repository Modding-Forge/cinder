package com.cinder.emissive;

import com.cinder.resource.PropertiesFile;

import java.io.IOException;
import java.io.Reader;
import java.util.Objects;

/**
 * Parser for OptiFine {@code optifine/emissive.properties}.
 *
 * <p>Threading: stateless and thread-safe. The parser is intentionally
 * forgiving; absent or malformed values fall back to the OptiFine-style
 * default suffix.
 */
public final class EmissiveProperties {

    public static final String DEFAULT_SUFFIX = "_e";

    private EmissiveProperties() {
    }

    /**
     * Parses an emissive properties file.
     */
    public static EmissiveSettings parse(Reader reader) throws IOException {
        Objects.requireNonNull(reader, "reader");
        return fromProperties(PropertiesFile.parse(reader));
    }

    /**
     * Converts a parsed properties file into immutable settings.
     */
    public static EmissiveSettings fromProperties(PropertiesFile props) {
        Objects.requireNonNull(props, "props");
        String suffix = props.get("suffix.emissive");
        if (suffix == null || suffix.trim().isEmpty()) {
            suffix = DEFAULT_SUFFIX;
        } else {
            suffix = suffix.trim();
        }
        return new EmissiveSettings(suffix);
    }
}
