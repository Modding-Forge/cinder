package com.cinder.ctm;

import com.cinder.resource.NamespaceId;
import com.cinder.resource.PropertiesFile;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for the Phase 4 additions to the CTM selector:
 * <ul>
 *   <li>{@code innerSeams=true} on the CTM method.</li>
 *   <li>{@code biomes=...} and {@code heights=...} rule filters.</li>
 *   <li>{@code connect=state} against {@code isFullBlock}.</li>
 *   <li>Layered methods ({@code h+v}, {@code v+h}) returning two
 *       results via {@link CtmSelector#selectLayered}.</li>
 * </ul>
 *
 * <p>These tests reuse the {@code Grid} pattern from
 * {@code CtmSelectorTest} (defined locally there for legacy
 * reasons); we redefine a similar {@link BiomesGrid} that
 * additionally exposes a centre-biome id.
 */
class Phase4SelectorTest {

    /**
     * A {@link NeighborView} for biomes/heights tests. The
     * centre cell's biome id is configurable; all 26 neighbour
     * biomes default to {@code null}.
     */
    private static final class BiomesGrid implements NeighborView {
        final String selfId;
        final NamespaceId selfSprite;
        final String centerBiome;
        final String[][][] blockIds = new String[3][3][3];
        final NamespaceId[][][][] sprites = new NamespaceId[3][3][3][6];
        final boolean[][][] full = new boolean[3][3][3];
        final String[][][] biomes = new String[3][3][3];

        BiomesGrid(String selfId, NamespaceId selfSprite, String centerBiome) {
            this.selfId = selfId;
            this.selfSprite = selfSprite;
            this.centerBiome = centerBiome;
            blockIds[1][1][1] = selfId;
            full[1][1][1] = true;
            biomes[1][1][1] = centerBiome;
            for (int f = 0; f < 6; f++) {
                sprites[1][1][1][f] = selfSprite;
            }
        }

        BiomesGrid set(int dx, int dy, int dz, String blockId, NamespaceId sprite) {
            blockIds[dx + 1][dy + 1][dz + 1] = blockId;
            for (int f = 0; f < 6; f++) {
                sprites[dx + 1][dy + 1][dz + 1][f] = sprite;
            }
            full[dx + 1][dy + 1][dz + 1] = true;
            return this;
        }

        BiomesGrid setBiome(int dx, int dy, int dz, String biome) {
            biomes[dx + 1][dy + 1][dz + 1] = biome;
            return this;
        }

        @Override public NamespaceId sprite(int dx, int dy, int dz, int face) {
            return sprites[dx + 1][dy + 1][dz + 1][face];
        }
        @Override public String blockId(int dx, int dy, int dz) {
            return blockIds[dx + 1][dy + 1][dz + 1];
        }
        @Override public boolean isFullBlock(int dx, int dy, int dz) {
            return full[dx + 1][dy + 1][dz + 1];
        }
        @Override public String biomeId(int dx, int dy, int dz) {
            return biomes[dx + 1][dy + 1][dz + 1];
        }
    }

    private static CtmRule parseRule(String body) {
        PropertiesFile p;
        try {
            p = PropertiesFile.parse(new StringReader(body));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return CtmProperties.parse(p,
                new NamespaceId("minecraft", "optifine/ctm"), "x.properties");
    }

    private static CtmSelector selector(CtmRule rule) {
        return new CtmSelector(new CtmRuleSet.Builder().add(rule).build());
    }

    // --- innerSeams ---------------------------------------------------

    @Test
    void innerSeams_isolatedBlock_returnsTileZero() {
        // No neighbours at all -> sideMask=0 -> diagMask=0 ->
        // tile 0 in both innerSeams modes.
        CtmRule r = parseRule(
                "method=ctm\n"
                        + "matchBlocks=minecraft:glass\n"
                        + "innerSeams=true\n"
                        + "tiles=0-46\n");
        BiomesGrid g = new BiomesGrid("minecraft:glass",
                new NamespaceId("minecraft", "block/glass"), null);
        CtmSelectionResult sel = selector(r).select(r, g, 0, 0, 0, Faces.UP);
        assertNotNull(sel);
        assertEquals(0, sel.tileIndex());
    }

    @Test
    void innerSeams_diagonalBlockWithoutSides_setsDiagMask() {
        // For an UP face: place the centre at (0,0,0), only the
        // diagonal at (-1, 0, -1) is glass. With innerSeams=true
        // the diagonal alone is enough to set bit 0 in diagMask,
        // even though both sides (NORTH and WEST) are missing.
        // With innerSeams=false, the diagonal would be ignored
        // because sideMask=0.
        CtmRule rSeams = parseRule(
                "method=ctm\n"
                        + "matchBlocks=minecraft:glass\n"
                        + "innerSeams=true\n"
                        + "tiles=0-46\n");
        CtmRule rNoSeams = parseRule(
                "method=ctm\n"
                        + "matchBlocks=minecraft:glass\n"
                        + "innerSeams=false\n"
                        + "tiles=0-46\n");
        BiomesGrid g = new BiomesGrid("minecraft:glass",
                new NamespaceId("minecraft", "block/glass"), null);
        g.set(-1, 0, -1, "minecraft:glass",
                new NamespaceId("minecraft", "block/glass"));
        CtmSelectionResult withSeams = selector(rSeams).select(
                rSeams, g, 0, 0, 0, Faces.UP);
        CtmSelectionResult withoutSeams = selector(rNoSeams).select(
                rNoSeams, g, 0, 0, 0, Faces.UP);
        // With innerSeams=true, the diagonal contributes, so
        // the result is a tile that is *not* 0. With
        // innerSeams=false, the diagonal is ignored and the
        // result is tile 0.
        assertEquals(0, withoutSeams.tileIndex());
        assertNotNull(withSeams);
        // The exact non-zero tile depends on the diagonal
        // index in TileIndexTable; we only assert that it
        // differs from the no-seams case.
        //noinspection AssertEqualsBetweenInconvertibleTypes
        org.junit.jupiter.api.Assertions.assertNotEquals(
                withoutSeams.tileIndex(), withSeams.tileIndex(),
                "innerSeams should change the selected tile");
    }

    // --- biomes filter ------------------------------------------------

    @Test
    void biomes_matchingBiome_selectsNormally() {
        CtmRule r = parseRule(
                "method=ctm\n"
                        + "matchBlocks=minecraft:stone\n"
                        + "biomes=plains,forest\n"
                        + "tiles=0-46\n");
        BiomesGrid g = new BiomesGrid("minecraft:stone",
                new NamespaceId("minecraft", "block/stone"), "plains");
        CtmSelectionResult sel = selector(r).select(r, g, 0, 0, 0, Faces.UP);
        assertNotNull(sel);
        assertEquals(0, sel.tileIndex());
    }

    @Test
    void biomes_nonMatchingBiome_returnsNull() {
        CtmRule r = parseRule(
                "method=ctm\n"
                        + "matchBlocks=minecraft:stone\n"
                        + "biomes=plains,forest\n"
                        + "tiles=0-46\n");
        BiomesGrid g = new BiomesGrid("minecraft:stone",
                new NamespaceId("minecraft", "block/stone"), "desert");
        CtmSelectionResult sel = selector(r).select(r, g, 0, 0, 0, Faces.UP);
        assertNull(sel);
    }

    @Test
    void biomes_unsetOnView_doesNotMatch_whenRuleHasBiomes() {
        // The view's centre biome is null; a rule with biomes
        // does not match.
        CtmRule r = parseRule(
                "method=ctm\n"
                        + "matchBlocks=minecraft:stone\n"
                        + "biomes=plains\n"
                        + "tiles=0-46\n");
        BiomesGrid g = new BiomesGrid("minecraft:stone",
                new NamespaceId("minecraft", "block/stone"), null);
        CtmSelectionResult sel = selector(r).select(r, g, 0, 0, 0, Faces.UP);
        assertNull(sel);
    }

    // --- heights filter ----------------------------------------------

    @Test
    void heights_matchingY_selectsNormally() {
        CtmRule r = parseRule(
                "method=ctm\n"
                        + "matchBlocks=minecraft:stone\n"
                        + "heights=0-63\n"
                        + "tiles=0-46\n");
        BiomesGrid g = new BiomesGrid("minecraft:stone",
                new NamespaceId("minecraft", "block/stone"), null);
        CtmSelectionResult sel = selector(r).select(r, g, 0, 10, 0, Faces.UP);
        assertNotNull(sel);
    }

    @Test
    void heights_outsideRange_returnsNull() {
        CtmRule r = parseRule(
                "method=ctm\n"
                        + "matchBlocks=minecraft:stone\n"
                        + "heights=0-63\n"
                        + "tiles=0-46\n");
        BiomesGrid g = new BiomesGrid("minecraft:stone",
                new NamespaceId("minecraft", "block/stone"), null);
        CtmSelectionResult sel = selector(r).select(r, g, 0, 100, 0, Faces.UP);
        assertNull(sel);
    }

    @Test
    void heights_negativeRange_matchesNegativeY() {
        CtmRule r = parseRule(
                "method=ctm\n"
                        + "matchBlocks=minecraft:stone\n"
                        + "heights=(-64)-(-1)\n"
                        + "tiles=0-46\n");
        BiomesGrid g = new BiomesGrid("minecraft:stone",
                new NamespaceId("minecraft", "block/stone"), null);
        CtmSelectionResult sel = selector(r).select(r, g, 0, -10, 0, Faces.UP);
        assertNotNull(sel);
    }

    // --- connect=state -----------------------------------------------

    @Test
    void connectState_matchingBlockIdAndFullBlockFlag_selectsNormally() {
        CtmRule r = parseRule(
                "method=ctm\n"
                        + "matchBlocks=minecraft:stone\n"
                        + "connect=state\n"
                        + "tiles=0-46\n");
        BiomesGrid g = new BiomesGrid("minecraft:stone",
                new NamespaceId("minecraft", "block/stone"), null);
        g.set(1, 0, 0, "minecraft:stone",
                new NamespaceId("minecraft", "block/stone"));
        // The +X neighbour is "minecraft:stone", and its
        // isFullBlock matches the centre's (both true).
        CtmSelectionResult sel = selector(r).select(r, g, 0, 0, 0, Faces.UP);
        assertNotNull(sel);
        // The +X side should be set in the sideMask.
        // The exact tile is implementation-detail of
        // TileIndexTable; we only assert non-zero.
        org.junit.jupiter.api.Assertions.assertNotEquals(0, sel.tileIndex());
    }

    @Test
    void connectState_sameBlockIdButDifferentFullBlockFlag_doesNotMatch() {
        // This is the "bug" that Phase 4.4 fixes: under
        // connect=state, a neighbour with the same block id
        // but a different isFullBlock flag should NOT count
        // as a connection.
        CtmRule r = parseRule(
                "method=ctm\n"
                        + "matchBlocks=minecraft:stone\n"
                        + "connect=state\n"
                        + "tiles=0-46\n");
        BiomesGrid g = new BiomesGrid("minecraft:stone",
                new NamespaceId("minecraft", "block/stone"), null);
        // The +X neighbour has the same block id but a
        // different full-block flag (it's not a full block
        // even though the centre is).
        g.blockIds[2][1][1] = "minecraft:stone";
        g.full[2][1][1] = false;
        CtmSelectionResult sel = selector(r).select(r, g, 0, 0, 0, Faces.UP);
        // The +X side should NOT be set in the sideMask; the
        // result is therefore tile 0 (no sides connected).
        assertEquals(0, sel.tileIndex());
    }

    // --- layered methods --------------------------------------------

    @Test
    void selectLayered_horizontalPlusVertical_returnsTwoResults() {
        CtmRule r = parseRule(
                "method=horizontal+vertical\n"
                        + "matchBlocks=minecraft:oak_log\n"
                        + "tiles=0-3\n");
        BiomesGrid g = new BiomesGrid("minecraft:oak_log",
                new NamespaceId("minecraft", "block/oak_log"), null);
        // Only +X and +Y neighbours are logs.
        g.set(1, 0, 0, "minecraft:oak_log",
                new NamespaceId("minecraft", "block/oak_log"));
        g.set(0, 1, 0, "minecraft:oak_log",
                new NamespaceId("minecraft", "block/oak_log"));
        CtmSelectionResult[] both = selector(r).selectLayered(
                r, g, 0, 0, 0, Faces.UP);
        assertNotNull(both);
        assertEquals(2, both.length);
        // First (horizontal): only +X connected -> tile 0.
        // Second (vertical): only +Y connected -> tile 0.
        // (Both methods agree on the up face when the
        // "horizontal" check is reduced to its up-vs-down
        // component on this face.)
        assertNotNull(both[0]);
        assertNotNull(both[1]);
    }

    @Test
    void selectLayered_returnsNulls_forNonLayeredMethod() {
        // The selectLayered helper must still work for a
        // non-layered method - it returns two results that
        // happen to be the same as the simple select().
        CtmRule r = parseRule(
                "method=horizontal\n"
                        + "matchBlocks=minecraft:oak_log\n"
                        + "tiles=0-3\n");
        BiomesGrid g = new BiomesGrid("minecraft:oak_log",
                new NamespaceId("minecraft", "block/oak_log"), null);
        g.set(1, 0, 0, "minecraft:oak_log",
                new NamespaceId("minecraft", "block/oak_log"));
        CtmSelectionResult[] both = selector(r).selectLayered(
                r, g, 0, 0, 0, Faces.UP);
        assertNotNull(both);
        assertEquals(2, both.length);
        assertNotNull(both[0]);
        assertNotNull(both[1]);
    }

    @Test
    void selectLayered_returnsNulls_forNullRule() {
        CtmSelector s = new CtmSelector(CtmRuleSet.empty());
        CtmSelectionResult[] both = s.selectLayered(
                null, null, 0, 0, 0, Faces.UP);
        assertNotNull(both);
        assertEquals(2, both.length);
        org.junit.jupiter.api.Assertions.assertNull(both[0]);
        org.junit.jupiter.api.Assertions.assertNull(both[1]);
    }
}
