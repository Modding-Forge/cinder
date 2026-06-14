package com.cinder.client.sodium;

import com.cinder.config.BetterGrassMode;
import com.cinder.config.CinderConfig;
import com.cinder.config.CinderConfigDefaults;
import com.cinder.config.CinderConfigHolder;
import com.cinder.fabric.config.FabricConfigLoader;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.caffeinemc.mods.sodium.api.config.option.OptionFlag;
import net.caffeinemc.mods.sodium.api.config.structure.BooleanOptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.EnumOptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.IntegerOptionBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Registers Cinder's Sodium settings page.
 *
 * <p>Sodium is Cinder's primary renderer integration. This adapter keeps the
 * menu wiring in the Fabric/Sodium source set while the active values still
 * flow through the shared immutable {@link CinderConfig} snapshot.
 *
 * <p>Threading: called by Sodium's client configuration UI. Config snapshots
 * are atomically replaced through {@link CinderConfigHolder}.
 *
 * <p>Performance: not in the render hot path.
 */
public final class CinderSodiumConfigEntryPoint implements ConfigEntryPoint {

    private static final String MOD_ID = "cinder";

    private final OptionStorage storage = new OptionStorage();
    private final StorageEventHandler storageHandler = this.storage::flush;

    /**
     * Adds Cinder's own Sodium menu entry with general, CTM, and Better Grass
     * pages.
     *
     * <p>The CTM enable option asks Sodium to rebuild the renderer because
     * already-built chunk meshes need to be regenerated after changing CTM
     * output. Debug toggles apply to future meshing/logging immediately.
     */
    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        builder.registerOwnModOptions()
                .setName("Cinder")
                .addPage(builder.createOptionPage()
                        .setName(Component.literal("General"))
                        .addOptionGroup(builder.createOptionGroup()
                                .setName(Component.literal("Renderer"))
                                .addOption(booleanOption(builder,
                                        "safe_mode",
                                        "Safe Mode",
                                        "Keep conservative fallbacks enabled "
                                                + "while debugging renderer "
                                                + "compatibility.",
                                        CinderConfigDefaults.SAFE_MODE,
                                        this.storage::setSafeMode,
                                        this.storage::getSafeMode))
                                .addOption(booleanOption(builder,
                                        "verify_mode",
                                        "Verify Mode",
                                        "Enable additional development "
                                                + "checks where available.",
                                        CinderConfigDefaults.VERIFY_MODE,
                                        this.storage::setVerifyMode,
                                        this.storage::getVerifyMode))
                                .addOption(booleanOption(builder,
                                        "duplicate_translucent_backfaces",
                                        "Duplicate Translucent Backfaces",
                                        "Emit reversed backface copies for "
                                                + "translucent replacement "
                                                + "quads.",
                                        CinderConfigDefaults
                                                .DUPLICATE_TRANSLUCENT_BACKFACES,
                                        this.storage
                                                ::setDuplicateTranslucentBackfaces,
                                        this.storage
                                                ::getDuplicateTranslucentBackfaces,
                                        OptionFlag
                                                .REQUIRES_RENDERER_RELOAD))
                                .addOption(booleanOption(builder,
                                        "custom_animations_enabled",
                                        "Enable Custom Animations",
                                        "Tick OptiFine custom texture "
                                                + "animations loaded from "
                                                + "resource packs.",
                                        CinderConfigDefaults
                                                .CUSTOM_ANIMATIONS_ENABLED,
                                        this.storage
                                                ::setCustomAnimationsEnabled,
                                        this.storage
                                                ::getCustomAnimationsEnabled))
                                .addOption(integerOption(builder,
                                        "custom_animations_mipmap_distance",
                                        "Animation Mipmap Distance",
                                        "Controls how many mipmap distance "
                                                + "levels Cinder updates for "
                                                + "custom texture animations.",
                                        CinderConfigDefaults
                                                .CUSTOM_ANIMATION_MIPMAP_DISTANCE,
                                        0, 4, 1,
                                        this.storage
                                                ::setCustomAnimationMipmapDistance,
                                        this.storage
                                                ::getCustomAnimationMipmapDistance))))
                .addPage(builder.createOptionPage()
                        .setName(Component.literal("CTM"))
                        .addOptionGroup(builder.createOptionGroup()
                                .setName(Component.literal("Connected Textures"))
                                .addOption(booleanOption(builder,
                                        "ctm_enabled",
                                        "Enable CTM",
                                        "Render connected textures through "
                                                + "Cinder's Sodium path.",
                                        CinderConfigDefaults.CTM_ENABLED,
                                        this.storage::setCtmEnabled,
                                        this.storage::getCtmEnabled,
                                        OptionFlag.REQUIRES_RENDERER_RELOAD))
                                .addOption(booleanOption(builder,
                                        "ctm_debug_logging",
                                        "CTM Debug Logging",
                                        "Write gated CTM quad diagnostics to "
                                                + "the client log.",
                                        CinderConfigDefaults.CTM_DEBUG_LOGGING,
                                        this.storage::setCtmDebugLogging,
                                        this.storage::getCtmDebugLogging))))
                .addPage(builder.createOptionPage()
                        .setName(Component.literal("CIT"))
                        .addOptionGroup(builder.createOptionGroup()
                                .setName(Component.literal(
                                        "Custom Item Textures"))
                                .addOption(booleanOption(builder,
                                        "cit_enabled",
                                        "Enable CIT",
                                        "Render OptiFine item texture and "
                                                + "model replacements through "
                                                + "Cinder's item model path.",
                                        CinderConfigDefaults.CIT_ENABLED,
                                        this.storage::setCitEnabled,
                                        this.storage::getCitEnabled))))
                .addPage(builder.createOptionPage()
                        .setName(Component.literal("Custom GUI"))
                        .addOptionGroup(builder.createOptionGroup()
                                .setName(Component.literal(
                                        "Custom GUI Textures"))
                                .addOption(booleanOption(builder,
                                        "custom_gui_enabled",
                                        "Enable Custom GUI",
                                        "Render OptiFine GUI texture "
                                                + "replacements for supported "
                                                + "container screens.",
                                        CinderConfigDefaults
                                                .CUSTOM_GUI_ENABLED,
                                        this.storage::setCustomGuiEnabled,
                                        this.storage::getCustomGuiEnabled))))
                .addPage(builder.createOptionPage()
                        .setName(Component.literal("Custom Colors"))
                        .addOptionGroup(builder.createOptionGroup()
                                .setName(Component.literal(
                                        "Custom Colors and Colormaps"))
                                .addOption(booleanOption(builder,
                                        "custom_colors_enabled",
                                        "Enable Custom Colors",
                                        "Apply OptiFine color.properties and "
                                                + "colormap resource-pack "
                                                + "overrides.",
                                        CinderConfigDefaults
                                                .CUSTOM_COLORS_ENABLED,
                                        this.storage::setCustomColorsEnabled,
                                        this.storage::getCustomColorsEnabled,
                                        OptionFlag
                                                .REQUIRES_RENDERER_RELOAD))))
                .addPage(builder.createOptionPage()
                        .setName(Component.literal("Better Grass"))
                        .addOptionGroup(builder.createOptionGroup()
                                .setName(Component.literal("Grass Sides"))
                                .addOption(builder.createEnumOption(
                                                id("better_grass_mode"),
                                                BetterGrassMode.class)
                                        .setName(Component.literal("Mode"))
                                        .setTooltip(Component.literal(
                                                "Off keeps vanilla sides, Fast "
                                                        + "uses grass top on "
                                                        + "all grass sides, "
                                                        + "Fancy only extends "
                                                        + "grass over matching "
                                                        + "neighbour edges."))
                                        .setElementNameProvider(
                                                EnumOptionBuilder.nameProviderFrom(
                                                        Component.literal("Off"),
                                                        Component.literal("Fast"),
                                                        Component.literal("Fancy")))
                                        .setStorageHandler(this.storageHandler)
                                        .setBinding(
                                                this.storage::setBetterGrassMode,
                                                this.storage::getBetterGrassMode)
                                        .setDefaultValue(
                                                CinderConfigDefaults
                                                        .BETTER_GRASS_MODE)
                                        .setFlags(
                                                OptionFlag
                                                        .REQUIRES_RENDERER_RELOAD))
                                .addOption(booleanOption(builder,
                                        "better_grass_ignore_resource_pack",
                                        "Ignore Resource Pack",
                                        "Use Cinder's Better Grass family "
                                                + "toggles and vanilla "
                                                + "textures even when a pack "
                                                + "provides "
                                                + "bettergrass.properties.",
                                        CinderConfigDefaults
                                                .BETTER_GRASS_IGNORE_RESOURCE_PACK,
                                        this.storage
                                                ::setBetterGrassIgnoreResourcePack,
                                        this.storage
                                                ::getBetterGrassIgnoreResourcePack,
                                        OptionFlag
                                                .REQUIRES_RENDERER_RELOAD))
                                .addOption(betterGrassBlockOption(builder,
                                        "better_grass_grass_block",
                                        "Grass Block",
                                        "Apply Better Grass to grass block "
                                                + "side faces.",
                                        CinderConfigDefaults
                                                .BETTER_GRASS_GRASS_BLOCK,
                                        this.storage
                                                ::setBetterGrassGrassBlock,
                                        this.storage
                                                ::getBetterGrassGrassBlock))
                                .addOption(betterGrassBlockOption(builder,
                                        "better_grass_snowy_grass_block",
                                        "Snowy Dirt Covers",
                                        "Apply Better Grass snow sides to "
                                                + "snowy grass, mycelium, "
                                                + "and podzol side faces.",
                                        CinderConfigDefaults
                                                .BETTER_GRASS_SNOWY_GRASS_BLOCK,
                                        this.storage
                                                ::setBetterGrassSnowyGrassBlock,
                                        this.storage
                                                ::getBetterGrassSnowyGrassBlock))
                                .addOption(betterGrassBlockOption(builder,
                                        "better_grass_dirt_path",
                                        "Dirt Path",
                                        "Apply Better Grass to dirt path "
                                                + "side faces.",
                                        CinderConfigDefaults
                                                .BETTER_GRASS_DIRT_PATH,
                                        this.storage::setBetterGrassDirtPath,
                                        this.storage::getBetterGrassDirtPath))
                                .addOption(betterGrassBlockOption(builder,
                                        "better_grass_farmland",
                                        "Farmland",
                                        "Apply Better Grass to farmland side "
                                                + "faces.",
                                        CinderConfigDefaults
                                                .BETTER_GRASS_FARMLAND,
                                        this.storage::setBetterGrassFarmland,
                                        this.storage::getBetterGrassFarmland))
                                .addOption(betterGrassBlockOption(builder,
                                        "better_grass_mycelium",
                                        "Mycelium",
                                        "Apply Better Grass to mycelium side "
                                                + "faces.",
                                        CinderConfigDefaults
                                                .BETTER_GRASS_MYCELIUM,
                                        this.storage::setBetterGrassMycelium,
                                        this.storage::getBetterGrassMycelium))
                                .addOption(betterGrassBlockOption(builder,
                                        "better_grass_podzol",
                                        "Podzol",
                                        "Apply Better Grass to podzol side "
                                                + "faces.",
                                        CinderConfigDefaults
                                                .BETTER_GRASS_PODZOL,
                                        this.storage::setBetterGrassPodzol,
                                        this.storage::getBetterGrassPodzol))
                                .addOption(betterGrassBlockOption(builder,
                                        "better_grass_crimson_nylium",
                                        "Crimson Nylium",
                                        "Apply Better Grass to crimson "
                                                + "nylium side faces.",
                                        CinderConfigDefaults
                                                .BETTER_GRASS_CRIMSON_NYLIUM,
                                        this.storage
                                                ::setBetterGrassCrimsonNylium,
                                        this.storage
                                                ::getBetterGrassCrimsonNylium))
                                .addOption(betterGrassBlockOption(builder,
                                        "better_grass_warped_nylium",
                                        "Warped Nylium",
                                        "Apply Better Grass to warped "
                                                + "nylium side faces.",
                                        CinderConfigDefaults
                                                .BETTER_GRASS_WARPED_NYLIUM,
                                        this.storage
                                                ::setBetterGrassWarpedNylium,
                                        this.storage
                                                ::getBetterGrassWarpedNylium))))
                ;
    }

    private BooleanOptionBuilder betterGrassBlockOption(ConfigBuilder builder,
                                                        String path,
                                                        String name,
                                                        String tooltip,
                                                        boolean defaultValue,
                                                        Consumer<Boolean> setter,
                                                        Supplier<Boolean> getter) {
        return booleanOption(builder, path, name, tooltip, defaultValue,
                setter, getter, OptionFlag.REQUIRES_RENDERER_RELOAD);
    }

    private BooleanOptionBuilder booleanOption(ConfigBuilder builder,
                                               String path,
                                               String name,
                                               String tooltip,
                                               boolean defaultValue,
                                               Consumer<Boolean> setter,
                                               Supplier<Boolean> getter,
                                               OptionFlag... flags) {
        return builder.createBooleanOption(id(path))
                .setName(Component.literal(name))
                .setTooltip(Component.literal(tooltip))
                .setStorageHandler(this.storageHandler)
                .setBinding(setter, getter)
                .setDefaultValue(defaultValue)
                .setFlags(flags);
    }

    private IntegerOptionBuilder integerOption(ConfigBuilder builder,
                                               String path,
                                               String name,
                                               String tooltip,
                                               int defaultValue,
                                               int min,
                                               int max,
                                               int step,
                                               Consumer<Integer> setter,
                                               Supplier<Integer> getter,
                                               OptionFlag... flags) {
        return builder.createIntegerOption(id(path))
                .setName(Component.literal(name))
                .setTooltip(Component.literal(tooltip))
                .setStorageHandler(this.storageHandler)
                .setBinding(setter, getter)
                .setDefaultValue(defaultValue)
                .setRange(min, max, step)
                .setValueFormatter(value -> Component.literal(
                        value == 0 ? "Near only" : "Mip " + value))
                .setFlags(flags);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    /**
     * Sodium option storage backed by Cinder's immutable config snapshot.
     *
     * <p>Threading: UI thread only. Each setter installs a complete replacement
     * snapshot so render code never observes a partially-mutated config.
     */
    private static final class OptionStorage {
        private final Path configDir =
                FabricLoader.getInstance().getConfigDir();

        boolean getCtmEnabled() {
            return CinderConfigHolder.get().ctmEnabled();
        }

        void setCtmEnabled(boolean value) {
            replace(withCtmEnabled(value));
        }

        boolean getSafeMode() {
            return CinderConfigHolder.get().safeMode();
        }

        void setSafeMode(boolean value) {
            replace(withSafeMode(value));
        }

        boolean getVerifyMode() {
            return CinderConfigHolder.get().verifyMode();
        }

        void setVerifyMode(boolean value) {
            replace(withVerifyMode(value));
        }

        boolean getCtmDebugLogging() {
            return CinderConfigHolder.get().ctmDebugLogging();
        }

        void setCtmDebugLogging(boolean value) {
            replace(withCtmDebugLogging(value));
        }

        boolean getCitEnabled() {
            return CinderConfigHolder.get().citEnabled();
        }

        void setCitEnabled(boolean value) {
            replace(withCitEnabled(value));
        }

        boolean getCustomGuiEnabled() {
            return CinderConfigHolder.get().customGuiEnabled();
        }

        void setCustomGuiEnabled(boolean value) {
            replace(withCustomGuiEnabled(value));
        }

        boolean getCustomColorsEnabled() {
            return CinderConfigHolder.get().customColorsEnabled();
        }

        void setCustomColorsEnabled(boolean value) {
            replace(withCustomColorsEnabled(value));
        }

        boolean getCustomAnimationsEnabled() {
            return CinderConfigHolder.get().customAnimationsEnabled();
        }

        void setCustomAnimationsEnabled(boolean value) {
            replace(withCustomAnimationsEnabled(value));
        }

        int getCustomAnimationMipmapDistance() {
            return CinderConfigHolder.get().customAnimationMipmapDistance();
        }

        void setCustomAnimationMipmapDistance(int value) {
            replace(withCustomAnimationMipmapDistance(value));
        }

        boolean getDuplicateTranslucentBackfaces() {
            return CinderConfigHolder.get().duplicateTranslucentBackfaces();
        }

        void setDuplicateTranslucentBackfaces(boolean value) {
            replace(withDuplicateTranslucentBackfaces(value));
        }

        BetterGrassMode getBetterGrassMode() {
            return CinderConfigHolder.get().betterGrassMode();
        }

        void setBetterGrassMode(BetterGrassMode value) {
            replace(withBetterGrassMode(value));
        }

        boolean getBetterGrassIgnoreResourcePack() {
            return CinderConfigHolder.get().betterGrassIgnoreResourcePack();
        }

        void setBetterGrassIgnoreResourcePack(boolean value) {
            replace(withBetterGrassIgnoreResourcePack(value));
        }

        boolean getBetterGrassGrassBlock() {
            return CinderConfigHolder.get().betterGrassGrassBlock();
        }

        void setBetterGrassGrassBlock(boolean value) {
            replace(withBetterGrassGrassBlock(value));
        }

        boolean getBetterGrassSnowyGrassBlock() {
            return CinderConfigHolder.get().betterGrassSnowyGrassBlock();
        }

        void setBetterGrassSnowyGrassBlock(boolean value) {
            replace(withBetterGrassSnowyGrassBlock(value));
        }

        boolean getBetterGrassDirtPath() {
            return CinderConfigHolder.get().betterGrassDirtPath();
        }

        void setBetterGrassDirtPath(boolean value) {
            replace(withBetterGrassDirtPath(value));
        }

        boolean getBetterGrassFarmland() {
            return CinderConfigHolder.get().betterGrassFarmland();
        }

        void setBetterGrassFarmland(boolean value) {
            replace(withBetterGrassFarmland(value));
        }

        boolean getBetterGrassMycelium() {
            return CinderConfigHolder.get().betterGrassMycelium();
        }

        void setBetterGrassMycelium(boolean value) {
            replace(withBetterGrassMycelium(value));
        }

        boolean getBetterGrassPodzol() {
            return CinderConfigHolder.get().betterGrassPodzol();
        }

        void setBetterGrassPodzol(boolean value) {
            replace(withBetterGrassPodzol(value));
        }

        boolean getBetterGrassCrimsonNylium() {
            return CinderConfigHolder.get().betterGrassCrimsonNylium();
        }

        void setBetterGrassCrimsonNylium(boolean value) {
            replace(withBetterGrassCrimsonNylium(value));
        }

        boolean getBetterGrassWarpedNylium() {
            return CinderConfigHolder.get().betterGrassWarpedNylium();
        }

        void setBetterGrassWarpedNylium(boolean value) {
            replace(withBetterGrassWarpedNylium(value));
        }

        void flush() {
            FabricConfigLoader.save(this.configDir, CinderConfigHolder.get());
        }

        private static void replace(CinderConfig config) {
            CinderConfigHolder.replace(config);
        }

        private static CinderConfig withCtmEnabled(boolean value) {
            CinderConfig cfg = CinderConfigHolder.get();
            return new CinderConfig(cfg.enabled(), cfg.safeMode(),
                    cfg.verifyMode(), value, cfg.ctmDebugLogging(),
                    cfg.duplicateTranslucentBackfaces(),
                    cfg.betterGrassMode(),
                    cfg.betterGrassIgnoreResourcePack(),
                    cfg.betterGrassGrassBlock(),
                    cfg.betterGrassSnowyGrassBlock(),
                    cfg.betterGrassDirtPath(), cfg.betterGrassFarmland(),
                    cfg.betterGrassMycelium(), cfg.betterGrassPodzol(),
                    cfg.betterGrassCrimsonNylium(),
                    cfg.betterGrassWarpedNylium(), cfg.citEnabled(),
                    cfg.customGuiEnabled(), cfg.customColorsEnabled(), cfg.customAnimationsEnabled(), cfg.customAnimationMipmapDistance());
        }

        private static CinderConfig withSafeMode(boolean value) {
            CinderConfig cfg = CinderConfigHolder.get();
            return new CinderConfig(cfg.enabled(), value, cfg.verifyMode(),
                    cfg.ctmEnabled(), cfg.ctmDebugLogging(),
                    cfg.duplicateTranslucentBackfaces(),
                    cfg.betterGrassMode(),
                    cfg.betterGrassIgnoreResourcePack(),
                    cfg.betterGrassGrassBlock(),
                    cfg.betterGrassSnowyGrassBlock(),
                    cfg.betterGrassDirtPath(), cfg.betterGrassFarmland(),
                    cfg.betterGrassMycelium(), cfg.betterGrassPodzol(),
                    cfg.betterGrassCrimsonNylium(),
                    cfg.betterGrassWarpedNylium(), cfg.citEnabled(),
                    cfg.customGuiEnabled(), cfg.customColorsEnabled(), cfg.customAnimationsEnabled(), cfg.customAnimationMipmapDistance());
        }

        private static CinderConfig withVerifyMode(boolean value) {
            CinderConfig cfg = CinderConfigHolder.get();
            return new CinderConfig(cfg.enabled(), cfg.safeMode(), value,
                    cfg.ctmEnabled(), cfg.ctmDebugLogging(),
                    cfg.duplicateTranslucentBackfaces(),
                    cfg.betterGrassMode(),
                    cfg.betterGrassIgnoreResourcePack(),
                    cfg.betterGrassGrassBlock(),
                    cfg.betterGrassSnowyGrassBlock(),
                    cfg.betterGrassDirtPath(), cfg.betterGrassFarmland(),
                    cfg.betterGrassMycelium(), cfg.betterGrassPodzol(),
                    cfg.betterGrassCrimsonNylium(),
                    cfg.betterGrassWarpedNylium(), cfg.citEnabled(),
                    cfg.customGuiEnabled(), cfg.customColorsEnabled(), cfg.customAnimationsEnabled(), cfg.customAnimationMipmapDistance());
        }

        private static CinderConfig withCtmDebugLogging(boolean value) {
            CinderConfig cfg = CinderConfigHolder.get();
            return new CinderConfig(cfg.enabled(), cfg.safeMode(),
                    cfg.verifyMode(), cfg.ctmEnabled(), value,
                    cfg.duplicateTranslucentBackfaces(),
                    cfg.betterGrassMode(),
                    cfg.betterGrassIgnoreResourcePack(),
                    cfg.betterGrassGrassBlock(),
                    cfg.betterGrassSnowyGrassBlock(),
                    cfg.betterGrassDirtPath(), cfg.betterGrassFarmland(),
                    cfg.betterGrassMycelium(), cfg.betterGrassPodzol(),
                    cfg.betterGrassCrimsonNylium(),
                    cfg.betterGrassWarpedNylium(), cfg.citEnabled(),
                    cfg.customGuiEnabled(), cfg.customColorsEnabled(), cfg.customAnimationsEnabled(), cfg.customAnimationMipmapDistance());
        }

        private static CinderConfig withCitEnabled(boolean value) {
            CinderConfig cfg = CinderConfigHolder.get();
            return new CinderConfig(cfg.enabled(), cfg.safeMode(),
                    cfg.verifyMode(), cfg.ctmEnabled(),
                    cfg.ctmDebugLogging(),
                    cfg.duplicateTranslucentBackfaces(),
                    cfg.betterGrassMode(),
                    cfg.betterGrassIgnoreResourcePack(),
                    cfg.betterGrassGrassBlock(),
                    cfg.betterGrassSnowyGrassBlock(),
                    cfg.betterGrassDirtPath(), cfg.betterGrassFarmland(),
                    cfg.betterGrassMycelium(), cfg.betterGrassPodzol(),
                    cfg.betterGrassCrimsonNylium(),
                    cfg.betterGrassWarpedNylium(), value,
                    cfg.customGuiEnabled(), cfg.customColorsEnabled(), cfg.customAnimationsEnabled(), cfg.customAnimationMipmapDistance());
        }

        private static CinderConfig withCustomGuiEnabled(boolean value) {
            CinderConfig cfg = CinderConfigHolder.get();
            return new CinderConfig(cfg.enabled(), cfg.safeMode(),
                    cfg.verifyMode(), cfg.ctmEnabled(),
                    cfg.ctmDebugLogging(),
                    cfg.duplicateTranslucentBackfaces(),
                    cfg.betterGrassMode(),
                    cfg.betterGrassIgnoreResourcePack(),
                    cfg.betterGrassGrassBlock(),
                    cfg.betterGrassSnowyGrassBlock(),
                    cfg.betterGrassDirtPath(), cfg.betterGrassFarmland(),
                    cfg.betterGrassMycelium(), cfg.betterGrassPodzol(),
                    cfg.betterGrassCrimsonNylium(),
                    cfg.betterGrassWarpedNylium(), cfg.citEnabled(), value,
                    cfg.customColorsEnabled(),
                    cfg.customAnimationsEnabled(),
                    cfg.customAnimationMipmapDistance());
        }

        private static CinderConfig withCustomColorsEnabled(boolean value) {
            CinderConfig cfg = CinderConfigHolder.get();
            return new CinderConfig(cfg.enabled(), cfg.safeMode(),
                    cfg.verifyMode(), cfg.ctmEnabled(),
                    cfg.ctmDebugLogging(),
                    cfg.duplicateTranslucentBackfaces(),
                    cfg.betterGrassMode(),
                    cfg.betterGrassIgnoreResourcePack(),
                    cfg.betterGrassGrassBlock(),
                    cfg.betterGrassSnowyGrassBlock(),
                    cfg.betterGrassDirtPath(), cfg.betterGrassFarmland(),
                    cfg.betterGrassMycelium(), cfg.betterGrassPodzol(),
                    cfg.betterGrassCrimsonNylium(),
                    cfg.betterGrassWarpedNylium(), cfg.citEnabled(),
                    cfg.customGuiEnabled(), value,
                    cfg.customAnimationsEnabled(),
                    cfg.customAnimationMipmapDistance());
        }

        private static CinderConfig withCustomAnimationsEnabled(
                boolean value) {
            CinderConfig cfg = CinderConfigHolder.get();
            return new CinderConfig(cfg.enabled(), cfg.safeMode(),
                    cfg.verifyMode(), cfg.ctmEnabled(),
                    cfg.ctmDebugLogging(),
                    cfg.duplicateTranslucentBackfaces(),
                    cfg.betterGrassMode(),
                    cfg.betterGrassIgnoreResourcePack(),
                    cfg.betterGrassGrassBlock(),
                    cfg.betterGrassSnowyGrassBlock(),
                    cfg.betterGrassDirtPath(), cfg.betterGrassFarmland(),
                    cfg.betterGrassMycelium(), cfg.betterGrassPodzol(),
                    cfg.betterGrassCrimsonNylium(),
                    cfg.betterGrassWarpedNylium(), cfg.citEnabled(),
                    cfg.customGuiEnabled(), cfg.customColorsEnabled(), value,
                    cfg.customAnimationMipmapDistance());
        }

        private static CinderConfig withCustomAnimationMipmapDistance(
                int value) {
            CinderConfig cfg = CinderConfigHolder.get();
            return new CinderConfig(cfg.enabled(), cfg.safeMode(),
                    cfg.verifyMode(), cfg.ctmEnabled(),
                    cfg.ctmDebugLogging(),
                    cfg.duplicateTranslucentBackfaces(),
                    cfg.betterGrassMode(),
                    cfg.betterGrassIgnoreResourcePack(),
                    cfg.betterGrassGrassBlock(),
                    cfg.betterGrassSnowyGrassBlock(),
                    cfg.betterGrassDirtPath(), cfg.betterGrassFarmland(),
                    cfg.betterGrassMycelium(), cfg.betterGrassPodzol(),
                    cfg.betterGrassCrimsonNylium(),
                    cfg.betterGrassWarpedNylium(), cfg.citEnabled(),
                    cfg.customGuiEnabled(), cfg.customColorsEnabled(), cfg.customAnimationsEnabled(),
                    value);
        }

        private static CinderConfig withDuplicateTranslucentBackfaces(
                boolean value) {
            CinderConfig cfg = CinderConfigHolder.get();
            return new CinderConfig(cfg.enabled(), cfg.safeMode(),
                    cfg.verifyMode(), cfg.ctmEnabled(), cfg.ctmDebugLogging(),
                    value,
                    cfg.betterGrassMode(),
                    cfg.betterGrassIgnoreResourcePack(),
                    cfg.betterGrassGrassBlock(),
                    cfg.betterGrassSnowyGrassBlock(),
                    cfg.betterGrassDirtPath(), cfg.betterGrassFarmland(),
                    cfg.betterGrassMycelium(), cfg.betterGrassPodzol(),
                    cfg.betterGrassCrimsonNylium(),
                    cfg.betterGrassWarpedNylium(), cfg.citEnabled(),
                    cfg.customGuiEnabled(), cfg.customColorsEnabled(), cfg.customAnimationsEnabled(), cfg.customAnimationMipmapDistance());
        }

        private static CinderConfig withBetterGrassMode(
                BetterGrassMode value) {
            CinderConfig cfg = CinderConfigHolder.get();
            return new CinderConfig(cfg.enabled(), cfg.safeMode(),
                    cfg.verifyMode(), cfg.ctmEnabled(), cfg.ctmDebugLogging(),
                    cfg.duplicateTranslucentBackfaces(),
                    value, cfg.betterGrassIgnoreResourcePack(),
                    cfg.betterGrassGrassBlock(),
                    cfg.betterGrassSnowyGrassBlock(),
                    cfg.betterGrassDirtPath(), cfg.betterGrassFarmland(),
                    cfg.betterGrassMycelium(), cfg.betterGrassPodzol(),
                    cfg.betterGrassCrimsonNylium(),
                    cfg.betterGrassWarpedNylium(), cfg.citEnabled(),
                    cfg.customGuiEnabled(), cfg.customColorsEnabled(), cfg.customAnimationsEnabled(), cfg.customAnimationMipmapDistance());
        }

        private static CinderConfig withBetterGrassIgnoreResourcePack(
                boolean value) {
            CinderConfig cfg = CinderConfigHolder.get();
            return new CinderConfig(cfg.enabled(), cfg.safeMode(),
                    cfg.verifyMode(), cfg.ctmEnabled(), cfg.ctmDebugLogging(),
                    cfg.duplicateTranslucentBackfaces(),
                    cfg.betterGrassMode(), value,
                    cfg.betterGrassGrassBlock(),
                    cfg.betterGrassSnowyGrassBlock(),
                    cfg.betterGrassDirtPath(), cfg.betterGrassFarmland(),
                    cfg.betterGrassMycelium(), cfg.betterGrassPodzol(),
                    cfg.betterGrassCrimsonNylium(),
                    cfg.betterGrassWarpedNylium(), cfg.citEnabled(),
                    cfg.customGuiEnabled(), cfg.customColorsEnabled(), cfg.customAnimationsEnabled(), cfg.customAnimationMipmapDistance());
        }

        private static CinderConfig withBetterGrassGrassBlock(boolean value) {
            CinderConfig cfg = CinderConfigHolder.get();
            return withBetterGrassBlocks(cfg, value,
                    cfg.betterGrassSnowyGrassBlock(),
                    cfg.betterGrassDirtPath(), cfg.betterGrassFarmland(),
                    cfg.betterGrassMycelium(), cfg.betterGrassPodzol(),
                    cfg.betterGrassCrimsonNylium(),
                    cfg.betterGrassWarpedNylium());
        }

        private static CinderConfig withBetterGrassSnowyGrassBlock(
                boolean value) {
            CinderConfig cfg = CinderConfigHolder.get();
            return withBetterGrassBlocks(cfg, cfg.betterGrassGrassBlock(),
                    value, cfg.betterGrassDirtPath(),
                    cfg.betterGrassFarmland(), cfg.betterGrassMycelium(),
                    cfg.betterGrassPodzol(), cfg.betterGrassCrimsonNylium(),
                    cfg.betterGrassWarpedNylium());
        }

        private static CinderConfig withBetterGrassDirtPath(boolean value) {
            CinderConfig cfg = CinderConfigHolder.get();
            return withBetterGrassBlocks(cfg, cfg.betterGrassGrassBlock(),
                    cfg.betterGrassSnowyGrassBlock(),
                    value, cfg.betterGrassFarmland(),
                    cfg.betterGrassMycelium(),
                    cfg.betterGrassPodzol(), cfg.betterGrassCrimsonNylium(),
                    cfg.betterGrassWarpedNylium());
        }

        private static CinderConfig withBetterGrassFarmland(boolean value) {
            CinderConfig cfg = CinderConfigHolder.get();
            return withBetterGrassBlocks(cfg, cfg.betterGrassGrassBlock(),
                    cfg.betterGrassSnowyGrassBlock(),
                    cfg.betterGrassDirtPath(), value,
                    cfg.betterGrassMycelium(), cfg.betterGrassPodzol(),
                    cfg.betterGrassCrimsonNylium(),
                    cfg.betterGrassWarpedNylium());
        }

        private static CinderConfig withBetterGrassMycelium(boolean value) {
            CinderConfig cfg = CinderConfigHolder.get();
            return withBetterGrassBlocks(cfg, cfg.betterGrassGrassBlock(),
                    cfg.betterGrassSnowyGrassBlock(),
                    cfg.betterGrassDirtPath(), cfg.betterGrassFarmland(),
                    value,
                    cfg.betterGrassPodzol(), cfg.betterGrassCrimsonNylium(),
                    cfg.betterGrassWarpedNylium());
        }

        private static CinderConfig withBetterGrassPodzol(boolean value) {
            CinderConfig cfg = CinderConfigHolder.get();
            return withBetterGrassBlocks(cfg, cfg.betterGrassGrassBlock(),
                    cfg.betterGrassSnowyGrassBlock(),
                    cfg.betterGrassDirtPath(), cfg.betterGrassFarmland(),
                    cfg.betterGrassMycelium(), value,
                    cfg.betterGrassCrimsonNylium(), cfg.betterGrassWarpedNylium());
        }

        private static CinderConfig withBetterGrassCrimsonNylium(
                boolean value) {
            CinderConfig cfg = CinderConfigHolder.get();
            return withBetterGrassBlocks(cfg, cfg.betterGrassGrassBlock(),
                    cfg.betterGrassSnowyGrassBlock(),
                    cfg.betterGrassDirtPath(), cfg.betterGrassFarmland(),
                    cfg.betterGrassMycelium(), cfg.betterGrassPodzol(), value,
                    cfg.betterGrassWarpedNylium());
        }

        private static CinderConfig withBetterGrassWarpedNylium(
                boolean value) {
            CinderConfig cfg = CinderConfigHolder.get();
            return withBetterGrassBlocks(cfg, cfg.betterGrassGrassBlock(),
                    cfg.betterGrassSnowyGrassBlock(),
                    cfg.betterGrassDirtPath(), cfg.betterGrassFarmland(),
                    cfg.betterGrassMycelium(), cfg.betterGrassPodzol(),
                    cfg.betterGrassCrimsonNylium(), value);
        }

        private static CinderConfig withBetterGrassBlocks(
                CinderConfig cfg,
                boolean grassBlock,
                boolean snowyGrassBlock,
                boolean dirtPath,
                boolean farmland,
                boolean mycelium,
                boolean podzol,
                boolean crimsonNylium,
                boolean warpedNylium) {
            return new CinderConfig(cfg.enabled(), cfg.safeMode(),
                    cfg.verifyMode(), cfg.ctmEnabled(), cfg.ctmDebugLogging(),
                    cfg.duplicateTranslucentBackfaces(),
                    cfg.betterGrassMode(),
                    cfg.betterGrassIgnoreResourcePack(),
                    grassBlock, snowyGrassBlock,
                    dirtPath, farmland, mycelium, podzol, crimsonNylium,
                    warpedNylium, cfg.citEnabled(), cfg.customGuiEnabled(), cfg.customColorsEnabled(),
                    cfg.customAnimationsEnabled(),
                    cfg.customAnimationMipmapDistance());
        }
    }
}
