package com.cinder.quad;

import com.cinder.ctm.CtmRegistry;
import com.cinder.ctm.CtmRenderResolver;
import com.cinder.ctm.CtmRenderSelection;
import com.cinder.ctm.CtmRule;
import com.cinder.ctm.CtmTileAtlas;
import com.cinder.ctm.CtmTileAtlasEntry;
import com.cinder.ctm.NeighborView;
import com.cinder.platform.Platforms;
import com.cinder.resource.NamespaceId;
import com.cinder.verify.VerifyRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Cinder's default {@link QuadDecorator}: consults the
 * {@link CtmRegistry} for every quad and, if a rule matches,
 * replaces the quad's sprite with the selected CTM tile.
 *
 * <h2>Coordination with the renderer</h2>
 *
 * <p>The {@link NeighborView} that the selector needs is
 * loader-specific (Minecraft exposes it via
 * {@code BlockAndTintGetter}, but a third-party renderer
 * might not). The loader-side adapter stores that view in
 * {@link QuadContext} for each quad, so parallel section
 * builds never mutate shared decorator state.
 *
 * <p>If no provider is registered, the decorator falls back
 * to an empty view (the selector returns tile 0 for every
 * quad, which is the vanilla look - i.e. the decorator
 * becomes a no-op). This is the right behaviour for tests
 * and for renderers that cannot supply a 3x3x3 view.
 *
 * <h2>Verify mode</h2>
 *
 * <p>When {@code cinder.verify_mode} is enabled (via the
 * config), the decorator records match/mismatch counts via
 * {@link VerifyRecorder}. The comparison logic is
 * deliberately simple (vanilla would have selected tile 0
 * for everything; Cinder's selection is recorded) - the
 * renderer-side pipeline is responsible for comparing
 * against a full vanilla pass.
 *
 * <h2>Performance</h2>
 *
 * <p>The hot path is "no rule matches, no retexture". In
 * that case the decorator does one O(1) registry lookup
 * and returns {@link Optional#empty()}, allocating
 * nothing. The per-quad cost is comparable to a direct
 * mixin hook.
 */
public final class CtmQuadDecorator implements QuadDecorator {

    private static final Logger LOGGER =
            LoggerFactory.getLogger("cinder/ctm-decorator");

    private final NeighborView fixedView;

    public CtmQuadDecorator() {
        this(null);
    }

    private CtmQuadDecorator(NeighborView fixedView) {
        this.fixedView = fixedView;
    }

    @Override
    public String id() {
        return "cinder:ctm";
    }

    @Override
    public int priority() {
        // Run after any third-party decorator that wants to
        // veto the quad, but before generic post-processors
        // (priority 200+).
        return 100;
    }

    @Override
    public Optional<QuadRef> decorate(QuadRef quad, QuadContext context) {
        CtmRegistry registry = registryOrNull();
        if (registry == null) {
            return Optional.empty();
        }
        NeighborView view = context.neighborView() != null
                ? context.neighborView()
                : fixedView;
        if (view == null) {
            // No view: we cannot run the selector. Skip the
            // retexture and continue. This is the right
            // behaviour for tests and for renderers that do
            // not expose a neighbour view.
            VerifyRecorder.get().recordSkip();
            return Optional.empty();
        }
        CtmRenderSelection selection;
        try {
            selection = new CtmRenderResolver(registry).resolve(
                    context.blockId(), quad.sprite(), view,
                    context.x(), context.y(), context.z(), context.face());
        } catch (RuntimeException e) {
            // Defensive: a buggy selector or rule must not
            // crash the renderer. The verify mode records
            // the miss as a skip; the quad passes through.
            LOGGER.debug(
                    "[cinder] CTM selector failed for {}@{} face {}: {}",
                    context.blockId(),
                    context.x() + "," + context.y() + "," + context.z(),
                    context.faceName(), e.getMessage());
            VerifyRecorder.get().recordSkip();
            return Optional.empty();
        }
        if (selection == null) {
            VerifyRecorder.get().recordSkip();
            return Optional.empty();
        }
        if (selection.isPrimarySkip()) {
            // Sentinel: the rule said "do not render this
            // quad at all". The decorator does not yet
            // support quad suppression (that would require
            // a return type that includes a "delete" flag);
            // we treat it as "pass through" for now.
            VerifyRecorder.get().recordSkip();
            return Optional.empty();
        }
        if (selection.isPrimaryDefault()) {
            // Sentinel: fall back to the next rule. We do
            // not recurse here (the registry's lookup is
            // already priority-ordered; the next rule would
            // have matched first if it were applicable).
            VerifyRecorder.get().recordSkip();
            return Optional.empty();
        }
        // Concrete selection: retexture the quad to the
        // tile at the given index. The rule's tile list
        // determines which sprite each tile index maps to;
        // the loader-side adapter implements the actual
        // UV rewrite via QuadRef.withSprite.
        // Phase 7: we look up the sprite id in the
        // CtmTileAtlas (populated by the same reload that
        // built the rule set). When the atlas has no entry
        // for this rule, the swap is skipped (the atlas
        // was built from a different .properties parsing
        // pass and may not know about this rule yet during
        // the first reload - the next reload will fix it).
        VerifyRecorder.get().recordMatch();
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(
                    "[cinder] CTM match: {}@{} face {} -> tile {}",
                    context.blockId(),
                    context.x() + "," + context.y() + "," + context.z(),
                    context.faceName(), selection.primaryTileIndex());
        }
        return resolveSwap(quad, selection.rule(), selection.primaryTileIndex());
    }

    /**
     * Looks up the sprite id for a (rule, tileIndex) pair
     * and returns a swapped {@link QuadRef} when one is
     * available. Returns empty when the atlas does not
     * know about the rule, when the tile index has no
     * resolved sprite (e.g. {@code <skip>}, which is
     * handled earlier in the pipeline), or when the
     * loader-side adapter declines to swap.
     *
     * <p>Performance: HOT PATH (per quad with a matching
     * rule). Allocation policy: one Optional + zero to one
     * QuadRef (PendingSwap marker).
     */
    private static Optional<QuadRef> resolveSwap(
            QuadRef quad, CtmRule match, int tileIndex) {
        CtmTileAtlas atlas = CtmTileAtlas.current();
        if (atlas.isEmpty()) {
            // No rule has a resolved sprite map yet
            // (e.g. first reload, or no OptiFine .properties
            // were parsed). Pass through.
            return Optional.empty();
        }
        Optional<CtmTileAtlasEntry> entryOpt =
                atlas.findEntryForRule(match);
        if (entryOpt.isEmpty()) {
            return Optional.empty();
        }
        CtmTileAtlasEntry entry = entryOpt.get();
        Optional<NamespaceId> spriteOpt = entry.spriteFor(tileIndex);
        if (spriteOpt.isEmpty()) {
            return Optional.empty();
        }
        NamespaceId spriteId = spriteOpt.get();
        // The withSprite implementation (in the loader
        // adapter) returns a PendingSwap marker that the
        // renderer recognises and honours with a UV
        // remap. The shared QuadRef interface default
        // throws, so a non-loader adapter would fail
        // here; this is by design (we are opt-in to
        // the loader integration).
        try {
            QuadRef swapped = quad.withSprite(spriteId);
            return Optional.ofNullable(swapped);
        } catch (UnsupportedOperationException e) {
            // Loader adapter that does not implement
            // withSprite. Pass through; the rule is
            // still recorded as a match.
            return Optional.empty();
        } catch (RuntimeException e) {
            // Defensive: a buggy adapter must not crash
            // the renderer. Pass through and log at
            // debug.
            LOGGER.debug(
                    "[cinder] withSprite failed for sprite {}: {}",
                    spriteId, e.getMessage());
            return Optional.empty();
        }
    }

    private static CtmRegistry registryOrNull() {
        try {
            return Platforms.tryGet()
                    .map(p -> p.ctmRegistry())
                    .orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Convenience: register a {@link CtmQuadDecorator} with
     * a fixed {@link NeighborView} for testing. The view is
     * used for every quad; tests should pre-populate it.
     */
    public static CtmQuadDecorator forView(NeighborView view) {
        return new CtmQuadDecorator(view);
    }
}
