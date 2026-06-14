package com.cinder.customsky;

import java.util.List;

/**
 * Parse output for custom-sky reloads with per-file error isolation.
 *
 * <p>Threading: immutable result. Performance: reload-time only.
 */
public record CustomSkyParseResult(
        List<CustomSkyLayer> layers,
        List<Error> errors) {

    public CustomSkyParseResult {
        layers = List.copyOf(layers);
        errors = List.copyOf(errors);
    }

    public record Error(String sourceFile, String message) {
    }
}
