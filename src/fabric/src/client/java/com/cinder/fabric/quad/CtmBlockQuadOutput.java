package com.cinder.fabric.quad;

import com.cinder.fabric.ctm.FabricNeighborView;
import com.cinder.quad.QuadContext;
import com.cinder.quad.QuadDecorators;
import com.cinder.quad.QuadRef;
import com.cinder.resource.NamespaceId;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric-side adapter that wraps a vanilla
 * {@link BlockQuadOutput} and runs every quad through
 * Cinder's {@link QuadDecorators} pipeline before the
 * original output is invoked.
 *
 * <h2>Sprite swap</h2>
 *
 * <p>The pipeline may return a replacement
 * {@link QuadRef}; this adapter forwards the replaced
 * {@link BakedQuad} (constructed via
 * {@link QuadRefSpriteSwapper#swap}) to the original
 * output. The wrapper is the only place where the
 * loader-agnostic {@link QuadRef} meets the Minecraft
 * {@code BakedQuad}.
 *
 * <h2>Threading</h2>
 *
 * <p>The wrapper is allocated per
 * {@code ModelBlockRenderer.tesselateBlock} call, which
 * happens on the section-build thread. The wrapper holds
 * a single reference to a per-block
 * {@link FabricNeighborView} that the renderer populates
 * lazily.
 *
 * <h2>Performance</h2>
 *
 * <p>The hot path is "no rule matches, no retexture". In
 * that case the pipeline returns the input quad and the
 * adapter forwards it without allocating a new
 * {@code BakedQuad}. The neighbour view is reused across
 * the 6 faces of the same block.
 */
public final class CtmBlockQuadOutput implements BlockQuadOutput {

    private static final Logger LOGGER =
            LoggerFactory.getLogger("cinder/ctm-output");

    private final BlockQuadOutput delegate;
    private final FabricNeighborView view;
    private final BlockPos pos;
    private final String blockId;

    private CtmBlockQuadOutput(BlockQuadOutput delegate,
                               FabricNeighborView view,
                               BlockPos pos,
                               String blockId) {
        this.delegate = delegate;
        this.view = view;
        this.pos = pos;
        this.blockId = blockId;
    }

    /**
     * Wraps a vanilla {@link BlockQuadOutput} for one
     * block. The neighbour view is populated for the 26
     * surrounding blocks once, then reused for the 6
     * faces.
     */
    public static BlockQuadOutput wrap(BlockQuadOutput delegate,
                                       BlockAndTintGetter level,
                                       BlockPos pos,
                                       BlockState blockState) {
        FabricNeighborView view = new FabricNeighborView(level);
        view.reset(pos);
        view.fillNeighbours();
        Identifier id = BuiltInRegistries.BLOCK.getKey(blockState.getBlock());
        String blockId = id == null ? "" : id.toString();
        return new CtmBlockQuadOutput(delegate, view, pos, blockId);
    }

    @Override
    public void put(float x, float y, float z,
                    BakedQuad quad, QuadInstance instance) {
        NamespaceId spriteId = new NamespaceId(
                quad.materialInfo().sprite().contents().name().getNamespace(),
                quad.materialInfo().sprite().contents().name().getPath());
        QuadContext ctx = new QuadContext(
                pos.getX(), pos.getY(), pos.getZ(),
                quad.direction().get3DDataValue(),
                blockId,
                spriteId,
                view);
        BakedQuadRef ref = new BakedQuadRef(quad);
        QuadRef result = QuadDecorators.apply(ref, ctx);
        if (result == ref) {
            // Hot path: no decorator replaced the quad.
            delegate.put(x, y, z, quad, instance);
            return;
        }
        if (result instanceof BakedQuadRef.PendingSwap pending) {
            // The CtmQuadDecorator's withSprite returned a
            // PendingSwap marker. Perform the UV re-mapping
            // using the block atlas.
            net.minecraft.client.renderer.texture.TextureAtlas atlas =
                    BlockAtlasProvider.blockAtlas();
            if (atlas == null) {
                delegate.put(x, y, z, quad, instance);
                return;
            }
            BakedQuad remapped = QuadRefSpriteSwapper.swap(
                    pending.original.quad(),
                    pending.newSprite, atlas);
            if (remapped == null) {
                delegate.put(x, y, z, quad, instance);
                return;
            }
            delegate.put(x, y, z, remapped, instance);
            return;
        }
        if (result instanceof BakedQuadRef swapped) {
            delegate.put(x, y, z, swapped.quad(), instance);
            return;
        }
        // A third-party decorator returned a non-BakedQuad
        // QuadRef (i.e. an adapter from a different
        // loader). We cannot honour it on Fabric; pass
        // through the original quad.
        LOGGER.debug(
                "[cinder] non-BakedQuadRef returned by decorator; "
                        + "passing through original quad");
        delegate.put(x, y, z, quad, instance);
    }

}
