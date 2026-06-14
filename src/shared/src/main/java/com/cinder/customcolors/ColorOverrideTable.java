package com.cinder.customcolors;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable table of hard-coded OptiFine color overrides from
 * {@code color.properties}.
 *
 * <p>Keys are stored after OptiFine-compatible alias normalization, for
 * example {@code dye.silver -> dye.light_gray}. Values are RGB ints without
 * alpha.
 *
 * <p>Threading: immutable. Performance: O(1) hash lookup; callers should keep
 * typed key strings precomputed in their adapters.
 */
public final class ColorOverrideTable {

    private static final Map<String, String> ALIASES = Map.of(
            "map.snow", "map.white",
            "collar.silver", "collar.light_gray",
            "dye.silver", "dye.light_gray");

    private final Map<String, Integer> colors;

    public ColorOverrideTable(Map<String, Integer> colors) {
        Objects.requireNonNull(colors, "colors");
        LinkedHashMap<String, Integer> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : colors.entrySet()) {
            normalized.put(normalizeKey(entry.getKey()), entry.getValue());
        }
        this.colors = Collections.unmodifiableMap(normalized);
    }

    public static ColorOverrideTable empty() {
        return new ColorOverrideTable(Map.of());
    }

    public Integer color(String key) {
        return colors.get(normalizeKey(key));
    }

    public int colorOr(String key, int fallback) {
        Integer value = color(key);
        return value == null ? fallback : value;
    }

    public boolean has(String key) {
        return color(key) != null;
    }

    public Map<String, Integer> colors() {
        return colors;
    }

    public int size() {
        return colors.size();
    }

    public static String normalizeKey(String key) {
        String trimmed = Objects.requireNonNull(key, "key").trim()
                .toLowerCase(java.util.Locale.ROOT);
        return ALIASES.getOrDefault(trimmed, trimmed);
    }
}
