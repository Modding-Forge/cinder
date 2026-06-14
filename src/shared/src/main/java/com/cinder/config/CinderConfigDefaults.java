package com.cinder.config;

/**
 * Default values for the {@link CinderConfig}. The defaults
 * match OptiFine's defaults so that resource packs work out of
 * the box:
 * <ul>
 *   <li>{@code enabled} = true (master switch)</li>
 *   <li>{@code safe_mode} = false (let features run; we are
 *       not yet at risk of crashing the renderer)</li>
 *   <li>{@code verify_mode} = false (debug-only comparison
 *       off by default; turned on for development builds)</li>
 *   <li>{@code ctm.enabled} = true (CTM is the primary
 *       feature in Phase 4)</li>
 *   <li>{@code ctm.debug_logging} = false (hot-path CTM
 *       diagnostics are opt-in)</li>
 *   <li>{@code general.duplicate_translucent_backfaces} = false
 *       (translucent quads keep Sodium's normal single-sided output
 *       unless explicitly requested)</li>
 *   <li>{@code better_grass.mode} = fast (OptiFine-style full grass sides
 *       out of the box)</li>
 *   <li>{@code cit.enabled} = true (OptiFine item CIT is active unless a
 *       compatibility adapter disables it)</li>
 *   <li>{@code custom_gui.enabled} = true (OptiFine Custom GUI is active
 *       unless a compatibility adapter disables it)</li>
 *   <li>{@code custom_colors.enabled} = true (OptiFine Custom Colors is
 *       active unless Colormatic is present)</li>
 *   <li>{@code custom_sky.enabled} = true (OptiFine Custom Sky layers are
 *       active unless a compatibility adapter disables them)</li>
 *   <li>{@code natural_textures.enabled} = true (OptiFine Natural Textures
 *       are active for terrain quads)</li>
 *   <li>{@code better_snow.enabled} = true (OptiFine-style snow layer
 *       coverage is active for supported non-solid blocks)</li>
 *   <li>{@code custom_animations.enabled} = true (OptiFine custom texture
 *       animations are active unless a compatibility adapter disables them)</li>
 * </ul>
 *
 * <p>Performance: a single static field, allocated once.
 */
public final class CinderConfigDefaults {

    /** Master switch: turns the whole mod on or off. */
    public static final boolean ENABLED = true;

    /** Vanilla-fallback on pipeline / shader errors. */
    public static final boolean SAFE_MODE = false;

    /** OFF/ON comparison in DEBUG. Never on in release. */
    public static final boolean VERIFY_MODE = false;

    /** CTM feature toggle. */
    public static final boolean CTM_ENABLED = true;

    /** CTM hot-path diagnostic logging toggle. */
    public static final boolean CTM_DEBUG_LOGGING = false;

    /** Translucent replacement quads should emit a reversed backface copy. */
    public static final boolean DUPLICATE_TRANSLUCENT_BACKFACES = false;

    /** Better Grass mode. */
    public static final BetterGrassMode BETTER_GRASS_MODE =
            BetterGrassMode.FAST;

    /** Better Grass resource-pack override switch. */
    public static final boolean BETTER_GRASS_IGNORE_RESOURCE_PACK = false;

    /** Better Grass block family toggles. */
    public static final boolean BETTER_GRASS_GRASS_BLOCK = true;
    public static final boolean BETTER_GRASS_SNOWY_GRASS_BLOCK = true;
    public static final boolean BETTER_GRASS_DIRT_PATH = true;
    public static final boolean BETTER_GRASS_FARMLAND = true;
    public static final boolean BETTER_GRASS_MYCELIUM = true;
    public static final boolean BETTER_GRASS_PODZOL = true;
    public static final boolean BETTER_GRASS_CRIMSON_NYLIUM = true;
    public static final boolean BETTER_GRASS_WARPED_NYLIUM = true;

    /** Custom Item Textures feature toggle. */
    public static final boolean CIT_ENABLED = true;

    /** Custom GUI texture replacement feature toggle. */
    public static final boolean CUSTOM_GUI_ENABLED = true;

    /** Custom Colors and Colormaps feature toggle. */
    public static final boolean CUSTOM_COLORS_ENABLED = true;

    /** Custom Sky feature toggle. */
    public static final boolean CUSTOM_SKY_ENABLED = true;

