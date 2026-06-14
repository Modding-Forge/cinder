package com.cinder.cem;

import java.util.List;

/**
 * Immutable Custom Entity Model snapshot.
 *
 * <p>Threading: safe to publish atomically. Runtime renderer adapters should
 * derive loader-specific model overrides from this snapshot during reload.
 */
public final class CemRuleSet {
    private static final CemRuleSet EMPTY = new CemRuleSet(List.of());

    private final List<CemModel> models;

    public CemRuleSet(List<CemModel> models) {
        this.models = List.copyOf(models);
    }

    public static CemRuleSet empty() {
        return EMPTY;
    }

    public List<CemModel> models() {
        return models;
    }

    public boolean isEmpty() {
        return models.isEmpty();
    }
}
