package com.cinder.cem;

import java.util.List;

/**
 * Immutable CEM model part declaration.
 *
 * <p>The part name remains the external OptiFine/EMF-compatible adapter key;
 * loader adapters decide whether a given vanilla renderer exposes a matching
 * part.
 */
public record CemPart(String part,
                      String attach,
                      float tx,
                      float ty,
                      float tz,
                      float rx,
                      float ry,
                      float rz,
                      List<CemBox> boxes,
                      List<CemPart> submodels) {
    public CemPart {
        if (part == null || part.isBlank()) {
            throw new IllegalArgumentException("part is required");
        }
        boxes = boxes == null ? List.of() : List.copyOf(boxes);
        submodels = submodels == null ? List.of() : List.copyOf(submodels);
    }
}
