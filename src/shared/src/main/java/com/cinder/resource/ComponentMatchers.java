package com.cinder.resource;

import com.cinder.condition.ConditionCost;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Parser for OptiFine-style component match expressions used in CIT and
 * random-entity matchers.
 *
 * <p>Each expression is a {@code String} that may carry one of the
 * following prefixes, all of which are documented in the OptiFine doc:
 *
 * <ul>
 *   <li>{@code pattern:foo*} - glob with {@code *} and {@code ?}.</li>
 *   <li>{@code ipattern:foo*} - case-insensitive glob.</li>
 *   <li>{@code regex:.*foo.*} - Java regex.</li>
 *   <li>{@code iregex:.*foo.*} - case-insensitive regex.</li>
 *   <li>{@code range:1-5 7} - applies to integer values.</li>
 *   <li>{@code exists:true} / {@code exists:false} - existence check.</li>
 *   <li>{@code raw:...} - raw NBT string, combinable with prefixes above
 *       (parsed but treated as plain text for now).</li>
 *   <li>{@code !} prefix - negation of whatever follows.</li>
 *   <li>no prefix - exact match (case-sensitive for strings).</li>
 * </ul>
 *
 * <p>Each match is encoded as a {@link Predicate}; tests in the same module
 * can feed a {@code String} or {@code int} value in and ask "does it match?".
 * Parsing is O(spec length); matching is O(pattern length) for regex/glob
 * and O(1) for range/exists.
 *
 * <p>This class is parser-level only. The actual NBT/component evaluation
 * (walking a Minecraft {@code com.mojang.datafixers.DataResult} tree) is
 * the job of the fabric/ adapter in a later phase.
 */
public final class ComponentMatchers {

    private ComponentMatchers() {
    }

    /**
     * A parsed component matcher. Provides two {@code matches} overloads:
     * one for string values, one for integer values.
     */
    public static class Compiled implements Predicate<String> {
        private final Predicate<String> stringPredicate;
        private final PredicateInt intPredicate;
        private final String originalSpec;
        private final String humanDescription;
        private final ConditionCost cost;
        private final ExistsMode existsMode;

        Compiled(Predicate<String> stringPredicate,
                 PredicateInt intPredicate,
                 String originalSpec,
                 String humanDescription,
                 ConditionCost cost,
                 ExistsMode existsMode) {
            this.stringPredicate = stringPredicate;
            this.intPredicate = intPredicate;
            this.originalSpec = originalSpec;
            this.humanDescription = humanDescription;
            this.cost = cost;
            this.existsMode = existsMode;
        }

        public boolean matches(String value) {
            return matches(value, value != null);
        }

        public boolean matches(int value) {
            return matches(value, true);
        }

        /**
         * Matches a string value while distinguishing "missing" from present
         * {@code null}-like values. Used by the shared condition engine for
         * correct {@code exists:false} semantics.
         */
        public boolean matches(String value, boolean exists) {
            if (existsMode == ExistsMode.WANT_PRESENT) {
                return exists;
            }
            if (existsMode == ExistsMode.WANT_MISSING) {
                return !exists;
            }
            return exists && stringPredicate.test(value);
        }

        /**
         * Matches an integer value while distinguishing missing facts.
         */
        public boolean matches(int value, boolean exists) {
            if (existsMode == ExistsMode.WANT_PRESENT) {
                return exists;
            }
            if (existsMode == ExistsMode.WANT_MISSING) {
                return !exists;
            }
            return exists && intPredicate.test(value);
        }

        @Override
        public boolean test(String value) {
            return matches(value);
        }

        public String originalSpec() {
            return originalSpec;
        }

        public String humanDescription() {
            return humanDescription;
        }

        /**
         * Returns the relative cost of this compiled matcher.
         */
        public ConditionCost cost() {
            return cost;
        }
    }

    /**
     * Functional interface for integer-valued matchers. Defined here so we
     * do not have to autobox per call.
     */
    @FunctionalInterface
    public interface PredicateInt {
        boolean test(int value);
    }

    private enum ExistsMode {
        NONE,
        WANT_PRESENT,
        WANT_MISSING
    }

    /**
     * Parses a single component expression. The result is a {@link Compiled}
     * matcher. If the input is empty, the returned matcher matches
     * nothing.
     */
    public static Compiled parse(String spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec is null");
        }
        String s = spec.trim();
        if (s.isEmpty()) {
            return new Compiled(v -> false, v -> false, spec, "<empty>",
                    ConditionCost.CHEAP, ExistsMode.NONE);
        }

        // Negation prefix '!' applies to the whole expression.
        boolean negate = false;
        if (s.startsWith("!")) {
            negate = true;
            s = s.substring(1).trim();
        }

