package com.cinder.quad;

import com.cinder.resource.NamespaceId;

import java.util.Objects;

/**
 * Loader-agnostic reference to a rendered quad. The actual
 * quad payload (vertex positions, UVs, normals, sprite) lives
 * in the loader-side adapter; this interface exposes only the
 * properties a decorator needs to decide whether to replace
 * the quad, plus the methods to obtain a replacement.
 *
 * <h2>Why an interface and not the raw {@code BakedQuad}?</h2>
 *
 * <p>The {@code shared/} module is forbidden from importing
 * Minecraft types. A decorator in a third-party mod (or in
 * Cinder itself) therefore cannot depend on
 * {@code net.minecraft.client.renderer.quad.BakedQuad}. The
 * adapter on the loader side wraps the BakedQuad in a
 * {@code QuadRef} and the decorator sees only the loader-
 * agnostic surface. The actual sprite swap happens via
 * {@link #withSprite(NamespaceId)}: the adapter implements
 * this method to clone the underlying BakedQuad with a
 * different sprite, returning a new {@code QuadRef}.
 *
 * <p>Decorators that do not need to mutate the quad (e.g.
 * statistics collectors) can return the input unchanged.
 *
 * <h2>Performance</h2>
 *
 * <p>The interface is small (six getters, one factory) and
 * the default {@code withSprite} throws. Adapter
 * implementations are expected to override {@code withSprite}
 * to return a new QuadRef with the new sprite; the
 * underlying vertex data is not copied unless the new
 * sprite has different UV bounds, which is the common case
 * (CTM swaps the entire sprite sheet, so the UVs change).
 *
 * <p>Allocation: at most one new QuadRef per quad that is
 * actually retextured. The hot path (no CTM match) returns
 * the same instance and allocates nothing.
 */
public interface QuadRef {

    /**
     * The current sprite of this quad. Same as the
     * {@code quad.materialInfo().sprite()} value in vanilla
     * terms.
     */
    NamespaceId sprite();

    /**
     * The block id of the block that emitted this quad.
     * Equivalent to the canonical id from the block registry.
     */
    String blockId();

    /**
     * Returns the quad's light emission (0-15). Used by
     * emissive-quad decorators (Tier 1) to decide whether a
     * face should bypass the CTM retexture.
     */
    int lightEmission();

    /**
     * Returns the quad's tint index. -1 means "no tint".
     * Decorators that need to preserve the tint pass it
     * through; this is a read-only view of the BakedQuad's
     * tint field.
     */
    int tintIndex();

    /**
     * Returns the AO shade (0.0 - 1.0). Read-only view of
     * the BakedQuad's shade field; not used by CTM itself
     * but by some future decorators (e.g. contrast
     * adjustments).
     */
    float aoShade();

    /**
     * Returns a new QuadRef that has the given sprite.
     * Implementations are expected to clone the underlying
     * quad and re-author the UVs for the new sprite's UV
     * bounds.
     *
     * <p>The default implementation throws: a vanilla
     * adapter (no retexturing) cannot honour this contract.
     * The Fabric/NeoForge adapter overrides it.
     */
    default QuadRef withSprite(NamespaceId newSprite) {
        Objects.requireNonNull(newSprite, "newSprite");
        throw new UnsupportedOperationException(
                "withSprite is not supported by this QuadRef implementation: "
                        + getClass().getName());
    }
}