    /** Natural Textures feature toggle. */
    public static final boolean NATURAL_TEXTURES_ENABLED = true;

    /** Better Snow feature toggle. */
    public static final boolean BETTER_SNOW_ENABLED = true;

    /** Custom texture animations feature toggle. */
    public static final boolean CUSTOM_ANIMATIONS_ENABLED = true;

    /** Random Entity Textures feature toggle. */
    public static final boolean RANDOM_ENTITIES_ENABLED = true;

    /** Custom Entity Models feature toggle. */
    public static final boolean CUSTOM_ENTITY_MODELS_ENABLED = true;

    /** Highest custom-animation mipmap level to update. */
    public static final int CUSTOM_ANIMATION_MIPMAP_DISTANCE = 4;

    /** Vanilla sky detail toggles. */
    public static final boolean DETAILS_SKY_ENABLED = true;
    public static final boolean DETAILS_SUN_ENABLED = true;
    public static final boolean DETAILS_MOON_ENABLED = true;
    public static final boolean DETAILS_STARS_ENABLED = true;
    public static final boolean DETAILS_CLOUDS_ENABLED = true;
    public static final int DETAILS_CLOUD_HEIGHT = 192;
    public static final boolean DETAILS_RAIN_SNOW_ENABLED = true;
    public static final boolean DETAILS_VIGNETTE_ENABLED = true;

    /** Vanilla atlas animation toggles. */
    public static final boolean ANIMATIONS_ENABLED = true;
    public static final boolean ANIMATION_WATER = true;
    public static final boolean ANIMATION_LAVA = true;
    public static final boolean ANIMATION_FIRE = true;
    public static final boolean ANIMATION_PORTAL = true;
    public static final boolean ANIMATION_SCULK_SENSOR = true;
    public static final boolean ANIMATION_BLOCKS = true;

    /** Coarse visual clutter toggles. */
    public static final boolean PARTICLES_ENABLED = true;
    public static final boolean PARTICLE_RAIN_SPLASH = true;
    public static final boolean PARTICLE_BLOCK_BREAK = true;
    public static final boolean PARTICLE_BLOCK_BREAKING = true;
    public static final boolean PARTICLE_EXPLOSION = true;
    public static final boolean PARTICLE_WATER = true;
    public static final boolean PARTICLE_SMOKE = true;
    public static final boolean PARTICLE_POTION = true;
    public static final boolean PARTICLE_PORTAL = true;
    public static final boolean PARTICLE_FLAME = true;
    public static final boolean PARTICLE_REDSTONE = true;
    public static final boolean PARTICLE_DRIPPING = true;
    public static final boolean PARTICLE_FIREWORK = true;
    public static final boolean FOG_ENABLED = true;
    public static final boolean FOG_WATER = true;
    public static final boolean FOG_LAVA = true;
    public static final boolean FOG_POWDER_SNOW = true;
    public static final boolean FOG_AIR = true;
    public static final boolean ENTITY_SHADOWS_ENABLED = true;
    public static final boolean ENTITY_NAME_TAGS_ENABLED = true;
    public static final boolean ENTITY_PLAYER_NAME_TAGS = true;
    public static final boolean ENTITY_ITEM_FRAMES = true;
    public static final boolean ENTITY_PAINTINGS = true;
    public static final boolean ENTITY_PISTON_ANIMATIONS = true;
    public static final boolean ENTITY_BEACON_BEAM = true;
    public static final boolean ENTITY_LIMIT_BEACON_BEAM_HEIGHT = false;
    public static final boolean ENTITY_ENCHANTING_TABLE_BOOK = true;
    public static final boolean SHOW_FPS = false;
    public static final boolean SHOW_FPS_EXTENDED = false;
    public static final boolean SHOW_COORDS = false;
    public static final OverlayCorner OVERLAY_CORNER = OverlayCorner.TOP_LEFT;
    public static final TextContrast TEXT_CONTRAST = TextContrast.SHADOW;
    public static final boolean STEADY_DEBUG_HUD = false;
    public static final int STEADY_DEBUG_HUD_REFRESH_INTERVAL = 10;
    public static final boolean TOAST_ADVANCEMENT = true;
    public static final boolean TOAST_RECIPE = true;
    public static final boolean TOAST_SYSTEM = true;
    public static final boolean TOAST_TUTORIAL = true;
    public static final boolean INSTANT_SNEAK = false;
    public static final FullscreenMode FULLSCREEN_MODE =
            FullscreenMode.WINDOWED;
    public static final boolean BIOME_COLORS_ENABLED = true;
    public static final boolean SKY_COLORS_ENABLED = true;

