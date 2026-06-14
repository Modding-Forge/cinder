package com.cinder.emissive;

import java.util.Objects;

/**
 * Immutable settings parsed from {@code optifine/emissive.properties}.
 *
 * @param suffix texture-name suffix that marks an emissive variant
 */
public record EmissiveSettings(String suffix) {

    public EmissiveSettings {
        Objects.requireNonNull(suffix, "suffix");
        if (suffix.isEmpty()) {
            throw new IllegalArgumentException("suffix is empty");
        }
    }
}
