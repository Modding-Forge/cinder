package com.cinder.config;

/**
 * Screen corner used by Cinder's small HUD overlay.
 *
 * <p>Threading: immutable enum, read from immutable config snapshots.
 *
 * <p>Performance: not a hot-path allocation source.
 */
public enum OverlayCorner {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT;

    /**
     * Parses a persisted config value.
     */
    public static OverlayCorner parse(String raw, OverlayCorner fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return OverlayCorner.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
