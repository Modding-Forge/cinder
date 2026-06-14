package com.cinder.natural;

import com.cinder.resource.NamespaceId;

/**
 * Immutable OptiFine Natural Textures rule for one atlas sprite.
 *
 * <p>Threading: reload-built and read-only in renderer snapshots.
 * Performance: renderer reads primitive fields only.
 */
public record NaturalTextureRule(
        NamespaceId sprite,
        int rotations,
        boolean flip) {

    public NaturalTextureRule {
        if (rotations != 1 && rotations != 2 && rotations != 4) {
            rotations = 1;
        }
    }
}
