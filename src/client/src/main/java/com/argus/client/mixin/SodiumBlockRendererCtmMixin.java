package com.argus.client.mixin;

import com.argus.client.sodium.CtmSodiumQuadPlan;
import com.argus.client.sodium.CtmSodiumQuadProcessor;
import com.argus.client.sodium.ArgusSodiumModelEmitter;
import com.argus.client.sodium.ArgusSodiumEmissive;
import com.argus.client.sodium.ArgusSodiumBetterGrass;
import com.argus.client.sodium.ArgusSodiumBetterSnow;
import com.argus.client.sodium.ArgusSodiumNaturalTextures;
import com.argus.client.benchmark.ArgusBenchmark;
import com.argus.config.ArgusConfig;
import com.argus.config.ArgusConfigHolder;
import com.argus.client.animation.CustomAnimationRuntime;
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
 * <p>Purpose: realizes Argus CTM selections directly in Sodium's terrain
 * meshing path. Replacement CTM remaps the current quad before Sodium applies
 * tint, AO, light, and buffering. Overlay CTM emits additional overlay quads
 * through the same Sodium processing method so overlays receive normal Sodium
 * lighting instead of Argus's old shader-side approximation.
 *
 * <p>Preserved behaviour: non-full-face quads, unmatched quads, and disabled
 * CTM pass through unchanged. The mixin does not change Sodium culling,
 * occlusion, chunk sorting, buffer layout, or shader binding.
 *
 * <p>Compatibility: this mixin is gated by {@link ArgusClientMixinPlugin} and
 * only applies when Sodium is loaded. It replaces Argus's old Mojang terrain
 * sidecar hooks under Sodium, leaving atlas/resource hooks intact.
 *
 * <p>Risk: medium. The hook sits in a Sodium hot path, but it mutates only the
 * current editable quad or emits bounded overlay copies.
 */
