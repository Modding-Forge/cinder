package com.cinder.cem;

import com.cinder.resource.NamespaceId;
import com.cinder.resource.OptifinePropertyParsers;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small clean-room parser for the first CEM data-model batch.
 *
 * <p>This parser intentionally covers only the stable structural fields needed
 * to build a renderer adapter later: texture, texture size, shadow size, part
 * names, attach modes, transforms, and simple boxes. It does not copy or
 * mirror OptiFine/EMF internals and does not try to evaluate animations.
 *
 * <p>Threading: stateless. Performance: reload-time only.
 */
public final class CemParser {
    private static final Pattern STRING_FIELD = Pattern.compile(
            "\"%s\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern NUMBER_FIELD = Pattern.compile(
            "\"%s\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern INT_PAIR_FIELD = Pattern.compile(
            "\"%s\"\\s*:\\s*\\[\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*]");
    private static final Pattern FLOAT_TRIPLE_FIELD = Pattern.compile(
            "\"%s\"\\s*:\\s*\\[\\s*(-?\\d+(?:\\.\\d+)?)\\s*,\\s*"
                    + "(-?\\d+(?:\\.\\d+)?)\\s*,\\s*"
                    + "(-?\\d+(?:\\.\\d+)?)\\s*]");
    private static final Pattern BOX_FIELD = Pattern.compile(
            "\"boxes\"\\s*:\\s*\\[(.*?)]\\s*[,}]",
            Pattern.DOTALL);
    private static final Pattern BOX = Pattern.compile(
            "\\[\\s*(-?\\d+(?:\\.\\d+)?)\\s*,\\s*"
                    + "(-?\\d+(?:\\.\\d+)?)\\s*,\\s*"
                    + "(-?\\d+(?:\\.\\d+)?)\\s*,\\s*"
                    + "(-?\\d+(?:\\.\\d+)?)\\s*,\\s*"
                    + "(-?\\d+(?:\\.\\d+)?)\\s*,\\s*"
                    + "(-?\\d+(?:\\.\\d+)?)\\s*]");

    private CemParser() {
    }

    public static CemParseResult parseAll(List<Source> sources) {
        ArrayList<CemModel> models = new ArrayList<>();
        ArrayList<CemParseResult.Error> errors = new ArrayList<>();
        for (Source source : sources) {
            try {
                models.add(parse(source.path(), source.body()));
            } catch (RuntimeException e) {
                errors.add(new CemParseResult.Error(source.path(),
                        e.getMessage()));
            }
        }
        return new CemParseResult(models, errors);
    }

    public static CemModel parse(String sourcePath, String json) {
        String textureRaw = string(json, "texture");
        NamespaceId texture = textureRaw == null ? null
                : OptifinePropertyParsers.resolveOptifinePath(
                ensurePng(textureRaw), "minecraft",
                OptifinePropertyParsers.parentOf(sourcePath));
        int[] size = intPair(json, "textureSize", 64, 32);
        float shadow = number(json, "shadowSize", 0.0f);
        List<CemPart> parts = parts(json);
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("no CEM parts found");
        }
        return new CemModel(sourcePath, texture, size[0], size[1], shadow,
                parts);
    }

    private static List<CemPart> parts(String json) {
        ArrayList<CemPart> out = new ArrayList<>();
        int index = 0;
        while (true) {
            int partKey = json.indexOf("\"part\"", index);
            if (partKey < 0) {
                break;
            }
            int objectStart = json.lastIndexOf('{', partKey);
            int objectEnd = findObjectEnd(json, objectStart);
            if (objectStart < 0 || objectEnd < 0) {
                break;
            }
            String object = json.substring(objectStart, objectEnd + 1);
            String part = string(object, "part");
            float[] translate = triple(object, "translate");
            float[] rotate = triple(object, "rotate");
            out.add(new CemPart(part, string(object, "attach"),
                    translate[0], translate[1], translate[2],
                    rotate[0], rotate[1], rotate[2],
                    boxes(object), List.of()));
            index = objectEnd + 1;
        }
        return out;
    }

    private static List<CemBox> boxes(String object) {
        Matcher field = BOX_FIELD.matcher(object);
        if (!field.find()) {
            return List.of();
        }
        ArrayList<CemBox> out = new ArrayList<>();
        Matcher matcher = BOX.matcher(field.group(1));
        while (matcher.find()) {
            out.add(new CemBox(floatAt(matcher, 1), floatAt(matcher, 2),
                    floatAt(matcher, 3), floatAt(matcher, 4),
                    floatAt(matcher, 5), floatAt(matcher, 6)));
        }
        return out;
    }

    private static int findObjectEnd(String json, int start) {
        if (start < 0) {
            return -1;
        }
        int depth = 0;
        boolean string = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                string = !string;
            }
            if (string) {
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String string(String json, String key) {
        Matcher matcher = Pattern.compile(String.format(
                STRING_FIELD.pattern(), Pattern.quote(key))).matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static float number(String json, String key, float fallback) {
        Matcher matcher = Pattern.compile(String.format(
                NUMBER_FIELD.pattern(), Pattern.quote(key))).matcher(json);
        return matcher.find() ? Float.parseFloat(matcher.group(1)) : fallback;
    }

    private static int[] intPair(String json, String key, int a, int b) {
        Matcher matcher = Pattern.compile(String.format(
                INT_PAIR_FIELD.pattern(), Pattern.quote(key))).matcher(json);
        return matcher.find()
                ? new int[]{Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2))}
                : new int[]{a, b};
    }

    private static float[] triple(String json, String key) {
        Matcher matcher = Pattern.compile(String.format(
                FLOAT_TRIPLE_FIELD.pattern(), Pattern.quote(key)))
                .matcher(json);
        return matcher.find()
                ? new float[]{Float.parseFloat(matcher.group(1)),
                Float.parseFloat(matcher.group(2)),
                Float.parseFloat(matcher.group(3))}
                : new float[]{0.0f, 0.0f, 0.0f};
    }

    private static float floatAt(Matcher matcher, int group) {
        return Float.parseFloat(matcher.group(group));
    }

    private static String ensurePng(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        return trimmed.endsWith(".png") ? trimmed : trimmed + ".png";
    }

    public record Source(String path, String body) {
    }
}
