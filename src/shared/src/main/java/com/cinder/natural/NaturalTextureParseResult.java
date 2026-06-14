package com.cinder.natural;

import java.util.List;

/**
 * Parse result with per-key error isolation.
 *
 * <p>Threading: immutable reload-time value.
 */
public record NaturalTextureParseResult(
        List<NaturalTextureRule> rules,
        List<Error> errors) {

    public NaturalTextureParseResult {
        rules = List.copyOf(rules);
        errors = List.copyOf(errors);
    }

    public record Error(String sourceFile, String key, String value,
                        String message) {
    }
}