    private CinderConfigDefaults() {
    }

    /**
     * Returns the default configuration. Used by
     * {@link CinderConfigLoader} when the file is missing or
     * when a key has no explicit value.
     */
    public static CinderConfig defaults() {
        return new CinderConfig(ENABLED, SAFE_MODE, VERIFY_MODE, CTM_ENABLED,
                CTM_DEBUG_LOGGING, DUPLICATE_TRANSLUCENT_BACKFACES,
                BETTER_GRASS_MODE,
                BETTER_GRASS_IGNORE_RESOURCE_PACK,
                BETTER_GRASS_GRASS_BLOCK,
                BETTER_GRASS_SNOWY_GRASS_BLOCK,
                BETTER_GRASS_DIRT_PATH,
                BETTER_GRASS_FARMLAND,
                BETTER_GRASS_MYCELIUM,
                BETTER_GRASS_PODZOL,
                BETTER_GRASS_CRIMSON_NYLIUM,
                BETTER_GRASS_WARPED_NYLIUM,
                CIT_ENABLED,
                CUSTOM_GUI_ENABLED,
                CUSTOM_COLORS_ENABLED,
                CUSTOM_SKY_ENABLED,
                NATURAL_TEXTURES_ENABLED,
                BETTER_SNOW_ENABLED,
                CUSTOM_ANIMATIONS_ENABLED,
                RANDOM_ENTITIES_ENABLED,
                CUSTOM_ENTITY_MODELS_ENABLED,
                CUSTOM_ANIMATION_MIPMAP_DISTANCE,
                DETAILS_SKY_ENABLED,
                DETAILS_SUN_ENABLED,
                DETAILS_MOON_ENABLED,
                DETAILS_STARS_ENABLED,
                DETAILS_CLOUDS_ENABLED,
                DETAILS_CLOUD_HEIGHT,
                DETAILS_RAIN_SNOW_ENABLED,
                DETAILS_VIGNETTE_ENABLED,
                ANIMATIONS_ENABLED,
                ANIMATION_WATER,
                ANIMATION_LAVA,
                ANIMATION_FIRE,
                ANIMATION_PORTAL,
                ANIMATION_SCULK_SENSOR,
                ANIMATION_BLOCKS,
                PARTICLES_ENABLED,
                PARTICLE_RAIN_SPLASH,
                PARTICLE_BLOCK_BREAK,
                PARTICLE_BLOCK_BREAKING,
                PARTICLE_EXPLOSION,
                PARTICLE_WATER,
                PARTICLE_SMOKE,
                PARTICLE_POTION,
                PARTICLE_PORTAL,
                PARTICLE_FLAME,
                PARTICLE_REDSTONE,
                PARTICLE_DRIPPING,
                PARTICLE_FIREWORK,
                FOG_ENABLED,
                FOG_WATER,
                FOG_LAVA,
                FOG_POWDER_SNOW,
                FOG_AIR,
                ENTITY_SHADOWS_ENABLED,
                ENTITY_NAME_TAGS_ENABLED,
                ENTITY_PLAYER_NAME_TAGS,
                ENTITY_ITEM_FRAMES,
                ENTITY_PAINTINGS,
                ENTITY_PISTON_ANIMATIONS,
                ENTITY_BEACON_BEAM,
                ENTITY_LIMIT_BEACON_BEAM_HEIGHT,
                ENTITY_ENCHANTING_TABLE_BOOK,
                SHOW_FPS,
                SHOW_FPS_EXTENDED,
                SHOW_COORDS,
                OVERLAY_CORNER,
                TEXT_CONTRAST,
                STEADY_DEBUG_HUD,
                STEADY_DEBUG_HUD_REFRESH_INTERVAL,
                TOAST_ADVANCEMENT,
                TOAST_RECIPE,
                TOAST_SYSTEM,
                TOAST_TUTORIAL,
                INSTANT_SNEAK,
                FULLSCREEN_MODE,
                BIOME_COLORS_ENABLED,
                SKY_COLORS_ENABLED);
    }
}
