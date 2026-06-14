package com.cinder.cit;

import com.cinder.resource.NamespaceId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Renderer-facing replacement payload for a CIT rule.
 *
 * <p>Purpose: shared parsing keeps the OptiFine texture/model declarations in
 * a backend-neutral shape. Fabric can later turn the selected model id into a
 * baked {@code ItemModel}; a Vulkan-native renderer can consume the same
 * metadata without inheriting the current CPU realization.
 *
 * <p>Threading: immutable and safe to share through reload snapshots.
 */
public final class CitReplacement {

    private final NamespaceId texture;
    private final NamespaceId model;
    private final Map<String, NamespaceId> namedTextures;
    private final Map<String, NamespaceId> namedModels;

    public CitReplacement(NamespaceId texture,
                          NamespaceId model,
                          Map<String, NamespaceId> namedTextures,
                          Map<String, NamespaceId> namedModels) {
        this.texture = texture;
        this.model = model;
        this.namedTextures = copyMap(namedTextures);
        this.namedModels = copyMap(namedModels);
    }

    /**
     * Primary {@code texture=} replacement, or {@code null} when absent.
     */
    public NamespaceId texture() {
        return texture;
    }

    /**
     * Primary {@code model=} replacement, or {@code null} when absent.
     */
    public NamespaceId model() {
        return model;
    }

    /**
     * Immutable {@code texture.<name>} replacements in file order.
     */
    public Map<String, NamespaceId> namedTextures() {
        return namedTextures;
    }

    /**
     * Immutable {@code model.<name>} replacements in file order.
     */
    public Map<String, NamespaceId> namedModels() {
        return namedModels;
    }

    /**
     * Returns true when the rule has any visible replacement declaration.
     */
    public boolean hasWork() {
        return texture != null || model != null
                || !namedTextures.isEmpty() || !namedModels.isEmpty();
    }

    private static Map<String, NamespaceId> copyMap(
            Map<String, NamespaceId> map) {
        if (map == null || map.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, NamespaceId> copy = new LinkedHashMap<>();
        for (Map.Entry<String, NamespaceId> entry : map.entrySet()) {
            String key = Objects.requireNonNull(entry.getKey(), "key");
            NamespaceId value =
                    Objects.requireNonNull(entry.getValue(), "value");
            copy.put(key, value);
        }
        return Collections.unmodifiableMap(copy);
    }
}
