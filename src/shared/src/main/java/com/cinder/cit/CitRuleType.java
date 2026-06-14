package com.cinder.cit;

import java.util.Locale;

/**
 * OptiFine CIT rule families known to Cinder.
 *
 * <p>Purpose: Phase E renders {@link #ITEM} rules. Other values are parsed so
 * reload diagnostics can explain skipped files without treating known future
 * rule types as malformed.
 *
 * <p>Threading: enum constants are immutable.
 */
public enum CitRuleType {
    ITEM,
    ENCHANTMENT,
    ARMOR,
    ELYTRA;

    /**
     * Parses an OptiFine {@code type=} value. Missing values default to
     * {@link #ITEM}; unknown values throw so the caller can isolate the file.
     */
    public static CitRuleType parse(String value) {
        if (value == null || value.isBlank()) {
            return ITEM;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "item" -> ITEM;
            case "enchantment" -> ENCHANTMENT;
            case "armor" -> ARMOR;
            case "elytra" -> ELYTRA;
            default -> throw new IllegalArgumentException(
                    "unknown CIT type: " + value);
        };
    }
}
