package com.cinder.customcolors;

/**
 * OptiFine-compatible colormap sampling mode.
 *
 * <p>Threading: enum constants are immutable and safe to share.
 * Performance: parsed once during resource reload; hot paths compare enum
 * references.
 */
public enum ColormapFormat {
    VANILLA,
    GRID,
    FIXED;

    /**
     * Parses a format token, falling back when the token is absent or unknown.
     */
    public static ColormapFormat parse(String raw, ColormapFormat fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "vanilla" -> VANILLA;
            case "grid" -> GRID;
            case "fixed" -> FIXED;
            default -> fallback;
        };
    }
}
