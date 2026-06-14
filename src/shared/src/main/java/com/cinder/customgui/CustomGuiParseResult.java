package com.cinder.customgui;

import java.util.List;
import java.util.Objects;

/**
 * Parser result for Custom GUI reloads.
 *
 * <p>Purpose: keeps malformed files isolated so a broken pack entry does not
 * prevent other GUI rules from loading.
 *
 * <p>Threading: immutable value object.
 */
public record CustomGuiParseResult(
        List<CustomGuiRule> rules,
        List<Error> errors) {

    public CustomGuiParseResult {
        rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
        errors = List.copyOf(Objects.requireNonNull(errors, "errors"));
    }

    /**
     * Describes one malformed GUI properties file.
     */
    public record Error(String sourceFile, String message) {
        public Error {
            Objects.requireNonNull(sourceFile, "sourceFile");
            Objects.requireNonNull(message, "message");
        }
    }
}
