package com.cinder.randomentity;

import com.cinder.resource.ComponentMatchers;
import com.cinder.resource.NamespaceId;
import com.cinder.resource.OptifinePropertyParsers;
import com.cinder.resource.PropertiesFile;
import com.cinder.resource.RangeListInt;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Clean-room parser for OptiFine Random Entity property files.
 *
 * <p>Threading: stateless. Performance: reload-time only.
 */
public final class RandomEntityRuleParser {
    private RandomEntityRuleParser() {
    }

    public static RandomEntityRuleSet.Entry parseEntry(
            NamespaceId baseTexture,
            List<RandomEntityVariant> variants,
            PropertiesFile properties) {
        List<RandomEntityRule> rules = new ArrayList<>();
        for (int index : ruleIndices(properties)) {
            String textureSpec = first(properties, "textures." + index,
                    "skins." + index);
            if (textureSpec == null) {
                continue;
            }
            int[] textures = parseIntList(textureSpec);
            int[] weights = parseOptionalIntList(properties.get("weights." + index));
            RandomEntityRule rule = new RandomEntityRule(
                    textures,
                    weights,
                    parseIds(properties.get("biomes." + index)),
                    parseRange(properties.get("heights." + index)),
                    parseMatcher(properties.get("name." + index)),
                    parseStrings(properties.get("professions." + index)),
                    parseStrings(first(properties, "colors." + index,
                            "collarColors." + index)),
                    parseBoolean(properties.get("baby." + index)),
                    parseHealth(properties.get("health." + index)),
                    isPercent(properties.get("health." + index)),
                    parseRange(properties.get("moonPhase." + index)),
                    parseRange(properties.get("dayTime." + index)),
                    parseStrings(properties.get("weather." + index)),
                    parseRange(properties.get("sizes." + index)),
                    parseIds(properties.get("blocks." + index)),
                    OptifinePropertyParsers.parseIntOrDefault(
                            properties.get("seedOffset." + index), 0,
                            Integer.MIN_VALUE, Integer.MAX_VALUE),
                    "vehicle".equals(properties.get("seedSource." + index)));
            rules.add(rule);
        }
        return new RandomEntityRuleSet.Entry(baseTexture, variants, rules);
    }

    public static NamespaceId baseTextureForRandomPath(NamespaceId randomPath) {
        String path = randomPath.path();
        if (path.startsWith("optifine/random/")) {
            path = "textures/" + path.substring("optifine/random/".length());
        } else if (path.startsWith("optifine/mob/")) {
            path = "textures/entity/" + path.substring("optifine/mob/".length());
        }
        path = stripVariant(path);
        return new NamespaceId(randomPath.namespace(), path);
    }

    public static int variantIndex(NamespaceId texture) {
        String file = texture.path();
        int slash = file.lastIndexOf('/');
        if (slash >= 0) {
            file = file.substring(slash + 1);
        }
        if (!file.endsWith(".png")) {
            return 1;
        }
        String stem = file.substring(0, file.length() - 4);
        int sep = stem.lastIndexOf('.');
        int digitStart = sep >= 0 ? sep + 1 : -1;
        if (sep < 0) {
            digitStart = lastNonDigit(stem) + 1;
        }
        if (digitStart <= 0 || digitStart >= stem.length()) {
            return 1;
        }
        try {
            return Integer.parseInt(stem.substring(digitStart));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private static String stripVariant(String path) {
        if (!path.endsWith(".png") && !path.endsWith(".properties")) {
            return path;
        }
        String suffix = path.endsWith(".png") ? ".png" : ".properties";
        String noSuffix = path.substring(0, path.length() - suffix.length());
        int slash = noSuffix.lastIndexOf('/');
        String dir = slash < 0 ? "" : noSuffix.substring(0, slash + 1);
        String stem = slash < 0 ? noSuffix : noSuffix.substring(slash + 1);
        int sep = stem.lastIndexOf('.');
        int digitStart = sep >= 0 ? sep + 1 : -1;
        if (sep < 0) {
            digitStart = lastNonDigit(stem) + 1;
        }
        if (digitStart > 0 && digitStart < stem.length()
                && stem.substring(digitStart).chars()
                .allMatch(Character::isDigit)) {
            stem = stem.substring(0, sep >= 0 ? sep : digitStart);
        }
        return dir + stem + ".png";
    }

    private static int lastNonDigit(String value) {
        for (int i = value.length() - 1; i >= 0; i--) {
            if (!Character.isDigit(value.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static Set<Integer> ruleIndices(PropertiesFile properties) {
        Set<Integer> out = new TreeSet<>();
        for (String key : properties.entries().keySet()) {
            int dot = key.indexOf('.');
            if (dot > 0 && dot + 1 < key.length()) {
                try {
                    out.add(Integer.parseInt(key.substring(dot + 1)));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return out;
    }

    private static String first(PropertiesFile props, String a, String b) {
        String value = props.get(a);
        return value != null ? value : props.get(b);
    }

    private static int[] parseIntList(String raw) {
        RangeListInt ranges = RangeListInt.parse(raw);
        List<Integer> values = new ArrayList<>();
        for (String token : OptifinePropertyParsers.tokens(raw)) {
            if (token.contains("-")) {
                String[] parts = token.split("-", 2);
                int start = Integer.parseInt(parts[0]);
                int end = Integer.parseInt(parts[1]);
                for (int i = start; i <= end; i++) {
                    if (ranges.contains(i)) {
                        values.add(i);
                    }
                }
            } else {
                values.add(Integer.parseInt(token));
            }
        }
        int[] out = new int[values.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = values.get(i);
        }
        return out;
    }

    private static int[] parseOptionalIntList(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] tokens = OptifinePropertyParsers.tokens(raw);
        int[] out = new int[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            out[i] = Integer.parseInt(tokens[i]);
        }
        return out;
    }

    private static RangeListInt parseRange(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return RangeListInt.parse(raw.replace("%", ""));
    }

    private static RangeListInt parseHealth(String raw) {
        return parseRange(raw);
    }

    private static boolean isPercent(String raw) {
        return raw != null && raw.contains("%");
    }

    private static ComponentMatchers.Compiled parseMatcher(String raw) {
        return raw == null || raw.isBlank() ? null : ComponentMatchers.parse(raw);
    }

    private static Boolean parseBoolean(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Boolean.parseBoolean(raw);
    }

    private static Set<String> parseStrings(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String token : OptifinePropertyParsers.tokens(raw)) {
            out.add(token.toLowerCase(Locale.ROOT));
        }
        return out;
    }

    private static Set<NamespaceId> parseIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        Set<NamespaceId> out = new LinkedHashSet<>();
        for (String token : OptifinePropertyParsers.tokens(raw)) {
            out.add(NamespaceId.parse(token));
        }
        return out;
    }
}
