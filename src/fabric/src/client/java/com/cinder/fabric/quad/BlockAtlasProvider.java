package com.cinder.fabric.quad;

import net.minecraft.client.renderer.texture.TextureAtlas;
import org.jspecify.annotations.Nullable;

/**
 * Holder for the active block texture atlas. The renderer
 * installs the atlas once on init; the {@link CtmBlockQuadOutput}
 * reads it for every sprite swap.
 *
 * <h2>Why a holder instead of a method call?</h2>
 *
 * <p>{@code TextureAtlas} is a singleton owned by the
 * texture manager. Looking it up via the manager on every
 * quad would add an O(1) hashmap lookup per quad (cheap
 * but not free). Holding a direct reference is the same
 * pattern Mojang's own renderer uses internally.
 *
 * <h2>Threading</h2>
 *
 * <p>The holder is set on client init (single-threaded) and
 * read on the section-build thread. The reference is
 * {@code volatile} so that the JIT cannot reorder writes
 * before reads.
 */
public final class BlockAtlasProvider {

    private static volatile @Nullable TextureAtlas BLOCK_ATLAS;

    private BlockAtlasProvider() {
    }

    public static void setBlockAtlas(TextureAtlas atlas) {
        BLOCK_ATLAS = atlas;
    }

    public static @Nullable TextureAtlas blockAtlas() {
        return BLOCK_ATLAS;
    }
}
