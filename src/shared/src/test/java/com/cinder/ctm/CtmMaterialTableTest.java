package com.cinder.ctm;

import com.cinder.resource.NamespaceId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link CtmMaterialTable}, the immutable CTM material-id snapshot
 * used by future backend-native renderers.
 */
class CtmMaterialTableTest {

    private static final NamespaceId GLASS =
            new NamespaceId("minecraft", "block/glass");

    @AfterEach
    void resetGlobalTables() {
        CtmMaterialTable.resetForTest();
        CtmTileAtlas.resetForTest();
    }

    @Test
    void emptyTable_reservesZeroForPassThrough() {
        CtmMaterialTable table = CtmMaterialTable.empty();

        assertTrue(table.isEmpty());
        assertEquals(0, table.size());
        assertEquals(0, CtmMaterialTable.PASS_THROUGH_MATERIAL_ID);
        assertTrue(table.entries().isEmpty());
    }

    @Test
    void of_assignsStablePositiveMaterialIds() {
        CtmRule rule = rule(CtmMethod.CTM, 3);
        CtmTileAtlas atlas = CtmTileAtlas.of(List.of(
                new CtmTileAtlasEntry(rule, List.of(
                        resolution(0, "cinder:ctm/glass/0", true, null),
                        resolution(1, "cinder:ctm/glass/1", true, null),
                        resolution(2, "minecraft:block/glass_edge", false, null)
                ))));

        CtmMaterialTable table = CtmMaterialTable.of(atlas);

        assertFalse(table.isEmpty());
        assertEquals(3, table.size());
        assertEquals(1, table.entries().get(0).materialId());
        assertEquals(2, table.entries().get(1).materialId());
        assertEquals(3, table.entries().get(2).materialId());
        assertEquals("cinder:ctm/glass/1",
                table.find(rule, 1).orElseThrow().sprite().toString());
        assertTrue(table.find(rule, 99).isEmpty());
    }

    @Test
    void of_skipsSpecialTileResolutions() {
        CtmRule rule = rule(CtmMethod.OVERLAY, 3);
        CtmTileAtlas atlas = CtmTileAtlas.of(List.of(
                new CtmTileAtlasEntry(rule, List.of(
                        resolution(0, "cinder:ctm/overlay/0", true, null),
                        new CtmTileResolver.Resolution(1, null, null, false),
                        new CtmTileResolver.Resolution(2, null, null, false)
                ))));

        CtmMaterialTable table = CtmMaterialTable.of(atlas);

        assertEquals(1, table.size());
        assertTrue(table.find(rule, 1).isEmpty());
        assertTrue(table.find(rule, 2).isEmpty());
    }

    @Test
    void entriesExposeResourceStrategyFlags() {
        NamespaceId fallback =
                new NamespaceId("minecraft", "block/glass");
        CtmRule rule = rule(CtmMethod.CTM, 3);
        CtmTileAtlas atlas = CtmTileAtlas.of(List.of(
                new CtmTileAtlasEntry(rule, List.of(
                        resolution(0, "cinder:ctm/glass/0", true,
                                "minecraft:optifine/ctm/glass/0.png"),
                        resolution(1, "cinder:ctm/glass/1", true,
                                null, fallback),
                        resolution(2, "minecraft:block/glass_edge", false,
                                null)
                ))));

        CtmMaterialTable table = CtmMaterialTable.of(atlas);

        CtmMaterialEntry explicit = table.find(rule, 0).orElseThrow();
        CtmMaterialEntry generated = table.find(rule, 1).orElseThrow();
        CtmMaterialEntry named = table.find(rule, 2).orElseThrow();
        assertTrue(explicit.hasExplicitResource());
        assertFalse(explicit.isGeneratedFallback());
        assertTrue(generated.isGeneratedFallback());
        assertEquals(fallback, generated.fallbackSourceSprite());
        assertTrue(named.isNamedSprite());
    }

    @Test
    void renderSelection_resolvesPrimaryAndSecondaryMaterialIds() {
        CtmRule rule = rule(CtmMethod.HORIZONTAL_VERTICAL, 4);
        ArrayList<CtmTileResolver.Resolution> resolutions =
                new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            resolutions.add(resolution(
                    i, "cinder:ctm/layered/" + i, true, null));
        }
        CtmMaterialTable table = CtmMaterialTable.of(CtmTileAtlas.of(List.of(
                new CtmTileAtlasEntry(rule, resolutions))));
        CtmRenderSelection selection = CtmRenderSelection.from(
                rule,
                Faces.NORTH,
                GLASS,
                CtmSelectionResult.ofTile(1),
                CtmSelectionResult.ofTile(2));