@Mixin(value = BlockRenderer.class, remap = false)
public abstract class SodiumBlockRendererCtmMixin
        extends AbstractBlockRenderContext {

    @Unique
    private static final float ARGUS_OVERLAY_OFFSET = 0.0001F;

    @Shadow
    protected abstract void processQuad(MutableQuadViewImpl quad);

    @Shadow
    public abstract void renderModel(BlockStateModel model, BlockState state,
                                     BlockPos pos, BlockPos origin);

    @Unique
    private @Nullable CtmSodiumQuadProcessor argus$processor;

    @Unique
    private @Nullable ArgusSodiumBetterGrass argus$betterGrass;

    @Unique
    private @Nullable ArgusSodiumBetterSnow argus$betterSnow;

    @Unique
    private @Nullable ArgusSodiumNaturalTextures argus$naturalTextures;

    @Unique
    private @Nullable ArgusSodiumEmissive argus$emissive;

    @Unique
    private @Nullable CtmSodiumQuadPlan argus$plan;

    @Unique
    private @Nullable MutableQuadViewImpl argus$overlaySource;

    @Unique
    private @Nullable MutableQuadViewImpl argus$overlayQuad;

    @Unique
    private boolean argus$emittingOverlay;

    @Unique
    private boolean argus$hasOverlayPlan;

    @Inject(method = "processQuad", at = @At("HEAD"), cancellable = true)
    private void argus$prepareCtmQuad(MutableQuadViewImpl quad,
                                       CallbackInfo ci) {
        if (ArgusSodiumModelEmitter.featurePipelineActive()) {
            return;
        }
        long totalStart = ArgusBenchmark.start();
        if (argus$emittingOverlay) {
            long naturalStart = ArgusBenchmark.start();
            argus$naturalTextures().apply(quad, state, pos);
            ArgusBenchmark.record(ArgusBenchmark.SODIUM_NATURAL,
                    naturalStart);
            ArgusBenchmark.record(ArgusBenchmark.SODIUM_PROCESS_QUAD,
                    totalStart);
            return;
        }
        if (CustomAnimationRuntime.shouldMarkTerrainSprites()) {
            CustomAnimationRuntime.markTerrainSprite(argus$sourceSprite(quad));
        }
        long betterGrassStart = ArgusBenchmark.start();
        argus$betterGrass().applySnowSideRemap(quad, level, state, pos);
        argus$betterGrass().apply(quad, level, state, pos);
        ArgusBenchmark.record(ArgusBenchmark.SODIUM_BETTER_GRASS,
                betterGrassStart);
        long ctmStart = ArgusBenchmark.start();
        argus$hasOverlayPlan = argus$processor().prepare(
                quad, level, state, pos, argus$overlaySource(),
                argus$plan());
        ArgusBenchmark.record(ArgusBenchmark.SODIUM_CTM, ctmStart);
        long emissiveStart = ArgusBenchmark.start();
        argus$hasOverlayPlan = argus$emissive().prepare(
                quad, argus$overlaySource(), argus$plan())
                || argus$hasOverlayPlan;
        ArgusBenchmark.record(ArgusBenchmark.SODIUM_EMISSIVE, emissiveStart);
        CtmSodiumQuadPlan plan = argus$plan();
        if (plan.discardsOriginal()) {
            argus$hasOverlayPlan = false;
            ci.cancel();
            ArgusBenchmark.record(ArgusBenchmark.SODIUM_PROCESS_QUAD,
                    totalStart);
            return;
        }
        if (!plan.hasReplacements()) {
            long naturalStart = ArgusBenchmark.start();
            argus$naturalTextures().apply(quad, state, pos);
            ArgusBenchmark.record(ArgusBenchmark.SODIUM_NATURAL,
                    naturalStart);
            ArgusBenchmark.record(ArgusBenchmark.SODIUM_PROCESS_QUAD,
                    totalStart);
            return;
        }
        argus$hasOverlayPlan = false;
        argus$emittingOverlay = true;
        try {
            MutableQuadViewImpl source = argus$overlaySource();
            MutableQuadViewImpl replacement = argus$overlayQuad();
            TextureAtlasSprite sourceSprite = argus$sourceSprite(source);
            if (sourceSprite != null) {
                boolean duplicateBackface =
                        argus$duplicatesTranslucentBackface(source);
                for (int quadrant = 0;
                     quadrant < plan.replacementCount();
                     quadrant++) {
                    replacement.copyFrom(source);
                    if (plan.shapesReplacementQuadrants()) {
                        argus$shapeReplacementQuadrant(
                                source, replacement, sourceSprite, quadrant);
                    }
                    CtmSodiumQuadProcessor.remapSprite(
                            replacement,
                            sourceSprite,
                            plan.replacementSprite(quadrant));
                    replacement.setRenderType(plan.replacementLayer());
                    processQuad(replacement);
                    if (duplicateBackface) {
                        argus$flipReplacementBackface(replacement);
                        processQuad(replacement);
                    }
                }
            }
            if (plan.hasOverlays()) {
                argus$emitOverlayPlan(plan);
            }
        } finally {
            argus$emittingOverlay = false;
        }
        ci.cancel();
        ArgusBenchmark.record(ArgusBenchmark.SODIUM_PROCESS_QUAD,
                totalStart);
    }

    @Inject(method = "processQuad", at = @At("RETURN"))
    private void argus$emitCtmOverlays(MutableQuadViewImpl quad,
                                        CallbackInfo ci) {
        if (ArgusSodiumModelEmitter.featurePipelineActive()) {
            return;
        }
        long start = ArgusBenchmark.start();
        if (argus$emittingOverlay) {
            return;
        }
        boolean hasOverlayPlan = argus$hasOverlayPlan;
        boolean duplicateBackface =
                argus$duplicatesTranslucentBackface(quad);
        if (!hasOverlayPlan && !duplicateBackface) {
            return;
        }
        argus$hasOverlayPlan = false;
        CtmSodiumQuadPlan plan = argus$plan();
        argus$emittingOverlay = true;
        try {
            if (hasOverlayPlan) {
                MutableQuadViewImpl overlaySource = argus$overlaySource();
                TextureAtlasSprite sourceSprite =
                        argus$sourceSprite(overlaySource);
                if (sourceSprite != null) {
                    argus$emitOverlayPlan(plan);
                }
            }
            if (duplicateBackface) {
                argus$emitDuplicateTranslucentBackface(quad);
            }
        } finally {
            argus$emittingOverlay = false;
        }
        ArgusBenchmark.record(ArgusBenchmark.SODIUM_OVERLAY_RETURN, start);
    }

    @Inject(method = "renderModel", at = @At("TAIL"))
    private void argus$emitBetterSnowLayer(BlockStateModel model,
                                            BlockState state,
                                            BlockPos pos,
                                            BlockPos origin,
                                            CallbackInfo ci) {
        long start = ArgusBenchmark.start();
        if (argus$emittingOverlay) {
            return;
        }
        argus$emittingOverlay = true;
        try {
            if (!argus$betterSnow().shouldRenderSnowLayer(level, state,
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
            argus$emittingOverlay = false;
            ArgusBenchmark.record(ArgusBenchmark.SODIUM_BETTER_SNOW, start);
        }
    }

    @Unique
    private void argus$emitOverlayPlan(CtmSodiumQuadPlan plan) {
        MutableQuadViewImpl overlaySource = argus$overlaySource();
        MutableQuadViewImpl overlayQuad = argus$overlayQuad();
        TextureAtlasSprite sourceSprite = argus$sourceSprite(overlaySource);
        for (int i = 0; i < plan.overlayCount(); i++) {
            overlayQuad.copyFrom(overlaySource);
            TextureAtlasSprite overlaySprite = plan.overlaySprite(i);
            if (plan.overlayRemapsFromSource(i) && sourceSprite != null) {
                CtmSodiumQuadProcessor.remapSprite(
                        overlayQuad, sourceSprite, overlaySprite);
            } else {
                argus$mapOverlaySprite(overlayQuad, overlaySprite);
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
            argus$alignTopOverlayToSourceHeight(overlayQuad);
            argus$offsetOverlay(overlayQuad, i + 1);
            processQuad(overlayQuad);
        }
    }

    @Unique
    private void argus$emitDuplicateTranslucentBackface(
            MutableQuadViewImpl source) {
        MutableQuadViewImpl backface = argus$overlayQuad();
        backface.copyFrom(source);
        argus$flipReplacementBackface(backface);
        processQuad(backface);
    }

    @Unique
    private CtmSodiumQuadProcessor argus$processor() {
        CtmSodiumQuadProcessor processor = argus$processor;
        if (processor == null) {
            processor = new CtmSodiumQuadProcessor();
            argus$processor = processor;
        }
        return processor;
    }

    @Unique
    private ArgusSodiumBetterGrass argus$betterGrass() {
        ArgusSodiumBetterGrass betterGrass = argus$betterGrass;
        if (betterGrass == null) {
            betterGrass = new ArgusSodiumBetterGrass();
            argus$betterGrass = betterGrass;
        }
        return betterGrass;
    }

    @Unique
    private ArgusSodiumBetterSnow argus$betterSnow() {
        ArgusSodiumBetterSnow betterSnow = argus$betterSnow;
        if (betterSnow == null) {
            betterSnow = new ArgusSodiumBetterSnow();
            argus$betterSnow = betterSnow;
        }
        return betterSnow;
    }

    @Unique
    private ArgusSodiumNaturalTextures argus$naturalTextures() {
        ArgusSodiumNaturalTextures naturalTextures = argus$naturalTextures;
        if (naturalTextures == null) {
            naturalTextures = new ArgusSodiumNaturalTextures();
            argus$naturalTextures = naturalTextures;
        }
        return naturalTextures;
    }

    @Unique
    private ArgusSodiumEmissive argus$emissive() {
        ArgusSodiumEmissive emissive = argus$emissive;
        if (emissive == null) {
            emissive = new ArgusSodiumEmissive();
            argus$emissive = emissive;
        }
        return emissive;
    }

    @Unique
    private CtmSodiumQuadPlan argus$plan() {
        CtmSodiumQuadPlan plan = argus$plan;
        if (plan == null) {
            plan = new CtmSodiumQuadPlan();
            argus$plan = plan;
        }
        return plan;
    }

    @Unique
    private MutableQuadViewImpl argus$overlaySource() {
        MutableQuadViewImpl quad = argus$overlaySource;
        if (quad == null) {
            quad = argus$newScratchQuad();
            argus$overlaySource = quad;
        }
        return quad;
    }

    @Unique
    private MutableQuadViewImpl argus$overlayQuad() {
        MutableQuadViewImpl quad = argus$overlayQuad;
        if (quad == null) {
            quad = argus$newScratchQuad();
            argus$overlayQuad = quad;
        }
        return quad;
    }

    @Unique
    private static MutableQuadViewImpl argus$newScratchQuad() {
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
    private static TextureAtlasSprite argus$sourceSprite(
            MutableQuadViewImpl quad) {
        TextureAtlasSprite sprite = quad.cachedSprite();
        if (sprite != null) {
            return sprite;
        }
        return quad.sprite(SpriteFinderCache.forBlockAtlas());
    }

    @Unique
    private static boolean argus$duplicatesTranslucentBackface(
            MutableQuadViewImpl source) {
        if (source.getRenderType() != ChunkSectionLayer.TRANSLUCENT) {
            return false;
        }
        ArgusConfig config = ArgusConfigHolder.get();
        return config.enabled() && config.duplicateTranslucentBackfaces();
    }

    @Unique
    private static void argus$alignTopOverlayToSourceHeight(
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
    private static void argus$offsetOverlay(MutableQuadViewImpl quad,
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
        float offset = ARGUS_OVERLAY_OFFSET * layer;
        float dx = direction.getStepX() * offset;
        float dy = direction.getStepY() * offset;
        float dz = direction.getStepZ() * offset;
        if (dx != 0.0F || dy != 0.0F || dz != 0.0F) {
            quad.translate(dx, dy, dz);
        }
    }

    @Unique
    private static void argus$mapOverlaySprite(MutableQuadViewImpl quad,
                                                TextureAtlasSprite sprite) {
        Direction direction = quad.getCullFace();
        if (direction == null) {
            direction = quad.getNominalFace();
        }
        if (direction == null) {
            direction = quad.getLightFace();
        }
        if (direction == null) {
            TextureAtlasSprite source = argus$sourceSprite(quad);
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
            float h = argus$overlayLocalH(quad, vertex, direction);
            float v = argus$overlayLocalV(quad, vertex, direction);
            quad.setUV(vertex, u0 + du * h, v0 + dv * v);
        }
        quad.cachedSprite(sprite);
    }

    @Unique
    private static float argus$overlayLocalH(MutableQuadViewImpl quad,
                                              int vertex,
                                              Direction direction) {
        return switch (direction) {
            case DOWN, UP, SOUTH -> argus$clamp01(quad.getX(vertex));
            case NORTH -> argus$clamp01(1.0F - quad.getX(vertex));
            case WEST -> argus$clamp01(quad.getZ(vertex));
            case EAST -> argus$clamp01(1.0F - quad.getZ(vertex));
        };
    }

    @Unique
    private static float argus$overlayLocalV(MutableQuadViewImpl quad,
                                              int vertex,
                                              Direction direction) {
        return switch (direction) {
            case UP -> argus$clamp01(quad.getZ(vertex));
            case DOWN -> argus$clamp01(1.0F - quad.getZ(vertex));
            case NORTH, SOUTH, WEST, EAST ->
                    argus$clamp01(1.0F - quad.getY(vertex));
        };
    }

    @Unique
    private static void argus$flipReplacementBackface(
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
    private static void argus$shapeReplacementQuadrant(
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
            float h = argus$localTextureU(source, i, sourceSprite);
            float v = argus$localTextureV(source, i, sourceSprite);
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
            float h = argus$localTextureU(source, i, sourceSprite) >= 0.5F
                    ? h1 : h0;
            float v = argus$localTextureV(source, i, sourceSprite) >= 0.5F
                    ? v1 : v0;
            out.setPos(i,
                    argus$lerp(source.getX(topLeft), source.getX(topRight),
                            source.getX(bottomLeft), source.getX(bottomRight),
                            h, v),
                    argus$lerp(source.getY(topLeft), source.getY(topRight),
                            source.getY(bottomLeft), source.getY(bottomRight),
                            h, v),
                    argus$lerp(source.getZ(topLeft), source.getZ(topRight),
                            source.getZ(bottomLeft), source.getZ(bottomRight),
                            h, v));
            out.setUV(i,
                    argus$lerp(source.getTexU(topLeft),
                            source.getTexU(topRight),
                            source.getTexU(bottomLeft),
                            source.getTexU(bottomRight),
                            h, v),
                    argus$lerp(source.getTexV(topLeft),
                            source.getTexV(topRight),
                            source.getTexV(bottomLeft),
                            source.getTexV(bottomRight),
                            h, v));
        }
    }

    @Unique
    private static float argus$localTextureU(MutableQuadViewImpl quad,
                                              int vertex,
                                              TextureAtlasSprite sprite) {
        float du = sprite.getU1() - sprite.getU0();
        if (du == 0.0F) {
            return 0.0F;
        }
        return argus$clamp01((quad.getTexU(vertex) - sprite.getU0()) / du);
    }

    @Unique
    private static float argus$localTextureV(MutableQuadViewImpl quad,
                                              int vertex,
                                              TextureAtlasSprite sprite) {
        float dv = sprite.getV1() - sprite.getV0();
        if (dv == 0.0F) {
            return 0.0F;
        }
        return argus$clamp01((quad.getTexV(vertex) - sprite.getV0()) / dv);
    }

    @Unique
    private static float argus$clamp01(float value) {
        if (value < 0.0F) {
            return 0.0F;
        }
        if (value > 1.0F) {
            return 1.0F;
        }
        return value;
    }

    @Unique
    private static float argus$lerp(float topLeft,
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
