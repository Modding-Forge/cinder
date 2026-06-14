package com.cinder.emissive;

import com.cinder.resource.NamespaceId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class EmissivePropertiesTest {

    @AfterEach
    void clearTable() {
        EmissiveSpriteTable.replace(null);
    }

    @Test
    void emptyProperties_useDefaultSuffix() throws Exception {
        EmissiveSettings settings = EmissiveProperties.parse(
                new StringReader(""));

        assertEquals("_e", settings.suffix());
    }

    @Test
    void suffixEmissive_isParsed() throws Exception {
        EmissiveSettings settings = EmissiveProperties.parse(
                new StringReader("suffix.emissive=_glow\n"));

        assertEquals("_glow", settings.suffix());
    }

    @Test
    void tablePublishesMappings() {
        NamespaceId base = new NamespaceId("minecraft", "block/stone");
        NamespaceId emissive = new NamespaceId("minecraft", "block/stone_e");
        EmissiveSpriteTable table = EmissiveSpriteTable.of(
                Map.of(base, emissive));

        EmissiveSpriteTable.replace(table);

        assertSame(table, EmissiveSpriteTable.current());
        assertEquals(emissive, EmissiveSpriteTable.current()
                .emissiveSprite(base));
        assertNull(EmissiveSpriteTable.current().emissiveSprite(
                new NamespaceId("minecraft", "block/dirt")));
    }
}
