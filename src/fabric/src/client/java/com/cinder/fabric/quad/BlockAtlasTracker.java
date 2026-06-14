package com.cinder.fabric.quad;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.data.AtlasIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks the live block {@link TextureAtlas} and keeps
 * {@link BlockAtlasProvider} in sync. The block atlas is
 * recreated by the renderer on every resource reload, so we
 * re-publish the reference every client tick.
 *
 * <h2>Why a tick and not a ResourceReloadListener?</h2>
 *
 * <p>{@code ModelManager} exposes the atlas only after the
 * reload future has been joined and uploaded. The upload
 * happens in a worker thread; the resulting
 * {@link TextureAtlas} reference becomes valid on the
 * client thread slightly later. Polling once per client
 * tick is the cheapest way to discover the swap without
 * hooking the upload path itself.
 *
 * <h2>Threading</h2>
 *
 * <p>All calls happen on the client thread. The
 * {@link BlockAtlasProvider} uses a volatile field, so
 * reads from the section-build thread see the latest
 * reference.
 *
 * <h2>Performance</h2>
 *
 * <p>The tick callback does an object-identity compare
 * against the cached atlas; the body is O(1) in the
 * common case. The atlas reference changes only on
 * resource reload (rare).
 *
 * <p>Performance: HOT PATH (every client tick).
 * Allocation policy: none.
 * Reasoning: identity check + null check.
 */
public final class BlockAtlasTracker {

    private static final Logger LOGGER =
            LoggerFactory.getLogger("cinder/atlas-tracker");

    private static TextureAtlas lastPublished;

    private BlockAtlasTracker() {
    }

    /**
     * Wires the tracker into the Fabric client tick event.
     * Idempotent: calling it twice has no extra effect.
     */
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client));
    }

    private static void tick(Minecraft client) {
        if (client == null) {
            return;
        }
        if (client.getAtlasManager() == null) {
            return;
        }
        TextureAtlas atlas;
        try {
            atlas = client.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            // Atlas not stitched yet (mid-reload) or the
            // id has been renamed in a future mapping.
            return;
        }
        if (atlas == lastPublished) {
            return;
        }
        lastPublished = atlas;
        BlockAtlasProvider.setBlockAtlas(atlas);
        LOGGER.debug("[cinder] block atlas published: {}",
                atlas.location());
    }
}
