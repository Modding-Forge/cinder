package com.cinder.natural;

import com.cinder.resource.NamespaceId;
import com.cinder.resource.OptifinePropertyParsers;
import com.cinder.resource.PropertiesFile;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;

/**
 * Parser for OptiFine {@code optifine/natural.properties}.
 *
 * <p>Threading: stateless reload-time parser. Performance: normalizes texture
 * names to block atlas sprite IDs so render lookup is direct.
 */
public final class NaturalTextureProperties {

    private static final String DEFAULT_RULES = """
            grass_block_top=4F
            dirt=4F
            coarse_dirt=4F
            rooted_dirt=4F
            stone=4
            cobblestone=4
            mossy_cobblestone=4
            deepslate=4
            tuff=4
            granite=4
            diorite=4
            andesite=4
            sand=4
            red_sand=4
            gravel=4
            clay=4
            snow=4F
            netherrack=4
            end_stone=4
            """;

    private NaturalTextureProperties() {
    }

    public static NaturalTextureParseResult parse(String body,
                                                  String sourceFile) {
        PropertiesFile props;
        try {
            props = PropertiesFile.parse(new StringReader(
                    body == null ? "" : body));
        } catch (IOException e) {
            return new NaturalTextureParseResult(java.util.List.of(),
                    java.util.List.of(new NaturalTextureParseResult.Error(
                            sourceFile, "<file>", "", e.getMessage())));
        }
        ArrayList<NaturalTextureRule> rules = new ArrayList<>();
        ArrayList<NaturalTextureParseResult.Error> errors = new ArrayList<>();
        for (Map.Entry<String, String> entry : props.entries().entrySet()) {
            try {
                NaturalTextureRule rule = parseRule(entry.getKey(),
                        entry.getValue());
                if (rule != null) {
                    rules.add(rule);
                }
            } catch (RuntimeException e) {
                errors.add(new NaturalTextureParseResult.Error(sourceFile,
                        entry.getKey(), entry.getValue(), e.getMessage()));
            }
        }
        return new NaturalTextureParseResult(rules, errors);
    }

    /**
     * Returns Cinder's clean-room default Natural Textures table.
     */
    public static NaturalTextureRuleSet defaults() {
        return NaturalTextureRuleSet.of(parse(DEFAULT_RULES,
                "cinder:default_natural.properties").rules());
    }

    private static NaturalTextureRule parseRule(String key, String raw) {
        if (key == null || key.isBlank() || raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        int rotations = 1;
        boolean flip = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '2') {
                rotations = 2;
            } else if (c == '4') {
                rotations = 4;
            } else if (c == 'F') {
                flip = true;
            } else {
                throw new IllegalArgumentException(
                        "invalid natural texture token: " + raw);
            }
        }
        return new NaturalTextureRule(spriteId(key.trim()), rotations, flip);
    }

    private static NamespaceId spriteId(String key) {
        String normalized = OptifinePropertyParsers.stripPng(key);
        NamespaceId id = normalized.indexOf(':') > 0
                ? NamespaceId.parse(normalized)
                : new NamespaceId("minecraft", normalized);
        String path = id.path();
        if (!path.startsWith("block/") && !path.startsWith("item/")) {
            path = "block/" + path;
        }
        return new NamespaceId(id.namespace(), path);
    }
}
