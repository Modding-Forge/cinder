package com.cinder.customgui;

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
 * Clean-room parser for OptiFine-style Custom GUI properties.
 *
 * <p>Purpose: parses resource-pack files below {@code optifine/gui/container}
 * into loader-agnostic immutable rules. It resolves textual paths but does not
 * inspect Minecraft screens, block entities, or texture managers.
 *
 * <p>Threading: stateless utility class.
 *
 * <p>Performance: reload-only.
 */
public final class CustomGuiRuleParser {

    private static final String TEXTURE_PREFIX = "texture.";

    private CustomGuiRuleParser() {
    }

    /**
     * One raw source file supplied by a loader resource scanner.
     */
    public record RuleSource(String body, String sourceLabel) {
        public RuleSource {
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(sourceLabel, "sourceLabel");
        }
    }

    /**
     * Parses a single properties string.
     */
    public static CustomGuiRule parseString(String body, String sourceLabel) {
        try {
            PropertiesFile props =
                    PropertiesFile.parse(new StringReader(body));
            return parse(props, sourceLabel);
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "failed to parse " + sourceLabel, e);
        }
    }

    /**
     * Parses all provided sources, isolating malformed files.
     */
    public static CustomGuiParseResult parseAll(List<RuleSource> sources) {
        ArrayList<CustomGuiRule> rules = new ArrayList<>();
        ArrayList<CustomGuiParseResult.Error> errors = new ArrayList<>();
        if (sources == null) {
            return new CustomGuiParseResult(rules, errors);
        }
        for (RuleSource source : sources) {
            try {
                rules.add(parseString(source.body(), source.sourceLabel()));
            } catch (RuntimeException e) {
                errors.add(new CustomGuiParseResult.Error(
                        source.sourceLabel(), e.getMessage()));
            }
        }
        return new CustomGuiParseResult(rules, errors);
    }

    /**
     * Parses and sorts a complete rule set.
     */
    public static CustomGuiRuleSet buildRuleSet(List<RuleSource> sources) {
        return CustomGuiRuleSet.of(parseAll(sources).rules());
    }

    private static CustomGuiRule parse(PropertiesFile props,
                                       String sourceLabel) {
        String container = props.get("container");
        if (container == null || container.isBlank()) {
            throw new IllegalArgumentException(
                    "container is required in " + sourceLabel);
        }
        int weight = parseInt(props.get("weight"), 0, "weight");
        CustomGuiReplacement replacement =
                parseReplacement(props, sourceLabel);
        return new CustomGuiRule(sourceLabel, container, weight,
                ConditionPropertiesReader.readGuiConditions(props),
                replacement);
    }

    private static CustomGuiReplacement parseReplacement(PropertiesFile props,
                                                         String sourceLabel) {
        NamespaceId texture = resolveTexture(
                props.get("texture"), sourceLabel);
        LinkedHashMap<String, NamespaceId> namedTextures =
                new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : props.entries().entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(TEXTURE_PREFIX)
                    && key.length() > TEXTURE_PREFIX.length()) {
                namedTextures.put(key.substring(TEXTURE_PREFIX.length()),
                        resolveTexture(entry.getValue(), sourceLabel));
            }
        }
        return new CustomGuiReplacement(texture, namedTextures);
    }

    private static NamespaceId resolveTexture(String raw, String sourceLabel) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        NamespaceId id = resolvePath(raw.trim(), sourceLabel);
        return id.path().endsWith(".png")
                ? id
                : new NamespaceId(id.namespace(), id.path() + ".png");
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

    private static String directory(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
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
