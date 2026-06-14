package com.cinder.condition;

import com.cinder.resource.ComponentMatchers;
import com.cinder.resource.NamespaceId;
import com.cinder.resource.RangeListInt;

import java.util.Arrays;
import java.util.Objects;

/**
 * One immutable predicate over a {@link ConditionContext}.
 *
 * <p>Purpose: represents a compiled resource-pack condition independent of
 * the feature that parsed it. CIT, Custom GUI, and later systems can share
 * the same matching semantics and cost model.
 *
 * <p>Threading: implementations are immutable and safe to share between
 * section-build, item-render, and GUI threads as long as the supplied
 * {@link ConditionContext} follows its contract.
 *
 * <p>Performance: conditions allocate only at reload/parse time. Matching is
 * allocation-free except for adapter implementations outside this class.
 */
public sealed interface Condition permits Condition.StringCondition,
        Condition.IntRangeCondition, Condition.BooleanCondition,
        Condition.NamespaceSetCondition, Condition.StringSetCondition {

    /**
     * Returns the fact key read by this condition.
     */
    ConditionKey key();

    /**
     * Returns an optional sub-key such as an NBT path or GUI flag name.
     */
    String qualifier();

    /**
     * Returns the relative matching cost.
     */
    ConditionCost cost();

    /**
     * Evaluates this condition against {@code context}.
     */
    boolean matches(ConditionContext context);

    /**
     * Builds a string matcher condition.
     */
    static Condition string(ConditionKey key,
                            String qualifier,
                            ComponentMatchers.Compiled matcher) {
        return new StringCondition(key, qualifier, matcher, null);
    }

    /**
     * Builds a string matcher condition with an explicit adapter cost.
     */
    static Condition string(ConditionKey key,
                            String qualifier,
                            ComponentMatchers.Compiled matcher,
                            ConditionCost cost) {
        return new StringCondition(key, qualifier, matcher, cost);
    }

    /**
     * Builds an integer range condition.
     */
    static Condition intRange(ConditionKey key,
                              String qualifier,
                              RangeListInt range) {
        return new IntRangeCondition(key, qualifier, range);
    }

    /**
     * Builds a boolean equality condition.
     */
    static Condition bool(ConditionKey key,
                          String qualifier,
                          boolean expected) {
        return new BooleanCondition(key, qualifier, expected);
    }

    /**
     * Builds a set membership condition. It matches when the context exposes
     * at least one of {@code allowed} for the given key.
     */
    static Condition namespaceSet(ConditionKey key,
                                  String qualifier,
                                  NamespaceId[] allowed,
                                  ConditionCost cost) {
        return new NamespaceSetCondition(key, qualifier, allowed, cost);
    }

    /**
     * Builds a string set membership condition.
     */
    static Condition stringSet(ConditionKey key,
                               String qualifier,
                               String[] allowed,
                               ConditionCost cost) {
        return new StringSetCondition(key, qualifier, allowed, cost);
    }

    /**
     * String matcher backed by {@link ComponentMatchers}.
     */
    final class StringCondition implements Condition {
        private final ConditionKey key;
        private final String qualifier;
        private final ComponentMatchers.Compiled matcher;
        private final ConditionCost explicitCost;

        private StringCondition(ConditionKey key,
                                String qualifier,
                                ComponentMatchers.Compiled matcher,
                                ConditionCost explicitCost) {
            this.key = Objects.requireNonNull(key, "key");
            this.qualifier = qualifier;
            this.matcher = Objects.requireNonNull(matcher, "matcher");
            this.explicitCost = explicitCost;
        }

        @Override
        public ConditionKey key() {
            return key;
        }

        @Override
        public String qualifier() {
            return qualifier;
        }

        @Override
        public ConditionCost cost() {
            return explicitCost == null ? matcher.cost() : explicitCost;
        }

        @Override
        public boolean matches(ConditionContext context) {
            Objects.requireNonNull(context, "context");
            return matcher.matches(context.stringValue(key, qualifier),
                    context.has(key, qualifier));
        }
    }

    /**
     * Integer range matcher.
     */
    final class IntRangeCondition implements Condition {
        private final ConditionKey key;
        private final String qualifier;
        private final RangeListInt range;

        private IntRangeCondition(ConditionKey key,
                                  String qualifier,
                                  RangeListInt range) {
            this.key = Objects.requireNonNull(key, "key");
            this.qualifier = qualifier;
            this.range = Objects.requireNonNull(range, "range");
        }

        @Override
        public ConditionKey key() {
            return key;
        }

        @Override
        public String qualifier() {
            return qualifier;
        }

        @Override
        public ConditionCost cost() {
            return ConditionCost.CHEAP;
        }

        @Override
        public boolean matches(ConditionContext context) {
            Objects.requireNonNull(context, "context");
            return context.has(key, qualifier)
                    && range.contains(context.intValue(
                    key, qualifier, Integer.MIN_VALUE));
        }
    }

    /**
     * Boolean equality matcher.
     */
    final class BooleanCondition implements Condition {
        private final ConditionKey key;
        private final String qualifier;
        private final boolean expected;

        private BooleanCondition(ConditionKey key,
                                 String qualifier,
                                 boolean expected) {
            this.key = Objects.requireNonNull(key, "key");
            this.qualifier = qualifier;
            this.expected = expected;
        }

        @Override
        public ConditionKey key() {
            return key;
        }

        @Override
        public String qualifier() {
            return qualifier;
        }

        @Override
        public ConditionCost cost() {
            return ConditionCost.CHEAP;
        }

        @Override
        public boolean matches(ConditionContext context) {
            Objects.requireNonNull(context, "context");
            return context.has(key, qualifier)
                    && context.booleanValue(key, qualifier, !expected)
                    == expected;
        }
    }

    /**
     * Namespace set membership matcher.
     */
    final class NamespaceSetCondition implements Condition {
        private final ConditionKey key;
        private final String qualifier;
        private final NamespaceId[] allowed;
        private final ConditionCost cost;

        private NamespaceSetCondition(ConditionKey key,
                                      String qualifier,
                                      NamespaceId[] allowed,
                                      ConditionCost cost) {
            this.key = Objects.requireNonNull(key, "key");
            this.qualifier = qualifier;
            Objects.requireNonNull(allowed, "allowed");
            this.allowed = allowed.clone();
            this.cost = Objects.requireNonNull(cost, "cost");
            if (this.allowed.length == 0) {
                throw new IllegalArgumentException("allowed is empty");
            }
            for (NamespaceId id : this.allowed) {
                Objects.requireNonNull(id, "allowed id");
            }
        }

        @Override
        public ConditionKey key() {
            return key;
        }

        @Override
        public String qualifier() {
            return qualifier;
        }

        @Override
        public ConditionCost cost() {
            return cost;
        }

        @Override
        public boolean matches(ConditionContext context) {
            Objects.requireNonNull(context, "context");
            if (!context.has(key, qualifier)) {
                return false;
            }
            for (NamespaceId id : allowed) {
                if (context.contains(key, qualifier, id)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Returns a defensive copy of the accepted ids.
         */
        public NamespaceId[] allowed() {
            return allowed.clone();
        }

        @Override
        public String toString() {
            return "NamespaceSetCondition{"
                    + "key=" + key
                    + ", qualifier='" + qualifier + '\''
                    + ", allowed=" + Arrays.toString(allowed)
                    + ", cost=" + cost
                    + '}';
        }
    }

    /**
     * String set membership matcher.
     */
    final class StringSetCondition implements Condition {
        private final ConditionKey key;
        private final String qualifier;
        private final String[] allowed;
        private final ConditionCost cost;

        private StringSetCondition(ConditionKey key,
                                   String qualifier,
                                   String[] allowed,
                                   ConditionCost cost) {
            this.key = Objects.requireNonNull(key, "key");
            this.qualifier = qualifier;
            Objects.requireNonNull(allowed, "allowed");
            this.allowed = allowed.clone();
            this.cost = Objects.requireNonNull(cost, "cost");
            if (this.allowed.length == 0) {
                throw new IllegalArgumentException("allowed is empty");
            }
            for (String value : this.allowed) {
                if (value == null || value.isEmpty()) {
                    throw new IllegalArgumentException(
                            "allowed contains empty value");
                }
            }
        }

        @Override
        public ConditionKey key() {
            return key;
        }

        @Override
        public String qualifier() {
            return qualifier;
        }

        @Override
        public ConditionCost cost() {
            return cost;
        }

        @Override
        public boolean matches(ConditionContext context) {
            Objects.requireNonNull(context, "context");
            if (!context.has(key, qualifier)) {
                return false;
            }
            String actual = context.stringValue(key, qualifier);
            if (actual == null) {
                return false;
            }
            for (String accepted : allowed) {
                if (accepted.equals(actual)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Returns a defensive copy of accepted values.
         */
        public String[] allowed() {
            return allowed.clone();
        }
    }
}
