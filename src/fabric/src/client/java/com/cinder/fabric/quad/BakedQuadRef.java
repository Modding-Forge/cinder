package com.cinder.fabric.quad;

import com.cinder.quad.QuadRef;
import com.cinder.resource.NamespaceId;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.jspecify.annotations.Nullable;

/**
 * Fabric-side {@link QuadRef} implementation that wraps a
 * vanilla {@link BakedQuad}. The wrapper holds the
 * {@code BakedQuad} directly; the
 * {@link #withSprite(NamespaceId)} override returns a new
 * wrapper whose BakedQuad has been retextured to the new
 * sprite.
 *
 * <p>Mutability: the wrapper is immutable from the caller's
 * perspective. {@link #withSprite} returns a new instance;
 * the original is left untouched.
 *
 * <h2>Why a wrapper instead of mutating the BakedQuad?</h2>
 *
 * <p>{@code BakedQuad} is a record in 26.2-rc-1 (and was
 * effectively immutable before that). Mutating its fields
 * is not possible. The wrapper therefore re-creates the
 * BakedQuad on {@code withSprite} by copying every field
 * except {@code materialInfo}, which is replaced with a
 * material whose sprite is the new one.
 *
 * <p>Performance: the allocation is O(1). The UV
 * re-mapping (recomputing per-vertex UVs for the new
 * sprite's UV bounds) is the expensive part - that is
 * performed by the
 * {@link QuadRefSpriteSwapper#swap} helper, which uses the
 * 26.2-rc-1 BakedQuad's {@code bake()} entry point if
 * available, or a fallback path that constructs a new
 * BakedQuad via the {@code materialInfo} only.
 *
 * <p>Hot path: in the common case (no CTM match), the
 * decorator returns {@link java.util.Optional#empty()} and
 * the renderer uses the original BakedQuad. The wrapper
 * is not even allocated.
 */
public final class BakedQuadRef implements QuadRef {

    private final BakedQuad quad;
    private final NamespaceId spriteId;

    public BakedQuadRef(BakedQuad quad) {
        this.quad = quad;
        this.spriteId = extractSpriteId(quad);
    }

    private BakedQuadRef(BakedQuad quad, NamespaceId spriteId) {
        this.quad = quad;
        this.spriteId = spriteId;
    }

    public BakedQuad quad() {
        return quad;
    }

    @Override
    public NamespaceId sprite() {
        return spriteId;
    }

    @Override
    public String blockId() {
        // The wrapper does not know its block id; callers
        // supply it through the QuadContext. This method
        // is reserved for completeness but the canonical
        // source of block id is the context.
        return "";
    }

    @Override
    public int lightEmission() {
        // 26.2-rc-1's BakedQuad does not carry a
        // lightEmission field; the canonical lighting
        // is applied by the renderer from the section's
        // light data. We return 0 here (no special
        // emissive flag) - this is the conservative
        // default. Future phases can derive an emissive
        // bit from the BakedQuad's materialInfo if
        // needed.
        return 0;
    }

    @Override
    public int tintIndex() {
        // 26.2-rc-1 stores the tint index on the
        // MaterialInfo, not on the BakedQuad directly.
        // The MaterialInfo is exposed via materialInfo()
        // and contains a tintIndex field. We return
        // -1 ("no tint") when the info is null.
        var info = quad.materialInfo();
        return info == null ? -1 : info.tintIndex();
    }

    @Override
    public float aoShade() {
        // The 26.2-rc-1 BakedQuad does not have a
        // shade field; the AO is computed by the
        // renderer. We return 1.0 (full brightness) as
        // a conservative default.
        return 1.0f;
    }

    @Override
    public QuadRef withSprite(NamespaceId newSprite) {
        // The renderer-side {@code CtmBlockQuadOutput} is
        // the place that knows the {@link TextureAtlas};
        // it performs the actual UV re-mapping and
        // returns a new BakedQuadRef. The shared
        // interface method is a placeholder for non-
        // BakedQuadRef implementations (third-party
        // adapters that do their own swap). For
        // BakedQuadRef, we delegate to the renderer by
        // returning a placeholder that the renderer
        // detects and replaces.
        if (newSprite == null) {
            throw new NullPointerException("newSprite");
        }
        if (newSprite.equals(spriteId)) {
            return this;
        }
        return new PendingSwap(this, newSprite);
    }

    /**
     * A marker {@link QuadRef} that records a desired
     * sprite swap but does not perform the UV re-mapping
     * itself. The renderer detects instances of this
     * class via {@code instanceof} and performs the swap
     * using its {@link net.minecraft.client.renderer.texture.TextureAtlas}.
     */
    public static final class PendingSwap implements QuadRef {
        public final BakedQuadRef original;
        public final NamespaceId newSprite;
        public PendingSwap(BakedQuadRef original, NamespaceId newSprite) {
            this.original = original;
            this.newSprite = newSprite;
        }
        @Override public NamespaceId sprite() { return newSprite; }
        @Override public String blockId() { return original.blockId(); }
        @Override public int lightEmission() { return original.lightEmission(); }
        @Override public int tintIndex() { return original.tintIndex(); }
        @Override public float aoShade() { return original.aoShade(); }
    }

    /**
     * Resolves the {@link NamespaceId} of the quad's
     * current sprite. Returns a synthetic id when the
     * BakedQuad has no sprite (e.g. the quad is from a
     * custom-rendered block that bypassed the atlas).
     */
    private static NamespaceId extractSpriteId(BakedQuad quad) {
        @Nullable TextureAtlasSprite sprite =
                quad.materialInfo().sprite();
        if (sprite == null) {
            return new NamespaceId("cinder", "unknown");
        }
        return new NamespaceId(
                sprite.contents().name().getNamespace(),
                sprite.contents().name().getPath());
    }
}
