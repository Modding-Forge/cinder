package com.cinder.config;

import java.util.Locale;

/**
 * Runtime mode for Cinder's Better Grass feature.
 *
 * <p>The names intentionally match the user-facing OptiFine modes while the
 * implementation remains Cinder-owned. The enum lives in shared config so all
 * loaders can expose the same option without depending on Sodium or Fabric.
 */
public enum BetterGrassMode {
    OFF,
    FAST,
    FANCY;

    /**
     * Parses a config value. Unknown values fall back to the supplied default.
     *
     * <p>Performance: not hot path; used only while reading config.
     */
    public static BetterGrassMode parse(String value,
                                        BetterGrassMode fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (BetterGrassMode mode : values()) {
            if (mode.name().equals(normalized)) {
                return mode;
            }
        }
        return fallback;
    }
}
