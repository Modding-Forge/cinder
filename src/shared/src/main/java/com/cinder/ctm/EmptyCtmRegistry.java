package com.cinder.ctm;

import com.cinder.resource.NamespaceId;

import java.util.List;
import java.util.Optional;

/**
 * Read-only fallback {@link CtmRegistry} that always returns empty
 * results. Returned by {@link CtmRegistry#empty(String)} and used
 * by loaders that have not (yet) populated the registry, so callers
 * can rely on a non-null {@code ctmRegistry()} at all times.
 *
 * <p>This class is package-private; the only public entry point is
 * {@link CtmRegistry#empty(String)}.
 */
final class EmptyCtmRegistry extends CtmRegistry {

    EmptyCtmRegistry(String modId) {
        super(modId);
    }

    @Override
    public void replace(CtmRuleSet newSet) {
        // No-op: an empty registry is always empty.
    }

    @Override
    public List<CtmRule> rulesForSprite(NamespaceId sprite) {
        return List.of();
    }

    @Override
    public List<CtmRule> rulesForBlock(String blockId) {
        return List.of();
    }

    @Override
    public Optional<CtmRule> firstRuleFor(NamespaceId sprite, String blockId,
                                         CtmMethod method) {
        return Optional.empty();
    }

    @Override
    public List<CtmRule> rulesWithMethod(CtmMethod method) {
        return List.of();
    }
}
