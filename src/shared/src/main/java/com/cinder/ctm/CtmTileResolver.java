package com.cinder.ctm;

import com.cinder.resource.NamespaceId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves the per-tile sprite identifiers for a single parsed
 * {@link CtmRule}.
 *
 * <p>The OptiFine CTM format stores each rule's tile list in the
 * {@code tiles=...} key of a {@code .properties} file. The tile entries
 * are references to images in the same resource pack. To inject the
 * tile PNGs into the vanilla block atlas (or any other atlas) we must
 * turn each {@link CtmTileSpec} into:
 * <ol>
 *   <li>A {@link NamespaceId} for the sprite as it will be looked up
 *       in the atlas.</li>
 *   <li>The resource-pack relative path of the PNG file, for the
 *       loader-side adapter to resolve via the resource manager.</li>
 * </ol>
 *
 * <h2>Resolution rules (Phase 7)</h2>
 *
 * <p>The OptiFine layout is:
 * <pre>
 *   assets/&lt;ns&gt;/optifine/ctm/&lt;family_key&gt;/&lt;tile_index&gt;.png
 * </pre>
 *
 * <p>Numeric tile entries (e.g. {@code 0}, {@code 1}) and numeric
 * ranges (e.g. {@code 0-46}) refer to the PNG whose file name is the
 * integer tile index in the same directory as the
 * {@code .properties} file. The corresponding sprite is registered in
 * our own {@code cinder} namespace as
 * {@code cinder:ctm/&lt;dir-relative-path&gt;/&lt;tile_index&gt;},
 * so that the vanilla block atlas can host them.
 *
 * <p>Named tile entries (e.g. {@code glass}, {@code 1.png}, or
 * absolute paths) already point at a sprite in a real atlas; we
 * reuse the existing sprite id and do <b>not</b> emit an injection
 * entry. Such tiles are skipped on the resource-resolution side
 * (they are already in the atlas by some other path), but the
 * returned {@link Resolution} still carries the {@code resolvedSprite}
 * for the renderer.
 *
 * <p>Special tokens {@code <skip>} and {@code <default>} resolve to
 * a {@code null} sprite id and a {@code null} resource path; the
 * loader side uses them to decide whether to drop the quad or to
 * fall through to the vanilla selection.
 *
 * <h2>Why a class and not a method on {@link CtmRule}?</h2>
 *
 * <p>Resolution needs the {@code .properties} source path (the
 * directory the rule was loaded from), which the rule itself does
 * not carry in its public API. Keeping the resolver separate also
 * makes it unit-testable without the rule builder.
 *
 * <h2>Performance</h2>
 *
 * <p>O(n) over the rule's tile list. Allocation policy: one
 * {@link Resolution} per tile, no other objects on the hot path.
 *
 * <p>Performance: NOT on hot path. Runs once per resource reload per
 * rule.
 */
public final class CtmTileResolver {

    /**
     * The namespace into which Cinder injects OptiFine CTM tile
     * sprites. Kept in the {@code cinder} namespace so that the
     * vanilla block atlas can host the sprites without colliding
     * with vanilla block sprite ids.
     */
    public static final String CINDER_NAMESPACE = "cinder";

    /**
     * The path prefix that precedes the rule's directory under the
     * resource root. Used to build sprite ids of the form
     * {@code cinder:optifine/ctm/<dir>/<index>}.
     */
    public static final String CTM_PATH_PREFIX = "optifine/ctm/";

    /**
     * Synthetic tile-index range used by renderer-facing compact CTM
     * selections. These indices do not address {@link CtmRule#tiles()}; they
     * address injected material entries generated from a compact rule's five
     * source tiles.
     */
    public static final int COMPACT_FULL_TILE_OFFSET = 1024;

    /** Number of full CTM template slots per rendered face. */
    public static final int COMPACT_FULL_TILE_COUNT = 47;

