package com.cinder.ctm;

import com.cinder.resource.NamespaceId;

import java.util.List;
import java.util.Objects;

/**
 * Backend-neutral CTM selection metadata for one rendered block face.
 *
 * <p>This type is the boundary between CTM behaviour and renderer
 * realization. The selector decides which rule, method, tile indices,
 * and layers apply; Fabric or another backend later decides whether that
 * becomes a Vulkan material id, texture-array layer, shader lookup, or a
 * temporary debug sprite swap.
 *
 * <h2>Threading</h2>
 *
 * <p>Instances are immutable and may be shared freely between section-build
 * tasks after construction. They hold references to immutable rule and tile
 * objects.
 *
 * <h2>Performance</h2>
 *
 * <p>Performance: HOT PATH. Allocation policy: one small immutable object per
 * matching CTM face when renderer-facing metadata is requested. The older
 * {@link CtmSelectionResult} path remains available for tests and callers that
 * only need a primitive tile index.
 */
public record CtmRenderSelection(
        CtmRule rule,
        CtmMethod method,
        int face,
        int primaryTileIndex,
        int secondaryTileIndex,
        int flags,
        NamespaceId baseSprite,
        CtmTileSpec primaryTile,
        CtmTileSpec secondaryTile,
        List<Integer> overlayTileIndices,
        List<CtmOverlayTile> overlayTiles) {

    /** No second layer was selected. */
    public static final int NO_TILE = -3;

    /** The rule method has a second renderer-visible layer. */
    public static final int FLAG_LAYERED = 1;

    /** The rule method is an overlay variant. */
    public static final int FLAG_OVERLAY = 1 << 1;

    /** Primary selection resolved to {@code <skip>}. */
    public static final int FLAG_PRIMARY_SKIP = 1 << 2;

    /** Primary selection resolved to {@code <default>}. */
    public static final int FLAG_PRIMARY_DEFAULT = 1 << 3;

    /** Secondary selection resolved to {@code <skip>}. */
    public static final int FLAG_SECONDARY_SKIP = 1 << 4;

    /** Secondary selection resolved to {@code <default>}. */
    public static final int FLAG_SECONDARY_DEFAULT = 1 << 5;

    /**
     * Canonical constructor: validates stable renderer-facing invariants.
     */
    public CtmRenderSelection {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(baseSprite, "baseSprite");
        overlayTileIndices = List.copyOf(overlayTileIndices);
        overlayTiles = List.copyOf(overlayTiles);
        if (face < Faces.DOWN || face > Faces.EAST) {
            throw new IllegalArgumentException("face out of range: " + face);
        }
    }

    /**
     * Builds a renderer-facing selection from primitive selector results.
     *
     * <p>The secondary result may be {@code null} for one-layer methods.
     */
    public static CtmRenderSelection from(CtmRule rule, int face,
                                          NamespaceId baseSprite,
                                          CtmSelectionResult primary,
                                          CtmSelectionResult secondary) {
        Objects.requireNonNull(primary, "primary");

        int flags = methodFlags(rule.method());
        int primaryIndex = primary.tileIndex();
        int secondaryIndex = secondary == null ? NO_TILE : secondary.tileIndex();

        if (primary.isSkip()) {
            flags |= FLAG_PRIMARY_SKIP;
        } else if (primary.isDefault()) {
            flags |= FLAG_PRIMARY_DEFAULT;
        }

        if (secondary != null) {
            flags |= FLAG_LAYERED;
            if (secondary.isSkip()) {
                flags |= FLAG_SECONDARY_SKIP;
            } else if (secondary.isDefault()) {
                flags |= FLAG_SECONDARY_DEFAULT;
            }
        }

        return new CtmRenderSelection(
                rule,
                rule.method(),
                face,
                primaryIndex,
                secondaryIndex,
                flags,
                baseSprite,
                tileSpec(rule, primaryIndex),
                tileSpec(rule, secondaryIndex),
                overlayIndices(rule, primaryIndex),
                overlayTiles(rule, primaryIndex));
    }

    /**
     * Builds a renderer-facing overlay selection carrying all overlay tiles
     * that should be composited over the base face.
     */
    public static CtmRenderSelection overlay(CtmRule rule, int face,
                                             NamespaceId baseSprite,
                                             List<Integer> tileIndices) {
        int primaryIndex = tileIndices.isEmpty()
                ? CtmSelectionResult.SKIP_INDEX
                : tileIndices.getFirst();
        int flags = methodFlags(rule.method());
        if (tileIndices.isEmpty()) {
            flags |= FLAG_PRIMARY_SKIP;
        }
        return new CtmRenderSelection(
                rule,
                rule.method(),
                face,
                primaryIndex,
                NO_TILE,
                flags,
                baseSprite,
                tileSpec(rule, primaryIndex),
                null,
                tileIndices,
                overlayTiles(rule, tileIndices));
    }

    /**
     * Builds a renderer-facing overlay selection from stacked overlay tiles.
     */
    public static CtmRenderSelection overlayStack(
            int face,
            NamespaceId baseSprite,
            List<CtmOverlayTile> overlayTiles) {
        if (overlayTiles.isEmpty()) {
            throw new IllegalArgumentException("overlayTiles must not be empty");
        }
        CtmOverlayTile first = overlayTiles.getFirst();
        CtmRule rule = first.rule();
        int primaryIndex = first.tileIndex();
        return new CtmRenderSelection(
                rule,
                rule.method(),
                face,
                primaryIndex,
                NO_TILE,
                methodFlags(rule.method()),
                baseSprite,
                tileSpec(rule, primaryIndex),
                null,
                overlayIndices(overlayTiles),
                overlayTiles);
    }

    /**
     * Returns {@code true} when the primary layer is a concrete tile.
     */
    public boolean hasPrimaryTile() {
        return primaryTileIndex >= 0
                && (primaryTile != null
                || CtmTileResolver.isCompactFullTileIndex(primaryTileIndex));
    }

    /**
     * Returns {@code true} when the secondary layer is a concrete tile.
     */
    public boolean hasSecondaryTile() {
        return secondaryTileIndex >= 0 && secondaryTile != null;
    }

    /**
     * Returns {@code true} when this selection carries a second layer.
     */
    public boolean isLayered() {
        return (flags & FLAG_LAYERED) != 0;
    }

    /**
     * Returns {@code true} for overlay methods.
     */
    public boolean isOverlay() {
        return (flags & FLAG_OVERLAY) != 0;
    }

    /**
     * Returns {@code true} when the primary result is {@code <skip>}.
     */
    public boolean isPrimarySkip() {
        return (flags & FLAG_PRIMARY_SKIP) != 0;
    }

    /**
     * Returns {@code true} when the primary result is {@code <default>}.
     */
    public boolean isPrimaryDefault() {
        return (flags & FLAG_PRIMARY_DEFAULT) != 0;
    }

    private static int methodFlags(CtmMethod method) {
        return switch (method) {
            case OVERLAY, OVERLAY_CTM, OVERLAY_RANDOM,
                    OVERLAY_REPEAT, OVERLAY_FIXED -> FLAG_OVERLAY;
            default -> 0;
        };
    }

    private static CtmTileSpec tileSpec(CtmRule rule, int tileIndex) {
        if (tileIndex < 0 || tileIndex >= rule.tiles().size()) {
            return null;
        }
        return rule.tiles().get(tileIndex);
    }

    private static List<Integer> overlayIndices(CtmRule rule, int primaryIndex) {
        if ((methodFlags(rule.method()) & FLAG_OVERLAY) == 0
                || primaryIndex < 0) {
            return List.of();
        }
        return List.of(primaryIndex);
    }

    private static List<Integer> overlayIndices(
            List<CtmOverlayTile> overlayTiles) {
        Integer[] indices = new Integer[overlayTiles.size()];
        for (int i = 0; i < overlayTiles.size(); i++) {
            indices[i] = overlayTiles.get(i).tileIndex();
        }
        return List.of(indices);
    }

    private static List<CtmOverlayTile> overlayTiles(
            CtmRule rule,
            int primaryIndex) {
        if ((methodFlags(rule.method()) & FLAG_OVERLAY) == 0
                || primaryIndex < 0) {
            return List.of();
        }
        return List.of(new CtmOverlayTile(rule, primaryIndex));
    }

    private static List<CtmOverlayTile> overlayTiles(
            CtmRule rule,
            List<Integer> tileIndices) {
        if ((methodFlags(rule.method()) & FLAG_OVERLAY) == 0
                || tileIndices.isEmpty()) {
            return List.of();
        }
        CtmOverlayTile[] out = new CtmOverlayTile[tileIndices.size()];
        int count = 0;
        for (int tileIndex : tileIndices) {
            if (tileIndex >= 0) {
                out[count++] = new CtmOverlayTile(rule, tileIndex);
            }
        }
        if (count == 0) {
            return List.of();
        }
        if (count == out.length) {
            return List.of(out);
        }
        CtmOverlayTile[] trimmed = new CtmOverlayTile[count];
        System.arraycopy(out, 0, trimmed, 0, count);
        return List.of(trimmed);
    }
}
