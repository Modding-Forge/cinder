package com.cinder.client.sodium;

import com.cinder.config.BetterGrassMode;
import com.cinder.config.CinderConfig;
import com.cinder.config.CinderConfigDefaults;
import com.cinder.config.CinderConfigHolder;
import com.cinder.config.FullscreenMode;
import com.cinder.config.OverlayCorner;
import com.cinder.config.TextContrast;
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
 * Registers Cinder's Sodium settings pages.
 *
 * <p>The menu is grouped by player-facing feature family instead of by
 * implementation history. Each visible option has an active runtime hook; the
 * dynamic per-resource-pack animation page is intentionally not exposed until
 * the config format supports stable map serialization.
 *
 * <p>Threading: Sodium calls this on the client configuration UI thread. The
 * storage publishes immutable {@link CinderConfig} snapshots through
 * {@link CinderConfigHolder}.
 *
 * <p>Performance: not in render hot paths.
 */
public final class CinderSodiumConfigEntryPoint implements ConfigEntryPoint {

    private static final String MOD_ID = "cinder";
    private static final Identifier CONFIG_ICON =
            Identifier.fromNamespaceAndPath(MOD_ID,
                    "textures/gui/config_icon.png");

    private final OptionStorage storage = new OptionStorage();
    private final StorageEventHandler storageHandler = this.storage::flush;

    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        builder.registerOwnModOptions()
                .setName("Cinder")
                .setVersion(cinderVersion())
                .setIcon(CONFIG_ICON)
                .addPage(builder.createOptionPage()
                        .setName(Component.literal("General"))
                        .addOptionGroup(builder.createOptionGroup()
                                .setName(Component.literal("Renderer"))
                                .addOption(booleanOption(builder, "enabled",
                                        "Enable Cinder",
                                        "Master switch for all Cinder client "
                                                + "visual features.",
                                        CinderConfigDefaults.ENABLED,
                                        this.storage::setEnabled,
                                        this.storage::getEnabled,
                                        OptionFlag.REQUIRES_RENDERER_RELOAD))
                                .addOption(booleanOption(builder, "safe_mode",
                                        "Safe Mode",
                                        "Keep conservative renderer fallbacks "
                                                + "enabled while debugging.",
                                        CinderConfigDefaults.SAFE_MODE,
                                        this.storage::setSafeMode,
                                        this.storage::getSafeMode))
                                .addOption(booleanOption(builder,
                                        "verify_mode", "Verify Mode",
                                        "Enable additional development checks "
                                                + "where available.",
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
                                        OptionFlag.REQUIRES_RENDERER_RELOAD))))
                .addPage(builder.createOptionPage()
                        .setName(Component.literal("Resource Pack Features"))
                        .addOptionGroup(builder.createOptionGroup()
                                .setName(Component.literal("OptiFine Features"))
                                .addOption(booleanOption(builder,
                                        "ctm_enabled", "Connected Textures",
                                        "Render connected textures through "
                                                + "Cinder's Sodium path.",
                                        CinderConfigDefaults.CTM_ENABLED,
                                        this.storage::setCtmEnabled,
                                        this.storage::getCtmEnabled,
                                        OptionFlag.REQUIRES_RENDERER_RELOAD))
                                .addOption(booleanOption(builder,
                                        "cit_enabled", "Custom Item Textures",
                                        "Render OptiFine item texture and "
                                                + "model replacements.",
                                        CinderConfigDefaults.CIT_ENABLED,
                                        this.storage::setCitEnabled,
                                        this.storage::getCitEnabled))
                                .addOption(booleanOption(builder,
                                        "custom_gui_enabled", "Custom GUI",
                                        "Render OptiFine GUI texture "
                                                + "replacements.",
                                        CinderConfigDefaults
                                                .CUSTOM_GUI_ENABLED,
                                        this.storage::setCustomGuiEnabled,
                                        this.storage::getCustomGuiEnabled))
                                .addOption(booleanOption(builder,
                                        "custom_colors_enabled",
                                        "Custom Colors",
                                        "Apply color.properties and colormap "
                                                + "resource-pack overrides.",
                                        CinderConfigDefaults
                                                .CUSTOM_COLORS_ENABLED,
                                        this.storage::setCustomColorsEnabled,
                                        this.storage::getCustomColorsEnabled,
                                        OptionFlag.REQUIRES_RENDERER_RELOAD))
                                .addOption(booleanOption(builder,
                                        "custom_sky_enabled", "Custom Sky",
                                        "Render OptiFine sky layers loaded "
                                                + "from resource packs.",
                                        CinderConfigDefaults
                                                .CUSTOM_SKY_ENABLED,
                                        this.storage::setCustomSkyEnabled,
                                        this.storage::getCustomSkyEnabled))
                                .addOption(booleanOption(builder,
                                        "custom_animations_enabled",
                                        "Custom Animations",
                                        "Tick OptiFine custom texture "
                                                + "animations loaded from "
                                                + "resource packs.",
                                        CinderConfigDefaults
                                                .CUSTOM_ANIMATIONS_ENABLED,
                                        this.storage
                                                ::setCustomAnimationsEnabled,
                                        this.storage
                                                ::getCustomAnimationsEnabled))
                                .addOption(booleanOption(builder,
                                        "random_entities_enabled",
                                        "Random Entities",
                                        "Apply OptiFine/ETF-style random "
                                                + "entity texture variants.",
                                        CinderConfigDefaults
                                                .RANDOM_ENTITIES_ENABLED,
                                        this.storage
                                                ::setRandomEntitiesEnabled,
                                        this.storage
                                                ::getRandomEntitiesEnabled))
                                .addOption(booleanOption(builder,
                                        "custom_entity_models_enabled",
                                        "Custom Entity Models",
                                        "Apply OptiFine/EMF-style custom "
                                                + "entity models when "
                                                + "supported.",
                                        CinderConfigDefaults
                                                .CUSTOM_ENTITY_MODELS_ENABLED,
                                        this.storage
                                                ::setCustomEntityModelsEnabled,
                                        this.storage
                                                ::getCustomEntityModelsEnabled))
                                .addOption(booleanOption(builder,
                                        "natural_textures_enabled",
                                        "Natural Textures",
                                        "Apply natural.properties UV "
                                                + "rotations to terrain.",
                                        CinderConfigDefaults
                                                .NATURAL_TEXTURES_ENABLED,
                                        this.storage
                                                ::setNaturalTexturesEnabled,
                                        this.storage
                                                ::getNaturalTexturesEnabled,
                                        OptionFlag.REQUIRES_RENDERER_RELOAD))
                                .addOption(booleanOption(builder,
                                        "better_snow_enabled", "Better Snow",
                                        "Emit OptiFine-style snow layer "
                                                + "coverage for supported "
                                                + "non-solid blocks.",
                                        CinderConfigDefaults
                                                .BETTER_SNOW_ENABLED,
                                        this.storage::setBetterSnowEnabled,
                                        this.storage::getBetterSnowEnabled,
                                        OptionFlag.REQUIRES_RENDERER_RELOAD))
                                .addOption(booleanOption(builder,
                                        "ctm_debug_logging",
                                        "CTM Debug Logging",
                                        "Write gated CTM diagnostics to the "
                                                + "client log.",
                                        CinderConfigDefaults
                                                .CTM_DEBUG_LOGGING,
                                        this.storage::setCtmDebugLogging,
                                        this.storage::getCtmDebugLogging))))
                .addPage(detailsPage(builder))
                .addPage(animationsPage(builder))
                .addPage(particlesPage(builder))
                .addPage(entitiesPage(builder))
                .addPage(hudPage(builder))
                .addPage(extrasPage(builder))
                .addPage(betterGrassPage(builder));
    }

    private net.caffeinemc.mods.sodium.api.config.structure.OptionPageBuilder
    detailsPage(ConfigBuilder builder) {
        return builder.createOptionPage()
                .setName(Component.literal("Details"))
                .addOptionGroup(builder.createOptionGroup()
                        .setName(Component.literal("Sky and Weather"))
                        .addOption(booleanOption(builder,
                                "details_sky_enabled", "Sky",
                                "Render vanilla sky details. Turning this off "
                                        + "also disables Cinder Custom Sky.",
                                CinderConfigDefaults.DETAILS_SKY_ENABLED,
                                this.storage::setDetailsSkyEnabled,
                                this.storage::getDetailsSkyEnabled))
                        .addOption(booleanOption(builder,
                                "details_sun_enabled", "Sun",
                                "Render the vanilla sun.",
                                CinderConfigDefaults.DETAILS_SUN_ENABLED,
                                this.storage::setDetailsSunEnabled,
                                this.storage::getDetailsSunEnabled))
                        .addOption(booleanOption(builder,
                                "details_moon_enabled", "Moon",
                                "Render the vanilla moon.",
                                CinderConfigDefaults.DETAILS_MOON_ENABLED,
                                this.storage::setDetailsMoonEnabled,
                                this.storage::getDetailsMoonEnabled))
                        .addOption(booleanOption(builder,
                                "details_stars_enabled", "Stars",
                                "Render vanilla stars.",
                                CinderConfigDefaults.DETAILS_STARS_ENABLED,
                                this.storage::setDetailsStarsEnabled,
                                this.storage::getDetailsStarsEnabled))
                        .addOption(integerOption(builder,
                                "details_cloud_height", "Cloud Height",
                                "Override the cloud render height used by "
                                        + "Minecraft's cloud pass.",
                                CinderConfigDefaults.DETAILS_CLOUD_HEIGHT,
                                0, 512, 1,
                                this.storage::setDetailsCloudHeight,
                                this.storage::getDetailsCloudHeight))
                        .addOption(booleanOption(builder,
                                "details_rain_snow_enabled", "Rain and Snow",
                                "Render vanilla weather effects.",
                                CinderConfigDefaults
                                        .DETAILS_RAIN_SNOW_ENABLED,
                                this.storage::setDetailsRainSnowEnabled,
                                this.storage::getDetailsRainSnowEnabled))
                        .addOption(booleanOption(builder,
                                "colors_biome_enabled", "Biome Colors",
                                "Allow Custom Colors to override terrain and "
                                        + "biome tints.",
                                CinderConfigDefaults.BIOME_COLORS_ENABLED,
                                this.storage::setBiomeColorsEnabled,
                                this.storage::getBiomeColorsEnabled,
                                OptionFlag.REQUIRES_RENDERER_RELOAD))
                        .addOption(booleanOption(builder,
                                "colors_sky_enabled", "Sky and Fog Colors",
                                "Allow Custom Colors to override sky and fog "
                                        + "color targets.",
                                CinderConfigDefaults.SKY_COLORS_ENABLED,
                                this.storage::setSkyColorsEnabled,
                                this.storage::getSkyColorsEnabled)))
                .addOptionGroup(builder.createOptionGroup()
                        .setName(Component.literal("Fog"))
                        .addOption(booleanOption(builder,
                                "fog_enabled", "Fog",
                                "Render vanilla fog distances.",
                                CinderConfigDefaults.FOG_ENABLED,
                                this.storage::setFogEnabled,
                                this.storage::getFogEnabled))
                        .addOption(booleanOption(builder,
                                "fog_air", "Air Fog",
                                "Render atmospheric fog outside fluids.",
                                CinderConfigDefaults.FOG_AIR,
                                this.storage::setFogAir,
                                this.storage::getFogAir))
                        .addOption(booleanOption(builder,
                                "fog_water", "Water Fog",
                                "Render water fog.",
                                CinderConfigDefaults.FOG_WATER,
                                this.storage::setFogWater,
                                this.storage::getFogWater))
                        .addOption(booleanOption(builder,
                                "fog_lava", "Lava Fog",
                                "Render lava fog.",
                                CinderConfigDefaults.FOG_LAVA,
                                this.storage::setFogLava,
                                this.storage::getFogLava))
                        .addOption(booleanOption(builder,
                                "fog_powder_snow", "Powder Snow Fog",
                                "Render powder snow fog.",
                                CinderConfigDefaults.FOG_POWDER_SNOW,
                                this.storage::setFogPowderSnow,
                                this.storage::getFogPowderSnow)));
    }

    private net.caffeinemc.mods.sodium.api.config.structure.OptionPageBuilder
    animationsPage(ConfigBuilder builder) {
        return builder.createOptionPage()
                .setName(Component.literal("Animations"))
                .addOptionGroup(builder.createOptionGroup()
                        .setName(Component.literal("Texture Animations"))
                        .addOption(booleanOption(builder,
                                "animations_enabled", "Animations",
                                "Tick vanilla atlas sprite animations and "
                                        + "Cinder custom animations.",
                                CinderConfigDefaults.ANIMATIONS_ENABLED,
                                this.storage::setAnimationsEnabled,
                                this.storage::getAnimationsEnabled))
                        .addOption(booleanOption(builder,
                                "animation_water", "Water",
                                "Tick water texture animations.",
                                CinderConfigDefaults.ANIMATION_WATER,
                                this.storage::setAnimationWater,
                                this.storage::getAnimationWater))
                        .addOption(booleanOption(builder,
                                "animation_lava", "Lava",
                                "Tick lava texture animations.",
                                CinderConfigDefaults.ANIMATION_LAVA,
                                this.storage::setAnimationLava,
                                this.storage::getAnimationLava))
                        .addOption(booleanOption(builder,
                                "animation_fire", "Fire",
                                "Tick fire texture animations.",
                                CinderConfigDefaults.ANIMATION_FIRE,
                                this.storage::setAnimationFire,
                                this.storage::getAnimationFire))
                        .addOption(booleanOption(builder,
                                "animation_portal", "Portal",
                                "Tick portal texture animations.",
                                CinderConfigDefaults.ANIMATION_PORTAL,
                                this.storage::setAnimationPortal,
                                this.storage::getAnimationPortal))
                        .addOption(booleanOption(builder,
                                "animation_sculk_sensor", "Sculk Sensor",
                                "Tick sculk sensor texture animations.",
                                CinderConfigDefaults.ANIMATION_SCULK_SENSOR,
                                this.storage::setAnimationSculkSensor,
                                this.storage::getAnimationSculkSensor))
                        .addOption(booleanOption(builder,
                                "animation_blocks", "Other Blocks",
                                "Tick remaining block texture animations.",
                                CinderConfigDefaults.ANIMATION_BLOCKS,
                                this.storage::setAnimationBlocks,
                                this.storage::getAnimationBlocks))
                        .addOption(integerOption(builder,
                                "custom_animations_mipmap_distance",
                                "Custom Animation Mipmap Distance",
                                "Controls how many mipmap levels Cinder "
                                        + "updates for custom animations.",
                                CinderConfigDefaults
                                        .CUSTOM_ANIMATION_MIPMAP_DISTANCE,
                                0, 4, 1,
                                this.storage
                                        ::setCustomAnimationMipmapDistance,
                                this.storage
                                        ::getCustomAnimationMipmapDistance)));
    }

    private net.caffeinemc.mods.sodium.api.config.structure.OptionPageBuilder
    particlesPage(ConfigBuilder builder) {
        return builder.createOptionPage()
                .setName(Component.literal("Particles"))
                .addOptionGroup(builder.createOptionGroup()
                        .setName(Component.literal("Particle Spawning"))
                        .addOption(booleanOption(builder,
                                "particle_rain_splash", "Rain Splash",
                                "Allow rain splash particles.",
                                CinderConfigDefaults.PARTICLE_RAIN_SPLASH,
                                this.storage::setParticleRainSplash,
                                this.storage::getParticleRainSplash))
                        .addOption(booleanOption(builder,
                                "particle_block_break", "Block Break",
                                "Allow terrain block break particles.",
                                CinderConfigDefaults.PARTICLE_BLOCK_BREAK,
                                this.storage::setParticleBlockBreak,
                                this.storage::getParticleBlockBreak))
                        .addOption(booleanOption(builder,
                                "particle_block_breaking", "Block Breaking",
                                "Allow block marker and hit particles.",
                                CinderConfigDefaults.PARTICLE_BLOCK_BREAKING,
                                this.storage::setParticleBlockBreaking,
                                this.storage::getParticleBlockBreaking))
                        .addOption(booleanOption(builder,
                                "particle_explosion", "Explosions",
                                "Allow explosion and poof particles.",
                                CinderConfigDefaults.PARTICLE_EXPLOSION,
                                this.storage::setParticleExplosion,
                                this.storage::getParticleExplosion))
                        .addOption(booleanOption(builder,
                                "particle_water", "Water",
                                "Allow underwater water particles.",
                                CinderConfigDefaults.PARTICLE_WATER,
                                this.storage::setParticleWater,
                                this.storage::getParticleWater))
                        .addOption(booleanOption(builder,
                                "particle_smoke", "Smoke",
                                "Allow smoke and large smoke particles.",
                                CinderConfigDefaults.PARTICLE_SMOKE,
                                this.storage::setParticleSmoke,
                                this.storage::getParticleSmoke))
                        .addOption(booleanOption(builder,
                                "particle_potion", "Potion",
                                "Allow potion, effect and witch particles.",
                                CinderConfigDefaults.PARTICLE_POTION,
                                this.storage::setParticlePotion,
                                this.storage::getParticlePotion))
                        .addOption(booleanOption(builder,
                                "particle_portal", "Portal",
                                "Allow portal particles.",
                                CinderConfigDefaults.PARTICLE_PORTAL,
                                this.storage::setParticlePortal,
                                this.storage::getParticlePortal))
                        .addOption(booleanOption(builder,
                                "particle_flame", "Flame",
                                "Allow flame and soul flame particles.",
                                CinderConfigDefaults.PARTICLE_FLAME,
                                this.storage::setParticleFlame,
                                this.storage::getParticleFlame))
                        .addOption(booleanOption(builder,
                                "particle_redstone", "Redstone",
                                "Allow redstone dust particles.",
                                CinderConfigDefaults.PARTICLE_REDSTONE,
                                this.storage::setParticleRedstone,
                                this.storage::getParticleRedstone))
                        .addOption(booleanOption(builder,
                                "particle_dripping", "Dripping",
                                "Allow dripping water and lava particles.",
                                CinderConfigDefaults.PARTICLE_DRIPPING,
                                this.storage::setParticleDripping,
                                this.storage::getParticleDripping))
                        .addOption(booleanOption(builder,
                                "particle_firework", "Fireworks",
                                "Allow firework particles.",
                                CinderConfigDefaults.PARTICLE_FIREWORK,
                                this.storage::setParticleFirework,
                                this.storage::getParticleFirework)));
    }

    private net.caffeinemc.mods.sodium.api.config.structure.OptionPageBuilder
    entitiesPage(ConfigBuilder builder) {
        return builder.createOptionPage()
                .setName(Component.literal("Entities"))
                .addOptionGroup(builder.createOptionGroup()
                        .setName(Component.literal("Entity Rendering"))
                        .addOption(booleanOption(builder,
                                "entity_name_tags_enabled", "Name Tags",
                                "Render entity name tags.",
                                CinderConfigDefaults
                                        .ENTITY_NAME_TAGS_ENABLED,
                                this.storage::setEntityNameTagsEnabled,
                                this.storage::getEntityNameTagsEnabled))
                        .addOption(booleanOption(builder,
                                "entity_player_name_tags", "Player Names",
                                "Render player name tags.",
                                CinderConfigDefaults
                                        .ENTITY_PLAYER_NAME_TAGS,
                                this.storage::setEntityPlayerNameTags,
                                this.storage::getEntityPlayerNameTags))
                        .addOption(booleanOption(builder,
                                "entity_item_frames", "Item Frames",
                                "Render item frames.",
                                CinderConfigDefaults.ENTITY_ITEM_FRAMES,
                                this.storage::setEntityItemFrames,
                                this.storage::getEntityItemFrames))
                        .addOption(booleanOption(builder,
                                "entity_paintings", "Paintings",
                                "Render paintings.",
                                CinderConfigDefaults.ENTITY_PAINTINGS,
                                this.storage::setEntityPaintings,
                                this.storage::getEntityPaintings))
                        .addOption(booleanOption(builder,
                                "entity_piston_animations", "Pistons",
                                "Render moving piston block entities.",
                                CinderConfigDefaults
                                        .ENTITY_PISTON_ANIMATIONS,
                                this.storage::setEntityPistonAnimations,
                                this.storage::getEntityPistonAnimations))
                        .addOption(booleanOption(builder,
                                "entity_beacon_beam", "Beacon Beam",
                                "Render beacon beams.",
                                CinderConfigDefaults.ENTITY_BEACON_BEAM,
                                this.storage::setEntityBeaconBeam,
                                this.storage::getEntityBeaconBeam))
                        .addOption(booleanOption(builder,
                                "entity_enchanting_table_book",
                                "Enchanting Book",
                                "Render the enchanting table book.",
                                CinderConfigDefaults
                                        .ENTITY_ENCHANTING_TABLE_BOOK,
                                this.storage::setEntityEnchantingTableBook,
                                this.storage::getEntityEnchantingTableBook)));
    }

    private net.caffeinemc.mods.sodium.api.config.structure.OptionPageBuilder
    hudPage(ConfigBuilder builder) {
        return builder.createOptionPage()
                .setName(Component.literal("HUD"))
                .addOptionGroup(builder.createOptionGroup()
                        .setName(Component.literal("Overlay"))
                        .addOption(booleanOption(builder,
                                "hud_fps", "FPS",
                                "Show Cinder's compact FPS overlay.",
                                CinderConfigDefaults.SHOW_FPS,
                                this.storage::setShowFps,
                                this.storage::getShowFps))
                        .addOption(booleanOption(builder,
                                "hud_fps_extended", "Extended FPS",
                                "Show frame time next to FPS.",
                                CinderConfigDefaults.SHOW_FPS_EXTENDED,
                                this.storage::setShowFpsExtended,
                                this.storage::getShowFpsExtended))
                        .addOption(booleanOption(builder,
                                "hud_coords", "Coordinates",
                                "Show player coordinates.",
                                CinderConfigDefaults.SHOW_COORDS,
                                this.storage::setShowCoords,
                                this.storage::getShowCoords))
                        .addOption(enumOption(builder,
                                "hud_corner", "Corner",
                                "Screen corner for the Cinder HUD overlay.",
                                OverlayCorner.class,
                                CinderConfigDefaults.OVERLAY_CORNER,
                                this.storage::setOverlayCorner,
                                this.storage::getOverlayCorner))
                        .addOption(enumOption(builder,
                                "hud_text_contrast", "Text Contrast",
                                "Contrast style for Cinder HUD text.",
                                TextContrast.class,
                                CinderConfigDefaults.TEXT_CONTRAST,
                                this.storage::setTextContrast,
                                this.storage::getTextContrast)));
    }

    private net.caffeinemc.mods.sodium.api.config.structure.OptionPageBuilder
    extrasPage(ConfigBuilder builder) {
        return builder.createOptionPage()
                .setName(Component.literal("Extras"))
                .addOptionGroup(builder.createOptionGroup()
                        .setName(Component.literal("Toasts"))
                        .addOption(booleanOption(builder,
                                "toast_advancement", "Advancement Toasts",
                                "Show advancement toasts.",
                                CinderConfigDefaults.TOAST_ADVANCEMENT,
                                this.storage::setToastAdvancement,
                                this.storage::getToastAdvancement))
                        .addOption(booleanOption(builder,
                                "toast_recipe", "Recipe Toasts",
                                "Show recipe toasts.",
                                CinderConfigDefaults.TOAST_RECIPE,
                                this.storage::setToastRecipe,
                                this.storage::getToastRecipe))
                        .addOption(booleanOption(builder,
                                "toast_system", "System Toasts",
                                "Show system toasts.",
                                CinderConfigDefaults.TOAST_SYSTEM,
                                this.storage::setToastSystem,
                                this.storage::getToastSystem))
                        .addOption(booleanOption(builder,
                                "toast_tutorial", "Tutorial Toasts",
                                "Show tutorial toasts.",
                                CinderConfigDefaults.TOAST_TUTORIAL,
                                this.storage::setToastTutorial,
                                this.storage::getToastTutorial)))
                .addOptionGroup(builder.createOptionGroup()
                        .setName(Component.literal("Movement"))
                        .addOption(booleanOption(builder,
                                "instant_sneak", "Instant Sneak",
                                "Snap the client camera to crouch height "
                                        + "without changing gameplay state.",
                                CinderConfigDefaults.INSTANT_SNEAK,
                                this.storage::setInstantSneak,
                                this.storage::getInstantSneak)));
    }

    private net.caffeinemc.mods.sodium.api.config.structure.OptionPageBuilder
    betterGrassPage(ConfigBuilder builder) {
        return builder.createOptionPage()
                .setName(Component.literal("Better Grass"))
                .addOptionGroup(builder.createOptionGroup()
                        .setName(Component.literal("Grass Sides"))
                        .addOption(builder.createEnumOption(
                                        id("better_grass_mode"),
                                        BetterGrassMode.class)
                                .setName(Component.literal("Mode"))
                                .setTooltip(Component.literal(
                                        "Off keeps vanilla sides, Fast uses "
                                                + "top textures on all sides, "
                                                + "Fancy only extends over "
                                                + "matching neighbour edges."))
                                .setElementNameProvider(
                                        EnumOptionBuilder.nameProviderFrom(
                                                Component.literal("Off"),
                                                Component.literal("Fast"),
                                                Component.literal("Fancy")))
                                .setStorageHandler(this.storageHandler)
                                .setBinding(this.storage::setBetterGrassMode,
                                        this.storage::getBetterGrassMode)
                                .setDefaultValue(
                                        CinderConfigDefaults
                                                .BETTER_GRASS_MODE)
                                .setFlags(
                                        OptionFlag.REQUIRES_RENDERER_RELOAD))
                        .addOption(booleanOption(builder,
                                "better_grass_ignore_resource_pack",
                                "Ignore Resource Pack",
                                "Use Cinder's Better Grass toggles and "
                                        + "vanilla textures even when a pack "
                                        + "provides bettergrass.properties.",
                                CinderConfigDefaults
                                        .BETTER_GRASS_IGNORE_RESOURCE_PACK,
                                this.storage
                                        ::setBetterGrassIgnoreResourcePack,
                                this.storage
                                        ::getBetterGrassIgnoreResourcePack,
                                OptionFlag.REQUIRES_RENDERER_RELOAD))
                        .addOption(betterGrassBlockOption(builder,
                                "better_grass_grass_block", "Grass Block",
                                "Apply Better Grass to grass side faces.",
                                CinderConfigDefaults
                                        .BETTER_GRASS_GRASS_BLOCK,
                                this.storage::setBetterGrassGrassBlock,
                                this.storage::getBetterGrassGrassBlock))
                        .addOption(betterGrassBlockOption(builder,
                                "better_grass_snowy_grass_block",
                                "Snowy Dirt Covers",
                                "Apply snow sides to snowy grass, mycelium "
                                        + "and podzol faces.",
                                CinderConfigDefaults
                                        .BETTER_GRASS_SNOWY_GRASS_BLOCK,
                                this.storage::setBetterGrassSnowyGrassBlock,
                                this.storage::getBetterGrassSnowyGrassBlock))
                        .addOption(betterGrassBlockOption(builder,
                                "better_grass_dirt_path", "Dirt Path",
                                "Apply Better Grass to dirt path faces.",
                                CinderConfigDefaults.BETTER_GRASS_DIRT_PATH,
                                this.storage::setBetterGrassDirtPath,
                                this.storage::getBetterGrassDirtPath))
                        .addOption(betterGrassBlockOption(builder,
                                "better_grass_farmland", "Farmland",
                                "Apply Better Grass to farmland faces.",
                                CinderConfigDefaults.BETTER_GRASS_FARMLAND,
                                this.storage::setBetterGrassFarmland,
                                this.storage::getBetterGrassFarmland))
                        .addOption(betterGrassBlockOption(builder,
                                "better_grass_mycelium", "Mycelium",
                                "Apply Better Grass to mycelium faces.",
                                CinderConfigDefaults.BETTER_GRASS_MYCELIUM,
                                this.storage::setBetterGrassMycelium,
                                this.storage::getBetterGrassMycelium))
                        .addOption(betterGrassBlockOption(builder,
                                "better_grass_podzol", "Podzol",
                                "Apply Better Grass to podzol faces.",
                                CinderConfigDefaults.BETTER_GRASS_PODZOL,
                                this.storage::setBetterGrassPodzol,
                                this.storage::getBetterGrassPodzol))
                        .addOption(betterGrassBlockOption(builder,
                                "better_grass_crimson_nylium",
                                "Crimson Nylium",
                                "Apply Better Grass to crimson nylium.",
                                CinderConfigDefaults
                                        .BETTER_GRASS_CRIMSON_NYLIUM,
                                this.storage::setBetterGrassCrimsonNylium,
                                this.storage::getBetterGrassCrimsonNylium))
                        .addOption(betterGrassBlockOption(builder,
                                "better_grass_warped_nylium",
                                "Warped Nylium",
                                "Apply Better Grass to warped nylium.",
                                CinderConfigDefaults
                                        .BETTER_GRASS_WARPED_NYLIUM,
                                this.storage::setBetterGrassWarpedNylium,
                                this.storage::getBetterGrassWarpedNylium)));
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
                        Integer.toString(value)))
                .setFlags(flags);
    }

    private <T extends Enum<T>> EnumOptionBuilder<T> enumOption(
            ConfigBuilder builder,
            String path,
            String name,
            String tooltip,
            Class<T> type,
            T defaultValue,
            Consumer<T> setter,
            Supplier<T> getter,
            OptionFlag... flags) {
        return builder.createEnumOption(id(path), type)
                .setName(Component.literal(name))
                .setTooltip(Component.literal(tooltip))
                .setElementNameProvider(value -> Component.literal(
                        enumDisplayName(value.name())))
                .setStorageHandler(this.storageHandler)
                .setBinding(setter, getter)
                .setDefaultValue(defaultValue)
                .setFlags(flags);
    }

    private static String enumDisplayName(String name) {
        String[] parts = name.toLowerCase().split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                out.append(part.substring(1));
            }
        }
        return out.toString();
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    private static String cinderVersion() {
        return FabricLoader.getInstance()
                .getModContainer(MOD_ID)
                .map(container -> container.getMetadata()
                        .getVersion()
                        .getFriendlyString())
                .orElse("unknown");
    }

    private static final class OptionStorage {
        private final Path configDir = FabricLoader.getInstance()
                .getConfigDir();

        void flush() {
            FabricConfigLoader.save(this.configDir, CinderConfigHolder.get());
        }

        private void update(Consumer<CinderConfig.Builder> updater) {
            CinderConfig.Builder builder = CinderConfigHolder.get()
                    .toBuilder();
            updater.accept(builder);
            CinderConfigHolder.replace(builder.build());
        }

        boolean getEnabled() { return CinderConfigHolder.get().enabled(); }
        void setEnabled(boolean value) { update(b -> b.enabled(value)); }
        boolean getSafeMode() { return CinderConfigHolder.get().safeMode(); }
        void setSafeMode(boolean value) { update(b -> b.safeMode(value)); }
        boolean getVerifyMode() { return CinderConfigHolder.get().verifyMode(); }
        void setVerifyMode(boolean value) { update(b -> b.verifyMode(value)); }
        boolean getCtmEnabled() { return CinderConfigHolder.get().ctmEnabled(); }
        void setCtmEnabled(boolean value) { update(b -> b.ctmEnabled(value)); }
        boolean getCtmDebugLogging() { return CinderConfigHolder.get().ctmDebugLogging(); }
        void setCtmDebugLogging(boolean value) { update(b -> b.ctmDebugLogging(value)); }
        boolean getDuplicateTranslucentBackfaces() { return CinderConfigHolder.get().duplicateTranslucentBackfaces(); }
        void setDuplicateTranslucentBackfaces(boolean value) { update(b -> b.duplicateTranslucentBackfaces(value)); }
        boolean getCitEnabled() { return CinderConfigHolder.get().citEnabled(); }
        void setCitEnabled(boolean value) { update(b -> b.citEnabled(value)); }
        boolean getCustomGuiEnabled() { return CinderConfigHolder.get().customGuiEnabled(); }
        void setCustomGuiEnabled(boolean value) { update(b -> b.customGuiEnabled(value)); }
        boolean getCustomColorsEnabled() { return CinderConfigHolder.get().customColorsEnabled(); }
        void setCustomColorsEnabled(boolean value) { update(b -> b.customColorsEnabled(value)); }
        boolean getCustomSkyEnabled() { return CinderConfigHolder.get().customSkyEnabled(); }
        void setCustomSkyEnabled(boolean value) { update(b -> b.customSkyEnabled(value)); }
        boolean getNaturalTexturesEnabled() { return CinderConfigHolder.get().naturalTexturesEnabled(); }
        void setNaturalTexturesEnabled(boolean value) { update(b -> b.naturalTexturesEnabled(value)); }
        boolean getBetterSnowEnabled() { return CinderConfigHolder.get().betterSnowEnabled(); }
        void setBetterSnowEnabled(boolean value) { update(b -> b.betterSnowEnabled(value)); }
        boolean getCustomAnimationsEnabled() { return CinderConfigHolder.get().customAnimationsEnabled(); }
        void setCustomAnimationsEnabled(boolean value) { update(b -> b.customAnimationsEnabled(value)); }
        boolean getRandomEntitiesEnabled() { return CinderConfigHolder.get().randomEntitiesEnabled(); }
        void setRandomEntitiesEnabled(boolean value) { update(b -> b.randomEntitiesEnabled(value)); }
        boolean getCustomEntityModelsEnabled() { return CinderConfigHolder.get().customEntityModelsEnabled(); }
        void setCustomEntityModelsEnabled(boolean value) { update(b -> b.customEntityModelsEnabled(value)); }
        int getCustomAnimationMipmapDistance() { return CinderConfigHolder.get().customAnimationMipmapDistance(); }
        void setCustomAnimationMipmapDistance(int value) { update(b -> b.customAnimationMipmapDistance(value)); }
        boolean getDetailsSkyEnabled() { return CinderConfigHolder.get().detailsSkyEnabled(); }
        void setDetailsSkyEnabled(boolean value) { update(b -> b.detailsSkyEnabled(value)); }
        boolean getDetailsSunEnabled() { return CinderConfigHolder.get().detailsSunEnabled(); }
        void setDetailsSunEnabled(boolean value) { update(b -> b.detailsSunEnabled(value)); }
        boolean getDetailsMoonEnabled() { return CinderConfigHolder.get().detailsMoonEnabled(); }
        void setDetailsMoonEnabled(boolean value) { update(b -> b.detailsMoonEnabled(value)); }
        boolean getDetailsStarsEnabled() { return CinderConfigHolder.get().detailsStarsEnabled(); }
        void setDetailsStarsEnabled(boolean value) { update(b -> b.detailsStarsEnabled(value)); }
        boolean getDetailsCloudsEnabled() { return CinderConfigHolder.get().detailsCloudsEnabled(); }
        void setDetailsCloudsEnabled(boolean value) { update(b -> b.detailsCloudsEnabled(value)); }
        int getDetailsCloudHeight() { return CinderConfigHolder.get().detailsCloudHeight(); }
        void setDetailsCloudHeight(int value) { update(b -> b.detailsCloudHeight(value)); }
        boolean getDetailsRainSnowEnabled() { return CinderConfigHolder.get().detailsRainSnowEnabled(); }
        void setDetailsRainSnowEnabled(boolean value) { update(b -> b.detailsRainSnowEnabled(value)); }
        boolean getDetailsVignetteEnabled() { return CinderConfigHolder.get().detailsVignetteEnabled(); }
        void setDetailsVignetteEnabled(boolean value) { update(b -> b.detailsVignetteEnabled(value)); }
        boolean getAnimationsEnabled() { return CinderConfigHolder.get().animationsEnabled(); }
        void setAnimationsEnabled(boolean value) { update(b -> b.animationsEnabled(value)); }
        boolean getAnimationWater() { return CinderConfigHolder.get().animationWater(); }
        void setAnimationWater(boolean value) { update(b -> b.animationWater(value)); }
        boolean getAnimationLava() { return CinderConfigHolder.get().animationLava(); }
        void setAnimationLava(boolean value) { update(b -> b.animationLava(value)); }
        boolean getAnimationFire() { return CinderConfigHolder.get().animationFire(); }
        void setAnimationFire(boolean value) { update(b -> b.animationFire(value)); }
        boolean getAnimationPortal() { return CinderConfigHolder.get().animationPortal(); }
        void setAnimationPortal(boolean value) { update(b -> b.animationPortal(value)); }
        boolean getAnimationSculkSensor() { return CinderConfigHolder.get().animationSculkSensor(); }
        void setAnimationSculkSensor(boolean value) { update(b -> b.animationSculkSensor(value)); }
        boolean getAnimationBlocks() { return CinderConfigHolder.get().animationBlocks(); }
        void setAnimationBlocks(boolean value) { update(b -> b.animationBlocks(value)); }
        boolean getParticlesEnabled() { return CinderConfigHolder.get().particlesEnabled(); }
        void setParticlesEnabled(boolean value) { update(b -> b.particlesEnabled(value)); }
        boolean getParticleRainSplash() { return CinderConfigHolder.get().particleRainSplash(); }
        void setParticleRainSplash(boolean value) { update(b -> b.particleRainSplash(value)); }
        boolean getParticleBlockBreak() { return CinderConfigHolder.get().particleBlockBreak(); }
        void setParticleBlockBreak(boolean value) { update(b -> b.particleBlockBreak(value)); }
        boolean getParticleBlockBreaking() { return CinderConfigHolder.get().particleBlockBreaking(); }
        void setParticleBlockBreaking(boolean value) { update(b -> b.particleBlockBreaking(value)); }
        boolean getParticleExplosion() { return CinderConfigHolder.get().particleExplosion(); }
        void setParticleExplosion(boolean value) { update(b -> b.particleExplosion(value)); }
        boolean getParticleWater() { return CinderConfigHolder.get().particleWater(); }
        void setParticleWater(boolean value) { update(b -> b.particleWater(value)); }
        boolean getParticleSmoke() { return CinderConfigHolder.get().particleSmoke(); }
        void setParticleSmoke(boolean value) { update(b -> b.particleSmoke(value)); }
        boolean getParticlePotion() { return CinderConfigHolder.get().particlePotion(); }
        void setParticlePotion(boolean value) { update(b -> b.particlePotion(value)); }
        boolean getParticlePortal() { return CinderConfigHolder.get().particlePortal(); }
        void setParticlePortal(boolean value) { update(b -> b.particlePortal(value)); }
        boolean getParticleFlame() { return CinderConfigHolder.get().particleFlame(); }
        void setParticleFlame(boolean value) { update(b -> b.particleFlame(value)); }
        boolean getParticleRedstone() { return CinderConfigHolder.get().particleRedstone(); }
        void setParticleRedstone(boolean value) { update(b -> b.particleRedstone(value)); }
        boolean getParticleDripping() { return CinderConfigHolder.get().particleDripping(); }
        void setParticleDripping(boolean value) { update(b -> b.particleDripping(value)); }
        boolean getParticleFirework() { return CinderConfigHolder.get().particleFirework(); }
        void setParticleFirework(boolean value) { update(b -> b.particleFirework(value)); }
        boolean getFogEnabled() { return CinderConfigHolder.get().fogEnabled(); }
        void setFogEnabled(boolean value) { update(b -> b.fogEnabled(value)); }
        boolean getFogWater() { return CinderConfigHolder.get().fogWater(); }
        void setFogWater(boolean value) { update(b -> b.fogWater(value)); }
        boolean getFogLava() { return CinderConfigHolder.get().fogLava(); }
        void setFogLava(boolean value) { update(b -> b.fogLava(value)); }
        boolean getFogPowderSnow() { return CinderConfigHolder.get().fogPowderSnow(); }
        void setFogPowderSnow(boolean value) { update(b -> b.fogPowderSnow(value)); }
        boolean getFogAir() { return CinderConfigHolder.get().fogAir(); }
        void setFogAir(boolean value) { update(b -> b.fogAir(value)); }
        boolean getEntityShadowsEnabled() { return CinderConfigHolder.get().entityShadowsEnabled(); }
        void setEntityShadowsEnabled(boolean value) { update(b -> b.entityShadowsEnabled(value)); }
        boolean getEntityNameTagsEnabled() { return CinderConfigHolder.get().entityNameTagsEnabled(); }
        void setEntityNameTagsEnabled(boolean value) { update(b -> b.entityNameTagsEnabled(value)); }
        boolean getEntityPlayerNameTags() { return CinderConfigHolder.get().entityPlayerNameTags(); }
        void setEntityPlayerNameTags(boolean value) { update(b -> b.entityPlayerNameTags(value)); }
        boolean getEntityItemFrames() { return CinderConfigHolder.get().entityItemFrames(); }
        void setEntityItemFrames(boolean value) { update(b -> b.entityItemFrames(value)); }
        boolean getEntityPaintings() { return CinderConfigHolder.get().entityPaintings(); }
        void setEntityPaintings(boolean value) { update(b -> b.entityPaintings(value)); }
        boolean getEntityPistonAnimations() { return CinderConfigHolder.get().entityPistonAnimations(); }
        void setEntityPistonAnimations(boolean value) { update(b -> b.entityPistonAnimations(value)); }
        boolean getEntityBeaconBeam() { return CinderConfigHolder.get().entityBeaconBeam(); }
        void setEntityBeaconBeam(boolean value) { update(b -> b.entityBeaconBeam(value)); }
        boolean getEntityLimitBeaconBeamHeight() { return CinderConfigHolder.get().entityLimitBeaconBeamHeight(); }
        void setEntityLimitBeaconBeamHeight(boolean value) { update(b -> b.entityLimitBeaconBeamHeight(value)); }
        boolean getEntityEnchantingTableBook() { return CinderConfigHolder.get().entityEnchantingTableBook(); }
        void setEntityEnchantingTableBook(boolean value) { update(b -> b.entityEnchantingTableBook(value)); }
        boolean getShowFps() { return CinderConfigHolder.get().showFps(); }
        void setShowFps(boolean value) { update(b -> b.showFps(value)); }
        boolean getShowFpsExtended() { return CinderConfigHolder.get().showFpsExtended(); }
        void setShowFpsExtended(boolean value) { update(b -> b.showFpsExtended(value)); }
        boolean getShowCoords() { return CinderConfigHolder.get().showCoords(); }
        void setShowCoords(boolean value) { update(b -> b.showCoords(value)); }
        OverlayCorner getOverlayCorner() { return CinderConfigHolder.get().overlayCorner(); }
        void setOverlayCorner(OverlayCorner value) { update(b -> b.overlayCorner(value)); }
        TextContrast getTextContrast() { return CinderConfigHolder.get().textContrast(); }
        void setTextContrast(TextContrast value) { update(b -> b.textContrast(value)); }
        boolean getSteadyDebugHud() { return CinderConfigHolder.get().steadyDebugHud(); }
        void setSteadyDebugHud(boolean value) { update(b -> b.steadyDebugHud(value)); }
        int getSteadyDebugHudRefreshInterval() { return CinderConfigHolder.get().steadyDebugHudRefreshInterval(); }
        void setSteadyDebugHudRefreshInterval(int value) { update(b -> b.steadyDebugHudRefreshInterval(value)); }
        boolean getToastAdvancement() { return CinderConfigHolder.get().toastAdvancement(); }
        void setToastAdvancement(boolean value) { update(b -> b.toastAdvancement(value)); }
        boolean getToastRecipe() { return CinderConfigHolder.get().toastRecipe(); }
        void setToastRecipe(boolean value) { update(b -> b.toastRecipe(value)); }
        boolean getToastSystem() { return CinderConfigHolder.get().toastSystem(); }
        void setToastSystem(boolean value) { update(b -> b.toastSystem(value)); }
        boolean getToastTutorial() { return CinderConfigHolder.get().toastTutorial(); }
        void setToastTutorial(boolean value) { update(b -> b.toastTutorial(value)); }
        boolean getInstantSneak() { return CinderConfigHolder.get().instantSneak(); }
        void setInstantSneak(boolean value) { update(b -> b.instantSneak(value)); }
        FullscreenMode getFullscreenMode() { return CinderConfigHolder.get().fullscreenMode(); }
        void setFullscreenMode(FullscreenMode value) { update(b -> b.fullscreenMode(value)); }
        boolean getBiomeColorsEnabled() { return CinderConfigHolder.get().biomeColorsEnabled(); }
        void setBiomeColorsEnabled(boolean value) { update(b -> b.biomeColorsEnabled(value)); }
        boolean getSkyColorsEnabled() { return CinderConfigHolder.get().skyColorsEnabled(); }
        void setSkyColorsEnabled(boolean value) { update(b -> b.skyColorsEnabled(value)); }
        BetterGrassMode getBetterGrassMode() { return CinderConfigHolder.get().betterGrassMode(); }
        void setBetterGrassMode(BetterGrassMode value) { update(b -> b.betterGrassMode(value)); }
        boolean getBetterGrassIgnoreResourcePack() { return CinderConfigHolder.get().betterGrassIgnoreResourcePack(); }
        void setBetterGrassIgnoreResourcePack(boolean value) { update(b -> b.betterGrassIgnoreResourcePack(value)); }
        boolean getBetterGrassGrassBlock() { return CinderConfigHolder.get().betterGrassGrassBlock(); }
        void setBetterGrassGrassBlock(boolean value) { update(b -> b.betterGrassGrassBlock(value)); }
        boolean getBetterGrassSnowyGrassBlock() { return CinderConfigHolder.get().betterGrassSnowyGrassBlock(); }
        void setBetterGrassSnowyGrassBlock(boolean value) { update(b -> b.betterGrassSnowyGrassBlock(value)); }
        boolean getBetterGrassDirtPath() { return CinderConfigHolder.get().betterGrassDirtPath(); }
        void setBetterGrassDirtPath(boolean value) { update(b -> b.betterGrassDirtPath(value)); }
        boolean getBetterGrassFarmland() { return CinderConfigHolder.get().betterGrassFarmland(); }
        void setBetterGrassFarmland(boolean value) { update(b -> b.betterGrassFarmland(value)); }
        boolean getBetterGrassMycelium() { return CinderConfigHolder.get().betterGrassMycelium(); }
        void setBetterGrassMycelium(boolean value) { update(b -> b.betterGrassMycelium(value)); }
        boolean getBetterGrassPodzol() { return CinderConfigHolder.get().betterGrassPodzol(); }
        void setBetterGrassPodzol(boolean value) { update(b -> b.betterGrassPodzol(value)); }
        boolean getBetterGrassCrimsonNylium() { return CinderConfigHolder.get().betterGrassCrimsonNylium(); }
        void setBetterGrassCrimsonNylium(boolean value) { update(b -> b.betterGrassCrimsonNylium(value)); }
        boolean getBetterGrassWarpedNylium() { return CinderConfigHolder.get().betterGrassWarpedNylium(); }
        void setBetterGrassWarpedNylium(boolean value) { update(b -> b.betterGrassWarpedNylium(value)); }
    }
}
