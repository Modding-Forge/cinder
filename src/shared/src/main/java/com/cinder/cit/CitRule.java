package com.cinder.cit;

import com.cinder.condition.ConditionSet;
import com.cinder.resource.NamespaceId;

import java.util.Arrays;
import java.util.Objects;

/**
 * One immutable OptiFine CIT rule.
 *
 * <p>Purpose: describes selection and replacement metadata without referencing
 * Minecraft item classes. Loader adapters resolve {@link #items()} to their
 * own item objects and keep this rule as the stable snapshot payload.
 *
 * <p>Threading: immutable and safe to read concurrently after publication.
 *
 * <p>Performance: arrays are cloned on construction/access; renderer adapters
 * should keep resolved per-item arrays in their own immutable snapshots.
 */
public final class CitRule implements Comparable<CitRule> {

    private final String sourceFile;
    private final CitRuleType type;
    private final int weight;
    private final NamespaceId[] items;
    private final ConditionSet conditions;
    private final CitReplacement replacement;

    public CitRule(String sourceFile,
                   CitRuleType type,
                   int weight,
                   NamespaceId[] items,
                   ConditionSet conditions,
                   CitReplacement replacement) {
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
        this.type = Objects.requireNonNull(type, "type");
        this.weight = weight;
        Objects.requireNonNull(items, "items");
        this.items = items.clone();
        for (NamespaceId item : this.items) {
            Objects.requireNonNull(item, "item");
        }
        this.conditions = Objects.requireNonNull(conditions, "conditions");
        this.replacement =
                Objects.requireNonNull(replacement, "replacement");
    }

    public String sourceFile() {
        return sourceFile;
    }

    public CitRuleType type() {
        return type;
    }

    public int weight() {
        return weight;
    }

    public NamespaceId[] items() {
        return items.clone();
    }

    public ConditionSet conditions() {
        return conditions;
    }

    public CitReplacement replacement() {
        return replacement;
    }

    public boolean isRenderableItemRule() {
        return type == CitRuleType.ITEM && replacement.hasWork()
                && items.length > 0;
    }

    @Override
    public int compareTo(CitRule other) {
        int byWeight = Integer.compare(other.weight, this.weight);
        if (byWeight != 0) {
            return byWeight;
        }
        return this.sourceFile.compareTo(other.sourceFile);
    }

    @Override
    public String toString() {
        return "CitRule{"
                + "sourceFile='" + sourceFile + '\''
                + ", type=" + type
                + ", weight=" + weight
                + ", items=" + Arrays.toString(items)
                + '}';
    }
}
