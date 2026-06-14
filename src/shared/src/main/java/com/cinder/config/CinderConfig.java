package com.cinder.config;

import java.util.Objects;

/**
 * Immutable configuration of the Cinder mod. The
 * configuration is a flat record of booleans; new toggles are
 * added by extending the record and the corresponding default
 * constant in {@link CinderConfigDefaults}.
 *
 * <p>The class is a {@code record} so that instances are
 * automatically immutable, value-equal, and easy to log. Tests
 * should use the public accessors only.
 *
 * <h2>Why a flat boolean record?</h2>
 *
 * <p>The current Phase 4 scope is small enough that a single
 * record of booleans is the simplest representation. When the
 * number of toggles grows (Tier 2-4 features), the structure
 * will be split per feature (CTM, Natural Textures, etc.) but
 * stay immutable.
 *
 * <p>Performance: a {@code CinderConfig} is built once at
 * config-load time and read on every selection. The record's
 * accessors are inlined by the JIT.
 */
public record CinderConfig(
        boolean enabled,
        boolean safeMode,
        boolean verifyMode,
        boolean ctmEnabled,
        boolean ctmDebugLogging,
        boolean duplicateTranslucentBackfaces,
        BetterGrassMode betterGrassMode,
        boolean betterGrassIgnoreResourcePack,
        boolean betterGrassGrassBlock,
        boolean betterGrassSnowyGrassBlock,
        boolean betterGrassDirtPath,
        boolean betterGrassFarmland,
        boolean betterGrassMycelium,
        boolean betterGrassPodzol,
        boolean betterGrassCrimsonNylium,
        boolean betterGrassWarpedNylium,
        boolean citEnabled,
        boolean customGuiEnabled,
        boolean customColorsEnabled,
        boolean customAnimationsEnabled,
        int customAnimationMipmapDistance) {

    public CinderConfig {
        // Defensive copies not needed: record components are
        // already final. We only validate that the booleans are
        // non-null (records reject nulls by default, but the
        // explicit check is documentation).
        Objects.requireNonNull(enabled, "enabled");
        Objects.requireNonNull(safeMode, "safeMode");
        Objects.requireNonNull(verifyMode, "verifyMode");
        Objects.requireNonNull(ctmEnabled, "ctmEnabled");
        Objects.requireNonNull(ctmDebugLogging, "ctmDebugLogging");
        Objects.requireNonNull(duplicateTranslucentBackfaces,
                "duplicateTranslucentBackfaces");
        Objects.requireNonNull(betterGrassMode, "betterGrassMode");
        Objects.requireNonNull(betterGrassIgnoreResourcePack,
                "betterGrassIgnoreResourcePack");
        Objects.requireNonNull(betterGrassGrassBlock, "betterGrassGrassBlock");
        Objects.requireNonNull(betterGrassSnowyGrassBlock,
                "betterGrassSnowyGrassBlock");
        Objects.requireNonNull(betterGrassDirtPath, "betterGrassDirtPath");
        Objects.requireNonNull(betterGrassFarmland, "betterGrassFarmland");
        Objects.requireNonNull(betterGrassMycelium, "betterGrassMycelium");
        Objects.requireNonNull(betterGrassPodzol, "betterGrassPodzol");
        Objects.requireNonNull(betterGrassCrimsonNylium,
                "betterGrassCrimsonNylium");
        Objects.requireNonNull(betterGrassWarpedNylium,
                "betterGrassWarpedNylium");
        Objects.requireNonNull(citEnabled, "citEnabled");
        Objects.requireNonNull(customGuiEnabled, "customGuiEnabled");
        Objects.requireNonNull(customColorsEnabled, "customColorsEnabled");
        Objects.requireNonNull(customAnimationsEnabled,
                "customAnimationsEnabled");
        if (customAnimationMipmapDistance < 0
                || customAnimationMipmapDistance > 4) {
            throw new IllegalArgumentException(
                    "customAnimationMipmapDistance must be 0-4");
        }
    }

    public CinderConfig(boolean enabled,
                        boolean safeMode,
                        boolean verifyMode,
                        boolean ctmEnabled,
                        boolean ctmDebugLogging,
                        BetterGrassMode betterGrassMode) {
        this(enabled, safeMode, verifyMode, ctmEnabled, ctmDebugLogging,
                CinderConfigDefaults.DUPLICATE_TRANSLUCENT_BACKFACES,
                betterGrassMode,
                CinderConfigDefaults.BETTER_GRASS_IGNORE_RESOURCE_PACK,
                CinderConfigDefaults.BETTER_GRASS_GRASS_BLOCK,
                CinderConfigDefaults.BETTER_GRASS_SNOWY_GRASS_BLOCK,
                CinderConfigDefaults.BETTER_GRASS_DIRT_PATH,
                CinderConfigDefaults.BETTER_GRASS_FARMLAND,
                CinderConfigDefaults.BETTER_GRASS_MYCELIUM,
                CinderConfigDefaults.BETTER_GRASS_PODZOL,
                CinderConfigDefaults.BETTER_GRASS_CRIMSON_NYLIUM,
                CinderConfigDefaults.BETTER_GRASS_WARPED_NYLIUM,
                CinderConfigDefaults.CIT_ENABLED,
                CinderConfigDefaults.CUSTOM_GUI_ENABLED,
                CinderConfigDefaults.CUSTOM_COLORS_ENABLED,
                CinderConfigDefaults.CUSTOM_ANIMATIONS_ENABLED,
                CinderConfigDefaults.CUSTOM_ANIMATION_MIPMAP_DISTANCE);
    }

    public CinderConfig(boolean enabled,
                        boolean safeMode,
                        boolean verifyMode,
                        boolean ctmEnabled,
                        boolean ctmDebugLogging,
                        BetterGrassMode betterGrassMode,
                        boolean betterGrassIgnoreResourcePack,
                        boolean betterGrassGrassBlock,
                        boolean betterGrassSnowyGrassBlock,
                        boolean betterGrassDirtPath,
                        boolean betterGrassFarmland,
                        boolean betterGrassMycelium,
                        boolean betterGrassPodzol,
                        boolean betterGrassCrimsonNylium,
                        boolean betterGrassWarpedNylium) {
        this(enabled, safeMode, verifyMode, ctmEnabled, ctmDebugLogging,
                CinderConfigDefaults.DUPLICATE_TRANSLUCENT_BACKFACES,
                betterGrassMode,
                betterGrassIgnoreResourcePack,
                betterGrassGrassBlock, betterGrassSnowyGrassBlock,
                betterGrassDirtPath, betterGrassFarmland, betterGrassMycelium,
                betterGrassPodzol, betterGrassCrimsonNylium,
                betterGrassWarpedNylium,
                CinderConfigDefaults.CIT_ENABLED,
                CinderConfigDefaults.CUSTOM_GUI_ENABLED,
                CinderConfigDefaults.CUSTOM_COLORS_ENABLED,
                CinderConfigDefaults.CUSTOM_ANIMATIONS_ENABLED,
                CinderConfigDefaults.CUSTOM_ANIMATION_MIPMAP_DISTANCE);
    }

    public CinderConfig(boolean enabled,
                        boolean safeMode,
                        boolean verifyMode,
                        boolean ctmEnabled,
                        boolean ctmDebugLogging,
                        BetterGrassMode betterGrassMode,
                        boolean betterGrassGrassBlock,
                        boolean betterGrassSnowyGrassBlock,
                        boolean betterGrassDirtPath,
                        boolean betterGrassFarmland,
                        boolean betterGrassMycelium,
                        boolean betterGrassPodzol,
                        boolean betterGrassCrimsonNylium,
                        boolean betterGrassWarpedNylium) {
        this(enabled, safeMode, verifyMode, ctmEnabled, ctmDebugLogging,
                CinderConfigDefaults.DUPLICATE_TRANSLUCENT_BACKFACES,
                betterGrassMode,
                CinderConfigDefaults.BETTER_GRASS_IGNORE_RESOURCE_PACK,
                betterGrassGrassBlock, betterGrassSnowyGrassBlock,
                betterGrassDirtPath, betterGrassFarmland, betterGrassMycelium,
                betterGrassPodzol, betterGrassCrimsonNylium,
                betterGrassWarpedNylium,
                CinderConfigDefaults.CIT_ENABLED,
                CinderConfigDefaults.CUSTOM_GUI_ENABLED,
                CinderConfigDefaults.CUSTOM_COLORS_ENABLED,
                CinderConfigDefaults.CUSTOM_ANIMATIONS_ENABLED,
                CinderConfigDefaults.CUSTOM_ANIMATION_MIPMAP_DISTANCE);
    }

    public CinderConfig(boolean enabled,
                        boolean safeMode,
                        boolean verifyMode,
                        boolean ctmEnabled,
                        boolean ctmDebugLogging,
                        boolean duplicateTranslucentBackfaces,
                        BetterGrassMode betterGrassMode,
                        boolean betterGrassIgnoreResourcePack,
                        boolean betterGrassGrassBlock,
                        boolean betterGrassSnowyGrassBlock,
                        boolean betterGrassDirtPath,
                        boolean betterGrassFarmland,
                        boolean betterGrassMycelium,
                        boolean betterGrassPodzol,
                        boolean betterGrassCrimsonNylium,
                        boolean betterGrassWarpedNylium) {
        this(enabled, safeMode, verifyMode, ctmEnabled, ctmDebugLogging,
                duplicateTranslucentBackfaces, betterGrassMode,
                betterGrassIgnoreResourcePack,
                betterGrassGrassBlock, betterGrassSnowyGrassBlock,
                betterGrassDirtPath, betterGrassFarmland, betterGrassMycelium,
                betterGrassPodzol, betterGrassCrimsonNylium,
                betterGrassWarpedNylium,
                CinderConfigDefaults.CIT_ENABLED,
                CinderConfigDefaults.CUSTOM_GUI_ENABLED,
                CinderConfigDefaults.CUSTOM_COLORS_ENABLED,
                CinderConfigDefaults.CUSTOM_ANIMATIONS_ENABLED,
                CinderConfigDefaults.CUSTOM_ANIMATION_MIPMAP_DISTANCE);
    }

    /**
     * Returns {@code true} iff the CTM feature is on. Convenience
     * for the renderer-side code, which only has to test a single
     * flag instead of the master {@code enabled} + per-feature
     * {@code ctmEnabled} combination.
     */
    public boolean ctmActive() {
        return enabled && ctmEnabled;
    }

    /**
     * Returns {@code true} when Better Grass should run in the renderer.
     */
    public boolean betterGrassActive() {
        return enabled && betterGrassMode != BetterGrassMode.OFF;
    }

    /**
     * Returns {@code true} when the Better Grass feature has at least one
     * enabled block family.
     */
    public boolean anyBetterGrassBlockEnabled() {
        return betterGrassGrassBlock
                || betterGrassSnowyGrassBlock
                || betterGrassDirtPath
                || betterGrassFarmland
                || betterGrassMycelium
                || betterGrassPodzol
                || betterGrassCrimsonNylium
                || betterGrassWarpedNylium;
    }

    /**
     * Returns {@code true} when Custom Item Textures should run.
     */
    public boolean citActive() {
        return enabled && citEnabled;
    }

    /**
     * Returns {@code true} when Custom GUI texture replacement should run.
     */
    public boolean customGuiActive() {
        return enabled && customGuiEnabled;
    }

    /**
     * Returns {@code true} when Custom Colors and Colormaps should run.
     */
    public boolean customColorsActive() {
        return enabled && customColorsEnabled;
    }

    /**
     * Returns {@code true} when Custom Animations should tick and upload.
     */
    public boolean customAnimationsActive() {
        return enabled && customAnimationsEnabled;
    }
}
