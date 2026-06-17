package com.argus.client.sodium;

import com.argus.client.animation.CustomAnimationRuntime;
import com.argus.client.benchmark.ArgusBenchmark;
import net.caffeinemc.mods.sodium.client.render.model.EncodingFormat;
import net.caffeinemc.mods.sodium.client.render.model.MutableQuadViewImpl;
import net.caffeinemc.mods.sodium.client.render.texture.SpriteFinderCache;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.TriState;

import java.util.function.Consumer;

/**
 * Argus feature pipeline for Sodium terrain quads.
 *
 * <p>The pipeline is used by {@link ArgusSodiumModelEmitter} before Sodium's
 * default quad sink shades and buffers each quad. It reuses the same editor
 * quad for all emitted replacement/overlay geometry so Sodium's render context
 * remains intact.
 *
 * <p>Threading: one instance is held in a {@link ThreadLocal} by the model
 * emitter. It is never shared between section-build workers.
 *
 * <p>Performance: HOT PATH. Allocation policy: scratch quads and processors
 * are allocated once per worker thread.
 */
public final class ArgusSodiumQuadPipeline {

    private static final float OVERLAY_OFFSET = 0.0001F;

    private final CtmSodiumQuadProcessor ctm = new CtmSodiumQuadProcessor();
    private final ArgusSodiumBetterGrass betterGrass =
            new ArgusSodiumBetterGrass();
    private final ArgusSodiumNaturalTextures naturalTextures =
            new ArgusSodiumNaturalTextures();
    private final ArgusSodiumEmissive emissive = new ArgusSodiumEmissive();
    private final CtmSodiumQuadPlan quadPlan = new CtmSodiumQuadPlan();
    private final MutableQuadViewImpl source = newScratchQuad();

    /**
     * Emits one terrain quad through Argus features and then Sodium's original
     * sink.
     */
    public void emit(MutableQuadViewImpl quad,
                     Consumer<MutableQuadViewImpl> sink,
                     ArgusSodiumBlockRenderPlan blockPlan) {
        long totalStart = ArgusBenchmark.start();
        if (CustomAnimationRuntime.shouldMarkTerrainSprites()) {
            CustomAnimationRuntime.markTerrainSprite(sourceSprite(quad));
        }

        long betterGrassStart = ArgusBenchmark.start();
        betterGrass.applySnowSideRemap(quad, blockPlan.level(),
                blockPlan.state(), blockPlan.pos());
        betterGrass.apply(quad, blockPlan.level(), blockPlan.state(),
                blockPlan.pos());
        ArgusBenchmark.record(ArgusBenchmark.SODIUM_BETTER_GRASS,
                betterGrassStart);

        long ctmStart = ArgusBenchmark.start();
        boolean hasOverlayPlan = ctm.prepare(quad, blockPlan, source,
                quadPlan);
        ArgusBenchmark.record(ArgusBenchmark.SODIUM_CTM, ctmStart);

        long emissiveStart = ArgusBenchmark.start();
        hasOverlayPlan = emissive.prepare(quad, source, quadPlan)
                || hasOverlayPlan;
        ArgusBenchmark.record(ArgusBenchmark.SODIUM_EMISSIVE, emissiveStart);

        if (quadPlan.discardsOriginal()) {
            ArgusBenchmark.record(ArgusBenchmark.SODIUM_PROCESS_QUAD,
                    totalStart);
            return;
        }

        if (quadPlan.hasReplacements()) {
            emitReplacementPlan(quad, sink, blockPlan);
            if (quadPlan.hasOverlays()) {
                emitOverlayPlan(quad, sink);
            }
            ArgusBenchmark.record(ArgusBenchmark.SODIUM_PROCESS_QUAD,
                    totalStart);
            return;
        }

        long naturalStart = ArgusBenchmark.start();
        naturalTextures.apply(quad, blockPlan.state(), blockPlan.pos());
        ArgusBenchmark.record(ArgusBenchmark.SODIUM_NATURAL, naturalStart);

        boolean duplicateBackface = duplicatesTranslucentBackface(quad,
                blockPlan);
        sink.accept(quad);

        if (hasOverlayPlan || duplicateBackface) {
            long overlayReturnStart = ArgusBenchmark.start();
            if (hasOverlayPlan) {
                emitOverlayPlan(quad, sink);
            }
            if (duplicateBackface) {
                emitDuplicateBackface(quad, sink);
            }
            ArgusBenchmark.record(ArgusBenchmark.SODIUM_OVERLAY_RETURN,
                    overlayReturnStart);
        }
        ArgusBenchmark.record(ArgusBenchmark.SODIUM_PROCESS_QUAD, totalStart);
    }

