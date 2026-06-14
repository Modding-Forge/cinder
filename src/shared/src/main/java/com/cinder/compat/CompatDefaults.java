package com.cinder.compat;

import java.util.List;

/**
 * Default list of mod ids that Cinder probes for. The list is
 * loader-agnostic; loaders do not need to be aware of which
 * mods are on the list - they only implement
 * {@link CompatProbe}.
 *
 * <p>The list is intentionally short. Adding a mod here means
 * Cinder will at least <i>know</i> about it; the actual
 * degradation strategy is a Phase 5+ concern.
 */
public final class CompatDefaults {

    /** Sodium: alternate chunk renderer. */
    public static final String SODIUM = "sodium";

    /** Iris: OpenGL shader-pipeline mod. */
    public static final String IRIS = "iris";

    /** Continuity: alternate CTM implementation. */
    public static final String CONTINUITY = "continuity";

    /** ETF: entity texture features. */
    public static final String ETF = "entity_texture_features";

    /** EMF: entity model features. */
    public static final String EMF = "entity_model_features";

    /** CIT Resewn: item texture replacements. */
    public static final String CIT_RESEWN = "citresewn";

    /** Colormatic: colormap overrides. */
    public static final String COLORMATIC = "colormatic";

    private CompatDefaults() {
    }

    /**
     * Returns the list of mod ids that Cinder probes for. The
     * list is unmodifiable.
     */
    public static List<String> knownModIds() {
        return List.of(SODIUM, IRIS, CONTINUITY, ETF, EMF,
                CIT_RESEWN, COLORMATIC);
    }
}
