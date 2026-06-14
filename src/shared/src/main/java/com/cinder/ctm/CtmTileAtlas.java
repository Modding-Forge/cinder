package com.cinder.ctm;

import com.cinder.resource.NamespaceId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Process-wide registry of resolved CTM tile-to-sprite maps.
 *
 * <p>The registry is consulted at two distinct points:
 *
 * <ol>
 *   <li>During chunk build, by {@code CtmQuadDecorator}, to
 *       resolve a concrete {@code tileIndex} from the selector
 *       into a {@link NamespaceId} sprite id to swap to.</li>
 *   <li>During atlas stitching, by the loader-specific
 *       adapter, to enumerate every (sprite id, resource path)
 *       that must be injected into the block atlas via a
 *       custom {@code SpriteSource}.</li>
 * </ol>
 *
 * <p>The registry is backed by an {@link AtomicReference} so
 * that the renderer (which reads on the section-build thread)
 * and the resource-reload thread (which writes) can share the
 * same instance without locks.
 *
 * <h2>Layout</h2>
 *
 * <p>The atlas is a flat collection of
 * {@link CtmTileAtlasEntry} (one per rule) plus secondary
 * indices: from a sprite id to entries that match it, and
 * from a rule identity to its entry. All lookups are O(1).
 *
 * <h2>Threading</h2>
 *
 * <p>{@link #replace} must be called on the resource-reload
 * thread. {@link #current()} and the lookup methods are
 * lock-free reads.
 *
 * <h2>Performance</h2>
 *
 * <p>{@link #findEntryForRule} is O(1) via an internal map.
 * The renderer hot path consults {@link #current()} once per
 * section build (cached in a local) and looks up the entry
 * once per quad with a matching rule.
 *
 * <p>Performance: HOT PATH (per quad with a matching rule).
 * Allocation policy: none (returns the cached entry by ref).
 */
public final class CtmTileAtlas {

    /** Empty atlas returned by {@link #empty()}. */
    private static final CtmTileAtlas EMPTY =
            new CtmTileAtlas(List.of(), Map.of(), Map.of());

    private final List<CtmTileAtlasEntry> entries;
    /** Sprite/block id -> entries that match this id. O(1) lookup. */
    private final Map<String, List<CtmTileAtlasEntry>> bySpriteOrBlock;
    /** Rule identity -> entry. O(1) lookup. */
    private final Map<CtmRule, CtmTileAtlasEntry> byRule;

    private CtmTileAtlas(List<CtmTileAtlasEntry> entries,
                         Map<String, List<CtmTileAtlasEntry>> bySpriteOrBlock,
                         Map<CtmRule, CtmTileAtlasEntry> byRule) {
        this.entries = List.copyOf(entries);
        this.bySpriteOrBlock = Map.copyOf(bySpriteOrBlock);
        this.byRule = Map.copyOf(byRule);
    }

    /**
     * Returns the empty atlas, used when no resource pack
     * contributed any OptiFine CTM rules.
     */
    public static CtmTileAtlas empty() {
        return EMPTY;
    }

    /**
     * Builds a new atlas from the given entries. The
     * secondary indices are populated as a side effect of
     * construction; the caller does not need to populate
     * them.
     */
    public static CtmTileAtlas of(List<CtmTileAtlasEntry> entries) {
        Objects.requireNonNull(entries, "entries");
        if (entries.isEmpty()) {
            return EMPTY;
        }
        HashMap<String, List<CtmTileAtlasEntry>> bySpriteOrBlock =
                new HashMap<>();
        HashMap<CtmRule, CtmTileAtlasEntry> byRule =
                new HashMap<>();
        for (CtmTileAtlasEntry e : entries) {
            byRule.put(e.rule(), e);
            for (NamespaceId matchTile : e.rule().matchTiles()) {
                bySpriteOrBlock.computeIfAbsent(
                        matchTile.toString(), k -> new ArrayList<>())
                        .add(e);
            }
            for (BlockSpec matchBlock : e.rule().matchBlocks()) {
                String ns = matchBlock.namespace();
                String name = matchBlock.name();
                if (ns == null || name == null) {
                    continue;
                }
                String blockId = ns + ":" + name;
                bySpriteOrBlock.computeIfAbsent(
                        blockId, k -> new ArrayList<>())
                        .add(e);
            }
        }
        // Freeze the inner lists to unmodifiable.
        HashMap<String, List<CtmTileAtlasEntry>> frozen =
                new HashMap<>();
        for (var kv : bySpriteOrBlock.entrySet()) {
            frozen.put(kv.getKey(),
                    Collections.unmodifiableList(kv.getValue()));
        }
        return new CtmTileAtlas(entries, frozen, byRule);
    }

    /**
     * Returns all entries. The returned list is unmodifiable.
     */
    public List<CtmTileAtlasEntry> entries() {
        return entries;
    }

    /**
     * Returns the entries that have a {@code matchTiles}
     * entry for the given sprite id (e.g.
     * {@code minecraft:block/glass}), in the same order they
     * were added.
     */
    public List<CtmTileAtlasEntry> findEntriesForSprite(NamespaceId sprite) {
        if (sprite == null) {
            return List.of();
        }
        return findEntriesForBlockId(sprite.toString());
    }

    /**
     * Returns the entries that have a {@code matchBlocks}
     * entry for the given canonical block id (e.g.
     * {@code minecraft:tinted_glass}), in the same order
     * they were added.
     */
    public List<CtmTileAtlasEntry> findEntriesForBlockId(String blockId) {
        if (blockId == null || bySpriteOrBlock.isEmpty()) {
            return List.of();
        }
        List<CtmTileAtlasEntry> list = bySpriteOrBlock.get(blockId);
        return list == null ? List.of() : list;
    }

    /**
     * Returns the entry for the given rule, or empty when
     * the rule is not in the atlas.
     */
    public Optional<CtmTileAtlasEntry> findEntryForRule(CtmRule rule) {
        if (rule == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byRule.get(rule));
    }

    /**
     * Returns true if no entries are present.
     */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * Returns the total number of numeric tile resolutions
     * (those that need a PNG injection) across all entries.
     * Useful for the loader-side adapter when sizing or
     * logging.
     */
    public int numericTileCount() {
        int sum = 0;
        for (CtmTileAtlasEntry e : entries) {
            for (CtmTileResolver.Resolution r : e.resolutions()) {
                if (r.needsInjection()) {
                    sum++;
                }
            }
        }
        return sum;
    }

    // ---- process-wide holder ----

    private static final AtomicReference<CtmTileAtlas> INSTANCE =
            new AtomicReference<>(EMPTY);

    /**
     * Returns the current process-wide atlas. Never null.
     */
    public static CtmTileAtlas current() {
        return INSTANCE.get();
    }

    /**
     * Atomically replaces the process-wide atlas. The
     * renderer threads observe the new instance on their
     * next read.
     */
    public static void replace(CtmTileAtlas atlas) {
        Objects.requireNonNull(atlas, "atlas");
        INSTANCE.set(atlas);
    }

    /**
     * Resets to the empty atlas. Used in tests.
     */
    public static void resetForTest() {
        INSTANCE.set(EMPTY);
    }
}