    /** Number of face-specific compact full material slots. */
    public static final int COMPACT_FULL_RENDER_TILE_COUNT =
            COMPACT_FULL_TILE_COUNT * 6;

    private CtmTileResolver() {
    }

    /**
     * The resolution of a single {@link CtmTileSpec}.
     *
     * <p>Fields:
     * <ul>
     *   <li>{@code tileIndex} - the index of the spec in the rule's
     *       {@code tiles} list; matches
     *       {@link CtmSelectionResult#tileIndex()} for selector
     *       output.</li>
     *   <li>{@code resolvedSprite} - the sprite id the renderer
     *       should swap to. {@code null} for {@code <skip>} and
     *       {@code <default>}.</li>
     *   <li>{@code resourcePath} - the resource-pack relative path
     *       of an explicit PNG, or {@code null} when the sprite must
     *       be generated from a base texture, already exists in the
     *       atlas under its own name (named spec), or is a special
     *       token.</li>
     *   <li>{@code needsInjection} - {@code true} if the loader-side
     *       adapter must call {@code output.add} for this entry. This
     *       includes explicit PNGs and generated fallback sprites.</li>
     *   <li>{@code fallbackSourceSprite} - source sprite used to
     *       generate a fallback tile when a numeric PNG is absent.</li>
     * </ul>
     */
    public record Resolution(
            int tileIndex,
            NamespaceId resolvedSprite,
            String resourcePath,
            boolean needsInjection,
            NamespaceId fallbackSourceSprite) {

        public Resolution(int tileIndex,
                          NamespaceId resolvedSprite,
                          String resourcePath,
                          boolean needsInjection) {
            this(tileIndex, resolvedSprite, resourcePath, needsInjection, null);
        }

        public Resolution {
            // resolvedSprite may be null for <skip>/<default>;
            // resourcePath may be null when no explicit PNG exists.
            // fallbackSourceSprite is non-null only for generated
            // fallback tiles.
        }

        /**
         * Returns true if the resolution has a sprite the renderer
         * can use. Skips ({@code <skip>}) and defaults
         * ({@code <default>}) return false.
         */
        public boolean isConcrete() {
            return resolvedSprite != null;
        }
    }

    /**
     * Resolves the rule's tile list given the source resource path
     * of the originating {@code .properties} file. Every numeric
     * tile is treated as needing injection; use
     * {@link #resolve(CtmRule, String, java.util.function.IntPredicate)}
     * when the resource manager is available so that numeric
     * tiles with no matching PNG can become generated fallback
     * sprites derived from the rule's fallback source sprite.
     *
     * @param rule          the parsed rule
     * @param propertiesResourcePath the resource-pack relative path
     *                              of the {@code .properties} file
     *                              (e.g.
     *                              {@code minecraft:optifine/ctm/default/20_glass/glass.properties})
     * @return a list of {@link Resolution} entries, one per tile in
     *         {@code rule.tiles()}, in the same order
     */
    public static List<Resolution> resolve(CtmRule rule, String propertiesResourcePath) {
        return resolve(rule, propertiesResourcePath, null);
    }

