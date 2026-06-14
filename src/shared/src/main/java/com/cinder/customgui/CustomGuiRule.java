package com.cinder.customgui;

import com.cinder.condition.ConditionContext;
import com.cinder.condition.ConditionSet;

import java.util.Objects;

/**
 * Immutable parsed Custom GUI rule.
 *
 * <p>Purpose: represents one OptiFine-style GUI properties file without
 * referencing Minecraft runtime classes. The container id is kept as a plain
 * stable string so Fabric and later NeoForge adapters can map their screen
 * types independently.
 *
 * <p>Threading: immutable and safe to publish atomically in rule snapshots.
 *
 * <p>Performance: matching delegates to a cost-sorted {@link ConditionSet};
 * rules are sorted once at reload.
 */
public final class CustomGuiRule {

    private final String sourcePath;
    private final String container;
    private final int weight;
    private final ConditionSet conditions;
    private final CustomGuiReplacement replacement;

    public CustomGuiRule(String sourcePath,
                         String container,
                         int weight,
                         ConditionSet conditions,
                         CustomGuiReplacement replacement) {
        this.sourcePath = normalizeSource(sourcePath);
        this.container = normalizeContainer(container);
        this.weight = weight;
        this.conditions = Objects.requireNonNull(conditions, "conditions");
        this.replacement = Objects.requireNonNull(replacement, "replacement");
    }

    /**
     * Returns the normalized source label used for stable ordering.
     */
    public String sourcePath() {
        return sourcePath;
    }

    /**
     * Returns the normalized OptiFine container id.
     */
    public String container() {
        return container;
    }

    /**
     * Returns the rule weight. Higher weights sort first.
     */
    public int weight() {
        return weight;
    }

    /**
     * Returns the cost-sorted conditions for this rule.
     */
    public ConditionSet conditions() {
        return conditions;
    }

    /**
     * Returns the parsed replacement texture payload.
     */
    public CustomGuiReplacement replacement() {
        return replacement;
    }

    /**
     * Evaluates this rule against a loader-provided context.
     */
    public boolean matches(ConditionContext context) {
        return conditions.matches(context);
    }

    private static String normalizeSource(String sourcePath) {
        Objects.requireNonNull(sourcePath, "sourcePath");
        return sourcePath.replace('\\', '/');
    }

    private static String normalizeContainer(String container) {
        if (container == null || container.isBlank()) {
            return "";
        }
        return container.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
