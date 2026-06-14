package com.cinder.resource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link ResourcePath} covering all seven supported syntactic
 * forms documented in the OptiFine documentation.
 */
class ResourcePathTest {

    @Test
    void fullPathWithNamespace() {
        NamespaceId id = ResourcePath.resolveOptifine(
                "minecraft:textures/block/stone.png", NamespaceId.DEFAULT_NAMESPACE, null);
        assertEquals("minecraft", id.namespace());
        assertEquals("textures/block/stone.png", id.path());
    }

    @Test
    void modNamespace() {
        NamespaceId id = ResourcePath.resolveOptifine(
                "botania:blazeblock", NamespaceId.DEFAULT_NAMESPACE, null);
        assertEquals("botania", id.namespace());
        assertEquals("blazeblock", id.path());
    }

    @Test
    void tildePrefixBecomesOptifineRoot() {
        NamespaceId id = ResourcePath.resolveOptifine(
                "~/dial/clock0.png", NamespaceId.DEFAULT_NAMESPACE, null);
        assertEquals("minecraft", id.namespace());
        assertEquals("optifine/dial/clock0.png", id.path());
    }

    @Test
    void bareTildeBecomesOptifineRoot() {
        NamespaceId id = ResourcePath.resolveOptifine(
                "~", NamespaceId.DEFAULT_NAMESPACE, null);
        assertEquals("minecraft", id.namespace());
        assertEquals("optifine/", id.path());
    }

    @Test
    void relativeToParentDirectory() {
        NamespaceId parent = new NamespaceId("minecraft", "optifine/ctm/glass");
        NamespaceId id = ResourcePath.resolveOptifine(
                "1.png", NamespaceId.DEFAULT_NAMESPACE, parent);
        assertEquals("minecraft", id.namespace());
        assertEquals("optifine/ctm/glass/1.png", id.path());
    }

    @Test
    void dotSlashRelativeToParentDirectory() {
        NamespaceId parent = new NamespaceId("minecraft", "optifine/ctm/glass");
        NamespaceId id = ResourcePath.resolveOptifine(
                "./subfolder/2.png", NamespaceId.DEFAULT_NAMESPACE, parent);
        assertEquals("minecraft", id.namespace());
        assertEquals("optifine/ctm/glass/subfolder/2.png", id.path());
    }

    @Test
    void relativePathResolvesToParent() {
        NamespaceId parent = new NamespaceId("minecraft", "optifine/ctm");
        NamespaceId id = ResourcePath.resolveOptifine(
                "ctm/glass/1.png", NamespaceId.DEFAULT_NAMESPACE, parent);
        // "ctm/glass/1.png" matches the relative-form: the parent dir
        // contributes, so the result is the literal input under the
        // default namespace.
        assertEquals("minecraft", id.namespace());
        assertEquals("optifine/ctm/glass/1.png", id.path());
    }

    @Test
    void backwardSlashesNormalised() {
        NamespaceId id = ResourcePath.resolveOptifine(
                "minecraft:textures\\\\block\\\\stone.png",
                NamespaceId.DEFAULT_NAMESPACE, null);
        assertEquals("textures/block/stone.png", id.path());
    }

    @Test
    void emptyPath_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> ResourcePath.resolveOptifine("", NamespaceId.DEFAULT_NAMESPACE, null));
    }

    @Test
    void nullPath_throws() {
        assertThrows(NullPointerException.class,
                () -> ResourcePath.resolveOptifine(null, NamespaceId.DEFAULT_NAMESPACE, null));
    }

    @Test
    void defaultNamespaceUsedWhenNoColon() {
        NamespaceId id = ResourcePath.resolveOptifine(
                "textures/block/stone.png", "customns", null);
        assertEquals("customns", id.namespace());
    }

    @Test
    void nonOptifineResolve_doesNotInjectRoot() {
        NamespaceId id = ResourcePath.resolve(
                "minecraft:textures/block/stone.png", NamespaceId.DEFAULT_NAMESPACE, null);
        assertEquals("textures/block/stone.png", id.path());
    }
}