    /**
     * Resolves the rule's tile list with awareness of which tile
     * PNGs actually exist in the resource pack.
     *
     * <p>For each numeric tile spec, the resolver asks
     * {@code tileExists.test(index)} whether the corresponding
     * {@code <dir>/<index>.png} is present. When the PNG is
     * present, the resolver returns a {@code cinder:optifine/ctm/.../<n>}
     * sprite id with {@code needsInjection=true}. When the PNG is
     * <b>absent</b>, the resolver still returns a stable
     * {@code cinder:optifine/ctm/.../<n>} sprite id. Its
     * {@code resourcePath} is {@code null}, and
     * {@code fallbackSourceSprite} points at the rule's fallback source
     * sprite so the loader-side atlas source can generate a transitional
     * tile sprite. Ordinary CTM rules use the rule's base block sprite.
     * Overlay rules first prefer {@code connectTiles}, because
     * properties-only transition packs use that key to name the texture
     * that should be copied from the active base resource pack.
     *
     * <p>This is the transitional atlas-backed realization of the
     * OptiFine default. The shared selector still returns only the
     * tile index; Fabric generates atlas sprites so the current CPU
     * quad-swap path can make the result visible. A future backend can
     * consume the same tile index through material or texture-array
     * metadata instead.
     *
     * <p>When {@code tileExists} is {@code null}, every numeric
     * tile is treated as needing injection (the same behaviour
     * as the no-arg overload).
     *
     * @param rule          the parsed rule
     * @param propertiesResourcePath resource-pack relative path of
     *                              the {@code .properties} file
     * @param tileExists    a predicate that returns true when the
     *                      PNG for the given numeric tile index
     *                      exists; may be {@code null}
     * @return a list of {@link Resolution} entries, one per tile
     *         in {@code rule.tiles()}, in the same order
     */
    public static List<Resolution> resolve(CtmRule rule,
                                            String propertiesResourcePath,
                                            java.util.function.IntPredicate tileExists) {
        Objects.requireNonNull(rule, "rule");
        List<CtmTileSpec> tiles = rule.tiles();
        List<Resolution> out = new ArrayList<>(tiles.size()
                + (rule.method() == CtmMethod.CTM_COMPACT
                ? COMPACT_FULL_RENDER_TILE_COUNT : 0));
        if (tiles.isEmpty()) {
            return out;
        }
        // The tile images live in the same directory as the
        // .properties file. The directory is the parent of
        // propertiesResourcePath. For
        //   "minecraft:optifine/ctm/default/20_glass/glass.properties"
        // the directory is
        //   "minecraft:optifine/ctm/default/20_glass"
        String dirPath = propertiesDirectoryPath(propertiesResourcePath);
        // For sprite ids we use the "cinder" namespace and a
        // human-readable path: cinder:optifine/ctm/<dir>/<index>.
        // The <dir> is the directory portion relative to the
        // resource root's "optifine/ctm/" prefix (e.g. "default/20_glass").
        String dirForSprite = stripOptifinePrefix(dirPath);
        // Resolve the missing-PNG fallback source once. For overlay
        // transition rules this is usually the connect texture, not the
        // substrate listed in matchBlocks.
        NamespaceId fallbackSprite = fallbackSourceSprite(rule);
        for (int i = 0; i < tiles.size(); i++) {
            CtmTileSpec spec = tiles.get(i);
            out.add(resolveSpec(spec, i, dirPath, dirForSprite,
                    fallbackSprite, tileExists));
        }
        appendCompactFullResolutions(rule, out, dirForSprite, fallbackSprite);
        return out;
    }

    /**
     * Returns true when {@code tileIndex} is in the synthetic renderer-only
     * compact CTM full-tile range.
     */
    public static boolean isCompactFullTileIndex(int tileIndex) {
        return tileIndex >= COMPACT_FULL_TILE_OFFSET
                && tileIndex < COMPACT_FULL_TILE_OFFSET
                + COMPACT_FULL_RENDER_TILE_COUNT;
    }

    /**
     * Converts a synthetic compact full-tile index back to the public
     * 47-tile CTM template index.
     */
    public static int compactFullTileIndex(int tileIndex) {
        if (!isCompactFullTileIndex(tileIndex)) {
            throw new IllegalArgumentException(
                    "not a compact full tile index: " + tileIndex);
        }
        return (tileIndex - COMPACT_FULL_TILE_OFFSET)
                % COMPACT_FULL_TILE_COUNT;
    }

    /**
     * Converts a synthetic compact full-tile index to the face ordinal it was
     * generated for.
     */
    public static int compactFullTileFace(int tileIndex) {
        if (!isCompactFullTileIndex(tileIndex)) {
            throw new IllegalArgumentException(
                    "not a compact full tile index: " + tileIndex);
        }
        return (tileIndex - COMPACT_FULL_TILE_OFFSET)
                / COMPACT_FULL_TILE_COUNT;
    }

