package com.argus.client.sodium;

import com.argus.ctm.CtmRenderScratch;
import com.argus.ctm.CtmRenderSelection;
import com.argus.ctm.CtmRule;
import org.jspecify.annotations.Nullable;

/**
 * Cached CTM resolver output for one block face and source sprite.
 *
 * <p>Purpose: lets all quads emitted for the same block/face/sprite consume
 * the same shared CTM selection instead of re-running the selector. The object
 * deliberately stores shared CTM selections, not Sodium sprites; material and
 * layer realization remains quad-specific.
 *
 * <p>Threading: owned by one {@link ArgusCtmBlockPlan} on one section-build
 * worker call stack.
 *
 * <p>Performance: HOT PATH. Allocation policy: created only on the first
 * matching face/sprite lookup for a rendered block model and then reused by
 * later quads in that model.
 */
public final class ArgusCtmFaceSpriteResult {

    static final ArgusCtmFaceSpriteResult NO_WORK =
            new ArgusCtmFaceSpriteResult(null, new CtmRule[0], new int[0], 0);

    private final @Nullable CtmRenderSelection replacement;
    private final CtmRule[] overlayRules;
    private final int[] overlayTileIndices;
    private final int overlayCount;

    private ArgusCtmFaceSpriteResult(@Nullable CtmRenderSelection replacement,
                                     CtmRule[] overlayRules,
                                     int[] overlayTileIndices,
                                     int overlayCount) {
        this.replacement = replacement;
        this.overlayRules = overlayRules;
        this.overlayTileIndices = overlayTileIndices;
        this.overlayCount = overlayCount;
    }

    static ArgusCtmFaceSpriteResult copyOf(CtmRenderScratch scratch) {
        if (!scratch.hasWork()) {
            return NO_WORK;
        }
        int count = scratch.overlayCount();
        CtmRule[] rules = count == 0 ? new CtmRule[0] : new CtmRule[count];
        int[] tileIndices = count == 0 ? new int[0] : new int[count];
        for (int i = 0; i < count; i++) {
            rules[i] = scratch.overlayRule(i);
            tileIndices[i] = scratch.overlayTileIndex(i);
        }
        return new ArgusCtmFaceSpriteResult(scratch.replacement(), rules,
                tileIndices, count);
    }

    public boolean hasWork() {
        return replacement != null || overlayCount > 0;
    }

    public @Nullable CtmRenderSelection replacement() {
        return replacement;
    }

    public boolean hasOverlays() {
        return overlayCount > 0;
    }

    public int overlayCount() {
        return overlayCount;
    }

    public CtmRule overlayRule(int index) {
        if (index < 0 || index >= overlayCount) {
            throw new IndexOutOfBoundsException(index);
        }
        return overlayRules[index];
    }

    public int overlayTileIndex(int index) {
        if (index < 0 || index >= overlayCount) {
            throw new IndexOutOfBoundsException(index);
        }
        return overlayTileIndices[index];
    }
}
