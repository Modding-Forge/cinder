package com.cinder.client.sodium;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.jspecify.annotations.Nullable;

/**
 * Reusable Sodium CTM overlay emission plan for one base terrain quad.
 *
 * <p>The plan intentionally stores renderer-native sprites and colors rather
 * than shared CTM material ids. It belongs to the Sodium realization layer; the
 * shared CTM selector remains backend-neutral.
 *
 * <p>No Fabric types are used here. The class is loader-neutral within the
 * client renderer boundary.
 *
 * <h2>Threading</h2>
 *
 * <p>Owned by one Sodium {@code BlockRenderer} instance on a section-build
 * worker. Not thread-safe and never shared.
 *
 * <h2>Performance</h2>
 *
 * <p>Performance: HOT PATH. Allocation policy: allocated once per renderer
 * mixin instance and reused for each quad.
 */
public final class CtmSodiumQuadPlan {

    public static final int MAX_OVERLAYS = 16;
    public static final int MAX_REPLACEMENTS = 4;

    private final TextureAtlasSprite[] replacementSprites =
            new TextureAtlasSprite[MAX_REPLACEMENTS];
    private final TextureAtlasSprite[] overlaySprites =
            new TextureAtlasSprite[MAX_OVERLAYS];
    private final int[] overlayColors = new int[MAX_OVERLAYS];
    private final int[] overlayLights = new int[MAX_OVERLAYS];
    private final ChunkSectionLayer[] overlayLayers =
            new ChunkSectionLayer[MAX_OVERLAYS];
    private final boolean[] overlayRemapFromSource =
            new boolean[MAX_OVERLAYS];
    private int replacementCount;
    private int overlayCount;
    private @Nullable ChunkSectionLayer replacementLayer;
    private boolean discardOriginal;
    private boolean shapeReplacementQuadrants;

    public void clear() {
        for (int i = 0; i < replacementCount; i++) {
            replacementSprites[i] = null;
        }
        for (int i = 0; i < overlayCount; i++) {
            overlaySprites[i] = null;
            overlayColors[i] = -1;
            overlayLights[i] = -1;
            overlayLayers[i] = null;
            overlayRemapFromSource[i] = false;
        }
        replacementCount = 0;
        overlayCount = 0;
        replacementLayer = null;
        discardOriginal = false;
        shapeReplacementQuadrants = false;
    }

    public boolean hasReplacements() {
        return replacementCount > 0;
    }

    public boolean discardsOriginal() {
        return discardOriginal;
    }

    public void discardOriginal() {
        discardOriginal = true;
    }

    public boolean shapesReplacementQuadrants() {
        return shapeReplacementQuadrants;
    }

    public void shapeReplacementQuadrants() {
        shapeReplacementQuadrants = true;
    }

    public boolean hasOverlays() {
        return overlayCount > 0;
    }

    public boolean hasWork() {
        return hasReplacements() || hasOverlays() || discardOriginal;
    }

    public int replacementCount() {
        return replacementCount;
    }

    public int overlayCount() {
        return overlayCount;
    }

    public void addReplacement(TextureAtlasSprite sprite) {
        if (replacementCount >= MAX_REPLACEMENTS || sprite == null) {
            return;
        }
        replacementSprites[replacementCount] = sprite;
        replacementCount++;
    }

    public void replacementLayer(@Nullable ChunkSectionLayer layer) {
        replacementLayer = layer;
    }

    public void addOverlay(TextureAtlasSprite sprite,
                           int color,
                           ChunkSectionLayer layer) {
        addOverlay(sprite, color, -1, layer);
    }

    public void addOverlay(TextureAtlasSprite sprite,
                           int color,
                           int light,
                           ChunkSectionLayer layer) {
        addOverlay(sprite, color, light, layer, false);
    }

    public void addOverlay(TextureAtlasSprite sprite,
                           int color,
                           int light,
                           ChunkSectionLayer layer,
                           boolean remapFromSource) {
        if (overlayCount >= MAX_OVERLAYS || sprite == null) {
            return;
        }
        overlaySprites[overlayCount] = sprite;
        overlayColors[overlayCount] = color;
        overlayLights[overlayCount] = light;
        overlayLayers[overlayCount] = layer;
        overlayRemapFromSource[overlayCount] = remapFromSource;
        overlayCount++;
    }

    public TextureAtlasSprite replacementSprite(int index) {
        return replacementSprites[index];
    }

    public @Nullable ChunkSectionLayer replacementLayer() {
        return replacementLayer;
    }

    public boolean allReplacementSpritesMatch() {
        if (replacementCount <= 1) {
            return replacementCount == 1;
        }
        TextureAtlasSprite first = replacementSprites[0];
        for (int i = 1; i < replacementCount; i++) {
            if (replacementSprites[i] != first) {
                return false;
            }
        }
        return true;
    }

    public TextureAtlasSprite overlaySprite(int index) {
        return overlaySprites[index];
    }

    public int overlayColor(int index) {
        return overlayColors[index];
    }

    public int overlayLight(int index) {
        return overlayLights[index];
    }

    public ChunkSectionLayer overlayLayer(int index) {
        return overlayLayers[index];
    }

    public boolean overlayRemapsFromSource(int index) {
        return overlayRemapFromSource[index];
    }
}
