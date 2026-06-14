package com.cinder.cem;

import java.util.List;

/**
 * Result of parsing Custom Entity Model resources.
 */
public record CemParseResult(List<CemModel> models, List<Error> errors) {
    public CemParseResult {
        models = List.copyOf(models);
        errors = List.copyOf(errors);
    }

    public record Error(String sourcePath, String message) {
    }
}
