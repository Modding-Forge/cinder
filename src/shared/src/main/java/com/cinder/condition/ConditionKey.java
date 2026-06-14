package com.cinder.condition;

/**
 * Stable loader-agnostic identifiers for facts that future feature adapters
 * can expose to the shared condition engine.
 *
 * <p>Purpose: shared CIT and Custom GUI rules can depend on the same keys
 * without importing Minecraft, Fabric, Sodium, or screen classes.
 *
 * <p>Threading: enum constants are immutable and shared.
 *
 * <p>Performance: adapters should implement {@link ConditionContext} lookups
 * with direct field access or small fixed tables where possible.
 */
public enum ConditionKey {
    ITEM_ID,
    STACK_SIZE,
    DAMAGE,
    DAMAGE_MASK,
    DAMAGE_PERCENT,
    ENCHANTMENT_ID,
    ENCHANTMENT_LEVEL,
    HAND,
    CUSTOM_NAME,
    LORE,
    COMPONENT,
    NBT_RAW,
    SCREEN_ID,
    BIOME,
    HEIGHT,
    CONTAINER_FLAG
}
