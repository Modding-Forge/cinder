package com.cinder.condition;

/**
 * Relative evaluation cost for a compiled condition.
 *
 * <p>Purpose: lets {@link ConditionSet} sort cheap rejection checks before
 * expensive string or tree traversal checks while preserving rule semantics.
 *
 * <p>Threading: enum constants are immutable and shared.
 *
 * <p>Performance: compared by ordinal in hot-ish selector paths; keep the
 * declaration order from cheapest to most expensive.
 */
public enum ConditionCost {
    /** Constant or direct identity checks. */
    CONSTANT,
    /** Small primitive/range/set checks. */
    CHEAP,
    /** Regex, glob, component, or raw NBT traversal checks. */
    EXPENSIVE
}
