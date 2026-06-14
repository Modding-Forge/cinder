package com.cinder.client.mixin;

import com.cinder.client.sodium.CtmSodiumQuadPlan;
import com.cinder.client.sodium.CtmSodiumQuadProcessor;
import com.cinder.client.sodium.CinderSodiumEmissive;
import com.cinder.client.sodium.CinderSodiumBetterGrass;
import com.cinder.client.sodium.CinderSodiumBetterSnow;
import com.cinder.client.sodium.CinderSodiumNaturalTextures;
import com.cinder.config.CinderConfig;
import com.cinder.config.CinderConfigHolder;
import com.cinder.fabric.animation.CustomAnimationRuntime;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext;
import net.caffeinemc.mods.sodium.client.render.model.EncodingFormat;
import net.caffeinemc.mods.sodium.client.render.model.MutableQuadViewImpl;
import net.caffeinemc.mods.sodium.client.render.texture.SpriteFinderCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.TriState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Target: Sodium {@link BlockRenderer#processQuad(MutableQuadViewImpl)}.
 *
 * <p>Purpose: realizes Cinder CTM selections directly in Sodium's terrain
 * meshing path. Replacement CTM remaps the current quad before Sodium applies
 * tint, AO, light, and buffering. Overlay CTM emits additional overlay quads
 * through the same Sodium processing method so overlays receive normal Sodium
 * lighting instead of Cinder's old shader-side approximation.
 *
 * <p>Preserved behaviour: non-full-face quads, unmatched quads, and disabled
 * CTM pass through unchanged. The mixin does not change Sodium culling,
 * occlusion, chunk sorting, buffer layout, or shader binding.
 *
 * <p>Compatibility: this mixin is gated by {@link CinderClientMixinPlugin} and
 * only applies when Sodium is loaded. It replaces Cinder's old Mojang terrain
 * sidecar hooks under Sodium, leaving atlas/resource hooks intact.
 *
 * <p>Risk: medium. The hook sits in a Sodium hot path, but it mutates only the
 * current editable quad or emits bounded overlay copies.
 */
@Mixin(value = BlockRenderer.class, remap = false)
public abstract class SodiumBlockRendererCtmMixin
        extends AbstractBlockRenderContext {

    @Unique
    private static final float CINDER_OVERLAY_OFFSET = 0.0001F;

    @Shadow
    protected abstract void processQuad(MutableQuadViewImpl quad);

    @Shadow
    public abstract void renderModel(BlockStateModel model, BlockState state,
                                     BlockPos pos, BlockPos origin);

    @Unique
    private @Nullable CtmSodiumQuadProcessor cinder$processor;

    @Unique
    private @Nullable CinderSodiumBetterGrass cinder$betterGrass;

    @Unique
    private @Nullable CinderSodiumBetterSnow cinder$betterSnow;

    @Unique
    private @Nullable CinderSodiumNaturalTextures cinder$naturalTextures;

    @Unique
    private @Nullable CinderSodiumEmissive cinder$emissive;

    @Unique
    private @Nullable CtmSodiumQuadPlan cinder$plan;

    @Unique
    private @Nullable MutableQuadViewImpl cinder$overlaySource;

    @Unique
    private @Nullable MutableQuadViewImpl cinder$overlayQuad;

    @Unique
    private boolean cinder$emittingOverlay;

    @Unique
    private boolean cinder$hasOverlayPlan;

    @Inject(method = "processQuad", at = @At("HEAD"), cancellable = true)
    private void cinder$prepareCtmQuad(MutableQuadViewImpl quad,
                                       CallbackInfo ci) {
        if (cinder$emittingOverlay) {
            cinder$naturalTextures().apply(quad, state, pos);
            return;
        }
        CustomAnimationRuntime.markTerrainSprite(cinder$sourceSprite(quad));
        cinder$betterGrass().applySnowSideRemap(quad, level, state, pos);
        cinder$betterGrass().apply(quad, level, state, pos);
        cinder$hasOverlayPlan = cinder$processor().prepare(
                quad, level, state, pos, cinder$overlaySource(),
                cinder$plan());
        cinder$hasOverlayPlan = cinder$emissive().prepare(
                quad, cinder$overlaySource(), cinder$plan())
                || cinder$hasOverlayPlan;
        CtmSodiumQuadPlan plan = cinder$plan();
        if (plan.discardsOriginal()) {
            cinder$hasOverlayPlan = false;
            ci.cancel();
            return;
        }
        if (!plan.hasReplacements()) {
            cinder$naturalTextures().apply(quad, state, pos);
            return;
        }
        cinder$hasOverlayPlan = false;
        cinder$emittingOverlay = true;
        try {
            MutableQuadViewImpl source = cinder$overlaySource();
            MutableQuadViewImpl replacement = cinder$overlayQuad();
            TextureAtlasSprite sourceSprite = cinder$sourceSprite(source);
            if (sourceSprite != null) {
                boolean duplicateBackface =
                        cinder$duplicatesTranslucentBackface(source);
                for (int quadrant = 0;
                     quadrant < plan.replacementCount();
                     quadrant++) {
                    replacement.copyFrom(source);
                    if (plan.shapesReplacementQuadrants()) {
                        cinder$shapeReplacementQuadrant(
                                source, replacement, sourceSprite, quadrant);
                    }
                    CtmSodiumQuadProcessor.remapSprite(
                            replacement,
                            sourceSprite,
                            plan.replacementSprite(quadrant));
                    replacement.setRenderType(plan.replacementLayer());
                    processQuad(replacement);
                    if (duplicateBackface) {
                        cinder$flipReplacementBackface(replacement);
                        processQuad(replacement);
                    }
                }
            }
            if (plan.hasOverlays()) {
                cinder$emitOverlayPlan(plan);
            }
        } finally {
            cinder$emittingOverlay = false;
        }
        ci.cancel();
    }

    @Inject(method = "processQuad", at = @At("RETURN"))
    private void cinder$emitCtmOverlays(MutableQuadViewImpl quad,
                                        CallbackInfo ci) {
        if (cinder$emittingOverlay) {
            return;
        }
        boolean hasOverlayPlan = cinder$hasOverlayPlan;
        boolean duplicateBackface =
                cinder$duplicatesTranslucentBackface(quad);
        if (!hasOverlayPlan && !duplicateBackface) {
            return;
        }
        cinder$hasOverlayPlan = false;
        CtmSodiumQuadPlan plan = cinder$plan();
        cinder$emittingOverlay = true;
        try {
            if (hasOverlayPlan) {
                MutableQuadViewImpl overlaySource = cinder$overlaySource();
                TextureAtlasSprite sourceSprite =
                        cinder$sourceSprite(overlaySource);
                if (sourceSprite != null) {
                    cinder$emitOverlayPlan(plan);
                }
            }
            if (duplicateBackface) {
                cinder$emitDuplicateTranslucentBackface(quad);
            }
        } finally {
            cinder$emittingOverlay = false;
        }
    }

    @Inject(method = "renderModel", at = @At("TAIL"))
    private void cinder$emitBetterSnowLayer(BlockStateModel model,
                                            BlockState state,
                                            BlockPos pos,
                                            BlockPos origin,
                                            CallbackInfo ci) {
        if (cinder$emittingOverlay) {
            return;
        }
        cinder$emittingOverlay = true;
        try {
            if (!cinder$betterSnow().shouldRenderSnowLayer(level, state,
                    pos)) {
                return;
            }
            BlockState snowState = Blocks.SNOW.defaultBlockState();
            BlockStateModel snowModel = Minecraft.getInstance()
                    .getModelManager()
                    .getBlockStateModelSet()
                    .get(snowState);
            renderModel(snowModel, snowState, pos, origin);
        } finally {
            cinder$emittingOverlay = false;
        }
    }

    @Unique
    private void cinder$emitOverlayPlan(CtmSodiumQuadPlan plan) {
        MutableQuadViewImpl overlaySource = cinder$overlaySource();
        MutableQuadViewImpl overlayQuad = cinder$overlayQuad();
        TextureAtlasSprite sourceSprite = cinder$sourceSprite(overlaySource);
        for (int i = 0; i < plan.overlayCount(); i++) {
            overlayQuad.copyFrom(overlaySource);
            TextureAtlasSprite overlaySprite = plan.overlaySprite(i);
            if (plan.overlayRemapsFromSource(i) && sourceSprite != null) {
                CtmSodiumQuadProcessor.remapSprite(
                        overlayQuad, sourceSprite, overlaySprite);
            } else {
                cinder$mapOverlaySprite(overlayQuad, overlaySprite);
            }
            overlayQuad.setRenderType(plan.overlayLayer(i));
            overlayQuad.setTintIndex(-1);
            int color = plan.overlayColor(i);
            for (int vertex = 0; vertex < 4; vertex++) {
                overlayQuad.setColor(vertex, color);
            }
            int light = plan.overlayLight(i);
            if (light >= 0) {
                overlayQuad.setEmissive(true);
                overlayQuad.setDiffuseShade(false);
                overlayQuad.setAmbientOcclusion(TriState.FALSE);
                for (int vertex = 0; vertex < 4; vertex++) {
                    overlayQuad.setLight(vertex, light);
                }
            }
            cinder$alignTopOverlayToSourceHeight(overlayQuad);
            cinder$offsetOverlay(overlayQuad, i + 1);
            processQuad(overlayQuad);
        }
    }

    @Unique
    private void cinder$emitDuplicateTranslucentBackface(
            MutableQuadViewImpl source) {
        MutableQuadViewImpl backface = cinder$overlayQuad();
        backface.copyFrom(source);
        cinder$flipReplacementBackface(backface);
        processQuad(backface);
    }

    @Unique
    private CtmSodiumQuadProcessor cinder$processor() {
        CtmSodiumQuadProcessor processor = cinder$processor;
        if (processor == null) {
            processor = new CtmSodiumQuadProcessor();
            cinder$processor = processor;
        }
        return processor;
    }

    @Unique
    private CinderSodiumBetterGrass cinder$betterGrass() {
        CinderSodiumBetterGrass betterGrass = cinder$betterGrass;
        if (betterGrass == null) {
            betterGrass = new CinderSodiumBetterGrass();
            cinder$betterGrass = betterGrass;
        }
        return betterGrass;
    }

    @Unique
    private CinderSodiumBetterSnow cinder$betterSnow() {
        CinderSodiumBetterSnow betterSnow = cinder$betterSnow;
        if (betterSnow == null) {
            betterSnow = new CinderSodiumBetterSnow();
            cinder$betterSnow = betterSnow;
        }
        return betterSnow;
    }

    @Unique
    private CinderSodiumNaturalTextures cinder$naturalTextures() {
        CinderSodiumNaturalTextures naturalTextures = cinder$naturalTextures;
        if (naturalTextures == null) {
            naturalTextures = new CinderSodiumNaturalTextures();
            cinder$naturalTextures = naturalTextures;
        }
        return naturalTextures;
    }

    @Unique
    private CinderSodiumEmissive cinder$emissive() {
        CinderSodiumEmissive emissive = cinder$emissive;
        if (emissive == null) {
            emissive = new CinderSodiumEmissive();
            cinder$emissive = emissive;
        }
        return emissive;
    }

    @Unique
    private CtmSodiumQuadPlan cinder$plan() {
        CtmSodiumQuadPlan plan = cinder$plan;
        if (plan == null) {
            plan = new CtmSodiumQuadPlan();
            cinder$plan = plan;
        }
        return plan;
    }

    @Unique
    private MutableQuadViewImpl cinder$overlaySource() {
        MutableQuadViewImpl quad = cinder$overlaySource;
        if (quad == null) {
            quad = cinder$newScratchQuad();
            cinder$overlaySource = quad;
        }
        return quad;
    }

    @Unique
    private MutableQuadViewImpl cinder$overlayQuad() {
        MutableQuadViewImpl quad = cinder$overlayQuad;
        if (quad == null) {
            quad = cinder$newScratchQuad();
            cinder$overlayQuad = quad;
        }
        return quad;
    }

    @Unique
    private static MutableQuadViewImpl cinder$newScratchQuad() {
        return new MutableQuadViewImpl() {
            {
                data = new int[EncodingFormat.TOTAL_STRIDE];
                clear();
            }

            @Override
            public void emitDirectly() {
                // Scratch quads are submitted explicitly through processQuad.
            }
        };
    }

    @Unique
    private static TextureAtlasSprite cinder$sourceSprite(
            MutableQuadViewImpl quad) {
        TextureAtlasSprite sprite = quad.cachedSprite();
        if (sprite != null) {
            return sprite;
        }
        return quad.sprite(SpriteFinderCache.forBlockAtlas());
    }

    @Unique
    private static boolean cinder$duplicatesTranslucentBackface(
            MutableQuadViewImpl source) {
        if (source.getRenderType() != ChunkSectionLayer.TRANSLUCENT) {
            return false;
        }
        CinderConfig config = CinderConfigHolder.get();
        return config.enabled() && config.duplicateTranslucentBackfaces();
    }

    @Unique
    private static void cinder$alignTopOverlayToSourceHeight(
            MutableQuadViewImpl quad) {
        Direction direction = quad.getCullFace();
        if (direction == null) {
            direction = quad.getNominalFace();
        }
        if (direction == null) {
            direction = quad.getLightFace();
        }
        if (direction != Direction.UP) {
            return;
        }
        float maxY = Float.NEGATIVE_INFINITY;
        for (int vertex = 0; vertex < 4; vertex++) {
            maxY = Math.max(maxY, quad.getY(vertex));
        }
        float lift = 1.0F - maxY;
        if (lift > 0.0F && lift <= 0.25F) {
            quad.translate(0.0F, lift, 0.0F);
        }
    }

    @Unique
    private static void cinder$offsetOverlay(MutableQuadViewImpl quad,
                                             int layer) {
        Direction direction = quad.getCullFace();
        if (direction == null) {
            direction = quad.getNominalFace();
        }
        if (direction == null) {
            direction = quad.getLightFace();
        }
        if (direction == null) {
            return;
        }
        float offset = CINDER_OVERLAY_OFFSET * layer;
        float dx = direction.getStepX() * offset;
        float dy = direction.getStepY() * offset;
        float dz = direction.getStepZ() * offset;
        if (dx != 0.0F || dy != 0.0F || dz != 0.0F) {
            quad.translate(dx, dy, dz);
        }
    }

    @Unique
    private static void cinder$mapOverlaySprite(MutableQuadViewImpl quad,
                                                TextureAtlasSprite sprite) {
        Direction direction = quad.getCullFace();
        if (direction == null) {
            direction = quad.getNominalFace();
        }
        if (direction == null) {
            direction = quad.getLightFace();
        }
        if (direction == null) {
            TextureAtlasSprite source = cinder$sourceSprite(quad);
            if (source == null) {
                return;
            }
            CtmSodiumQuadProcessor.remapSprite(
                    quad, source, sprite);
            return;
        }
        float u0 = sprite.getU0();
        float v0 = sprite.getV0();
        float du = sprite.getU1() - u0;
        float dv = sprite.getV1() - v0;
        for (int vertex = 0; vertex < 4; vertex++) {
            float h = cinder$overlayLocalH(quad, vertex, direction);
            float v = cinder$overlayLocalV(quad, vertex, direction);
            quad.setUV(vertex, u0 + du * h, v0 + dv * v);
        }
        quad.cachedSprite(sprite);
    }

    @Unique
    private static float cinder$overlayLocalH(MutableQuadViewImpl quad,
                                              int vertex,
                                              Direction direction) {
        return switch (direction) {
            case DOWN, UP, SOUTH -> cinder$clamp01(quad.getX(vertex));
            case NORTH -> cinder$clamp01(1.0F - quad.getX(vertex));
            case WEST -> cinder$clamp01(quad.getZ(vertex));
            case EAST -> cinder$clamp01(1.0F - quad.getZ(vertex));
        };
    }

    @Unique
    private static float cinder$overlayLocalV(MutableQuadViewImpl quad,
                                              int vertex,
                                              Direction direction) {
        return switch (direction) {
            case UP -> cinder$clamp01(quad.getZ(vertex));
            case DOWN -> cinder$clamp01(1.0F - quad.getZ(vertex));
            case NORTH, SOUTH, WEST, EAST ->
                    cinder$clamp01(1.0F - quad.getY(vertex));
        };
    }

    @Unique
    private static void cinder$flipReplacementBackface(
            MutableQuadViewImpl quad) {
        float x0 = quad.getX(0);
        float y0 = quad.getY(0);
        float z0 = quad.getZ(0);
        float u0 = quad.getTexU(0);
        float v0 = quad.getTexV(0);
        int c0 = quad.baseColor(0);
        int l0 = quad.getLight(0);

        float x1 = quad.getX(1);
        float y1 = quad.getY(1);
        float z1 = quad.getZ(1);
        float u1 = quad.getTexU(1);
        float v1 = quad.getTexV(1);
        int c1 = quad.baseColor(1);
        int l1 = quad.getLight(1);

        float x2 = quad.getX(2);
        float y2 = quad.getY(2);
        float z2 = quad.getZ(2);
        float u2 = quad.getTexU(2);
        float v2 = quad.getTexV(2);
        int c2 = quad.baseColor(2);
        int l2 = quad.getLight(2);

        float x3 = quad.getX(3);
        float y3 = quad.getY(3);
        float z3 = quad.getZ(3);
        float u3 = quad.getTexU(3);
        float v3 = quad.getTexV(3);
        int c3 = quad.baseColor(3);
        int l3 = quad.getLight(3);

        quad.setPos(0, x3, y3, z3);
        quad.setUV(0, u3, v3);
        quad.setColor(0, c3);
        quad.setLight(0, l3);

        quad.setPos(1, x2, y2, z2);
        quad.setUV(1, u2, v2);
        quad.setColor(1, c2);
        quad.setLight(1, l2);

        quad.setPos(2, x1, y1, z1);
        quad.setUV(2, u1, v1);
        quad.setColor(2, c1);
        quad.setLight(2, l1);

        quad.setPos(3, x0, y0, z0);
        quad.setUV(3, u0, v0);
        quad.setColor(3, c0);
        quad.setLight(3, l0);

        Direction face = quad.getCullFace();
        if (face == null) {
            face = quad.getNominalFace();
        }
        if (face != null) {
            Direction backface = face.getOpposite();
            quad.setCullFace(backface);
            quad.setNormal(0, backface.getStepX(), backface.getStepY(),
                    backface.getStepZ());
            quad.setNormal(1, backface.getStepX(), backface.getStepY(),
                    backface.getStepZ());
            quad.setNormal(2, backface.getStepX(), backface.getStepY(),
                    backface.getStepZ());
            quad.setNormal(3, backface.getStepX(), backface.getStepY(),
                    backface.getStepZ());
        }
    }

    @Unique
    private static void cinder$shapeReplacementQuadrant(
            MutableQuadViewImpl source,
            MutableQuadViewImpl out,
            TextureAtlasSprite sourceSprite,
            int quadrant) {
        float h0 = (quadrant == 1 || quadrant == 3) ? 0.5F : 0.0F;
        float h1 = h0 + 0.5F;
        float v0 = quadrant >= 2 ? 0.5F : 0.0F;
        float v1 = v0 + 0.5F;

        int topLeft = -1;
        int topRight = -1;
        int bottomLeft = -1;
        int bottomRight = -1;
        for (int i = 0; i < 4; i++) {
            float h = cinder$localTextureU(source, i, sourceSprite);
            float v = cinder$localTextureV(source, i, sourceSprite);
            boolean right = h >= 0.5F;
            boolean bottom = v >= 0.5F;
            if (right) {
                if (bottom) {
                    bottomRight = i;
                } else {
                    topRight = i;
                }
            } else if (bottom) {
                bottomLeft = i;
            } else {
                topLeft = i;
            }
        }
        if (topLeft < 0 || topRight < 0
                || bottomLeft < 0 || bottomRight < 0) {
            return;
        }
        for (int i = 0; i < 4; i++) {
            float h = cinder$localTextureU(source, i, sourceSprite) >= 0.5F
                    ? h1 : h0;
            float v = cinder$localTextureV(source, i, sourceSprite) >= 0.5F
                    ? v1 : v0;
            out.setPos(i,
                    cinder$lerp(source.getX(topLeft), source.getX(topRight),
                            source.getX(bottomLeft), source.getX(bottomRight),
                            h, v),
                    cinder$lerp(source.getY(topLeft), source.getY(topRight),
                            source.getY(bottomLeft), source.getY(bottomRight),
                            h, v),
                    cinder$lerp(source.getZ(topLeft), source.getZ(topRight),
                            source.getZ(bottomLeft), source.getZ(bottomRight),
                            h, v));
            out.setUV(i,
                    cinder$lerp(source.getTexU(topLeft),
                            source.getTexU(topRight),
                            source.getTexU(bottomLeft),
                            source.getTexU(bottomRight),
                            h, v),
                    cinder$lerp(source.getTexV(topLeft),
                            source.getTexV(topRight),
                            source.getTexV(bottomLeft),
                            source.getTexV(bottomRight),
                            h, v));
        }
    }

    @Unique
    private static float cinder$localTextureU(MutableQuadViewImpl quad,
                                              int vertex,
                                              TextureAtlasSprite sprite) {
        float du = sprite.getU1() - sprite.getU0();
        if (du == 0.0F) {
            return 0.0F;
        }
        return cinder$clamp01((quad.getTexU(vertex) - sprite.getU0()) / du);
    }

    @Unique
    private static float cinder$localTextureV(MutableQuadViewImpl quad,
                                              int vertex,
                                              TextureAtlasSprite sprite) {
        float dv = sprite.getV1() - sprite.getV0();
        if (dv == 0.0F) {
            return 0.0F;
        }
        return cinder$clamp01((quad.getTexV(vertex) - sprite.getV0()) / dv);
    }

    @Unique
    private static float cinder$clamp01(float value) {
        if (value < 0.0F) {
            return 0.0F;
        }
        if (value > 1.0F) {
            return 1.0F;
        }
        return value;
    }

    @Unique
    private static float cinder$lerp(float topLeft,
                                     float topRight,
                                     float bottomLeft,
                                     float bottomRight,
                                     float h,
                                     float v) {
        float top = topLeft + (topRight - topLeft) * h;
        float bottom = bottomLeft + (bottomRight - bottomLeft) * h;
        return top + (bottom - top) * v;
    }
}
