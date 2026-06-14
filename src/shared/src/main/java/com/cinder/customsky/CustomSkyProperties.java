package com.cinder.customsky;

import com.cinder.resource.NamespaceId;
import com.cinder.resource.OptifinePropertyParsers;
import com.cinder.resource.PropertiesFile;
import com.cinder.resource.RangeListInt;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Clean-room parser for OptiFine custom-sky layer properties.
 *
 * <p>Threading: stateless reload-time parser. Performance: no runtime work;
 * all path, range, and time values are normalized into immutable layers.
 */
public final class CustomSkyProperties {

    private static final Pattern SKY_PATH = Pattern.compile(
            "(?:^|/)(optifine|mcpatcher)/sky/(world-?\\d+)/sky(\\d+)\\.properties$");

    private CustomSkyProperties() {
    }

    public record RuleSource(String body, String sourceLabel) {
        public RuleSource {
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(sourceLabel, "sourceLabel");
        }
    }

    public static CustomSkyParseResult parseAll(List<RuleSource> sources) {
        ArrayList<CustomSkyLayer> layers = new ArrayList<>();
        ArrayList<CustomSkyParseResult.Error> errors = new ArrayList<>();
        if (sources == null) {
            return new CustomSkyParseResult(layers, errors);
        }
        for (RuleSource source : sources) {
            try {
                layers.add(parseString(source.body(), source.sourceLabel()));
            } catch (RuntimeException e) {
                errors.add(new CustomSkyParseResult.Error(
                        source.sourceLabel(), e.getMessage()));
            }
        }
        return new CustomSkyParseResult(layers, errors);
    }

