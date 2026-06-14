package com.cinder.ctm;

import com.cinder.resource.NamespaceId;

import java.util.Objects;
import java.util.Optional;

/**
 * Loader-agnostic live registry of {@link CtmRuleSet}s.
 *
 * <p>The {@code common} module owns the data; the {@code fabric} (and
 * later {@code neoforge}) modules own the reload-listener registration
 * and the bridge from Minecraft resource packs to {@link CtmRule}
 * instances.
 *
 * <p>Use cases:
 * <ul>
 *   <li>The {@link CtmSelector} asks the registry for rules
 *       matching a block id or a sprite id.</li>
 *   <li>The reload listener replaces the active
 *       {@link CtmRuleSet} atomically with a freshly-built one.</li>
 * </ul>
 *
 * <p>Mutability: the active rule set is held in a single
 * {@code volatile} field and swapped on reload. Reads are lock-free.
 * The class is thread-safe; concurrent reads observe a stable
 * snapshot.
 *
 * <p>Performance: {@link #ruleSet()} returns a stable reference per
 * snapshot; the selector should cache the result and not call
 * {@code ruleSet()} on every quad.
 */
public class CtmRegistry {

    private final String modId;
    private volatile CtmRuleSet ruleSet = CtmRuleSet.empty();

    public CtmRegistry(String modId) {
        this.modId = Objects.requireNonNull(modId, "modId");
    }

    /**
     * Returns the shared empty registry for the given mod id.
     * The returned instance is a singleton-equivalent: replace() is
     * a no-op (always-empty rule set). Useful as a safe fallback
     * when a loader has not populated the registry.
     */
    public static CtmRegistry empty(String modId) {
        return new EmptyCtmRegistry(modId);
    }

    public String modId() {
        return modId;
    }

    /**
     * Returns the active rule set. The returned reference is stable
     * until the next {@link #replace(CtmRuleSet)} call.
     */
    public CtmRuleSet ruleSet() {
        return ruleSet;
    }

    /**
     * Atomically replaces the active rule set with a new one.
     * Concurrent readers will see either the old or the new set
     * but never a partial state.
     */
    public void replace(CtmRuleSet newSet) {
        this.ruleSet = Objects.requireNonNull(newSet, "newSet");
    }

    /**
     * Returns the rules associated with a sprite, in priority order.
     */
    public java.util.List<CtmRule> rulesForSprite(NamespaceId sprite) {
        return ruleSet.rulesForSprite(sprite);
    }

    /**
     * Returns the rules associated with a block id, in priority order.
     */
    public java.util.List<CtmRule> rulesForBlock(String blockId) {
        return ruleSet.rulesForBlock(blockId);
    }

    /**
     * Convenience: returns the first rule whose method matches, or
     * an empty optional.
     */
    public Optional<CtmRule> firstRuleFor(NamespaceId sprite, String blockId,
                                          CtmMethod method) {
        for (CtmRule r : ruleSet.rulesForSprite(sprite)) {
            if (r.method() == method) {
                return Optional.of(r);
            }
        }
        for (CtmRule r : ruleSet.rulesForBlock(blockId)) {
            if (r.method() == method) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    /**
     * Convenience: returns all rules whose method matches the given
     * one, in priority order.
     */
    public java.util.List<CtmRule> rulesWithMethod(CtmMethod method) {
        java.util.List<CtmRule> out = new java.util.ArrayList<>();
        for (CtmRule r : ruleSet.all()) {
            if (r.method() == method) {
                out.add(r);
            }
        }
        return out;
    }
}