    /**
     * Computes the base block sprite for a rule. The base sprite
     * is derived from the rule's first {@code matchBlocks} entry
     * (e.g. {@code minecraft:glass} becomes
     * {@code minecraft:block/glass}). If the rule has no
     * {@code matchBlocks}, the first {@code matchTiles} entry is
     * used as a fallback. Returns {@code null} when the rule
     * has neither.
     */
    public static NamespaceId baseBlockSprite(CtmRule rule) {
        if (!rule.matchBlocks().isEmpty()) {
            BlockSpec b = rule.matchBlocks().get(0);
            return new NamespaceId(b.namespace(), "block/" + b.name());
        }
        if (!rule.matchTiles().isEmpty()) {
            // The matchTiles already carry a sprite id
            // (typically "minecraft:block/<name>"); reuse as-is.
            return rule.matchTiles().get(0);
        }
        return null;
    }

    /**
     * Computes the source sprite used to synthesize missing numeric tile PNGs.
     *
     * <p>Most methods derive generated sprites from the matched base block.
     * Overlay methods are different: properties-only transition packs commonly
     * list many substrate blocks in {@code matchBlocks} and name the overlay
     * material through {@code connectTiles}. In that shape the first
     * {@code connectTiles} entry is the texture that should be copied from the
     * active base resource pack.
     */
    public static NamespaceId fallbackSourceSprite(CtmRule rule) {
        if (rule.method().isOverlay()) {
            NamespaceId overlaySprite = overlaySourceSprite(rule);
            if (overlaySprite != null) {
                return overlaySprite;
            }
        }
        return baseBlockSprite(rule);
    }

    private static NamespaceId overlaySourceSprite(CtmRule rule) {
        if (!rule.connectTiles().isEmpty()) {
            return rule.connectTiles().get(0);
        }
        if (!rule.connectBlocks().isEmpty()) {
            BlockSpec b = rule.connectBlocks().get(0);
            return new NamespaceId(b.namespace(), "block/" + b.name());
        }
        return null;
    }

    private static Resolution resolveSpec(CtmTileSpec spec,
                                            int index,
                                            String dirPath,
                                            String dirForSprite,
                                            NamespaceId baseSprite,
                                            java.util.function.IntPredicate tileExists) {
        // Special tokens: <skip> and <default>.
        if (spec.isSkip() || spec.isDefault()) {
            return new Resolution(index, null, null, false);
        }
        if (spec.isNumeric()) {
            return resolveNumericFile(index, spec.numericIndex(),
                    dirPath, dirForSprite, baseSprite, tileExists);
        }
        // Named tile: sprite is already in some atlas under the
        // resolved name; do not inject, but expose the resolved
        // sprite so the renderer can swap to it.
        return new Resolution(index, spec.resolvedSprite(), null, false);
    }

    private static void appendCompactFullResolutions(
            CtmRule rule,
            List<Resolution> out,
            String dirForSprite,
            NamespaceId baseSprite) {
        if (rule.method() != CtmMethod.CTM_COMPACT) {
            return;
        }
        int[] overrides = rule.ctmOverrides();
        for (int full = 0; full < COMPACT_FULL_TILE_COUNT; full++) {
            if (full == 0) {
                continue;
            }
            if (overrides != null
                    && full < overrides.length
                    && overrides[full] >= 0) {
                continue;
            }
            for (int face = Faces.DOWN; face <= Faces.EAST; face++) {
                out.add(resolveCompactFullFile(
                        COMPACT_FULL_TILE_OFFSET
                                + face * COMPACT_FULL_TILE_COUNT
                                + full,
                        full,
                        face,
                        dirForSprite,
                        baseSprite));
            }
        }
    }

