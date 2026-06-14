package com.cinder.ctm;

import com.cinder.resource.NamespaceId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * High-level CTM selection resolver for renderer integrations.
 *
 * <p>The resolver takes the renderer's current block id, base sprite, face,
 * position, and neighbour view, then returns a backend-neutral
 * {@link CtmRenderPlan}. It deliberately stops before any rendering
 * operation such as sprite swapping, UV mutation, or GPU buffer writes.
 *
 * <h2>Threading</h2>
 *
 * <p>The resolver is stateless and thread-safe. It reads immutable
 * {@link CtmRuleSet} snapshots through {@link CtmRegistry}.
 *
 * <h2>Performance</h2>
 *
 * <p>Performance: HOT PATH. Allocation policy: no allocation when the
 * prefilter has no candidates; one {@link CtmSelector} and one
 * {@link CtmRenderPlan} when CTM work is selected. Callers may keep a resolver
 * instance per section build to avoid repeatedly allocating it.
 */
public final class CtmRenderResolver {

    private final CtmRegistry registry;

    public CtmRenderResolver(CtmRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /**
     * Returns {@code true} when the active rule snapshot has any candidate for
     * the sprite or block on the requested face.
     */
    public boolean hasCandidates(String blockId, NamespaceId baseSprite,
                                 int face) {
        if (blockId == null || baseSprite == null) {
            return false;
        }
        return registry.ruleSet().renderIndex()
                .hasCandidate(baseSprite, blockId, face);
    }

    /**
     * Resolves applicable CTM render work for the given face.
     *
     * <p>Non-overlay methods keep OptiFine-style first-match behaviour.
     * Overlay methods are compositing rules, so matching overlay tiles before
     * the selected replacement are preserved in rule order.
     */
    public CtmRenderPlan resolvePlan(String blockId,
                                     NamespaceId baseSprite,
                                     NeighborView view,
                                     int x, int y, int z,
                                     int face) {
        if (blockId == null || baseSprite == null || view == null) {
            return null;
        }
        CtmRuleSet ruleSet = registry.ruleSet();
        if (!ruleSet.renderIndex().hasCandidate(baseSprite, blockId, face)) {
            return null;
        }
        CtmSelector selector = new CtmSelector(ruleSet);
        ArrayList<CtmOverlayTile> overlays = new ArrayList<>(4);
        CtmRenderSelection selection = collectFromRules(
                selector,
                ruleSet.rulesForSprite(baseSprite),
                view, x, y, z, face, baseSprite, overlays);
        if (selection != null) {
            return CtmRenderPlan.of(selection, overlays);
        }
        selection = collectFromRules(
                selector,
                ruleSet.rulesForBlock(blockId),
                view, x, y, z, face, baseSprite, overlays);
        if (selection != null) {
            return CtmRenderPlan.of(selection, overlays);
        }
        if (!overlays.isEmpty()) {
            return CtmRenderPlan.overlays(overlays);
        }
        return null;
    }

    /**
     * Compatibility wrapper for existing callers that expect one selection.
     */
    public CtmRenderSelection resolve(String blockId,
                                      NamespaceId baseSprite,
                                      NeighborView view,
                                      int x, int y, int z,
                                      int face) {
        CtmRenderPlan plan = resolvePlan(blockId, baseSprite, view, x, y, z,
                face);
        if (plan == null) {
            return null;
        }
        if (plan.hasReplacement()) {
            return plan.replacement();
        }
        return CtmRenderSelection.overlayStack(face, baseSprite,
                plan.overlays());
    }

    private static CtmRenderSelection collectFromRules(
            CtmSelector selector,
            List<CtmRule> rules,
            NeighborView view,
            int x, int y, int z,
            int face,
            NamespaceId baseSprite,
            ArrayList<CtmOverlayTile> overlays) {
        for (CtmRule rule : rules) {
            CtmRenderSelection selection = selector.selectRender(
                    rule, view, x, y, z, face, baseSprite);
            if (selection == null || selection.isPrimarySkip()) {
                continue;
            }
            if (selection.isOverlay()) {
                overlays.addAll(selection.overlayTiles());
            } else {
                return selection;
            }
        }
        return null;
    }
}
