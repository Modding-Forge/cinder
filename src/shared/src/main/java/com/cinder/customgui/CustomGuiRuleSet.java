package com.cinder.customgui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable Custom GUI rule snapshot.
 *
 * <p>Purpose: groups parsed rules by container id and keeps rule priority
 * deterministic. Runtime adapters can obtain the small candidate array for the
 * current screen without scanning unrelated rules.
 *
 * <p>Threading: immutable and safe for lock-free publication.
 *
 * <p>Performance: grouping and sorting happen at reload. Runtime lookup is a
 * single map read followed by an array walk for the active container.
 */
public final class CustomGuiRuleSet {

    private static final CustomGuiRuleSet EMPTY =
            new CustomGuiRuleSet(List.of(), Map.of());
    private static final CustomGuiRule[] EMPTY_RULES = new CustomGuiRule[0];

    private final List<CustomGuiRule> all;
    private final Map<String, CustomGuiRule[]> byContainer;

    private CustomGuiRuleSet(List<CustomGuiRule> all,
                             Map<String, CustomGuiRule[]> byContainer) {
        this.all = all;
        this.byContainer = byContainer;
    }

    /**
     * Returns an empty rule set.
     */
    public static CustomGuiRuleSet empty() {
        return EMPTY;
    }

    /**
     * Builds a deterministic immutable rule set.
     */
    public static CustomGuiRuleSet of(List<CustomGuiRule> rules) {
        Objects.requireNonNull(rules, "rules");
        if (rules.isEmpty()) {
            return EMPTY;
        }
        ArrayList<CustomGuiRule> sorted = new ArrayList<>(rules.size());
        for (CustomGuiRule rule : rules) {
            if (rule.replacement().hasTextures()) {
                sorted.add(Objects.requireNonNull(rule, "rule"));
            }
        }
        if (sorted.isEmpty()) {
            return EMPTY;
        }
        sorted.sort(Comparator
                .comparingInt(CustomGuiRule::weight).reversed()
                .thenComparing(CustomGuiRule::sourcePath));

        LinkedHashMap<String, ArrayList<CustomGuiRule>> grouped =
                new LinkedHashMap<>();
        for (CustomGuiRule rule : sorted) {
            grouped.computeIfAbsent(rule.container(), unused -> new ArrayList<>())
                    .add(rule);
        }
        LinkedHashMap<String, CustomGuiRule[]> byContainer =
                new LinkedHashMap<>();
        for (Map.Entry<String, ArrayList<CustomGuiRule>> entry
                : grouped.entrySet()) {
            byContainer.put(entry.getKey(),
                    entry.getValue().toArray(CustomGuiRule[]::new));
        }
        return new CustomGuiRuleSet(List.copyOf(sorted),
                Map.copyOf(byContainer));
    }

    /**
     * Returns every rule in final priority order.
     */
    public List<CustomGuiRule> all() {
        return all;
    }

    /**
     * Returns candidates for the given container id.
     */
    public CustomGuiRule[] candidates(String container) {
        if (container == null) {
            return EMPTY_RULES;
        }
        CustomGuiRule[] candidates = byContainer.get(
                container.toLowerCase(java.util.Locale.ROOT));
        return candidates != null ? candidates : EMPTY_RULES;
    }

    /**
     * Returns true when no rules are present.
     */
    public boolean isEmpty() {
        return all.isEmpty();
    }
}
