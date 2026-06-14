package com.cinder.cem;

import com.cinder.resource.NamespaceId;

import java.util.List;

/**
 * Immutable Custom Entity Model document.
 *
 * <p>Shared keeps the model loader-agnostic: texture and source paths are
 * namespace ids, while Fabric later owns conversion into Minecraft model
 * parts.
 */
public record CemModel(String sourcePath,
                       NamespaceId texture,
                       int textureWidth,
                       int textureHeight,
                       float shadowSize,
                       List<CemPart> parts) {
    public CemModel {
        if (sourcePath == null || sourcePath.isBlank()) {
            throw new IllegalArgumentException("sourcePath is required");
        }
        parts = parts == null ? List.of() : List.copyOf(parts);
    }
}
