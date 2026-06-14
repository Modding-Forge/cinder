package com.cinder.resource;

import java.util.Locale;

/**
 * Small parser helpers shared by OptiFine-compatible feature parsers.
 *
 * <p>This class intentionally stays feature-neutral. It does not replace the
 * dedicated CTM, CIT, Custom Sky, Custom Colors, GUI, animation, or natural
 * texture parsers; it only centralizes tiny repeat operations whose behaviour
 * must stay identical across those parsers.
 *
 * <p>Threading: stateless and safe for concurrent use. Performance: reload
 * path only; no renderer hot-path dependency.
 */
public final class OptifinePropertyParsers {

    private OptifinePropertyParsers() {
    }

    /**
     * Parses a strict OptiFine-style boolean. Accepted true values are
     * {@code true} and {@code 1}; accepted false values are {@code false} and
     * {@code 0}, case-insensitive for words.
     */
    public static boolean parseBoolean(String raw, boolean fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String trimmed = raw.trim();
        if (trimmed.equalsIgnoreCase("true") || trimmed.equals("1")) {
            return true;
        }
        if (trimmed.equalsIgnoreCase("false") || trimmed.equals("0")) {
            return false;
        }
        return fallback;
    }

    /**
     * Parses an integer and returns {@code fallback} when missing, malformed,
     * or outside the supplied inclusive bounds.
     */
    public static int parseIntOrDefault(String raw,
                                        int fallback,
                                        int min,
                                        int max) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed < min || parsed > max ? fallback : parsed;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Parses an integer and throws a key/value rich error for malformed or
     * out-of-range input.
     */
    public static int requireInt(String raw,
                                 int fallback,
                                 int min,
                                 int max,
                                 String key) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            if (parsed < min || parsed > max) {
                throw new IllegalArgumentException(key + " out of range: "
                        + raw);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid " + key + ": "
                    + raw, e);
        }
    }

    /**
     * Parses a float and returns {@code fallback} when the value is missing.
     * Malformed values throw so callers can isolate the broken rule.
     */
    public static float requireFloat(String raw, float fallback, String key) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Float.parseFloat(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid " + key + ": "
                    + raw, e);
        }
    }

    /**
     * Parses a 24-bit RGB value from {@code RRGGBB} or {@code #RRGGBB}.
     */
    public static int parseRgbOrDefault(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String value = raw.trim();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        try {
            return Integer.parseInt(value, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Splits an OptiFine whitespace token list into stable tokens.
     */
    public static String[] tokens(String raw) {
        if (raw == null || raw.isBlank()) {
            return new String[0];
        }
        return raw.trim().split("\\s+");
    }

    /**
     * Returns the directory containing a namespaced source file.
     */
    public static NamespaceId parentOf(String sourceFile) {
        NamespaceId source = NamespaceId.parse(sourceFile);
        String path = source.path();
        int slash = path.lastIndexOf('/');
        return new NamespaceId(source.namespace(),
                slash < 0 ? "" : path.substring(0, slash));
    }

    /**
     * Converts a {@code .properties} resource path to its sibling PNG path.
     */
    public static NamespaceId defaultPngFor(String sourceFile) {
        NamespaceId source = NamespaceId.parse(sourceFile);
        String path = source.path();
        if (path.endsWith(".properties")) {
            path = path.substring(0, path.length() - ".properties".length());
        }
        return new NamespaceId(source.namespace(), path + ".png");
    }

    /**
     * Removes a trailing {@code .png} extension, if present.
     */
    public static String stripPng(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().replace('\\', '/');
        return normalized.toLowerCase(Locale.ROOT).endsWith(".png")
                ? normalized.substring(0, normalized.length() - 4)
                : normalized;
    }

    /**
     * Resolves an OptiFine resource path using Cinder's shared path rules.
     */
    public static NamespaceId resolveOptifinePath(String raw,
                                                  String defaultNamespace,
                                                  NamespaceId parent) {
        return ResourcePath.resolveOptifine(raw, defaultNamespace, parent);
    }

    /**
     * Resolves a non-OptiFine-root resource path using Cinder's shared path
     * rules.
     */
    public static NamespaceId resolvePath(String raw,
                                          String defaultNamespace,
                                          NamespaceId parent) {
        return ResourcePath.resolve(raw, defaultNamespace, parent);
    }
}