        // raw: prefix is a hint to the NBT adapter; we keep the rest of
        // the spec intact.
        if (s.startsWith("raw:")) {
            s = s.substring("raw:".length()).trim();
        }

        Compiled inner;
        if (s.startsWith("pattern:")) {
            String glob = s.substring("pattern:".length());
            Predicate<String> p = compileGlob(glob, false);
            inner = new Compiled(p, stringToInt(p), spec,
                    "pattern:" + glob, ConditionCost.EXPENSIVE,
                    ExistsMode.NONE);
        } else if (s.startsWith("ipattern:")) {
            String glob = s.substring("ipattern:".length());
            Predicate<String> p = compileGlob(glob, true);
            inner = new Compiled(p, stringToInt(p), spec,
                    "ipattern:" + glob, ConditionCost.EXPENSIVE,
                    ExistsMode.NONE);
        } else if (s.startsWith("regex:")) {
            Pattern compiled = Pattern.compile(s.substring("regex:".length()));
            Predicate<String> p = compiled.asMatchPredicate();
            inner = new Compiled(p, stringToInt(p), spec,
                    "regex:" + compiled.pattern(), ConditionCost.EXPENSIVE,
                    ExistsMode.NONE);
        } else if (s.startsWith("iregex:")) {
            Pattern compiled = Pattern.compile(
                    s.substring("iregex:".length()),
                    Pattern.CASE_INSENSITIVE);
            Predicate<String> p = compiled.asMatchPredicate();
            inner = new Compiled(p, stringToInt(p), spec,
                    "iregex:" + compiled.pattern(), ConditionCost.EXPENSIVE,
                    ExistsMode.NONE);
        } else if (s.startsWith("range:")) {
            RangeListInt r = RangeListInt.parse(s.substring("range:".length()));
            inner = new Compiled(v -> false, r::contains, spec,
                    "range:" + r, ConditionCost.CHEAP, ExistsMode.NONE);
        } else if (s.startsWith("exists:")) {
            String value = s.substring("exists:".length()).trim();
            if (!value.equals("true") && !value.equals("false")) {
                throw new IllegalArgumentException(
                        "exists: requires true or false, got: " + value);
            }
            boolean want = Boolean.parseBoolean(value);
            inner = new Compiled(v -> want, v -> want, spec,
                    "exists:" + value, ConditionCost.CHEAP,
                    want ? ExistsMode.WANT_PRESENT
                            : ExistsMode.WANT_MISSING);
        } else {
            // Plain exact match (case-sensitive).
            String target = s;
            inner = new Compiled(target::equals, stringToInt(target::equals),
                    spec, "=" + target, ConditionCost.CHEAP,
                    ExistsMode.NONE);
        }

        if (!negate) {
            return inner;
        }
        return new Compiled(
                v -> !inner.matches(v, true),
                v -> !inner.matches(v, true),
                spec,
                "!(" + inner.humanDescription() + ")",
                inner.cost(),
                ExistsMode.NONE) {
            @Override
            public boolean matches(String value, boolean exists) {
                return !inner.matches(value, exists);
            }

            @Override
            public boolean matches(int value, boolean exists) {
                return !inner.matches(value, exists);
            }
        };
    }

    /**
     * Parses a list of space-separated component match expressions.
     * Each expression is independent; an empty list means "match nothing".
     */
    public static List<Compiled> parseList(String spec) {
        if (spec == null || spec.isBlank()) {
            return Collections.emptyList();
        }
        List<Compiled> out = new ArrayList<>();
        for (String token : spec.trim().split("\\s+")) {
            out.add(parse(token));
        }
        return out;
    }

    /**
     * Convenience: returns the first matcher's {@link Compiled#originalSpec()}
     * wrapped in an {@link Optional}, used by the resource loader to keep
     * the original spec for error messages.
     */
    public static Optional<Compiled> tryParse(String spec) {
        try {
            return Optional.of(parse(spec));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static Predicate<String> compileGlob(String glob, boolean caseInsensitive) {
        // Translate glob -> regex: '*' -> '.*', '?' -> '.', escape the rest.
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                case '.', '(', ')', '[', ']', '{', '}', '+', '|', '^', '$', '\\' ->
                        regex.append('\\').append(c);
                default -> regex.append(c);
            }
        }
        regex.append('$');
        int flags = caseInsensitive ? Pattern.CASE_INSENSITIVE : 0;
        return Pattern.compile(regex.toString(), flags).asMatchPredicate();
    }

    /**
     * Bridges a string predicate to the integer predicate path so that
     * {@code int} values can be matched against the same matcher when
     * the user has written a string-based expression. Integers are
     * first stringified with base 10 and then evaluated.
     */
    private static PredicateInt stringToInt(Predicate<String> p) {
        return value -> p.test(Integer.toString(value));
    }
}
