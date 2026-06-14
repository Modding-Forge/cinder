package com.cinder.ctm;

import com.cinder.resource.NamespaceId;
import com.cinder.resource.PropertiesFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Loader-agnostic helper that turns raw text/bytes from a resource
 * pack into {@link CtmRule} instances and aggregates them into a
 * {@link CtmRuleSet}.
 *
 * <p>The class itself does not walk resource packs - that is the
 * job of the loader-side adapter (Fabric/NeoForge) - but it owns
 * the conversion from a {@link PropertiesFile} body to a rule, so
 * that the rule-parsing logic stays out of loader code.
 *
 * <h2>Error isolation (Phase 5)</h2>
 *
 * <p>A single malformed {@code .properties} file in a resource
 * pack must not crash the entire resource reload: a player who
 * installs an experimental resource pack should still get CTM
 * for the other rules in the pack. Therefore
 * {@link #buildRuleSet(List, NamespaceId, ParseErrorListener)}
 * catches parse failures per source, records them via the
 * listener, and continues with the remaining sources. The result
 * is a {@link CtmRuleSet} that contains every successfully
 * parsed rule.
 *
 * <p>{@link #parseString(String, NamespaceId, String)} and
 * {@link #parseStream(InputStream, NamespaceId, String)} keep
 * the strict-throw contract for callers that want to handle
 * errors themselves (tests, single-file conversions).
 *
 * <p>Performance: parsing is O(file size) per file; aggregating
 * is O(n log n) for n rules. No allocation per call beyond the
 * result and one or two intermediate strings.
 */
public final class CtmRuleParser {

    /**
     * Listener for per-source parse errors raised by
     * {@link #buildRuleSet(List, NamespaceId, ParseErrorListener)}
     * and {@link #tryParseString}. The default
     * {@link #NOOP_LISTENER} discards messages; callers should
     * supply a logger-backed implementation.
     */
    @FunctionalInterface
    public interface ParseErrorListener {
        void onError(String sourceLabel, String message, Throwable cause);
    }

    /**
     * The default listener: silently discards errors. Used by
     * callers that do not have a logger (e.g. pure unit tests
     * that exercise the strict-throw path).
     */
    public static final ParseErrorListener NOOP_LISTENER =
            (label, message, cause) -> { };

    private CtmRuleParser() {
    }

    /**
     * Pairs a raw body with a source label (typically the resource
     * identifier's path). Used as input to
     * {@link #buildRuleSet(List, NamespaceId)}.
     */
    public record RuleSource(String body, String sourceLabel) {
        public RuleSource {
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(sourceLabel, "sourceLabel");
        }
    }

    /**
     * Parses a single body and returns a single rule.
     */
    public static CtmRule parseString(String body, NamespaceId parent, String src) {
        return parseFromReader(new java.io.StringReader(body), parent, src);
    }

    /**
     * Parses a single rule from an input stream (UTF-8). Throws
     * {@link RuntimeException} on parse error.
     */
    public static CtmRule parseStream(InputStream stream, NamespaceId parent, String src) {
        return parseFromReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8), parent, src);
    }

    /**
     * Non-throwing variant of {@link #parseString}. Returns
     * {@link Optional#empty()} on parse error and notifies the
     * listener.
     */
    public static Optional<CtmRule> tryParseString(
            String body, NamespaceId parent, String src,
            ParseErrorListener errorListener) {
        Objects.requireNonNull(errorListener, "errorListener");
        try {
            return Optional.of(
                    parseFromReader(new java.io.StringReader(body), parent, src));
        } catch (RuntimeException e) {
            errorListener.onError(src, e.getMessage(), e);
            return Optional.empty();
        }
    }

    private static CtmRule parseFromReader(Reader r, NamespaceId parent, String src) {
        try (BufferedReader br = (r instanceof BufferedReader b)
                ? b
                : new BufferedReader(r)) {
            PropertiesFile props = PropertiesFile.parse(br);
            return CtmProperties.parse(props, parent, src);
        } catch (IOException e) {
            throw new RuntimeException("failed to parse " + src, e);
        }
    }

    /**
     * Builds a {@link CtmRuleSet} from a list of rule sources.
     * Each source is parsed independently; a parse failure is
     * reported via {@code errorListener} and the source is
     * skipped. The returned set contains every successfully
     * parsed rule.
     */
    public static CtmRuleSet buildRuleSet(
            List<RuleSource> sources, NamespaceId parent,
            ParseErrorListener errorListener) {
        Objects.requireNonNull(errorListener, "errorListener");
        if (sources == null || sources.isEmpty()) {
            return CtmRuleSet.empty();
        }
        CtmRuleSet.Builder b = new CtmRuleSet.Builder();
        for (RuleSource src : sources) {
            try {
                NamespaceId sourceParent = parentForSource(
                        src.sourceLabel(), parent);
                CtmRule rule = parseFromReader(
                        new java.io.StringReader(src.body()),
                        sourceParent, src.sourceLabel());
                b.add(rule);
            } catch (RuntimeException e) {
                errorListener.onError(src.sourceLabel(), e.getMessage(), e);
            }
        }
        return b.build();
    }

    private static NamespaceId parentForSource(String sourceLabel,
                                               NamespaceId fallback) {
        if (sourceLabel == null || sourceLabel.isBlank()) {
            return fallback;
        }
        int colon = sourceLabel.indexOf(':');
        int slash = sourceLabel.lastIndexOf('/');
        if (colon <= 0 || slash <= colon + 1) {
            return fallback;
        }
        return new NamespaceId(
                sourceLabel.substring(0, colon),
                sourceLabel.substring(colon + 1, slash));
    }

    /**
     * Backwards-compatible overload: builds a rule set with a
     * strict error listener. Any parse failure propagates as a
     * {@link RuntimeException}. New code should use
     * {@link #buildRuleSet(List, NamespaceId, ParseErrorListener)}
     * instead.
     */
    public static CtmRuleSet buildRuleSet(List<RuleSource> sources, NamespaceId parent) {
        return buildRuleSet(sources, parent, (label, message, cause) -> {
            // Re-throw the original cause so the strict contract
            // is preserved.
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(
                    "failed to parse " + label + ": " + message, cause);
        });
    }
}
