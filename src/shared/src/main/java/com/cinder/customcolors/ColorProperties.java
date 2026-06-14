package com.cinder.customcolors;

import com.cinder.ctm.BlockSpec;
import com.cinder.resource.NamespaceId;
import com.cinder.resource.PropertiesFile;
import com.cinder.resource.ResourcePath;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser for OptiFine {@code color.properties} and colormap
 * {@code .properties} files.
 *
 * <p>Threading: stateless utility. Performance: reload-time only.
 */
public final class ColorProperties {

    public static final String COLOR_PROPERTIES =
            "minecraft:optifine/color.properties";

    private ColorProperties() {
    }

    public static CustomColorParseResult parseColorProperties(
            String body, String sourceFile) {
        PropertiesFile props = parse(body);
        LinkedHashMap<String, Integer> colors = new LinkedHashMap<>();
        ArrayList<ColormapRule> blockRules = new ArrayList<>();
        ArrayList<CustomColorParseResult.Error> errors = new ArrayList<>();
        ColormapFormat paletteFormat = ColormapFormat.parse(
                props.get("palette.format"), ColormapFormat.VANILLA);
        NamespaceId parent = parentOf(sourceFile);

        for (Map.Entry<String, String> entry : props.entries().entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if ("palette.format".equals(key)) {
                continue;
            }
            if (key.startsWith("palette.block.")) {
                parsePaletteBlock(key, value, sourceFile, parent,
                        paletteFormat, blockRules, errors);
                continue;
            }
            Integer color = parseRgb(value);
            if (color == null) {
                errors.add(new CustomColorParseResult.Error(
                        sourceFile, key, value, "invalid RGB color"));
                continue;
            }
            colors.put(ColorOverrideTable.normalizeKey(key), color);
        }
        return new CustomColorParseResult(new ColorOverrideTable(colors),
                blockRules, errors);
    }

    public static ColormapRule parseColormapProperties(String body,
                                                       String sourceFile) {
        PropertiesFile props = parse(body);
        NamespaceId parent = parentOf(sourceFile);
        ColormapFormat format = ColormapFormat.parse(
                props.get("format"), ColormapFormat.GRID);
        NamespaceId source = props.get("source") == null
                ? defaultPngFor(sourceFile)
                : ResourcePath.resolveOptifine(
                        stripPng(props.get("source")), "minecraft", parent);
        BlockSpec[] blocks = parseBlocks(props.get("blocks"));
        Integer fixedColor = parseRgb(props.get("color"));
        Integer itemColor = parseRgb(props.get("color"));
        int yVariance = parseInt(props.get("yVariance"), 0, 0,
                Integer.MAX_VALUE, "yVariance");
        int yOffset = parseInt(props.get("yOffset"), 0,
                Integer.MIN_VALUE, Integer.MAX_VALUE, "yOffset");
        return new ColormapRule(sourceFile, source, format, blocks,
                fixedColor, yVariance, yOffset, itemColor);
    }

    private static void parsePaletteBlock(String key,
                                          String value,
                                          String sourceFile,
                                          NamespaceId parent,
                                          ColormapFormat paletteFormat,
                                          List<ColormapRule> out,
                                          List<CustomColorParseResult.Error> errors) {
        String path = key.substring("palette.block.".length());
        try {
            NamespaceId source = ResourcePath.resolveOptifine(
                    stripPng(path), "minecraft", parent);
            BlockSpec[] blocks = parseBlocks(value);
            if (blocks.length == 0) {
                errors.add(new CustomColorParseResult.Error(
                        sourceFile, key, value, "missing block list"));
                return;
            }
            out.add(new ColormapRule(sourceFile, source, paletteFormat,
                    blocks, null, 0, 0, null));
        } catch (RuntimeException e) {
            errors.add(new CustomColorParseResult.Error(
                    sourceFile, key, value, e.getMessage()));
        }
    }

    private static PropertiesFile parse(String body) {
        try {
            return PropertiesFile.parse(new StringReader(body == null
                    ? "" : body));
        } catch (IOException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private static BlockSpec[] parseBlocks(String raw) {
        if (raw == null || raw.isBlank()) {
            return new BlockSpec[0];
        }
        String[] tokens = raw.trim().split("\\s+");
        BlockSpec[] out = new BlockSpec[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            out[i] = BlockSpec.parse(tokens[i]);
        }
        return out;
    }

    public static Integer parseRgb(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        if (s.startsWith("0x") || s.startsWith("0X")) {
            s = s.substring(2);
        }
        if (s.length() != 6) {
            return null;
        }
        try {
            return Integer.parseInt(s, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int parseInt(String raw, int fallback, int min, int max,
                                String key) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            if (parsed < min || parsed > max) {
                throw new IllegalArgumentException(key + " out of range");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid " + key + ": " + raw);
        }
    }

    private static NamespaceId parentOf(String sourceFile) {
        NamespaceId id = NamespaceId.parse(sourceFile);
        String path = id.path();
        int slash = path.lastIndexOf('/');
        return new NamespaceId(id.namespace(), slash < 0
                ? "" : path.substring(0, slash));
    }

    private static NamespaceId defaultPngFor(String sourceFile) {
        NamespaceId id = NamespaceId.parse(sourceFile);
        String path = id.path();
        if (path.endsWith(".properties")) {
            path = path.substring(0, path.length() - ".properties".length())
                    + ".png";
        }
        return new NamespaceId(id.namespace(), path);
    }

    public static String stripPng(String raw) {
        String s = raw.trim();
        return s.endsWith(".png") ? s.substring(0, s.length() - 4) : s;
    }
}
