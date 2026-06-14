package com.cinder.randomentity;

import com.cinder.resource.NamespaceId;

/**
 * One texture variant belonging to a vanilla entity texture.
 *
 * <p>The index follows OptiFine's public resource-pack contract: index
 * {@code 1} means the base vanilla texture, higher indices map to numbered
 * files under {@code optifine/random} or legacy {@code optifine/mob}.
 */
public record RandomEntityVariant(int index, NamespaceId texture) {
    public RandomEntityVariant {
        if (index < 1) {
            throw new IllegalArgumentException("variant index must be >= 1");
        }
    }
}
