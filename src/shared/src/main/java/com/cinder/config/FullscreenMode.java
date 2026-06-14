package com.cinder.config;

/**
 * Cinder-owned fullscreen helper mode.
 *
 * <p>{@link #WINDOWED} leaves Mojang's fullscreen behaviour untouched.
 * {@link #BORDERLESS} is a conservative borderless-window helper. Exclusive
 * fullscreen remains vanilla and is intentionally not represented here.
 */
public enum FullscreenMode {
    WINDOWED,
    BORDERLESS;

    /**
     * Parses a persisted config value.
     */
    public static FullscreenMode parse(String raw, FullscreenMode fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return FullscreenMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
