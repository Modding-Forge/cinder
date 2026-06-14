package com.cinder.ctm;

import java.util.Objects;

/**
 * One renderer-visible overlay tile selected for a block face.
 *
 * <p>Overlay resource-pack rules can stack: several independent overlay rules
 * may all match the same base face, and each tile keeps its original rule so
 * renderers can resolve the correct material metadata and tint source.
 *
 * <h2>Threading</h2>
 *
 * <p>Immutable and safe to share between section-build worker threads.
 *
 * <h2>Performance</h2>
 *
 * <p>Performance: HOT PATH. Allocation policy: one small record per selected
 * overlay tile only when an overlay rule matches.
 */
public record CtmOverlayTile(CtmRule rule, int tileIndex) {

    /**
     * Creates one selected overlay tile.
     */
    public CtmOverlayTile {
        Objects.requireNonNull(rule, "rule");
    }
}