    private static Resolution resolveCompactFullFile(
            int tileIndex,
            int fileIndex,
            int face,
            String dirForSprite,
            NamespaceId baseSprite) {
        String spritePath = CTM_PATH_PREFIX + dirForSprite
                + "/generated_face_" + face + "/" + fileIndex;
        NamespaceId spriteId = new NamespaceId(CINDER_NAMESPACE, spritePath);
        // Compact CTM's numeric files in the rule tile list are compact source
        // tiles, not implicit full 47-template PNGs. Non-overridden full cases
        // must always be synthesized from the compact source set; explicit
        // ctm.N overrides are resolved through the normal rule tile list and
        // skipped above.
        return new Resolution(tileIndex, spriteId, null, true, baseSprite);
    }

    private static Resolution resolveNumericFile(
            int tileIndex,
            int fileIndex,
            String dirPath,
            String dirForSprite,
            NamespaceId baseSprite,
            java.util.function.IntPredicate tileExists) {
        String spritePath = CTM_PATH_PREFIX + dirForSprite + "/" + fileIndex;
        NamespaceId spriteId = new NamespaceId(CINDER_NAMESPACE, spritePath);
        if (tileExists == null || tileExists.test(fileIndex)) {
            String resourcePath = dirPath + "/" + fileIndex + ".png";
            return new Resolution(tileIndex, spriteId, resourcePath, true);
        }
        return new Resolution(tileIndex, spriteId, null, true, baseSprite);
    }

    /**
     * Strips the leading {@code "minecraft:"} namespace and any
     * {@code "optifine/ctm/"} prefix from a directory path to produce
     * a fragment suitable for inclusion in a sprite id.
     */
    static String stripOptifinePrefix(String dirPath) {
        if (dirPath == null) {
            return "";
        }
        String stripped = dirPath;
        int colon = stripped.indexOf(':');
        if (colon >= 0) {
            stripped = stripped.substring(colon + 1);
        }
        if (stripped.startsWith(CTM_PATH_PREFIX)) {
            stripped = stripped.substring(CTM_PATH_PREFIX.length());
        }
        return stripped;
    }

    /**
     * Computes the directory portion of a {@code .properties} file's
     * resource path. For
     * {@code "minecraft:optifine/ctm/default/20_glass/glass.properties"}
     * the result is
     * {@code "minecraft:optifine/ctm/default/20_glass"}.
     */
    public static String propertiesDirectoryPath(String resourcePath) {
        if (resourcePath == null || resourcePath.isEmpty()) {
            throw new IllegalArgumentException(
                    "propertiesResourcePath is null/empty");
        }
        int colon = resourcePath.indexOf(':');
        int lastSlash = resourcePath.lastIndexOf('/');
        if (lastSlash < 0) {
            // File at the root: directory is the namespace only.
            if (colon < 0) {
                throw new IllegalArgumentException(
                        "cannot derive directory from " + resourcePath);
            }
            return resourcePath.substring(0, colon);
        }
        if (colon < 0) {
            return resourcePath.substring(0, lastSlash);
        }
        // Keep the namespace portion of the directory.
        int dirColon = resourcePath.lastIndexOf(':', lastSlash);
        int start = (dirColon < 0) ? 0 : 0;
        // Build "<ns>:<dir-without-filename>".
        String namespace = resourcePath.substring(0, colon);
        String pathPart = resourcePath.substring(colon + 1);
        int slashInPath = pathPart.lastIndexOf('/');
        String dirInPath = slashInPath < 0 ? "" : pathPart.substring(0, slashInPath);
        if (dirInPath.isEmpty()) {
            return namespace;
        }
        return namespace + ":" + dirInPath;
    }

    /**
     * Convenience: returns the first {@link Resolution} with a
     * matching tile index, or empty when the rule has no such tile.
     */
    public static Optional<Resolution> findResolution(List<Resolution> resolved, int tileIndex) {
        for (Resolution r : resolved) {
            if (r.tileIndex() == tileIndex) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }
}
