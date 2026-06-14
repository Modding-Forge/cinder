package com.cinder.ctm;

import com.cinder.resource.NamespaceId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An index of {@link CtmRule} entries built during a resource reload.
 *
 * <p>The index is dual-keyed:
 *
 * <ul>
 *   <li>{@code bySprite} - a map from {@link NamespaceId} (the rendered
 *       sprite) to a list of rules. This is the hot path: the renderer
 *       looks up {@code bySprite.get(spriteId)} on every face and walks
 *       the list in priority order.</li>
 *   <li>{@code byBlock} - a map from canonical block-id string (e.g.
 *       {@code minecraft:oak_log}) to a list of rules. Used by the
 *       block-state fallback path when a sprite lookup misses.</li>
 * </ul>
 *
 * <p>Both lists are kept sorted at insert time: higher weight first,
 * then by source filename (alphabetical), then by source line.
 * {@code CtmRule.weight=0} is the default; weight ties are broken
 * alphabetically by source file, which matches the documented OptiFine
 * behavior of "first match wins, file order on ties".
 *
 * <p>Mutability: instances are immutable after {@link Builder#build()}.
 *
 * <p>Performance: lookup is O(1) for the outer map, plus a linear
 * scan over the per-sprite list (which is typically very short: 0-3
 * rules per sprite in well-formed resource packs).
 *
 * <p>Thread expectations: instances are immutable; concurrent reads
 * are safe.
 */
public final class CtmRuleSet {

    private final Map<NamespaceId, List<CtmRule>> bySprite;
    private final Map<String, List<CtmRule>> byBlock;
    private final List<CtmRule> all;
    private final CtmRenderIndex renderIndex;

    private CtmRuleSet(Map<NamespaceId, List<CtmRule>> bySprite,
                       Map<String, List<CtmRule>> byBlock,
                       List<CtmRule> all,
                       CtmRenderIndex renderIndex) {
        this.bySprite = bySprite;
        this.byBlock = byBlock;
        this.all = all;
        this.renderIndex = renderIndex;
    }

    /**
     * Returns the rules associated with a sprite, in priority order
     * (highest weight first, then alphabetical source-file order).
     * The returned list is unmodifiable.
     */
    public List<CtmRule> rulesForSprite(NamespaceId sprite) {
        List<CtmRule> list = bySprite.get(sprite);
        return list == null ? List.of() : list;
    }

    /**
     * Returns the rules associated with a block id (no namespace or
     * property filtering yet), in priority order. The returned
     * list is unmodifiable.
     */
    public List<CtmRule> rulesForBlock(String blockId) {
        List<CtmRule> list = byBlock.get(blockId);
        return list == null ? List.of() : list;
    }

    /**
     * Returns every rule, in priority order. The returned list is
     * unmodifiable.
     */
    public List<CtmRule> all() {
        return all;
    }

    /**
     * Returns the immutable hot-path prefilter built with this rule snapshot.
     */
    public CtmRenderIndex renderIndex() {
        return renderIndex;
    }

    /**
     * Mutable builder that collects rules during a single reload pass
 * and then sorts the result.
     */
    public static final class Builder {
        private final List<CtmRule> rules = new ArrayList<>();

        public Builder add(CtmRule rule) {
            rules.add(rule);
            return this;
        }

        public Builder addAll(List<CtmRule> more) {
            rules.addAll(more);
            return this;
        }

        public CtmRuleSet build() {
            // Sort once globally; the per-key lookups re-use this order.
            rules.sort(RULE_ORDER);
            Map<NamespaceId, List<CtmRule>> mutableBySprite = new HashMap<>();
            Map<String, List<CtmRule>> mutableByBlock = new HashMap<>();
            Map<NamespaceId, CtmRenderIndex.CandidateFlags> spriteFlags =
                    new HashMap<>();
            Map<String, CtmRenderIndex.CandidateFlags> blockFlags =
                    new HashMap<>();
            boolean hasOverlayCandidates = false;
            for (CtmRule r : rules) {
                CtmRenderIndex.CandidateFlags flags =
                        CtmRenderIndex.flagsFor(r);
                hasOverlayCandidates |= flags.hasOverlay();
                for (NamespaceId sprite : r.matchTiles()) {
                    mutableBySprite.computeIfAbsent(sprite, k -> new ArrayList<>()).add(r);
                    spriteFlags.merge(sprite, flags,
                            CtmRenderIndex.CandidateFlags::merge);
                }
                for (BlockSpec block : r.matchBlocks()) {
                    String key = block.namespace() + ":" + block.name();
                    mutableByBlock.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
                    blockFlags.merge(key, flags,
                            CtmRenderIndex.CandidateFlags::merge);
                }
            }
            // Phase 5: defensive copies + unmodifiable wrapping.
            // The rule set is documented as immutable; we make
            // sure no caller can mutate the internal storage
            // (e.g. by adding rules through a returned list or
            // map). Each per-key list is wrapped in
            // unmodifiableList; the maps themselves are wrapped
            // via unmodifiableMap on a copy.
            Map<NamespaceId, List<CtmRule>> finalBySprite = new HashMap<>();
            mutableBySprite.forEach((k, v) -> finalBySprite.put(
                    k, Collections.unmodifiableList(v)));
            Map<String, List<CtmRule>> finalByBlock = new HashMap<>();
            mutableByBlock.forEach((k, v) -> finalByBlock.put(
                    k, Collections.unmodifiableList(v)));
            return new CtmRuleSet(
                    Collections.unmodifiableMap(finalBySprite),
                    Collections.unmodifiableMap(finalByBlock),
                    Collections.unmodifiableList(rules),
                    new CtmRenderIndex(spriteFlags, blockFlags,
                            hasOverlayCandidates));
        }
    }

    private static final Comparator<CtmRule> RULE_ORDER = (a, b) -> {
        int w = Integer.compare(b.weight(), a.weight());
        if (w != 0) {
            return w;
        }
        String fa = a.sourceFile().orElse("");
        String fb = b.sourceFile().orElse("");
        int f = fa.compareTo(fb);
        if (f != 0) {
            return f;
        }
        return Integer.compare(a.sourceLine(), b.sourceLine());
    };

    /**
     * Returns an empty rule set. Useful in tests and as a safe default.
     */
    public static CtmRuleSet empty() {
        CtmRenderIndex emptyIndex = new CtmRenderIndex(
                Collections.emptyMap(), Collections.emptyMap(), false);
        return new CtmRuleSet(Collections.emptyMap(), Collections.emptyMap(),
                Collections.emptyList(), emptyIndex);
    }
}
