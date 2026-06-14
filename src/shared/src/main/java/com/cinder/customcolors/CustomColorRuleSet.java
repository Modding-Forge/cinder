package com.cinder.customcolors;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable shared Custom Colors snapshot.
 *
 * <p>This is the renderer-neutral output of the parser/reload phase. Loader
 * adapters may derive faster tables from it, but the source of truth remains a
 * single atomically replaceable snapshot.
 *
 * <p>Threading: immutable and safe for concurrent render reads.
 */
public final class CustomColorRuleSet {

    private static final CustomColorRuleSet EMPTY =
            new CustomColorRuleSet(ColorOverrideTable.empty(),
                    new ColormapRule[0], Map.of(), Map.of());

    private final ColorOverrideTable overrides;
    private final ColormapRule[] blockRules;
    private final Map<String, ColormapImage> specialColormaps;
    private final Map<String, ColormapImage> environmentColormaps;

    public CustomColorRuleSet(ColorOverrideTable overrides,
                              ColormapRule[] blockRules,
                              Map<String, ColormapImage> specialColormaps,
                              Map<String, ColormapImage> environmentColormaps) {
        this.overrides = Objects.requireNonNull(overrides, "overrides");
        this.blockRules = blockRules == null ? new ColormapRule[0]
                : Arrays.copyOf(blockRules, blockRules.length);
        this.specialColormaps = immutableCopy(specialColormaps);
        this.environmentColormaps = immutableCopy(environmentColormaps);
    }

    public static CustomColorRuleSet empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return overrides.size() == 0
                && blockRules.length == 0
                && specialColormaps.isEmpty()
                && environmentColormaps.isEmpty();
    }

    public ColorOverrideTable overrides() {
        return overrides;
    }

    public ColormapRule[] blockRules() {
        return Arrays.copyOf(blockRules, blockRules.length);
    }

    public Map<String, ColormapImage> specialColormaps() {
        return specialColormaps;
    }

    public Map<String, ColormapImage> environmentColormaps() {
        return environmentColormaps;
    }

    public ColormapImage special(String key) {
        return specialColormaps.get(key);
    }

    public ColormapImage environment(String key) {
        return environmentColormaps.get(key);
    }

    private static Map<String, ColormapImage> immutableCopy(
            Map<String, ColormapImage> input) {
        if (input == null || input.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(input));
    }
}
