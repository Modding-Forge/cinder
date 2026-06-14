package com.cinder.condition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Immutable AND-list of compiled conditions.
 *
 * <p>Purpose: represents the complete condition block for one future CIT,
 * Custom GUI, or similar resource-pack rule. It sorts conditions by declared
 * cost so cheap failures happen before expensive regex/component work.
 *
 * <p>Threading: immutable and safe to share. The supplied
 * {@link ConditionContext} is owned by the caller.
 *
 * <p>Performance: allocated once at resource reload. Matching walks a compact
 * immutable list and short-circuits on first failure.
 */
public final class ConditionSet {

    private static final ConditionSet EMPTY =
            new ConditionSet(List.of());

    private final List<Condition> conditions;

    private ConditionSet(List<Condition> conditions) {
        this.conditions = conditions;
    }

    /**
     * Returns a set with no conditions, which matches every context.
     */
    public static ConditionSet empty() {
        return EMPTY;
    }

    /**
     * Builds an immutable, cost-sorted condition set.
     */
    public static ConditionSet of(List<Condition> conditions) {
        Objects.requireNonNull(conditions, "conditions");
        if (conditions.isEmpty()) {
            return EMPTY;
        }
        List<Condition> copy = new ArrayList<>(conditions.size());
        for (Condition condition : conditions) {
            copy.add(Objects.requireNonNull(condition, "condition"));
        }
        copy.sort(Comparator.comparing(Condition::cost));
        return new ConditionSet(List.copyOf(copy));
    }

    /**
     * Returns true when all conditions match. Empty sets match.
     */
    public boolean matches(ConditionContext context) {
        Objects.requireNonNull(context, "context");
        for (Condition condition : conditions) {
            if (!condition.matches(context)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the sorted immutable conditions.
     */
    public List<Condition> conditions() {
        return conditions;
    }

    /**
     * Returns true when no conditions are present.
     */
    public boolean isEmpty() {
        return conditions.isEmpty();
    }
}