    private void emitReplacementPlan(MutableQuadViewImpl quad,
                                     Consumer<MutableQuadViewImpl> sink,
                                     ArgusSodiumBlockRenderPlan blockPlan) {
        TextureAtlasSprite sourceSprite = sourceSprite(source);
        if (sourceSprite == null) {
            return;
        }
        boolean duplicateBackface = duplicatesTranslucentBackface(source,
                blockPlan);
        for (int quadrant = 0; quadrant < quadPlan.replacementCount();
             quadrant++) {
            quad.copyFrom(source);
            if (quadPlan.shapesReplacementQuadrants()) {
                shapeReplacementQuadrant(source, quad, sourceSprite,
                        quadrant);
            }
            CtmSodiumQuadProcessor.remapSprite(quad, sourceSprite,
                    quadPlan.replacementSprite(quadrant));
            quad.setRenderType(quadPlan.replacementLayer());
            sink.accept(quad);
            if (duplicateBackface) {
                flipReplacementBackface(quad);
                // Replacement backfaces used to bypass Sodium's cull check.
                // Null keeps the same visibility when emitted through the
                // model-emitter sink.
                quad.setCullFace(null);
                sink.accept(quad);
            }
        }
    }

    private void emitOverlayPlan(MutableQuadViewImpl quad,
                                 Consumer<MutableQuadViewImpl> sink) {
        TextureAtlasSprite sourceSprite = sourceSprite(source);
        if (sourceSprite == null) {
            return;
        }
        for (int i = 0; i < quadPlan.overlayCount(); i++) {
            quad.copyFrom(source);
            TextureAtlasSprite overlaySprite = quadPlan.overlaySprite(i);
            if (quadPlan.overlayRemapsFromSource(i)) {
                CtmSodiumQuadProcessor.remapSprite(quad, sourceSprite,
                        overlaySprite);
            } else {
                mapOverlaySprite(quad, overlaySprite);
            }
            quad.setRenderType(quadPlan.overlayLayer(i));
            quad.setTintIndex(-1);
            int color = quadPlan.overlayColor(i);
            for (int vertex = 0; vertex < 4; vertex++) {
                quad.setColor(vertex, color);
            }
            int light = quadPlan.overlayLight(i);
            if (light >= 0) {
                quad.setEmissive(true);
                quad.setDiffuseShade(false);
                quad.setAmbientOcclusion(TriState.FALSE);
                for (int vertex = 0; vertex < 4; vertex++) {
                    quad.setLight(vertex, light);
                }
            }
            alignTopOverlayToSourceHeight(quad);
            offsetOverlay(quad, i + 1);
            sink.accept(quad);
        }
    }

    private void emitDuplicateBackface(MutableQuadViewImpl quad,
                                       Consumer<MutableQuadViewImpl> sink) {
        source.copyFrom(quad);
        quad.copyFrom(source);
        flipReplacementBackface(quad);
        quad.setCullFace(null);
        sink.accept(quad);
    }

    private static MutableQuadViewImpl newScratchQuad() {
        return new MutableQuadViewImpl() {
            {
                data = new int[EncodingFormat.TOTAL_STRIDE];
                clear();
            }

            @Override
            public void emitDirectly() {
                // Scratch quads are copied into Sodium's editor quad.
            }
        };
    }

    private static TextureAtlasSprite sourceSprite(MutableQuadViewImpl quad) {
        TextureAtlasSprite sprite = quad.cachedSprite();
        if (sprite != null) {
            return sprite;
        }
        return quad.sprite(SpriteFinderCache.forBlockAtlas());
    }

    private static boolean duplicatesTranslucentBackface(
            MutableQuadViewImpl quad,
            ArgusSodiumBlockRenderPlan blockPlan) {
        if (quad.getRenderType() != ChunkSectionLayer.TRANSLUCENT) {
            return false;
        }
        if (blockPlan == null) {
            return true;
        }
        return blockPlan.config().enabled()
                && blockPlan.config().duplicateTranslucentBackfaces();
    }

