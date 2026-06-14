package com.cinder.customcolors;

import java.util.List;

/**
 * Result of parsing one or more Custom Colors property files.
 *
 * <p>Malformed entries are collected as errors so reload adapters can log
 * useful context while keeping other rules alive.
 */
public record CustomColorParseResult(
        ColorOverrideTable overrides,
        List<ColormapRule> blockRules,
        List<Error> errors) {

    public CustomColorParseResult {
        blockRules = List.copyOf(blockRules);
        errors = List.copyOf(errors);
    }

    public record Error(String sourceFile, String key, String value,
                        String message) {
    }
}
