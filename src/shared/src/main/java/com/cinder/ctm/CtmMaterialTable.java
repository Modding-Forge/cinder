package com.cinder.ctm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Immutable CTM material snapshot built from resolved resource-pack tiles.
 *
 * <p>The table is the renderer-facing successor to "tile index -> atlas
 * sprite". It assigns stable positive material ids to concrete CTM tiles while
 * preserving enough resource metadata for a backend-native implementation to
 * upload texture arrays, sidecar lookup tables, or shader material buffers.
 *
 * <h2>Threading</h2>
 *
 * <p>Instances are immutable. The process-wide holder uses atomic replacement
 * so resource reload can publish a new snapshot while section-build threads
 * read the previous one.
 *
 * <h2>Performance</h2>
 *
 * <p>Performance: HOT PATH. Lookup by {@code (rule, tileIndex)} is O(1) and
 * allocation-free after reload.
 */
public final class CtmMaterialTable {

    /** Material id 0 means vanilla/pass-through/no CTM material. */
    public static final int PASS_THROUGH_MATERIAL_ID = 0;

    private static final CtmMaterialTable EMPTY =
            new CtmMaterialTable(List.of(), Map.of());

    private final List<CtmMaterialEntry> entries;
    private final Map<Integer, CtmMaterialEntry> byMaterialId;
    private final Map<CtmRule, CtmMaterialEntry[]> byRule;

    private CtmMaterialTable(List<CtmMaterialEntry> entries,
                             Map<CtmRule, CtmMaterialEntry[]> byRule) {
        this.entries = List.copyOf(entries);
        HashMap<Integer, CtmMaterialEntry> materialIndex = new HashMap<>();
        for (CtmMaterialEntry entry : entries) {
            materialIndex.put(entry.materialId(), entry);
        }
        this.byMaterialId = Map.copyOf(materialIndex);
        this.byRule = Map.copyOf(byRule);
    }

    /**
     * Returns the empty material table.
     */
    public static CtmMaterialTable empty() {
        return EMPTY;
    }

    /**
     * Builds a material table from the current tile atlas snapshot.
     */
    public static CtmMaterialTable of(CtmTileAtlas atlas) {
        Objects.requireNonNull(atlas, "atlas");
        if (atlas.isEmpty()) {
            return EMPTY;
        }
        ArrayList<CtmMaterialEntry> entries = new ArrayList<>();
        HashMap<CtmRule, CtmMaterialEntry[]> byRule = new HashMap<>();
        int nextMaterialId = PASS_THROUGH_MATERIAL_ID + 1;
        for (CtmTileAtlasEntry atlasEntry : atlas.entries()) {
            CtmRule rule = atlasEntry.rule();
            int perTileSize = rule.tiles().size();
            for (CtmTileResolver.Resolution resolution
                    : atlasEntry.resolutions()) {
                if (resolution.isConcrete()
                        && resolution.tileIndex() >= perTileSize) {
                    perTileSize = resolution.tileIndex() + 1;
                }
            }
            CtmMaterialEntry[] perTile =
                    new CtmMaterialEntry[perTileSize];
            for (CtmTileResolver.Resolution resolution
                    : atlasEntry.resolutions()) {
                if (!resolution.isConcrete()) {
                    continue;
                }
                CtmMaterialEntry material = CtmMaterialEntry.from(
                        nextMaterialId++, rule, resolution);
                entries.add(material);
                int tileIndex = resolution.tileIndex();
                if (tileIndex >= 0 && tileIndex < perTile.length) {
                    perTile[tileIndex] = material;
                }
            }
            byRule.put(rule, perTile);
        }
        if (entries.isEmpty()) {
            return EMPTY;
        }
        return new CtmMaterialTable(entries, byRule);
    }

    /**
     * Returns all materials in material-id order.
     */
    public List<CtmMaterialEntry> entries() {
        return entries;
    }

    /**
     * Returns the number of concrete CTM materials.
     */
    public int size() {
        return entries.size();
    }