    private static void alignTopOverlayToSourceHeight(MutableQuadViewImpl quad) {
        Direction direction = faceDirection(quad);
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

    private static void offsetOverlay(MutableQuadViewImpl quad, int layer) {
        Direction direction = faceDirection(quad);
        if (direction == null) {
            return;
        }
        float offset = OVERLAY_OFFSET * layer;
        float dx = direction.getStepX() * offset;
        float dy = direction.getStepY() * offset;
        float dz = direction.getStepZ() * offset;
        if (dx != 0.0F || dy != 0.0F || dz != 0.0F) {
            quad.translate(dx, dy, dz);
        }
    }

    private static void mapOverlaySprite(MutableQuadViewImpl quad,
                                         TextureAtlasSprite sprite) {
        Direction direction = faceDirection(quad);
        if (direction == null) {
            TextureAtlasSprite source = sourceSprite(quad);
            if (source == null) {
                return;
            }
            CtmSodiumQuadProcessor.remapSprite(quad, source, sprite);
            return;
        }
        float u0 = sprite.getU0();
        float v0 = sprite.getV0();
        float du = sprite.getU1() - u0;
        float dv = sprite.getV1() - v0;
        for (int vertex = 0; vertex < 4; vertex++) {
            float h = overlayLocalH(quad, vertex, direction);
            float v = overlayLocalV(quad, vertex, direction);
            quad.setUV(vertex, u0 + du * h, v0 + dv * v);
        }
        quad.cachedSprite(sprite);
    }

    private static float overlayLocalH(MutableQuadViewImpl quad, int vertex,
                                       Direction direction) {
        return switch (direction) {
            case DOWN, UP, SOUTH -> clamp01(quad.getX(vertex));
            case NORTH -> clamp01(1.0F - quad.getX(vertex));
            case WEST -> clamp01(quad.getZ(vertex));
            case EAST -> clamp01(1.0F - quad.getZ(vertex));
        };
    }

    private static float overlayLocalV(MutableQuadViewImpl quad, int vertex,
                                       Direction direction) {
        return switch (direction) {
            case UP -> clamp01(quad.getZ(vertex));
            case DOWN -> clamp01(1.0F - quad.getZ(vertex));
            case NORTH, SOUTH, WEST, EAST ->
                    clamp01(1.0F - quad.getY(vertex));
        };
    }

    private static void flipReplacementBackface(MutableQuadViewImpl quad) {
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

        Direction face = faceDirection(quad);
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

    private static void shapeReplacementQuadrant(MutableQuadViewImpl source,
                                                 MutableQuadViewImpl out,
                                                 TextureAtlasSprite sprite,
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
            float h = localTextureU(source, i, sprite);
            float v = localTextureV(source, i, sprite);
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
            float h = localTextureU(source, i, sprite) >= 0.5F ? h1 : h0;
            float v = localTextureV(source, i, sprite) >= 0.5F ? v1 : v0;
            out.setPos(i,
                    lerp(source.getX(topLeft), source.getX(topRight),
                            source.getX(bottomLeft),
                            source.getX(bottomRight), h, v),
                    lerp(source.getY(topLeft), source.getY(topRight),
                            source.getY(bottomLeft),
                            source.getY(bottomRight), h, v),
                    lerp(source.getZ(topLeft), source.getZ(topRight),
                            source.getZ(bottomLeft),
                            source.getZ(bottomRight), h, v));
            out.setUV(i,
                    lerp(source.getTexU(topLeft), source.getTexU(topRight),
                            source.getTexU(bottomLeft),
                            source.getTexU(bottomRight), h, v),
                    lerp(source.getTexV(topLeft), source.getTexV(topRight),
                            source.getTexV(bottomLeft),
                            source.getTexV(bottomRight), h, v));
        }
    }

    private static Direction faceDirection(MutableQuadViewImpl quad) {
        Direction direction = quad.getCullFace();
        if (direction != null) {
            return direction;
        }
        direction = quad.getNominalFace();
        return direction == null ? quad.getLightFace() : direction;
    }

    private static float localTextureU(MutableQuadViewImpl quad, int vertex,
                                       TextureAtlasSprite sprite) {
        float du = sprite.getU1() - sprite.getU0();
        if (du == 0.0F) {
            return 0.0F;
        }
        return clamp01((quad.getTexU(vertex) - sprite.getU0()) / du);
    }

    private static float localTextureV(MutableQuadViewImpl quad, int vertex,
                                       TextureAtlasSprite sprite) {
        float dv = sprite.getV1() - sprite.getV0();
        if (dv == 0.0F) {
            return 0.0F;
        }
        return clamp01((quad.getTexV(vertex) - sprite.getV0()) / dv);
    }

    private static float clamp01(float value) {
        if (value < 0.0F) {
            return 0.0F;
        }
        if (value > 1.0F) {
            return 1.0F;
        }
        return value;
    }

    private static float lerp(float topLeft, float topRight,
                              float bottomLeft, float bottomRight,
                              float h, float v) {
        float top = topLeft + (topRight - topLeft) * h;
        float bottom = bottomLeft + (bottomRight - bottomLeft) * h;
        return top + (bottom - top) * v;
    }
}
