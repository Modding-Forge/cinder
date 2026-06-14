package com.cinder.client.sodium;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import org.jspecify.annotations.Nullable;

/**
 * Chooses terrain layers for Sodium CTM output.
 *
 * <p>Continuity and OptiFine keep CTM selection separate from final terrain
 * layer selection. They let vanilla/model material data and optional
 * {@code optifine/block.properties} decide whether a block is rendered as
 * solid, cutout, or translucent. Cinder does not yet implement the full custom
 * block layer file, so this policy is the narrow place for compatibility
 * decisions that affect Sodium's Vulkan terrain passes.
 *
 * <h2>Threading</h2>
 *
 * <p>Stateless and safe for concurrent section builds.
 *
 * <h2>Performance</h2>
 *
 * <p>Performance: HOT PATH. Allocation policy: no allocations. Keep checks
 * primitive or simple string comparisons until a reload-time block-id table is
 * introduced.
 */
public final class CtmSodiumLayerPolicy {

    /**
     * Returns the layer for replacement CTM quads.
     *
     * <p>PureBDcraft normal glass CTM is visually frame-like and relies on
     * normal terrain back-face/depth behaviour. Leaving the replacement in the
     * translucent pass can make the far-side frame visible through the near
     * face in Sodium's sorted translucent path. This mirrors the observed
     * layer-level behaviour of the reference renderers without copying their
     * implementation.
     */
    public @Nullable ChunkSectionLayer replacementLayer(
            String blockId,
            @Nullable ChunkSectionLayer sourceLayer) {
        if (sourceLayer == ChunkSectionLayer.TRANSLUCENT
                && "minecraft:glass".equals(blockId)) {
            return ChunkSectionLayer.CUTOUT;
        }
        return sourceLayer;
    }

    /**
     * Returns the layer for overlay CTM quads.
     *
     * <p>Overlays are emitted as additional geometry. They should stay
     * translucent only when the source quad was translucent; otherwise they use
     * cutout so alpha masks remain cheap and depth-stable.
     */
    public ChunkSectionLayer overlayLayer(
            @Nullable ChunkSectionLayer sourceLayer) {
        return sourceLayer == ChunkSectionLayer.TRANSLUCENT
                ? ChunkSectionLayer.TRANSLUCENT
                : ChunkSectionLayer.CUTOUT;
    }
}