    public static CustomSkyLayer parseString(String body, String sourceLabel) {
        try {
            return parse(PropertiesFile.parse(new StringReader(
                    body == null ? "" : body)), sourceLabel);
        } catch (IOException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private static CustomSkyLayer parse(PropertiesFile props,
                                        String sourceLabel) {
        Matcher matcher = SKY_PATH.matcher(NamespaceId.parse(sourceLabel)
                .path());
        if (!matcher.find()) {
            throw new IllegalArgumentException("not a sky layer path: "
                    + sourceLabel);
        }
        CustomSkyWorld world = CustomSkyWorld.parseFolder(matcher.group(2));
        int layerIndex = parsePositiveInt(matcher.group(3), "sky index");
        NamespaceId parent = OptifinePropertyParsers.parentOf(sourceLabel);
        NamespaceId source = resolveSource(props.get("source"),
                sourceLabel, parent);
        boolean hasFade = hasAnyFadeKey(props);
        int startFadeIn = parseTime(props.get("startFadeIn"),
                "startFadeIn", 0);
        int endFadeIn = parseTime(props.get("endFadeIn"),
                "endFadeIn", startFadeIn);
        int endFadeOut = parseTime(props.get("endFadeOut"),
                "endFadeOut", startFadeIn);
        int startFadeOut = props.get("startFadeOut") == null
                ? endFadeOut - wrappedDistance(startFadeIn, endFadeIn)
                : parseTime(props.get("startFadeOut"),
                "startFadeOut", endFadeOut);
        CustomSkyRotation rotation = new CustomSkyRotation(
                parseBoolean(props.get("rotate"), true),
                parseFloat(props.get("speed"), 1.0F, "speed"),
                parseAxis(props.get("axis"))[0],
                parseAxis(props.get("axis"))[1],
                parseAxis(props.get("axis"))[2]);
        RangeListInt days = props.get("days") == null
                ? RangeListInt.ALL
                : RangeListInt.parse(props.get("days"));
        int daysLoop = parseInt(props.get("daysLoop"), 8,
                1, Integer.MAX_VALUE, "daysLoop");
        int transitionTicks = Math.round(parseFloat(
                props.get("transition"), 1.0F, "transition") * 20.0F);
        return new CustomSkyLayer(sourceLabel, world, layerIndex, source,
                hasFade, startFadeIn, endFadeIn, startFadeOut, endFadeOut,
                CustomSkyBlendMode.parse(props.get("blend")), rotation, days,
                daysLoop, parseWeather(props.get("weather")),
                parseBiomes(props.get("biomes")),
                props.get("heights") == null
                        ? RangeListInt.ALL
                        : RangeListInt.parse(props.get("heights")),
                transitionTicks);
    }

    private static NamespaceId resolveSource(String raw,
                                             String sourceLabel,
                                             NamespaceId parent) {
        if (raw == null || raw.isBlank()) {
            return OptifinePropertyParsers.defaultPngFor(sourceLabel);
        }
        String value = raw.trim().replace('\\', '/');
        if (value.indexOf(':') > 0) {
            return NamespaceId.parse(value);
        }
        if (value.startsWith("./")) {
            return new NamespaceId(parent.namespace(),
                    parent.path() + "/" + value.substring(2));
        }
        if (value.startsWith("~/")) {
            return new NamespaceId("minecraft",
                    "optifine/" + value.substring(2));
        }
        return new NamespaceId("minecraft", value);
    }

    private static boolean hasAnyFadeKey(PropertiesFile props) {
        return props.get("startFadeIn") != null
                || props.get("endFadeIn") != null
                || props.get("startFadeOut") != null
                || props.get("endFadeOut") != null;
    }

    private static int parseTime(String raw, String key, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String normalized = raw.trim().replace("\\:", ":");
        String[] parts = normalized.split(":", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException("invalid " + key + ": " + raw);
        }
        int hour = parseInt(parts[0], 0, 0, 23, key + " hour");
        int minute = parseInt(parts[1], 0, 0, 59, key + " minute");
        return Math.floorMod((hour - 6) * 1000 + minute * 1000 / 60, 24000);
    }

    private static int parseWeather(String raw) {
        if (raw == null || raw.isBlank()) {
            return CustomSkyLayer.WEATHER_CLEAR;
        }
        int mask = 0;
        for (String token : raw.trim().split("\\s+")) {
            switch (token.toLowerCase(Locale.ROOT)) {
                case "clear" -> mask |= CustomSkyLayer.WEATHER_CLEAR;
                case "rain" -> mask |= CustomSkyLayer.WEATHER_RAIN;
                case "thunder" -> mask |= CustomSkyLayer.WEATHER_THUNDER;
                default -> throw new IllegalArgumentException(
                        "invalid weather: " + token);
            }
        }
        return mask;
    }

    private static NamespaceId[] parseBiomes(String raw) {
        if (raw == null || raw.isBlank()) {
            return new NamespaceId[0];
        }
        String[] tokens = raw.trim().split("\\s+");
        NamespaceId[] out = new NamespaceId[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            out[i] = tokens[i].indexOf(':') > 0
                    ? NamespaceId.parse(tokens[i])
                    : new NamespaceId("minecraft", tokens[i]);
        }
        return out;
    }

    private static float[] parseAxis(String raw) {
        if (raw == null || raw.isBlank()) {
            return new float[]{0.0F, 0.0F, 1.0F};
        }
        String[] parts = raw.trim().split("\\s+");
        if (parts.length != 3) {
            throw new IllegalArgumentException("axis must have 3 values: "
                    + raw);
        }
        return new float[]{
                parseFloat(parts[0], 0.0F, "axis.x"),
                parseFloat(parts[1], 0.0F, "axis.y"),
                parseFloat(parts[2], 1.0F, "axis.z")
        };
    }

    private static boolean parseBoolean(String raw, boolean fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return OptifinePropertyParsers.parseBoolean(raw, fallback);
    }

    private static int parsePositiveInt(String raw, String key) {
        return parseInt(raw, 0, 0, Integer.MAX_VALUE, key);
    }

    private static int parseInt(String raw, int fallback, int min, int max,
                                String key) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return OptifinePropertyParsers.requireInt(raw, fallback, min, max,
                key);
    }

    private static float parseFloat(String raw, float fallback, String key) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return OptifinePropertyParsers.requireFloat(raw, fallback, key);
    }

    private static int wrappedDistance(int start, int end) {
        return Math.floorMod(end - start, 24000);
    }
}