    /**
     * Returns true when no CTM materials are available.
     */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * Looks up a material for a concrete rule/tile pair.
     */
    public Optional<CtmMaterialEntry> find(CtmRule rule, int tileIndex) {
        if (rule == null || tileIndex < 0 || byRule.isEmpty()) {
            return Optional.empty();
        }
        CtmMaterialEntry[] perTile = byRule.get(rule);
        if (perTile == null || tileIndex >= perTile.length) {
            return Optional.empty();
        }
        return Optional.ofNullable(perTile[tileIndex]);
    }

    /**
     * Looks up the immutable material descriptor for a material id.
     */
    public Optional<CtmMaterialEntry> findByMaterialId(int materialId) {
        if (materialId <= PASS_THROUGH_MATERIAL_ID) {
            return Optional.empty();
        }
        return Optional.ofNullable(byMaterialId.get(materialId));
    }

    /**
     * Resolves the primary material id for a render selection, returning 0 for
     * pass-through, skip/default, or unresolved material data.
     */
    public int primaryMaterialId(CtmRenderSelection selection) {
        if (selection == null || selection.isPrimarySkip()
                || selection.isPrimaryDefault()) {
            return PASS_THROUGH_MATERIAL_ID;
        }
        if (selection.isOverlay() && !selection.overlayTiles().isEmpty()) {
            for (CtmOverlayTile overlayTile : selection.overlayTiles()) {
                int materialId = find(
                        overlayTile.rule(), overlayTile.tileIndex())
                        .map(CtmMaterialEntry::materialId)
                        .orElse(PASS_THROUGH_MATERIAL_ID);
                if (materialId != PASS_THROUGH_MATERIAL_ID) {
                    return materialId;
                }
            }
            return PASS_THROUGH_MATERIAL_ID;
        }
        if (selection.primaryTileIndex() < 0) {
            return PASS_THROUGH_MATERIAL_ID;
        }
        return find(selection.rule(), selection.primaryTileIndex())
                .map(CtmMaterialEntry::materialId)
                .orElse(PASS_THROUGH_MATERIAL_ID);
    }

    /**
     * Resolves the secondary material id for a layered render selection,
     * returning 0 when no second material is present.
     */
    public int secondaryMaterialId(CtmRenderSelection selection) {
        if (selection == null
                || selection.secondaryTileIndex() < 0
                || (selection.flags()
                & CtmRenderSelection.FLAG_SECONDARY_SKIP) != 0
                || (selection.flags()
                & CtmRenderSelection.FLAG_SECONDARY_DEFAULT) != 0) {
            return PASS_THROUGH_MATERIAL_ID;
        }
        return find(selection.rule(), selection.secondaryTileIndex())
                .map(CtmMaterialEntry::materialId)
                .orElse(PASS_THROUGH_MATERIAL_ID);
    }

    /**
     * Resolves a compact material payload for the given render selection.
     */
    public CtmMaterialPayload payloadFor(CtmRenderSelection selection) {
        if (selection == null) {
            return CtmMaterialPayload.PASS_THROUGH;
        }
        int primary = primaryMaterialId(selection);
        int secondary = secondaryMaterialId(selection);
        if (primary == PASS_THROUGH_MATERIAL_ID
                && secondary == PASS_THROUGH_MATERIAL_ID) {
            return CtmMaterialPayload.PASS_THROUGH;
        }
        return new CtmMaterialPayload(primary, secondary, selection.flags());
    }

    private static final AtomicReference<CtmMaterialTable> INSTANCE =
            new AtomicReference<>(EMPTY);

    /**
     * Returns the current process-wide material table. Never null.
     */
    public static CtmMaterialTable current() {
        return INSTANCE.get();
    }

    /**
     * Atomically publishes a new material table.
     */
    public static void replace(CtmMaterialTable table) {
        Objects.requireNonNull(table, "table");
        INSTANCE.set(table);
    }

    /**
     * Resets the global table for tests.
     */
    public static void resetForTest() {
        INSTANCE.set(EMPTY);
    }
}
