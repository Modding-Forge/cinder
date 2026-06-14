package com.cinder.client.sodium;

import com.cinder.emissive.EmissiveSpriteTable;
import com.cinder.resource.NamespaceId;
import net.caffeinemc.mods.sodium.client.render.model.MutableQuadViewImpl;
import net.caffeinemc.mods.sodium.client.render.texture.SpriteFinderCache;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Sodium meshing adapter for Cinder's OptiFine-style emissive texture MVP.
 *
 * <p>The adapter leaves the base quad unchanged and appends one fullbright
 * overlay quad when the source sprite has an emissive companion in the current
 * {@link EmissiveSpriteTable}.
 *
 * <p>Threading: owned by one Sodium {@code BlockRenderer} instance. Reads an
 * immutable table snapshot; no global mutable render state is written.
 *
 * <p>Performance: HOT PATH. Allocation policy: no allocation after the small
 * per-processor sprite-id cache is warm.
 */
public final class CinderSodiumEmissive {

    private static final int FULL_BRIGHT = 15728880;

    private final CtmSodiumSpriteLookup spriteLookup =
            new CtmSodiumSpriteLookup();
    private final CtmSodiumLayerPolicy layerPolicy =
            new CtmSodiumLayerPolicy();
    private final Map<Identifier, NamespaceId> spriteIds = new HashMap<>();

    /**
     * Appends an emissive overlay to {@code plan} when the current source
     * sprite has a known emissive pendant.
     *
     * @return {@code true} when an overlay was added
     */
    public boolean prepare(MutableQuadViewImpl quad,
                           MutableQuadViewImpl overlaySource,
                           CtmSodiumQuadPlan plan) {
        EmissiveSpriteTable table = EmissiveSpriteTable.current();
        if (table.isEmpty() || plan.hasReplacements()
                || plan.discardsOriginal()) {
            return false;
        }
        TextureAtlasSprite sourceSprite = sourceSprite(quad);
        if (sourceSprite == null) {
            return false;
        }
        NamespaceId emissiveId = table.emissiveSprite(
                namespaceId(sourceSprite));
        if (emissiveId == null) {
            return false;
        }
        TextureAtlasSprite emissiveSprite = spriteLookup.sprite(emissiveId);
        if (emissiveSprite == null
                || sourceSprite.contents().name()
                .equals(emissiveSprite.contents().name())) {
            return false;
        }
        if (!plan.hasOverlays()) {
            overlaySource.copyFrom(quad);
        }
        ChunkSectionLayer layer = layerPolicy.overlayLayer(
                quad.getRenderType());
        plan.addOverlay(emissiveSprite, -1, FULL_BRIGHT, layer, true);
        return true;
    }

    private static @Nullable TextureAtlasSprite sourceSprite(
            MutableQuadViewImpl quad) {
        TextureAtlasSprite sprite = quad.cachedSprite();
        if (sprite != null) {
            return sprite;
        }
        return quad.sprite(SpriteFinderCache.forBlockAtlas());
    }

    private NamespaceId namespaceId(TextureAtlasSprite sprite) {
        Identifier id = sprite.contents().name();
        NamespaceId cached = spriteIds.get(id);
        if (cached != null) {
            return cached;
        }
        NamespaceId created = new NamespaceId(id.getNamespace(), id.getPath());
        spriteIds.put(id, created);
        return created;
    }
}
