package com.cinder.client.sodium;

import com.cinder.resource.NamespaceId;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Resolves Cinder CTM material sprite ids against Minecraft's live block atlas.
 *
 * <p>Sodium still renders terrain from the stitched block atlas, so the
 * Sodium-native CTM mesh path can use the same generated and explicit sprites
 * that the previous atlas-backed renderer path injected during resource reload.
 *
 * <h2>Threading</h2>
 *
 * <p>This class deliberately depends only on Minecraft client classes, Sodium
 * callers, and Cinder shared metadata. It has no Fabric imports so the same
 * logic can move to another loader's client source set later.
 *
 * <h2>Performance</h2>
 *
 * <p>Performance: HOT PATH. Allocation policy: one Minecraft identifier per
 * successful lookup in the first Sodium migration. This keeps the integration
 * loader-neutral; a reload-time sprite cache can replace it if profiling shows
 * lookup pressure.
 */
public final class CtmSodiumSpriteLookup {

    /**
     * Looks up a CTM sprite in the current block atlas.
     */
    public @Nullable TextureAtlasSprite sprite(NamespaceId spriteId) {
        if (spriteId == null) {
            return null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getAtlasManager() == null) {
            return null;
        }
        TextureAtlas atlas;
        try {
            atlas = minecraft.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return null;
        }
        if (atlas == null) {
            return null;
        }
        Identifier id = Identifier.fromNamespaceAndPath(
                spriteId.namespace(), spriteId.path());
        TextureAtlasSprite sprite = atlas.getSprite(id);
        if (sprite == null
                || MissingTextureAtlasSprite.getLocation()
                .equals(sprite.contents().name())) {
            return null;
        }
        return sprite;
    }
}
