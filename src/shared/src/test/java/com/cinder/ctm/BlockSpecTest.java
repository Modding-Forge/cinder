package com.cinder.ctm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockSpecTest {

    @Test
    void bareName() {
        BlockSpec s = BlockSpec.parse("stone");
        assertEquals("minecraft", s.namespace());
        assertEquals("stone", s.name());
        assertTrue(s.properties().isEmpty());
    }

    @Test
    void namespacedName() {
        BlockSpec s = BlockSpec.parse("botania:blazeblock");
        assertEquals("botania", s.namespace());
        assertEquals("blazeblock", s.name());
    }

    @Test
    void withOneProperty() {
        BlockSpec s = BlockSpec.parse("minecraft:oak_stairs:facing=east");
        assertEquals("oak_stairs", s.name());
        assertEquals(1, s.properties().size());
        assertTrue(s.properties().get("facing").contains("east"));
    }

    @Test
    void withMultiValueProperty() {
        BlockSpec s = BlockSpec.parse(
                "minecraft:oak_stairs:facing=east,west:half=bottom");
        assertTrue(s.properties().get("facing").contains("east"));
        assertTrue(s.properties().get("facing").contains("west"));
        assertTrue(s.properties().get("half").contains("bottom"));
    }

    @Test
    void missingEquals_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> BlockSpec.parse("minecraft:oak_stairs:facing"));
    }

    @Test
    void emptySpec_throws() {
        assertThrows(IllegalArgumentException.class, () -> BlockSpec.parse(""));
    }

    @Test
    void nullSpec_throws() {
        assertThrows(IllegalArgumentException.class, () -> BlockSpec.parse(null));
    }

    @Test
    void toString_roundtrip() {
        String s = "minecraft:oak_stairs:facing=east,west:half=bottom";
        assertEquals(s, BlockSpec.parse(s).toString());
    }
}
