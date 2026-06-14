package com.cinder.ctm;

import com.cinder.resource.NamespaceId;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable hot-path prefilter for CTM render lookups.
 *
 * <p>The index is built together with a {@link CtmRuleSet}. It answers cheap
 * "can this quad possibly match" questions before renderer integrations build
 * neighbour views or run the full selector.
 *
 * <p>Threading: immutable and safe to share across section-build threads.
 *
 * <p>Performance: HOT PATH. Allocation policy: no allocation during reads;
 * lookups are map probes plus bit tests.
 */
public final class CtmRenderIndex {

    private final Map<NamespaceId, CandidateFlags> bySprite;
    private final Map<String, CandidateFlags> byBlock;
    private final boolean hasOverlayCandidates;

    CtmRenderIndex(Map<NamespaceId, CandidateFlags> bySprite,
                   Map<String, CandidateFlags> byBlock,
                   boolean hasOverlayCandidates) {
        this.bySprite = Map.copyOf(bySprite);
        this.byBlock = Map.copyOf(byBlock);
        this.hasOverlayCandidates = hasOverlayCandidates;
    }

    /**
     * Returns {@code true} when either the sprite or block key has any rule
     * candidate for the requested face.
     */
    public boolean hasCandidate(NamespaceId sprite, String blockId, int face) {
        return hasSpriteCandidate(sprite, face) || hasBlockCandidate(blockId, face);
    }

    /**
     * Returns {@code true} when the sprite has any rule candidate for a face.
     */
    public boolean hasSpriteCandidate(NamespaceId sprite, int face) {
        CandidateFlags flags = sprite == null ? null : bySprite.get(sprite);
        return flags != null && flags.matchesFace(face);
    }

    /**
     * Returns {@code true} when the block has any rule candidate for a face.
     */
    public boolean hasBlockCandidate(String blockId, int face) {
        CandidateFlags flags = blockId == null ? null : byBlock.get(blockId);
        return flags != null && flags.matchesFace(face);
    }

    /**
     * Returns {@code true} when this snapshot contains any overlay CTM rule.
     */
    public boolean hasOverlayCandidates() {
        return hasOverlayCandidates;
    }

    static CandidateFlags flagsFor(CtmRule rule) {
        Objects.requireNonNull(rule, "rule");
        return new CandidateFlags(rule.facesMask(), rule.method().isOverlay());
    }

    /**
     * Compact face/overlay metadata for one indexed key.
     */
    static final class CandidateFlags {
        private static final int ALL_FACES = 0x3F;

        private final int faceMask;
        private final boolean hasOverlay;

        CandidateFlags(int ruleFacesMask, boolean hasOverlay) {
            this.faceMask = ruleFacesMask == 0
                    ? ALL_FACES
                    : ruleFacesMask & ALL_FACES;
            this.hasOverlay = hasOverlay;
        }

        CandidateFlags merge(CandidateFlags other) {
            return new CandidateFlags(this.faceMask | other.faceMask,
                    this.hasOverlay || other.hasOverlay);
        }

        boolean matchesFace(int face) {
            return face >= Faces.DOWN && face <= Faces.EAST
                    && (faceMask & (1 << face)) != 0;
        }

        boolean hasOverlay() {
            return hasOverlay;
        }
    }
}
