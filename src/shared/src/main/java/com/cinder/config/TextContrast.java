package com.cinder.config;

/**
 * Contrast mode for Cinder-owned HUD text.
 *
 * <p>Threading: immutable enum, read from immutable config snapshots.
 */
public enum TextContrast {
    SHADOW,
    BACKDROP;

    /**
     * Parses a persisted config value.
     */
    public static TextContrast parse(String raw, TextContrast fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return TextContrast.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
