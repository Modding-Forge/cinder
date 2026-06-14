package com.cinder.ctm;

import java.util.List;
import java.util.Objects;

/**
 * Backend-neutral CTM render work selected for one block face.
 *
 * <p>A plan separates replacement from overlay compositing explicitly. This
 * avoids overloading {@link CtmRenderSelection} with "sometimes this is an
 * overlay stack" semantics while keeping individual selections reusable for
 * debug output and material lookup.
 *
 * <p>Threading: immutable and safe to share between renderer worker threads.
 *
 * <p>Performance: HOT PATH. Allocation policy: one small object only when a
 * face has CTM work.
 */
public record CtmRenderPlan(
        CtmRenderSelection replacement,
        List<CtmOverlayTile> overlays) {

    public CtmRenderPlan {
        overlays = List.copyOf(Objects.requireNonNull(overlays, "overlays"));
    }

    /**
     * Creates a plan with only replacement work.
     */
    public static CtmRenderPlan replacement(CtmRenderSelection replacement) {
        return new CtmRenderPlan(
                Objects.requireNonNull(replacement, "replacement"),
                List.of());
    }

    /**
     * Creates a plan with only overlay work.
     */
    public static CtmRenderPlan overlays(List<CtmOverlayTile> overlays) {
        return new CtmRenderPlan(null, overlays);
    }

    /**
     * Creates a plan with replacement and overlay work.
     */
    public static CtmRenderPlan of(CtmRenderSelection replacement,
                                   List<CtmOverlayTile> overlays) {
        return new CtmRenderPlan(replacement, overlays);
    }

    /**
     * Returns {@code true} when the face needs any renderer work.
     */
    public boolean hasWork() {
        return hasReplacement() || hasOverlays();
    }

    /**
     * Returns {@code true} when a non-overlay CTM selection should replace the
     * base face.
     */
    public boolean hasReplacement() {
        return replacement != null;
    }

    /**
     * Returns {@code true} when overlay quads should be emitted over the base
     * face.
     */
    public boolean hasOverlays() {
        return !overlays.isEmpty();
    }

}
