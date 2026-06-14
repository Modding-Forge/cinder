package com.cinder.resource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OptifinePropertyParsersTest {

    @Test
    void parsesStrictBooleansWithFallback() {
        assertEquals(true, OptifinePropertyParsers.parseBoolean("true",
                false));
        assertEquals(false, OptifinePropertyParsers.parseBoolean("0",
                true));
        assertEquals(true, OptifinePropertyParsers.parseBoolean("maybe",
                true));
    }

    @Test
    void parsesBoundedInts() {
        assertEquals(2, OptifinePropertyParsers.parseIntOrDefault("2",
                0, 0, 4));
        assertEquals(7, OptifinePropertyParsers.parseIntOrDefault("9",
                7, 0, 4));
        assertEquals(3, OptifinePropertyParsers.requireInt(null,
                3, 0, 4, "value"));
        assertThrows(IllegalArgumentException.class,
                () -> OptifinePropertyParsers.requireInt("-1",
                        0, 0, 4, "value"));
    }

    @Test
    void parsesRgbWithFallback() {
        assertEquals(0x12ABEF, OptifinePropertyParsers.parseRgbOrDefault(
                "#12ABEF", 0));
        assertEquals(0x000003, OptifinePropertyParsers.parseRgbOrDefault(
                "000003", 0));
        assertEquals(0x445566, OptifinePropertyParsers.parseRgbOrDefault(
                "nope", 0x445566));
    }

    @Test
    void resolvesCommonOptifinePaths() {
        NamespaceId parent = new NamespaceId("minecraft",
                "optifine/sky/world0");
        assertEquals(new NamespaceId("minecraft",
                        "optifine/sky/world0/stars.png"),
                OptifinePropertyParsers.resolveOptifinePath(
                        "./stars.png", "minecraft", parent));
        assertEquals(new NamespaceId("minecraft",
                        "optifine/sky/clouds.png"),
                OptifinePropertyParsers.resolveOptifinePath(
                        "~/sky/clouds.png", "minecraft", parent));
        assertEquals(new NamespaceId("example", "sky/nebula.png"),
                OptifinePropertyParsers.resolveOptifinePath(
                        "example:sky/nebula.png", "minecraft", parent));
    }

    @Test
    void derivesParentAndDefaultPng() {
        assertEquals(new NamespaceId("minecraft",
                        "optifine/anim"),
                OptifinePropertyParsers.parentOf(
                        "minecraft:optifine/anim/stone.properties"));
        assertEquals(new NamespaceId("minecraft",
                        "optifine/anim/stone.png"),
                OptifinePropertyParsers.defaultPngFor(
                        "minecraft:optifine/anim/stone.properties"));
    }
}
