package com.cinder.ctm;

/**
 * Compact CTM material payload for one rendered face.
 *
 * <p>This is the value a backend-native renderer can attach to terrain
 * geometry: as a vertex attribute, as an index into a sidecar section buffer,
 * or as an entry in a per-section material stream. Material id {@code 0}
 * always means vanilla/pass-through.
 *
 * <h2>Threading</h2>
 *
 * <p>Instances are immutable and may be shared between worker and render
 * threads.
 *
 * <h2>Performance</h2>
 *
 * <p>Performance: HOT PATH. Allocation policy: callers may use the static
 * {@link #PASS_THROUGH} singleton for no-CTM faces; concrete payloads are small
 * records that can be stack-allocated by the JVM.
 */
public record CtmMaterialPayload(
        int primaryMaterialId,
        int secondaryMaterialId,
        int flags) {

    /** No CTM material should be applied. */
    public static final CtmMaterialPayload PASS_THROUGH =
            new CtmMaterialPayload(
                    CtmMaterialTable.PASS_THROUGH_MATERIAL_ID,
                    CtmMaterialTable.PASS_THROUGH_MATERIAL_ID,
                    0);

    /**
     * Canonical constructor: validates material id range.
     */
    public CtmMaterialPayload {
        if (primaryMaterialId < 0 || secondaryMaterialId < 0) {
            throw new IllegalArgumentException(
                    "material ids must be non-negative");
        }
    }

    /**
     * Returns true when the payload references at least one CTM material.
     */
    public boolean hasCtmMaterial() {
        return primaryMaterialId != CtmMaterialTable.PASS_THROUGH_MATERIAL_ID
                || secondaryMaterialId != CtmMaterialTable.PASS_THROUGH_MATERIAL_ID;
    }

    /**
     * Packs the payload into a 32-bit value for sidecar experiments.
     *
     * <p>Layout:
     * <ul>
     *   <li>bits 0..15: primary material id</li>
     *   <li>bits 16..23: secondary material id, MVP-limited</li>
     *   <li>bits 24..31: low eight flag bits</li>
     * </ul>
     *
     * <p>This is an MVP packing format. The active shader path samples only the
     * primary material, so the primary field is intentionally wider than the
     * secondary field. Renderers that need more layered materials should use a
     * wider sidecar entry.
     */
    public int pack32() {
        if (primaryMaterialId > 0xFFFF) {
            throw new IllegalStateException(
                    "primary material id exceeds 16-bit payload capacity");
        }
        if (secondaryMaterialId > 0xFF) {
            throw new IllegalStateException(
                    "secondary material id exceeds 8-bit payload capacity");
        }
        return (primaryMaterialId & 0xFFFF)
                | ((secondaryMaterialId & 0xFF) << 16)
                | ((flags & 0xFF) << 24);
    }
}
