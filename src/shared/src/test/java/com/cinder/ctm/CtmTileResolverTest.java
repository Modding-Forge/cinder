package com.cinder.ctm;

import com.cinder.resource.NamespaceId;
import com.cinder.resource.PropertiesFile;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link CtmTileResolver} - the per-rule tile
 * resolver that maps a parsed {@link CtmRule} to a list of
 * (sprite id, resource path) pairs ready for atlas
 * injection.
 */
class CtmTileResolverTest {

    private static CtmRule parse(String body, String src) {
        PropertiesFile p;
        try {
            p = PropertiesFile.parse(new StringReader(body));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return CtmProperties.parse(p,
                new NamespaceId("minecraft", "optifine/ctm"), src);
    }

    @Test
    void numericRange_yieldsInjectionEntries() {
        CtmRule rule = parse(
                "method=ctm\n"
                        + "matchBlocks=minecraft:tinted_glass\n"
                        + "tiles=0-2\n",
                "minecraft:optifine/ctm/synthetic/tinted_glass/glass.properties");
        List<CtmTileResolver.Resolution> r =
                CtmTileResolver.resolve(rule,
                        "minecraft:optifine/ctm/synthetic/tinted_glass/glass.properties");
        assertEquals(3, r.size());
        for (int i = 0; i < 3; i++) {
            CtmTileResolver.Resolution entry = r.get(i);
            assertTrue(entry.needsInjection());
            assertEquals(
                    "minecraft:optifine/ctm/synthetic/tinted_glass/" + i + ".png",
                    entry.resourcePath());
            assertEquals(
                    new NamespaceId("cinder",
                            "optifine/ctm/synthetic/tinted_glass/" + i),
                    entry.resolvedSprite());
            assertEquals(i, entry.tileIndex());
        }
    }

    @Test
    void numericRange_missingPng_generatesFallbackSprites() {
        // Glass rule: no PNGs in the pack, so every tile
        // should get a stable cinder sprite id that the Fabric
        // SpriteSource can generate from minecraft:block/glass.
        CtmRule rule = parse(
                "method=ctm\n"
                        + "matchBlocks=minecraft:glass\n"
                        + "tiles=0-46\n",
                "minecraft:optifine/ctm/synthetic/glass/glass.properties");
        // Tile existence predicate returns false for every index.
        java.util.function.IntPredicate noPngs = n -> false;
        List<CtmTileResolver.Resolution> r =
                CtmTileResolver.resolve(rule,
                        "minecraft:optifine/ctm/synthetic/glass/glass.properties",
                        noPngs);
        assertEquals(47, r.size());
        NamespaceId baseSprite =
                new NamespaceId("minecraft", "block/glass");
        for (int i = 0; i < 47; i++) {
            CtmTileResolver.Resolution entry = r.get(i);
            assertTrue(entry.needsInjection(),
                    "missing-PNG tile must still be injected");
            assertNull(entry.resourcePath());
            assertEquals(baseSprite, entry.fallbackSourceSprite(),
                    "missing-PNG tile must remember its base sprite");
            assertEquals(
                    new NamespaceId("cinder",
                            "optifine/ctm/synthetic/glass/" + i),
                    entry.resolvedSprite(),
                    "missing-PNG tile must use a cinder sprite id");
        }
    }

    @Test
    void overlayMissingPngs_useConnectTileAsFallbackSource() {
        // Properties-only transition packs list the blocks that receive an
        // overlay in matchBlocks, while connectTiles names the texture to copy
        // from the active base pack.
        CtmRule rule = parse(
                "method=overlay\n"
                        + "matchBlocks=gravel sand\n"
                        + "tiles=0-16\n"
                        + "connectTiles=grass_block_top\n",
                "minecraft:optifine/ctm/transitions/4_grass/grass_overlay.properties");
        List<CtmTileResolver.Resolution> r =
                CtmTileResolver.resolve(rule,
                        "minecraft:optifine/ctm/transitions/4_grass/grass_overlay.properties",
                        n -> false);

        assertEquals(17, r.size());
        NamespaceId overlaySource =
                new NamespaceId("minecraft", "block/grass_block_top");
        for (CtmTileResolver.Resolution entry : r) {
            assertTrue(entry.needsInjection());
            assertNull(entry.resourcePath());
            assertEquals(overlaySource, entry.fallbackSourceSprite());
        }
        assertEquals(overlaySource,
                CtmTileResolver.fallbackSourceSprite(rule));
    }

    @Test
    void numericRange_partialPngs_fallsBackPerTile() {
        // The predicate simulates a pack that provides numbered PNGs.
        // The resolver should point each existing tile at a synthesized
        // cinder sprite id without needing real image fixtures.
        CtmRule rule = parse(
                "method=ctm\n"
                        + "matchBlocks=minecraft:tinted_glass\n"
                        + "tiles=0-2\n",
                "minecraft:optifine/ctm/synthetic/tinted_glass/glass.properties");
        java.util.function.IntPredicate allExist = n -> true;
        List<CtmTileResolver.Resolution> r =
                CtmTileResolver.resolve(rule,
                        "minecraft:optifine/ctm/synthetic/tinted_glass/glass.properties",
                        allExist);
        for (int i = 0; i < 3; i++) {
            assertTrue(r.get(i).needsInjection());
            assertEquals(
                    new NamespaceId("cinder",
                            "optifine/ctm/synthetic/tinted_glass/" + i),
                    r.get(i).resolvedSprite());
        }
    }

    @Test
    void baseBlockSprite_derivesFromMatchBlocks() {
        CtmRule rule = parse(
                "method=ctm\n"
                        + "matchBlocks=minecraft:white_stained_glass\n"
                        + "tiles=0-46\n",
                "minecraft:optifine/ctm/synthetic/white_glass/glass_white.properties");
        assertEquals(
                new NamespaceId("minecraft", "block/white_stained_glass"),
                CtmTileResolver.baseBlockSprite(rule));
    }

    @Test
    void baseBlockSprite_ignoresUnqualifiedBlockStateProperties() {
        CtmRule rule = parse(
                "method=horizontal\n"
                        + "matchBlocks=brick_slab:type=top\n"
                        + "tiles=0-3\n",
                "minecraft:optifine/ctm/hard_materials/brick/brick3.properties");
        assertEquals(
                new NamespaceId("minecraft", "block/brick_slab"),
                CtmTileResolver.baseBlockSprite(rule));
    }

    @Test
    void namedTile_yieldsNoInjection() {
        CtmRule rule = parse(
                "method=ctm\n"
                        + "matchBlocks=minecraft:glass\n"
                        + "tiles=glass\n",
                "minecraft:optifine/ctm/glass.properties");
        List<CtmTileResolver.Resolution> r =
                CtmTileResolver.resolve(rule,
                        "minecraft:optifine/ctm/glass.properties");
        assertEquals(1, r.size());
        CtmTileResolver.Resolution entry = r.get(0);
        assertFalse(entry.needsInjection());
        assertNull(entry.resourcePath());
        assertNotNull(entry.resolvedSprite());
        // The named tile is resolved relative to the .properties
        // parent directory by CtmTileSpec.fromSpec, so its sprite
        // is "minecraft:optifine/ctm/glass" (no injection needed;
        // the loader side treats it as already-in-atlas).
        assertEquals("minecraft:optifine/ctm/glass",
                entry.resolvedSprite().toString());
    }

    @Test
    void namedTexturePath_yieldsBlockAtlasSprite() {
        CtmRule rule = parse(
                "method=ctm_compact\n"
                        + "matchBlocks=iron_block\n"
                        + "tiles=textures/block/iron_block.png 26\n",
                "minecraft:optifine/ctm/minerals/iron/iron_block.properties");
        List<CtmTileResolver.Resolution> r =
                CtmTileResolver.resolve(rule,
                        "minecraft:optifine/ctm/minerals/iron/iron_block.properties");
        assertTrue(r.size() >= 2);
        assertFalse(r.get(0).needsInjection());
        assertEquals(new NamespaceId("minecraft", "block/iron_block"),
                r.get(0).resolvedSprite());
    }

    @Test
    void compactRules_generateRendererFullTileResolutionsForNonOverrides() {
        CtmRule rule = parse(
                "method=ctm_compact\n"
                        + "matchBlocks=iron_block\n"
                        + "tiles=textures/block/iron_block.png 26 24 2 47\n"
                        + "ctm.46=4\n",
                "minecraft:optifine/ctm/minerals/iron/iron_block.properties");
        List<CtmTileResolver.Resolution> r =
                CtmTileResolver.resolve(rule,
                        "minecraft:optifine/ctm/minerals/iron/iron_block.properties",
                        n -> true);

        int synthetic1 = CtmTileResolver.COMPACT_FULL_TILE_OFFSET
                + Faces.UP * CtmTileResolver.COMPACT_FULL_TILE_COUNT + 1;
        assertTrue(CtmTileResolver.findResolution(r, synthetic1).isPresent());
        CtmTileResolver.Resolution synthetic =
                CtmTileResolver.findResolution(r, synthetic1)
                        .orElseThrow();
        assertNull(synthetic.resourcePath());
        assertEquals(new NamespaceId("minecraft", "block/iron_block"),
                synthetic.fallbackSourceSprite());
        assertTrue(CtmTileResolver.findResolution(r,
                CtmTileResolver.COMPACT_FULL_TILE_OFFSET
                        + Faces.UP * CtmTileResolver.COMPACT_FULL_TILE_COUNT
                        + 46).isEmpty());
    }

    @Test
    void compactRules_doNotTreatCompactSourcePngsAsFullTemplatePngs() {
        CtmRule rule = parse(
                "method=ctm_compact\n"
                        + "matchBlocks=gold_block\n"
                        + "tiles=textures/block/gold_block.png 1-5\n"
                        + "ctm.46=5\n",
                "minecraft:optifine/ctm/minerals/gold/gold_block.properties");
        List<CtmTileResolver.Resolution> r =
                CtmTileResolver.resolve(rule,
                        "minecraft:optifine/ctm/minerals/gold/gold_block.properties",
                        n -> n >= 1 && n <= 5);

        int synthetic1 = CtmTileResolver.COMPACT_FULL_TILE_OFFSET
                + Faces.NORTH * CtmTileResolver.COMPACT_FULL_TILE_COUNT + 1;
        CtmTileResolver.Resolution synthetic =
                CtmTileResolver.findResolution(r, synthetic1)
                        .orElseThrow();
        assertNull(synthetic.resourcePath());
        assertTrue(synthetic.needsInjection());
        assertEquals(new NamespaceId("minecraft", "block/gold_block"),
                synthetic.fallbackSourceSprite());
        assertTrue(CtmTileResolver.findResolution(r,
                CtmTileResolver.COMPACT_FULL_TILE_OFFSET
                        + Faces.NORTH * CtmTileResolver.COMPACT_FULL_TILE_COUNT
                        + 46).isEmpty());
    }

    @Test
    void compactRules_addSyntheticFullEntriesWhenFullPngIsMissing() {
        CtmRule rule = parse(
                "method=ctm_compact\n"
                        + "matchBlocks=gold_block\n"
                        + "tiles=textures/block/gold_block.png 1-5\n"
                        + "ctm.46=5\n",
                "minecraft:optifine/ctm/minerals/gold/gold_block.properties");
        List<CtmTileResolver.Resolution> r =
                CtmTileResolver.resolve(rule,
                        "minecraft:optifine/ctm/minerals/gold/gold_block.properties",
                        n -> false);

        CtmTileResolver.Resolution synthetic =
                CtmTileResolver.findResolution(r,
                        CtmTileResolver.COMPACT_FULL_TILE_OFFSET + 1)
                        .orElseThrow();
        assertNull(synthetic.resourcePath());
        assertTrue(synthetic.needsInjection());
        assertEquals(1, CtmTileResolver.compactFullTileIndex(
                synthetic.tileIndex()));
        assertEquals(Faces.DOWN, CtmTileResolver.compactFullTileFace(
                synthetic.tileIndex()));
        assertTrue(CtmTileResolver.findResolution(r,
                CtmTileResolver.COMPACT_FULL_TILE_OFFSET).isEmpty());
    }

    @Test
    void namedTexturePathWithoutPng_yieldsBlockAtlasSprite() {
        CtmRule rule = parse(
                "method=ctm_compact\n"
                        + "matchBlocks=sandstone\n"
                        + "tiles=textures/block/sandstone 1-4\n",
                "minecraft:optifine/ctm/hard_materials/sandstone/sandstone/sandstone.properties");
        List<CtmTileResolver.Resolution> r =
                CtmTileResolver.resolve(rule,
                        "minecraft:optifine/ctm/hard_materials/sandstone/sandstone/sandstone.properties");
        assertTrue(r.size() >= 5);
        assertFalse(r.get(0).needsInjection());
        assertEquals(new NamespaceId("minecraft", "block/sandstone"),
                r.get(0).resolvedSprite());
    }

    @Test
    void skipAndDefault_yieldSentinelResolutions() {
        CtmRule rule = parse(
                "method=ctm\n"
                        + "matchBlocks=minecraft:stone\n"
                        + "tiles=0 <skip> <default> 1\n",
                "minecraft:optifine/ctm/stone.properties");
        List<CtmTileResolver.Resolution> r =
                CtmTileResolver.resolve(rule,
                        "minecraft:optifine/ctm/stone.properties");
        assertEquals(4, r.size());
        // r.get(0) = numeric 0  -> injection
        // r.get(1) = <skip>      -> null sprite, no injection
        // r.get(2) = <default>   -> null sprite, no injection
        // r.get(3) = numeric 1  -> injection
        assertTrue(r.get(0).needsInjection());
        assertTrue(r.get(0).isConcrete());
        assertFalse(r.get(1).isConcrete());
        assertFalse(r.get(1).needsInjection());
        assertNull(r.get(1).resolvedSprite());
        assertFalse(r.get(2).isConcrete());
        assertFalse(r.get(2).needsInjection());
        assertNull(r.get(2).resolvedSprite());
        assertTrue(r.get(3).needsInjection());
        assertTrue(r.get(3).isConcrete());
    }

    @Test
    void propertiesDirectoryPath_stripsFilename() {
        assertEquals(
                "minecraft:optifine/ctm/synthetic/tinted_glass",
                CtmTileResolver.propertiesDirectoryPath(
                        "minecraft:optifine/ctm/synthetic/tinted_glass/glass.properties"));
        assertEquals(
                "minecraft:optifine/ctm",
                CtmTileResolver.propertiesDirectoryPath(
                        "minecraft:optifine/ctm/foo.properties"));
    }

    @Test
    void stripOptifinePrefix_removesNamespaceAndPrefix() {
        assertEquals("synthetic/tinted_glass",
                CtmTileResolver.stripOptifinePrefix(
                        "minecraft:optifine/ctm/synthetic/tinted_glass"));
        assertEquals("foo",
                CtmTileResolver.stripOptifinePrefix("optifine/ctm/foo"));
    }

    @Test
    void findResolution_returnsCorrectEntry() {
        CtmRule rule = parse(
                "method=ctm\n"
                        + "matchBlocks=minecraft:glass\n"
                        + "tiles=0-3\n",
                "minecraft:optifine/ctm/glass.properties");
        List<CtmTileResolver.Resolution> r =
                CtmTileResolver.resolve(rule,
                        "minecraft:optifine/ctm/glass.properties");
        assertTrue(CtmTileResolver.findResolution(r, 2).isPresent());
        assertEquals("2", CtmTileResolver.findResolution(r, 2)
                .get().resolvedSprite().path()
                .substring(CtmTileResolver.findResolution(r, 2)
                        .get().resolvedSprite().path().lastIndexOf('/') + 1));
        assertTrue(CtmTileResolver.findResolution(r, 99).isEmpty());
    }
}
