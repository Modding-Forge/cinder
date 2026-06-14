package com.cinder.client.sodium;

import com.cinder.config.CinderConfigHolder;
import com.cinder.fabric.natural.NaturalTexturesRuntime;
import com.cinder.natural.NaturalTextureRule;
import com.cinder.natural.NaturalTextureRuleSet;
import com.cinder.resource.NamespaceId;
import net.caffeinemc.mods.sodium.client.render.model.MutableQuadViewImpl;
import net.caffeinemc.mods.sodium.client.render.texture.SpriteFinderCache;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Sodium hot-path adapter for OptiFine Natural Textures.
 *
 * <p>Performance: HOT PATH. Allocation policy: no allocation when disabled,
 * no rule exists, or no transform is selected. Matching quads only do a small
 * deterministic integer hash and four UV writes.
 */
public final class CinderSodiumNaturalTextures {

    public boolean apply(MutableQuadViewImpl quad,
                         BlockState state,
                         BlockPos pos) {
        if (quad == null || state == null || pos == null) {
            return false;
        }
        if (!CinderConfigHolder.get().naturalTexturesActive()) {
            return false;
        }
        NaturalTextureRuleSet rules = NaturalTexturesRuntime.snapshot();
        if (rules.isEmpty()) {
            return false;
        }
        TextureAtlasSprite sprite = sourceSprite(quad);
        if (sprite == null) {
            return false;
        }
        Identifier id = sprite.contents().name();
        NaturalTextureRule rule = rules.ruleFor(new NamespaceId(
                id.getNamespace(), id.getPath()));
        if (rule == null) {
            return false;
        }
        Direction face = faceDirection(quad);
        int hash = hash(pos, face, id);
        int rotation = rotation(rule.rotations(), hash);
        boolean flip = rule.flip() && ((hash >>> 7) & 1) != 0;
        if (rotation == 0 && !flip) {
            return false;
        }
        transformUv(quad, sprite, rotation, flip);
        quad.cachedSprite(sprite);
        return true;
    }

    private static int rotation(int rotations, int hash) {
        return switch (rotations) {
            case 2 -> ((hash & 1) == 0) ? 0 : 2;
            case 4 -> hash & 3;
            default -> 0;
        };
    }

    private static void transformUv(MutableQuadViewImpl quad,
                                    TextureAtlasSprite sprite,
                                    int rotation,
                                    boolean flip) {
        float[] us = new float[4];
        float[] vs = new float[4];
        float u0 = sprite.getU0();
        float v0 = sprite.getV0();
        float du = sprite.getU1() - u0;
        float dv = sprite.getV1() - v0;
        if (du == 0.0F || dv == 0.0F) {
            return;
        }
        for (int i = 0; i < 4; i++) {
            float u = clamp01((quad.getTexU(i) - u0) / du);
            float v = clamp01((quad.getTexV(i) - v0) / dv);
            if (flip) {
                u = 1.0F - u;
            }
            float ru;
            float rv;
            switch (rotation) {
                case 1 -> {
                    ru = 1.0F - v;
                    rv = u;
                }
                case 2 -> {
                    ru = 1.0F - u;
                    rv = 1.0F - v;
                }
                case 3 -> {
                    ru = v;
                    rv = 1.0F - u;
                }
                default -> {
                    ru = u;
                    rv = v;
                }
            }
            us[i] = u0 + du * ru;
            vs[i] = v0 + dv * rv;
        }
        for (int i = 0; i < 4; i++) {
            quad.setUV(i, us[i], vs[i]);
        }
    }

    private static int hash(BlockPos pos, @Nullable Direction face,
                            Identifier sprite) {
        int h = 0x811C9DC5;
        h = mix(h, pos.getX());
        h = mix(h, pos.getY());
        h = mix(h, pos.getZ());
        h = mix(h, face == null ? 7 : face.get3DDataValue());
        h = mix(h, sprite.hashCode());
        h ^= h >>> 16;
        return h;
    }

    private static int mix(int hash, int value) {
        hash ^= value;
        return hash * 0x01000193;
    }

    private static @Nullable Direction faceDirection(MutableQuadViewImpl quad) {
        Direction direction = quad.getCullFace();
        if (direction != null) {
            return direction;
        }
        direction = quad.getNominalFace();
        return direction == null ? quad.getLightFace() : direction;
    }

    private static @Nullable TextureAtlasSprite sourceSprite(
            MutableQuadViewImpl quad) {
        TextureAtlasSprite sprite = quad.cachedSprite();
        if (sprite != null) {
            return sprite;
        }
        return quad.sprite(SpriteFinderCache.forBlockAtlas());
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
}
