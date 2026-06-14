package com.cinder.quad;

import com.cinder.resource.NamespaceId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link QuadRef} - the loader-agnostic quad
 * handle passed through the decorator pipeline.
 *
 * <p>The default {@code withSprite} implementation throws;
 * tests verify that contract so the pipeline never silently
 * retextures a quad that the adapter cannot actually
 * retexture.
 */
class QuadRefTest {

    /** A minimal adapter-supplied QuadRef for tests. */
    private static final class StubRef implements QuadRef {
        private final NamespaceId sprite;
        private final String blockId;
        StubRef(NamespaceId sprite, String blockId) {
            this.sprite = sprite;
            this.blockId = blockId;
        }
        @Override public NamespaceId sprite() { return sprite; }
        @Override public String blockId() { return blockId; }
        @Override public int lightEmission() { return 0; }
        @Override public int tintIndex() { return -1; }
        @Override public float aoShade() { return 1.0f; }
    }

    @Test
    void defaultWithSprite_throws() {
        QuadRef ref = new StubRef(
                new NamespaceId("minecraft", "block/glass"),
                "minecraft:glass");
        NamespaceId newSprite = new NamespaceId(
                "minecraft", "block/glass_edge");
        assertThrows(UnsupportedOperationException.class,
                () -> ref.withSprite(newSprite));
    }

    @Test
    void defaultWithSprite_rejectsNull() {
        QuadRef ref = new StubRef(
                new NamespaceId("minecraft", "block/glass"),
                "minecraft:glass");
        assertThrows(NullPointerException.class,
                () -> ref.withSprite(null));
    }

    @Test
    void getters_returnConstructorValues() {
        NamespaceId sprite = new NamespaceId("minecraft", "block/glass");
        QuadRef ref = new StubRef(sprite, "minecraft:glass");
        assertEquals(sprite, ref.sprite());
        assertEquals("minecraft:glass", ref.blockId());
        assertEquals(0, ref.lightEmission());
        assertEquals(-1, ref.tintIndex());
        assertEquals(1.0f, ref.aoShade());
    }
}
