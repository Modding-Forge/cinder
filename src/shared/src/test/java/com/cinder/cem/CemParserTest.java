package com.cinder.cem;

import com.cinder.resource.NamespaceId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CemParserTest {
    @Test
    void parsesMinimalJemStructure() {
        CemModel model = CemParser.parse(
                "minecraft:optifine/cem/creeper.jem",
                """
                {
                  "texture": "./creeper_custom",
                  "textureSize": [64, 64],
                  "shadowSize": 0.6,
                  "models": [
                    {
                      "part": "head",
                      "attach": "true",
                      "translate": [1, 2, 3],
                      "rotate": [0.1, 0.2, 0.3],
                      "boxes": [[0, 1, 2, 3, 4, 5]]
                    }
                  ]
                }
                """);

        assertEquals(NamespaceId.parse(
                "minecraft:optifine/cem/creeper_custom.png"),
                model.texture());
        assertEquals(64, model.textureWidth());
        assertEquals(64, model.textureHeight());
        assertEquals(0.6f, model.shadowSize());
        assertEquals("head", model.parts().getFirst().part());
        assertEquals(1.0f, model.parts().getFirst().tx());
        assertEquals(1, model.parts().getFirst().boxes().size());
    }

    @Test
    void parseAllIsolatesBrokenModels() {
        CemParseResult result = CemParser.parseAll(List.of(
                new CemParser.Source("minecraft:optifine/cem/pig.jem",
                        "{\"models\":[{\"part\":\"body\"}]}"),
                new CemParser.Source("minecraft:optifine/cem/broken.jem",
                        "{\"texture\":\"x\"}")));

        assertEquals(1, result.models().size());
        assertEquals(1, result.errors().size());
    }
}
