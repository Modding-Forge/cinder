package com.argus.ctm;

import java.util.Objects;

/**
 * Precomputed runtime metadata for a {@link CtmRule}.
 *
 * <p>Purpose: captures stable method traits at resource-reload time so hot
 * renderer paths can route rules without re-deriving broad method categories
 * on every quad. The profile is intentionally semantic metadata; tile indices
 * and OptiFine-visible selection remain owned by {@link CtmSelector}.
 *
 * <p>Threading: immutable and safe to share as part of immutable rule
 * snapshots.
 *
 * <p>Performance: HOT PATH metadata. Allocation policy: one instance per
 * parsed rule, never per quad.
 */
public final class CtmRuleRuntimeProfile {

    private final CtmMethod method;
    private final CtmConnectivityProfile connectivity;
    private final boolean overlay;
    private final boolean layered;
    private final boolean random;
    private final boolean repeat;
    private final boolean fixed;

    private CtmRuleRuntimeProfile(CtmMethod method,
                                  CtmConnectivityProfile connectivity,
                                  boolean overlay,
                                  boolean layered,
                                  boolean random,
                                  boolean repeat,
                                  boolean fixed) {
        this.method = Objects.requireNonNull(method, "method");
        this.connectivity = Objects.requireNonNull(connectivity,
                "connectivity");
        this.overlay = overlay;
        this.layered = layered;
        this.random = random;
        this.repeat = repeat;
        this.fixed = fixed;
    }

    /**
     * Builds the profile for one parsed rule method.
     */
    public static CtmRuleRuntimeProfile of(CtmMethod method) {
        Objects.requireNonNull(method, "method");
        CtmConnectivityProfile connectivity = switch (method) {
            case FIXED, RANDOM, REPEAT,
                    OVERLAY_FIXED, OVERLAY_RANDOM, OVERLAY_REPEAT ->
                    CtmConnectivityProfile.NONE;
            case TOP -> CtmConnectivityProfile.TOP_1;
            case HORIZONTAL -> CtmConnectivityProfile.HORIZONTAL_2;
            case VERTICAL -> CtmConnectivityProfile.VERTICAL_2;
            case HORIZONTAL_VERTICAL, VERTICAL_HORIZONTAL ->
                    CtmConnectivityProfile.CARDINAL_4;
            case CTM, CTM_COMPACT, OVERLAY, OVERLAY_CTM ->
                    CtmConnectivityProfile.FULL_8;
        };
        boolean overlay = method.isOverlay();
        boolean layered = method == CtmMethod.HORIZONTAL_VERTICAL
                || method == CtmMethod.VERTICAL_HORIZONTAL;
        boolean random = method == CtmMethod.RANDOM
                || method == CtmMethod.OVERLAY_RANDOM;
        boolean repeat = method == CtmMethod.REPEAT
                || method == CtmMethod.OVERLAY_REPEAT;
        boolean fixed = method == CtmMethod.FIXED
                || method == CtmMethod.OVERLAY_FIXED;
        return new CtmRuleRuntimeProfile(method, connectivity, overlay,
                layered, random, repeat, fixed);
    }

    /**
     * Rule method this profile was derived from.
     */
    public CtmMethod method() {
        return method;
    }

    /**
     * Neighbour connectivity facts needed by the rule.
     */
    public CtmConnectivityProfile connectivity() {
        return connectivity;
    }

    public boolean isOverlay() {
        return overlay;
    }

    public boolean isLayered() {
        return layered;
    }

    public boolean isRandom() {
        return random;
    }

    public boolean isRepeat() {
        return repeat;
    }

    public boolean isFixed() {
        return fixed;
    }
}
