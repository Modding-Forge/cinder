package com.cinder.ctm;

import com.cinder.resource.NamespaceId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolved tile-to-sprite map for a single {@link CtmRule}.
 *
 * <p>The Phase 7 redesign of the OptiFine CTM tile pipeline
 * shifts tile resolution from a directory-scan-based atlas
 * injection (which does not handle OptiFine's nested
 * {@code default/} layout) to a per-rule map derived from the
 * rule's own {@code tiles=...} list and the source path of the
 * {@code .properties} file. This entry holds that map.
 *
 * <p>Each tile in the rule's tile list has a
 * {@link CtmTileResolver.Resolution}. Renderer-specific strategies may append
 * additional resolutions after those rule tiles, for example synthetic full
 * CTM entries used by {@code ctm_compact} material selection. Each resolution
 * gives:
 * <ul>
 *   <li>the sprite id the renderer should swap to (for numeric
 *       tiles, a {@code cinder:optifine/ctm/.../<n>} id; for
 *       named tiles, the sprite id already in some atlas);</li>
 *   <li>the resource-pack relative path of the PNG, when the
 *       loader-side adapter must inject the sprite into the
 *       block atlas.</li>
 * </ul>
 *
 * <h2>Lookup</h2>
 *
 * <p>The {@code CtmQuadDecorator} consults
 * {@link #spriteFor(int)} on the matched rule's entry: given a
 * concrete {@code tileIndex} from the selector, return the
 * sprite id to swap to.
 *
 * <h2>Immutability</h2>
 *
 * <p>The record is immutable. The list of resolutions is
 * defensively copied at construction time.
 */
public record CtmTileAtlasEntry(
        CtmRule rule,
        List<CtmTileResolver.Resolution> resolutions) {

    /**
     * Canonical constructor: validates inputs and wraps
     * the resolution list in an unmodifiable wrapper.
     */
    public CtmTileAtlasEntry {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(resolutions, "resolutions");
        if (resolutions.size() < rule.tiles().size()) {
            throw new IllegalArgumentException(
                    "resolutions.size() (" + resolutions.size()
                            + ") < rule.tiles().size() ("
                            + rule.tiles().size() + ")");
        }
        resolutions = List.copyOf(resolutions);
    }

    /**
     * Returns the sprite id for the given tile index, or
     * empty when the spec at that index is
     * {@code <skip>}/{@code <default>} (sentinel resolutions
     * have a {@code null} sprite id) or when the index is
     * out of range.
     */
    public Optional<NamespaceId> spriteFor(int tileIndex) {
        if (tileIndex < 0 || tileIndex >= resolutions.size()) {
            return Optional.empty();
        }
        return Optional.ofNullable(resolutions.get(tileIndex).resolvedSprite());
    }

    /**
     * Returns the resource path for the given tile index, or
     * empty when the spec at that index is a named tile (no
     * injection needed) or a special token.
     */
    public Optional<String> resourcePathFor(int tileIndex) {
        if (tileIndex < 0 || tileIndex >= resolutions.size()) {
            return Optional.empty();
        }
        return Optional.ofNullable(resolutions.get(tileIndex).resourcePath());
    }

    /**
     * Returns the resolution list as an unmodifiable view.
     */
    public List<CtmTileResolver.Resolution> resolutions() {
        return resolutions;
    }
}
