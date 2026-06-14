package com.cinder.customsky;

import java.util.Locale;

/**
 * OptiFine custom-sky blend names in a loader-neutral form.
 *
 * <p>Threading: enum constants are immutable. Performance: parsed at reload
 * time only.
 */
public enum CustomSkyBlendMode {
    ADD,
    ALPHA,
    MULTIPLY,
    SCREEN,
    REPLACE,
    SUBTRACT,
    DODGE,
    BURN,
    OVERLAY;

    /**
     * Parses a blend name. Unknown names fall back to the documented default.
     */
    public static CustomSkyBlendMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return ADD;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "alpha" -> ALPHA;
            case "multiply" -> MULTIPLY;
            case "screen" -> SCREEN;
            case "replace" -> REPLACE;
            case "subtract" -> SUBTRACT;
            case "dodge" -> DODGE;
            case "burn" -> BURN;
            case "overlay" -> OVERLAY;
            default -> ADD;
        };
    }
}
