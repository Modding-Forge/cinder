package com.cinder.fabric.quad;

import com.cinder.resource.NamespaceId;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.jspecify.annotations.Nullable;

/**
 * Helper that creates a new {@link BakedQuad} with the same
 * geometry as the input but a different sprite.
 *
 * <h2>UV re-mapping</h2>
 *
 * <p>The input quad stores 4 vertex positions and 4 packed
 * UV pairs. Each packed UV is a 64-bit long with two
 * {@code float} values ({@code u, v}) packed via
 * {@link UVPair#pack(float, float)}. The output quad must
 * have the same 4 positions and the same 4 UVs, but the UV
 * values must be re-mapped from the source sprite's
 * (u0, v0, u1, v1) to the target sprite's (u0, v0, u1, v1).
 *
 * <p>The mapping is a linear transformation per axis:
 * <pre>
 * u_out = u0_new + (u_in - u0_old) * (u1_new - u0_new) / (u1_old - u0_old)
 * v_out = v0_new + (v_in - v0_old) * (v1_new - v0_new) / (v1_old - v0_old)
 * </pre>
 * When the source sprite has zero-width or zero-height
 * bounds (degenerate), the mapping for that axis is
 * undefined; the swapper bails out and returns {@code null}.
 *
 * <h2>Material info</h2>
 *
 * <p>The output quad carries a new
 * {@link BakedQuad.MaterialInfo} with the new sprite but
 * the same layer, render-type, tint index, shade flag, and
 * light emission. All other MaterialInfo fields are
 * preserved from the source quad.
 *
 * <h2>Failure mode</h2>
 *
 * <p>The swapper returns {@code null} when the swap cannot
 * be performed: the new sprite is not in the atlas, the
 * source sprite has zero bounds, or the target sprite is
 * the same as the source. The caller (the renderer) is
 * expected to fall back to the original quad in that
 * case.
 */
public final class QuadRefSpriteSwapper {

    private QuadRefSpriteSwapper() {
    }

    /**
     * Returns a new {@link BakedQuad} with the new sprite,
     * or {@code null} if the swap cannot be performed.
     */
    @Nullable
    public static BakedQuad swap(BakedQuad in,
                                 NamespaceId newSprite,
                                 TextureAtlas atlas) {
        if (in == null || newSprite == null || atlas == null) {
            return null;
        }
        TextureAtlasSprite currentSprite = in.materialInfo().sprite();
        if (currentSprite == null) {
            return null;
        }
        if (sameSprite(currentSprite, newSprite)) {
            return in;
        }
        TextureAtlasSprite target = lookupSprite(atlas, newSprite);
        if (target == null) {
            return null;
        }
        return remap(in, currentSprite, target);
    }

    private static BakedQuad remap(BakedQuad in,
                                   TextureAtlasSprite currentSprite,
                                   TextureAtlasSprite target) {
        float srcU0 = currentSprite.getU0();
        float srcU1 = currentSprite.getU1();
        float srcV0 = currentSprite.getV0();
        float srcV1 = currentSprite.getV1();
        float tgtU0 = target.getU0();
        float tgtU1 = target.getU1();
        float tgtV0 = target.getV0();
        float tgtV1 = target.getV1();
        float srcDu = srcU1 - srcU0;
        float srcDv = srcV1 - srcV0;
        if (srcDu == 0.0f || srcDv == 0.0f) {
            return null;
        }
        float scaleU = (tgtU1 - tgtU0) / srcDu;
        float scaleV = (tgtV1 - tgtV0) / srcDv;
        long newUv0 = remapVertex(in.packedUV(0),
                srcU0, srcV0, scaleU, scaleV, tgtU0, tgtV0);
        long newUv1 = remapVertex(in.packedUV(1),
                srcU0, srcV0, scaleU, scaleV, tgtU0, tgtV0);
        long newUv2 = remapVertex(in.packedUV(2),
                srcU0, srcV0, scaleU, scaleV, tgtU0, tgtV0);
        long newUv3 = remapVertex(in.packedUV(3),
                srcU0, srcV0, scaleU, scaleV, tgtU0, tgtV0);
        BakedQuad.MaterialInfo oldInfo = in.materialInfo();
        BakedQuad.MaterialInfo newInfo = new BakedQuad.MaterialInfo(
                target,
                oldInfo.layer(),
                oldInfo.itemRenderType(),
                oldInfo.tintIndex(),
                oldInfo.shade(),
                oldInfo.lightEmission());
        return new BakedQuad(
                in.position0(), in.position1(), in.position2(), in.position3(),
                newUv0, newUv1, newUv2, newUv3,
                in.direction(),
                newInfo);
    }

    private static long remapVertex(long packedUv,
                                    float srcU0, float srcV0,
                                    float scaleU, float scaleV,
                                    float tgtU0, float tgtV0) {
        float u = UVPair.unpackU(packedUv);
        float v = UVPair.unpackV(packedUv);
        float newU = tgtU0 + (u - srcU0) * scaleU;
        float newV = tgtV0 + (v - srcV0) * scaleV;
        return UVPair.pack(newU, newV);
    }

    private static boolean sameSprite(TextureAtlasSprite current,
                                       NamespaceId target) {
        net.minecraft.resources.Identifier id =
                net.minecraft.resources.Identifier.fromNamespaceAndPath(
                        target.namespace(), target.path());
        return id.equals(current.contents().name());
    }

    @Nullable
    private static TextureAtlasSprite lookupSprite(TextureAtlas atlas,
                                                   NamespaceId id) {
        net.minecraft.resources.Identifier mcId =
                net.minecraft.resources.Identifier.fromNamespaceAndPath(
                        id.namespace(), id.path());
        return atlas.getSprite(mcId);
    }
}
