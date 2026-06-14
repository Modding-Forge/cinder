package com.cinder.resource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamespaceIdTest {

    @Test
    void parse_explicitNamespace() {
        NamespaceId id = NamespaceId.parse("minecraft:path/to/x.png");
        assertEquals("minecraft", id.namespace());
        assertEquals("path/to/x.png", id.path());
    }

    @Test
    void parse_defaultNamespace() {
        NamespaceId id = NamespaceId.parse("path/to/x.png");
        assertEquals("minecraft", id.namespace());
        assertEquals("path/to/x.png", id.path());
    }

    @Test
    void parse_backslashesToSlashes() {
        NamespaceId id = NamespaceId.parse("path\\\\to\\\\x.png");
        assertEquals("path/to/x.png", id.path());
    }

    @Test
    void parse_null_throws() {
        assertThrows(IllegalArgumentException.class, () -> NamespaceId.parse(null));
    }

    @Test
    void parse_emptyNamespace_throws() {
        assertThrows(IllegalArgumentException.class, () -> NamespaceId.parse(":foo"));
    }

    @Test
    void parse_emptyPath_throws() {
        assertThrows(IllegalArgumentException.class, () -> NamespaceId.parse("ns:"));
    }

    @Test
    void parse_invalidChars_throws() {
        assertThrows(IllegalArgumentException.class, () -> NamespaceId.parse("ns:foo bar"));
        assertThrows(IllegalArgumentException.class, () -> NamespaceId.parse("ns:foo?bar"));
    }

    @Test
    void toString_canonical() {
        NamespaceId id = new NamespaceId("minecraft", "foo/bar");
        assertEquals("minecraft:foo/bar", id.toString());
    }

    @Test
    void recordEquality() {
        NamespaceId a = new NamespaceId("minecraft", "foo");
        NamespaceId b = new NamespaceId("minecraft", "foo");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void record_constructor_guardsEmpty() {
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> new NamespaceId("", "x")) instanceof IllegalArgumentException);
    }
}
