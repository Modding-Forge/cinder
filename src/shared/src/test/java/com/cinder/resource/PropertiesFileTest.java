package com.cinder.resource;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link PropertiesFile}. The expected behavior is documented in
 * the class Javadoc and is what OptiFine-compatible resource packs depend on
 * (verified against {@code optifine/OptiFineDoc/doc/} examples).
 */
class PropertiesFileTest {

    @Test
    void simpleKeyValue() throws IOException {
        PropertiesFile p = PropertiesFile.parse(new StringReader(
                "method=ctm\n"
                        + "tiles=0-46\n"
                        + "matchBlocks=minecraft:stone\n"));
        assertEquals("ctm", p.get("method"));
        assertEquals("0-46", p.get("tiles"));
        assertEquals("minecraft:stone", p.get("matchBlocks"));
    }

    @Test
    void commentsAndBlankLines() throws IOException {
        PropertiesFile p = PropertiesFile.parse(new StringReader(
                "# this is a comment\n"
                        + "\n"
                        + "  # indented comment\n"
                        + "method=ctm\n"));
        assertEquals(1, p.size());
        assertEquals("ctm", p.get("method"));
    }

    @Test
    void caseSensitive() throws IOException {
        PropertiesFile p = PropertiesFile.parse(new StringReader(
                "Method=ctm\n"
                        + "method=horizontal\n"));
        // The OF loader is case-sensitive: both keys are stored.
        assertEquals(2, p.size());
        assertEquals("ctm", p.get("Method"));
        assertEquals("horizontal", p.get("method"));
    }

    @Test
    void flagKeysWithoutEquals() throws IOException {
        // Some OptiFine keys are flags: the key's presence is the signal.
        PropertiesFile p = PropertiesFile.parse(new StringReader(
                "useGlint\n"
                        + "method=ctm\n"));
        assertEquals("", p.get("useGlint"));
        assertEquals("ctm", p.get("method"));
    }

    @Test
    void firstWinsOnDuplicates() throws IOException {
        PropertiesFile p = PropertiesFile.parse(new StringReader(
                "method=ctm\n"
                        + "method=horizontal\n"));
        assertEquals("ctm", p.get("method"));
    }

    @Test
    void noBackslashEscaping() throws IOException {
        // Values are literal. A path like "C:\path" should round-trip
        // unchanged (OptiFine uses '/' exclusively, but we must not
        // mangle it).
        PropertiesFile p = PropertiesFile.parse(new StringReader(
                "texture=C:\\path\\to\\tex\n"));
        assertEquals("C:\\path\\to\\tex", p.get("texture"));
    }

    @Test
    void valuesCanContainEquals() throws IOException {
        PropertiesFile p = PropertiesFile.parse(new StringReader(
                "condition=nbt.display.Name=foo\n"));
        // We split on the first '=' only.
        assertEquals("nbt.display.Name=foo", p.get("condition"));
    }

    @Test
    void valuesCanContainColons() throws IOException {
        // Block-spec syntax uses ':' inside a value (e.g. matchBlocks).
        PropertiesFile p = PropertiesFile.parse(new StringReader(
                "matchBlocks=minecraft:oak_stairs:facing=east,west:half=bottom\n"));
        assertEquals("minecraft:oak_stairs:facing=east,west:half=bottom",
                p.get("matchBlocks"));
    }

    @Test
    void internalWhitespacePreserved() throws IOException {
        PropertiesFile p = PropertiesFile.parse(new StringReader(
                "name=  my   special   pack\n"));
        // Leading/trailing stripped, internal preserved.
        assertEquals("my   special   pack", p.get("name"));
    }

    @Test
    void getTokens_splitsOnWhitespace() throws IOException {
        PropertiesFile p = PropertiesFile.parse(new StringReader(
                "matchBlocks=stone dirt gold_ore\n"));
        String[] tokens = p.getTokens("matchBlocks");
        assertEquals(3, tokens.length);
        assertTrue(tokens[0].equals("stone")
                && tokens[1].equals("dirt")
                && tokens[2].equals("gold_ore"));
    }

    @Test
    void getTokens_missingKey_returnsEmpty() throws IOException {
        PropertiesFile p = PropertiesFile.parse(new StringReader(
                "method=ctm\n"));
        assertEquals(0, p.getTokens("missing").length);
    }

    @Test
    void missingKey_returnsNull() throws IOException {
        PropertiesFile p = PropertiesFile.parse(new StringReader(
                "method=ctm\n"));
        assertNull(p.get("missing"));
        assertEquals("default", p.get("missing", "default"));
    }
}
