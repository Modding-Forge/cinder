package com.cinder.customgui;

import com.cinder.resource.NamespaceId;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable texture replacement payload for one Custom GUI rule.
 *
 * <p>Purpose: stores the default GUI texture replacement and any named
 * {@code texture.<name>} overrides parsed from an OptiFine-style GUI
 * properties file.
 *
 * <p>Threading: immutable and safe to share between reload and render code.
 *
 * <p>Performance: created only during resource reload. Runtime render code
 * consumes pre-resolved Fabric identifiers derived from this payload.
 */
public record CustomGuiReplacement(
        NamespaceId defaultTexture,
        Map<String, NamespaceId> namedTextures) {

    public CustomGuiReplacement {
        namedTextures = Map.copyOf(Objects.requireNonNull(
                namedTextures, "namedTextures"));
    }

    /**
     * Returns true when this rule declares at least one replacement texture.
     */
    public boolean hasTextures() {
        return defaultTexture != null || !namedTextures.isEmpty();
    }
}
