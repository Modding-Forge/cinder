package com.cinder.compat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Aggregates the {@link CompatProbe} results for the mod ids
 * listed in {@link CompatDefaults}. The report is computed
 * once at startup and on each config reload; the renderer
 * consults it for any per-mod degradation decisions.
 *
 * <p>Performance: the report holds a small fixed map and is
 * built in O(n) where n is the size of
 * {@link CompatDefaults#knownModIds()} (currently 7). It is
 * safe to call {@link #build(CompatProbe)} on every reload.
 */
public final class CompatReport {

    private final Map<String, Boolean> present;

    private CompatReport(Map<String, Boolean> present) {
        this.present = present;
    }

    /**
     * Builds a report by probing each known mod id. The order
     * of the resulting map matches the order of
     * {@link CompatDefaults#knownModIds()}.
     */
    public static CompatReport build(CompatProbe probe) {
        Objects.requireNonNull(probe, "probe");
        Map<String, Boolean> m = new LinkedHashMap<>();
        for (String id : CompatDefaults.knownModIds()) {
            m.put(id, probe.isLoaded(id));
        }
        return new CompatReport(m);
    }

    /**
     * Returns {@code true} if the mod is loaded.
     */
    public boolean isPresent(String modId) {
        Boolean v = present.get(modId);
        return v != null && v;
    }

    /**
     * Returns the set of mod ids that are loaded.
     */
    public Set<String> presentMods() {
        return present.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /**
     * Returns the raw mod-id -> present map. Used by tests.
     */
    public Map<String, Boolean> raw() {
        return present;
    }
}