        assertEquals(2, table.primaryMaterialId(selection));
        assertEquals(3, table.secondaryMaterialId(selection));
    }

    @Test
    void renderSelection_resolvesSyntheticCompactFullMaterialIds() {
        CtmRule rule = rule(CtmMethod.CTM_COMPACT, 5);
        int synthetic = CtmTileResolver.COMPACT_FULL_TILE_OFFSET + 12;
        ArrayList<CtmTileResolver.Resolution> resolutions =
                new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            resolutions.add(resolution(i, "cinder:ctm/iron/base/" + i,
                    true, null));
        }
        resolutions.add(resolution(synthetic, "cinder:ctm/iron/12", true,
                "minecraft:optifine/ctm/iron/12.png"));
        CtmMaterialTable table = CtmMaterialTable.of(CtmTileAtlas.of(List.of(
                new CtmTileAtlasEntry(rule, resolutions))));
        CtmRenderSelection selection = CtmRenderSelection.from(
                rule,
                Faces.NORTH,
                GLASS,
                CtmSelectionResult.ofTile(synthetic),
                null);

        assertEquals(6, table.primaryMaterialId(selection));
        assertEquals("cinder:ctm/iron/12",
                table.find(rule, synthetic).orElseThrow().sprite().toString());
    }


    @Test
    void payloadFor_packsMaterialIdsAndFlags() {
        CtmRule rule = rule(CtmMethod.HORIZONTAL_VERTICAL, 4);
        ArrayList<CtmTileResolver.Resolution> resolutions =
                new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            resolutions.add(resolution(
                    i, "cinder:ctm/payload/" + i, true, null));
        }
        CtmMaterialTable table = CtmMaterialTable.of(CtmTileAtlas.of(List.of(
                new CtmTileAtlasEntry(rule, resolutions))));
        CtmRenderSelection selection = CtmRenderSelection.from(
                rule,
                Faces.NORTH,
                GLASS,
                CtmSelectionResult.ofTile(1),
                CtmSelectionResult.ofTile(2));

        CtmMaterialPayload payload = table.payloadFor(selection);

        assertTrue(payload.hasCtmMaterial());
        assertEquals(2, payload.primaryMaterialId());
        assertEquals(3, payload.secondaryMaterialId());
        assertEquals(selection.flags(), payload.flags());
        assertEquals(2 | (3 << 16)
                        | ((selection.flags() & 0xFF) << 24),
                payload.pack32());
    }

    @Test
    void replace_publishesCurrentSnapshot() {
        CtmRule rule = rule(CtmMethod.FIXED, 1);
        CtmMaterialTable table = CtmMaterialTable.of(CtmTileAtlas.of(List.of(
                new CtmTileAtlasEntry(rule, List.of(
                        resolution(0, "cinder:ctm/fixed/0", true, null))))));

        CtmMaterialTable.replace(table);

        assertSame(table, CtmMaterialTable.current());
    }

    private static CtmRule rule(CtmMethod method, int tileCount) {
        CtmRule.Builder builder = new CtmRule.Builder()
                .method(method)
                .addMatchTile(GLASS)
                .addMatchBlock(new BlockSpec("minecraft", "glass", Map.of()))
                .sourceFile(method.name().toLowerCase() + ".properties")
                .sourceLine(1);
        for (int i = 0; i < tileCount; i++) {
            builder.addTile(CtmTileSpec.numeric(i));
        }
        return builder.build();
    }

    private static CtmTileResolver.Resolution resolution(
            int tileIndex, String sprite, boolean injection, String path) {
        return resolution(tileIndex, sprite, injection, path, null);
    }

    private static CtmTileResolver.Resolution resolution(
            int tileIndex, String sprite, boolean injection, String path,
            NamespaceId fallback) {
        NamespaceId id = NamespaceId.parse(sprite);
        return new CtmTileResolver.Resolution(
                tileIndex, id, path, injection, fallback);
    }
}
