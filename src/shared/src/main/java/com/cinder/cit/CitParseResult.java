package com.cinder.cit;

import java.util.List;
import java.util.Objects;

/**
 * Non-throwing CIT parse aggregate.
 *
 * <p>Purpose: resource reload must isolate malformed files. The shared parser
 * therefore returns successful rules and structured errors for loader-side
 * logging instead of crashing the whole reload.
 *
 * <p>Threading: immutable.
 */
public record CitParseResult(List<CitRule> rules, List<Error> errors) {

    public CitParseResult {
        rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
        errors = List.copyOf(Objects.requireNonNull(errors, "errors"));
    }

    public record Error(String sourceFile, String message) {
        public Error {
            Objects.requireNonNull(sourceFile, "sourceFile");
            Objects.requireNonNull(message, "message");
        }
    }
}
