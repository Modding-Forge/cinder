package com.argus.ctm;

/**
 * Caller-owned CTM candidate storage for renderer hot paths.
 *
 * <p>The render index fills this object with face-filtered sprite and block
 * candidates. Resolver callers can then evaluate those arrays without probing
 * the index a second time.
 *
 * <p>Threading: not thread-safe. Keep one instance per renderer worker or
 * pipeline object.
 *
 * <p>Performance: HOT PATH. Allocation policy: allocated once by the renderer
 * and reused for each quad.
 */
public final class CtmCandidateScratch {

    public static final int NO_WORK = 0;
    public static final int REPLACEMENT_ONLY = 1;
    public static final int OVERLAY_ONLY = 2;
    public static final int BOTH = REPLACEMENT_ONLY | OVERLAY_ONLY;

    private CtmRule[] spriteRules = CtmRenderIndex.noRules();
    private CtmRule[] blockRules = CtmRenderIndex.noRules();
    private int workFlags;

    public void clear() {
        spriteRules = CtmRenderIndex.noRules();
        blockRules = CtmRenderIndex.noRules();
        workFlags = NO_WORK;
    }

    void set(CtmRule[] spriteRules, CtmRule[] blockRules, int workFlags) {
        this.spriteRules = spriteRules;
        this.blockRules = blockRules;
        this.workFlags = workFlags;
    }

    public CtmRule[] spriteRules() {
        return spriteRules;
    }

    public CtmRule[] blockRules() {
        return blockRules;
    }

    public int workFlags() {
        return workFlags;
    }

    public boolean hasWork() {
        return workFlags != NO_WORK;
    }

    public boolean hasReplacements() {
        return (workFlags & REPLACEMENT_ONLY) != 0;
    }

    public boolean hasOverlays() {
        return (workFlags & OVERLAY_ONLY) != 0;
    }
}
