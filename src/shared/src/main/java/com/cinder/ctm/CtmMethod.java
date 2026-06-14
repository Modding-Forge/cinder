package com.cinder.ctm;

/**
 * CTM methods supported by Cinder. The numeric values are
 * <b>NOT</b> chosen to match OptiFine's closed-source constants; we
 * use a clean-room ordering because we are not implementing a clone
 * of OptiFine.
 *
 * <p>The set of methods and their names is, however, exactly the
 * behaviour contract documented in
 * {@code optifine/OptiFineDoc/doc/ctm.properties} and that resource
 * packs depend on. Method names are matched case-sensitively in
 * property files.
 */
public enum CtmMethod {
    /** Full 8-way CTM with 47 tiles. */
    CTM("ctm"),
    /** Compact 8-way CTM with 5-16 tiles plus optional overrides. */
    CTM_COMPACT("ctm_compact"),
    /** Horizontal-only connections (bookshelves). */
    HORIZONTAL("horizontal"),
    /** Vertical-only connections. */
    VERTICAL("vertical"),
    /** Top-only connections (sandstone). */
    TOP("top"),
    /** Random variant per face, seeded by position+side. */
    RANDOM("random"),
    /** Repeating pattern with given {@code width} and {@code height}. */
    REPEAT("repeat"),
    /** A single fixed tile. */
    FIXED("fixed"),
    /** Horizontal first, vertical layered second. */
    HORIZONTAL_VERTICAL("horizontal+vertical"),
    /** Vertical first, horizontal layered second. */
    VERTICAL_HORIZONTAL("vertical+horizontal"),
    /** Overlay: extra layer over another tile selection. */
    OVERLAY("overlay"),
    /** Overlay with a CTM-style connection check. */
    OVERLAY_CTM("overlay_ctm"),
    /** Overlay with random variant. */
    OVERLAY_RANDOM("overlay_random"),
    /** Overlay with repeating pattern. */
    OVERLAY_REPEAT("overlay_repeat"),
    /** Overlay with a single fixed tile. */
    OVERLAY_FIXED("overlay_fixed");

    private final String key;

    CtmMethod(String key) {
        this.key = key;
    }

    /** The literal string used in {@code .properties} files. */
    public String key() {
        return key;
    }

    /**
     * Parses a method key, returning {@code null} for an unknown
     * value. The caller decides whether to error or skip the
     * rule.
     */
    public static CtmMethod fromKey(String key) {
        for (CtmMethod m : values()) {
            if (m.key.equals(key)) {
                return m;
            }
        }
        return null;
    }

    /**
     * Returns {@code true} for the methods that are overlay variants.
     */
    public boolean isOverlay() {
        return this == OVERLAY
                || this == OVERLAY_CTM
                || this == OVERLAY_RANDOM
                || this == OVERLAY_REPEAT
                || this == OVERLAY_FIXED;
    }
}
