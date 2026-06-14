package com.cinder.fabric.customgui;

import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable texture override table for the currently open screen.
 *
 * <p>Purpose: lets the GUI blit hook do a single map lookup without evaluating
 * resource-pack conditions.
 *
 * <p>Threading: immutable and published through {@link CustomGuiRuntime}.
 *
 * <p>Performance: HOT PATH via {@link #override(Identifier)}; no allocations.
 */
final class CustomGuiScreenOverrides {

    static final CustomGuiScreenOverrides EMPTY =
            new CustomGuiScreenOverrides(Map.of());

    private final Map<Identifier, Identifier> overrides;

    CustomGuiScreenOverrides(Map<Identifier, Identifier> overrides) {
        this.overrides = Map.copyOf(Objects.requireNonNull(
                overrides, "overrides"));
    }

    boolean isEmpty() {
        return overrides.isEmpty();
    }

    Identifier override(Identifier original) {
        Identifier replacement = overrides.get(original);
        return replacement != null ? replacement : original;
    }
}
