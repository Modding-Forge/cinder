package com.cinder.ctm;

import com.cinder.resource.NamespaceId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NeighborCacheTest {

    @Test
    void defaultState_isEmpty() {
        NeighborCache c = new NeighborCache();
        assertNull(c.sprite(0, 0, 0, Faces.UP));
        assertNull(c.blockId(0, 0, 0));
        assertEquals(false, c.isFullBlock(0, 0, 0));
    }

    @Test
    void setAndGet_roundtrip() {
        NeighborCache c = new NeighborCache();
        NamespaceId sprite = new NamespaceId("minecraft", "block/stone");
        c.setCenterSprite(Faces.UP, sprite);
        c.set(0, 0, 0, "minecraft:stone", true);
        assertEquals(sprite, c.sprite(0, 0, 0, Faces.UP));
        assertEquals("minecraft:stone", c.blockId(0, 0, 0));
        assertEquals(true, c.isFullBlock(0, 0, 0));
    }

    @Test
    void reset_clearsAllCells() {
        NeighborCache c = new NeighborCache();
        c.set(0, 0, 0, "minecraft:stone", true);
        c.setCenterSprite(Faces.UP, new NamespaceId("minecraft", "block/stone"));
        c.reset();
        assertNull(c.blockId(0, 0, 0));
        assertNull(c.sprite(0, 0, 0, Faces.UP));
    }

    @Test
    void neighbourOffsets_work() {
        NeighborCache c = new NeighborCache();
        c.set(1, 0, 0, "minecraft:dirt", false);
        c.setSprite(1, 0, 0, Faces.WEST, new NamespaceId("minecraft", "block/dirt"));
        assertEquals("minecraft:dirt", c.blockId(1, 0, 0));
        assertEquals(new NamespaceId("minecraft", "block/dirt"),
                c.sprite(1, 0, 0, Faces.WEST));
        // Other neighbour should still be empty.
        assertNull(c.blockId(-1, 0, 0));
    }

    @Test
    void all27Cells_addressable() {
        NeighborCache c = new NeighborCache();
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    c.set(dx, dy, dz, "block_" + dx + "_" + dy + "_" + dz, true);
                }
        // Spot-check corner.
        assertEquals("block_-1_-1_-1", c.blockId(-1, -1, -1));
        assertEquals("block_1_1_1", c.blockId(1, 1, 1));
    }
}
