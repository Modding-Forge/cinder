package com.argus.client.sodium;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.jspecify.annotations.Nullable;

/**
 * Small per-face cache for CTM source-sprite resolver results.
 *
 * <p>Most block models use one sprite per face. A tiny linear array keeps the
 * hot path cheaper than allocating a map per rendered block.
 *
 * <p>Threading: owned by one block render plan on one section-build worker.
 *
 * <p>Performance: HOT PATH. Allocation policy: one face plan per face and a
 * lazily grown small array only when a face has multiple source sprites.
 */
final class ArgusCtmFacePlan {

    private static final int INITIAL_CAPACITY = 2;

    private TextureAtlasSprite[] sprites;
    private ArgusCtmFaceSpriteResult[] results;
    private int count;

    @Nullable ArgusCtmFaceSpriteResult find(TextureAtlasSprite sprite) {
        if (sprites == null) {
            return null;
        }
        for (int i = 0; i < count; i++) {
            if (sprites[i] == sprite) {
                return results[i];
            }
        }
        return null;
    }

    void put(TextureAtlasSprite sprite, ArgusCtmFaceSpriteResult result) {
        if (sprites == null) {
            sprites = new TextureAtlasSprite[INITIAL_CAPACITY];
            results = new ArgusCtmFaceSpriteResult[INITIAL_CAPACITY];
        } else if (count >= sprites.length) {
            int nextLength = sprites.length * 2;
            TextureAtlasSprite[] nextSprites =
                    new TextureAtlasSprite[nextLength];
            ArgusCtmFaceSpriteResult[] nextResults =
                    new ArgusCtmFaceSpriteResult[nextLength];
            System.arraycopy(sprites, 0, nextSprites, 0, count);
            System.arraycopy(results, 0, nextResults, 0, count);
            sprites = nextSprites;
            results = nextResults;
        }
        sprites[count] = sprite;
        results[count] = result;
        count++;
    }
}
