package com.cinder.quad;

import com.cinder.resource.NamespaceId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link QuadContext} - the loader-agnostic
 * position/sprite description passed to every decorator.
 */
class QuadContextTest {

    @Test
    void constructor_rejectsNullBlockId() {
        NamespaceId sprite = new NamespaceId("minecraft", "block/glass");
        assertThrows(NullPointerException.class,
                () -> new QuadContext(0, 0, 0, 1, null, sprite));
    }

    @Test
    void constructor_rejectsNullSprite() {
        assertThrows(NullPointerException.class,
                () -> new QuadContext(0, 0, 0, 1, "minecraft:glass", null));
    }

    @Test
    void faceName_returnsCanonicalNames() {
        NamespaceId sprite = new NamespaceId("minecraft", "block/glass");
        String block = "minecraft:glass";
        assertEquals("down",  new QuadContext(0, 0, 0, 0, block, sprite).faceName());
        assertEquals("up",    new QuadContext(0, 0, 0, 1, block, sprite).faceName());
        assertEquals("north", new QuadContext(0, 0, 0, 2, block, sprite).faceName());
        assertEquals("south", new QuadContext(0, 0, 0, 3, block, sprite).faceName());
        assertEquals("west",  new QuadContext(0, 0, 0, 4, block, sprite).faceName());
        assertEquals("east",  new QuadContext(0, 0, 0, 5, block, sprite).faceName());
    }

    @Test
    void faceName_unknownOrdinal_returnsFaceN() {
        NamespaceId sprite = new NamespaceId("minecraft", "block/glass");
        String name = new QuadContext(0, 0, 0, 99,
                "minecraft:glass", sprite).faceName();
        assertEquals("face99", name);
    }

    @Test
    void record_components_areReturned() {
        NamespaceId sprite = new NamespaceId("minecraft", "block/glass");
        QuadContext c = new QuadContext(5, 64, -7, 1, "minecraft:stone", sprite);
        assertEquals(5, c.x());
        assertEquals(64, c.y());
        assertEquals(-7, c.z());
        assertEquals(1, c.face());
        assertEquals("minecraft:stone", c.blockId());
        assertEquals(sprite, c.sprite());
        assertNull(c.neighborView());
    }
}
