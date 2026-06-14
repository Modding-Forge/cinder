package com.cinder.cit;

import com.cinder.condition.ConditionPropertiesReader;
import com.cinder.resource.NamespaceId;
import com.cinder.resource.PropertiesFile;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Clean-room parser for OptiFine-style {@code optifine/cit/*.properties}.
 *
 * <p>Purpose: turns raw resource-pack files into immutable shared CIT rules.
 * It owns only format parsing and path resolution; it does not scan resource
 * packs, resolve Minecraft registry entries, or bake models.
 *
 * <p>Threading: stateless utility class.
 *
 * <p>Performance: reload-only; O(file size + number of declared keys).
 */
public final class CitRuleParser {

    private static final String TEXTURE_PREFIX = "texture.";
    private static final String MODEL_PREFIX = "model.";

    private CitRuleParser() {
    }

    public record RuleSource(String body, String sourceLabel) {
        public RuleSource {
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(sourceLabel, "sourceLabel");
        }
    }

    public static CitRule parseString(String body, String sourceLabel) {
        try {
            PropertiesFile props =
                    PropertiesFile.parse(new StringReader(body));
            return parse(props, sourceLabel);
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "failed to parse " + sourceLabel, e);
        }
    }

    public static CitParseResult parseAll(List<RuleSource> sources) {
        ArrayList<CitRule> rules = new ArrayList<>();
        ArrayList<CitParseResult.Error> errors = new ArrayList<>();
        if (sources == null) {
            return new CitParseResult(rules, errors);
        }
        for (RuleSource source : sources) {
            try {
                rules.add(parseString(source.body(), source.sourceLabel()));
            } catch (RuntimeException e) {
                errors.add(new CitParseResult.Error(
                        source.sourceLabel(), e.getMessage()));
            }
        }
        return new CitParseResult(rules, errors);
    }

    public static CitRuleSet buildRuleSet(List<RuleSource> sources) {
        return CitRuleSet.of(parseAll(sources).rules());
    }

    private static CitRule parse(PropertiesFile props, String sourceLabel) {
        CitRuleType type = CitRuleType.parse(props.get("type"));
        int weight = parseInt(props.get("weight"), 0, "weight");
        NamespaceId[] items = parseItems(props);
        CitReplacement replacement = parseReplacement(props, sourceLabel);
        return new CitRule(sourceLabel, type, weight, items,
                ConditionPropertiesReader.readCitConditions(props),
                replacement);
    }

    private static NamespaceId[] parseItems(PropertiesFile props) {
        String itemList = firstPresent(props, "matchItems", "items");
        if (itemList == null || itemList.isBlank()) {
            return new NamespaceId[0];
        }
        String[] tokens = itemList.trim().split("[,\\s]+");
        NamespaceId[] out = new NamespaceId[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            out[i] = NamespaceId.parse(tokens[i]);
        }
        return out;
    }

    private static CitReplacement parseReplacement(PropertiesFile props,
                                                   String sourceLabel) {
        NamespaceId texture = resolveTexture(
                props.get("texture"), sourceLabel, true);
        NamespaceId model = resolveModel(props.get("model"), sourceLabel);
        LinkedHashMap<String, NamespaceId> namedTextures =
                new LinkedHashMap<>();
        LinkedHashMap<String, NamespaceId> namedModels =
                new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : props.entries().entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(TEXTURE_PREFIX)
                    && key.length() > TEXTURE_PREFIX.length()) {
                namedTextures.put(key.substring(TEXTURE_PREFIX.length()),
                        resolveTexture(entry.getValue(), sourceLabel, false));
            } else if (key.startsWith(MODEL_PREFIX)
                    && key.length() > MODEL_PREFIX.length()) {
                namedModels.put(key.substring(MODEL_PREFIX.length()),
                        resolveModel(entry.getValue(), sourceLabel));
            }
        }
        return new CitReplacement(texture, model, namedTextures, namedModels);
    }

    private static NamespaceId resolveTexture(String raw,
                                              String sourceLabel,
                                              boolean defaultFromFile) {
        if (raw == null || raw.isBlank()) {
            if (!defaultFromFile) {
                return null;
            }
            return NamespaceId.parse(stripExtension(sourceLabel));
        }
        NamespaceId id = resolvePath(raw.trim(), sourceLabel);
        return id.path().endsWith(".png")
                ? new NamespaceId(id.namespace(),
                id.path().substring(0, id.path().length() - 4))
                : id;
    }

    private static NamespaceId resolveModel(String raw, String sourceLabel) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        NamespaceId id = resolvePath(raw.trim(), sourceLabel);
        return id.path().endsWith(".json")
                ? new NamespaceId(id.namespace(),
                id.path().substring(0, id.path().length() - 5))
                : id;
    }

    private static NamespaceId resolvePath(String raw, String sourceLabel) {
        String normalized = raw.replace('\\', '/');
        if (normalized.indexOf(':') > 0) {
            return NamespaceId.parse(normalized);
        }
        NamespaceId source = NamespaceId.parse(sourceLabel);
        String dir = directory(source.path());
        if (normalized.startsWith("./")) {
            return new NamespaceId(source.namespace(),
                    dir + "/" + normalized.substring(2));
        }
        if (normalized.indexOf('/') < 0) {
            return new NamespaceId(source.namespace(),
                    dir + "/" + normalized);
        }
        return new NamespaceId(source.namespace(), normalized);
    }

    private static String stripExtension(String sourceLabel) {
        NamespaceId source = NamespaceId.parse(sourceLabel);
        String path = source.path();
        if (path.endsWith(".properties")) {
            path = path.substring(0, path.length() - ".properties".length());
        }
        return source.namespace() + ":" + path;
    }

    private static String directory(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }

    private static String firstPresent(PropertiesFile props,
                                       String first,
                                       String second) {
        String value = props.get(first);
        return value != null ? value : props.get(second);
    }

    private static int parseInt(String value, int fallback, String key) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(String.format(
                    Locale.ROOT, "%s=%s is not an integer", key, value), e);
        }
    }
}
