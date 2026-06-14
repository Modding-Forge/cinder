package com.cinder.bettergrass;

import com.cinder.config.CinderConfig;
import com.cinder.resource.NamespaceId;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Immutable Better Grass resource-pack rules and texture choices.
 *
 * <p>The active resource-pack snapshot is process-wide and atomically replaced
 * on resource reload. When no pack snapshot is present, callers derive the
 * effective rules from the current {@link CinderConfig}; this keeps Sodium menu
 * family toggles live without mutating renderer state.
 *
 * <p>Threading: instances are immutable. The holder uses atomic/volatile
 * publication; renderer reads are lock-free.
 *
 * <p>Performance: HOT PATH. The renderer reads primitive booleans and
 * pre-parsed {@link NamespaceId}s only.
 */
public final class BetterGrassRules {

    public static final NamespaceId GRASS_TEXTURE =
            new NamespaceId("minecraft", "block/grass_block_top");
    public static final NamespaceId GRASS_SIDE_TEXTURE =
            new NamespaceId("minecraft", "block/grass_block_side");
    public static final NamespaceId DIRT_PATH_TEXTURE =
            new NamespaceId("minecraft", "block/dirt_path_top");
    public static final NamespaceId DIRT_PATH_SIDE_TEXTURE =
            new NamespaceId("minecraft", "block/dirt_path_side");
    public static final NamespaceId FARMLAND_TEXTURE =
            new NamespaceId("minecraft", "block/farmland");
    public static final NamespaceId FARMLAND_SIDE_TEXTURE =
            new NamespaceId("minecraft", "block/dirt");
    public static final NamespaceId MYCELIUM_TEXTURE =
            new NamespaceId("minecraft", "block/mycelium_top");
    public static final NamespaceId PODZOL_TEXTURE =
            new NamespaceId("minecraft", "block/podzol_top");
    public static final NamespaceId CRIMSON_NYLIUM_TEXTURE =
            new NamespaceId("minecraft", "block/crimson_nylium");
    public static final NamespaceId WARPED_NYLIUM_TEXTURE =
            new NamespaceId("minecraft", "block/warped_nylium");
    public static final NamespaceId SNOW_TEXTURE =
            new NamespaceId("minecraft", "block/snow");

    private static final AtomicReference<BetterGrassRules> PACK_RULES =
            new AtomicReference<>();
    private static volatile CinderConfig cachedConfig;
    private static volatile BetterGrassRules cachedConfigRules;

    private final boolean grass;
    private final boolean grassSnow;
    private final boolean dirtPath;
    private final boolean farmland;
    private final boolean mycelium;
    private final boolean myceliumSnow;
    private final boolean podzol;
    private final boolean podzolSnow;
    private final boolean crimsonNylium;
    private final boolean warpedNylium;
    private final boolean grassMultilayer;
    private final NamespaceId textureGrass;
    private final NamespaceId textureGrassSide;
    private final NamespaceId textureDirtPath;
    private final NamespaceId textureDirtPathSide;
    private final NamespaceId textureFarmland;
    private final NamespaceId textureFarmlandSide;
    private final NamespaceId textureMycelium;
    private final NamespaceId texturePodzol;
    private final NamespaceId textureCrimsonNylium;
    private final NamespaceId textureWarpedNylium;
    private final NamespaceId textureSnow;

    public BetterGrassRules(
            boolean grass,
            boolean grassSnow,
            boolean dirtPath,
            boolean farmland,
            boolean mycelium,
            boolean myceliumSnow,
            boolean podzol,
            boolean podzolSnow,
            boolean crimsonNylium,
            boolean warpedNylium,
            boolean grassMultilayer,
            NamespaceId textureGrass,
            NamespaceId textureGrassSide,
            NamespaceId textureDirtPath,
            NamespaceId textureDirtPathSide,
            NamespaceId textureFarmland,
            NamespaceId textureFarmlandSide,
            NamespaceId textureMycelium,
            NamespaceId texturePodzol,
            NamespaceId textureCrimsonNylium,
            NamespaceId textureWarpedNylium,
            NamespaceId textureSnow) {
        this.grass = grass;
        this.grassSnow = grassSnow;
        this.dirtPath = dirtPath;
        this.farmland = farmland;
        this.mycelium = mycelium;
        this.myceliumSnow = myceliumSnow;
        this.podzol = podzol;
        this.podzolSnow = podzolSnow;
        this.crimsonNylium = crimsonNylium;
        this.warpedNylium = warpedNylium;
        this.grassMultilayer = grassMultilayer;
        this.textureGrass = Objects.requireNonNull(textureGrass,
                "textureGrass");
        this.textureGrassSide = Objects.requireNonNull(textureGrassSide,
                "textureGrassSide");
        this.textureDirtPath = Objects.requireNonNull(textureDirtPath,
                "textureDirtPath");
        this.textureDirtPathSide = Objects.requireNonNull(textureDirtPathSide,
                "textureDirtPathSide");
        this.textureFarmland = Objects.requireNonNull(textureFarmland,
                "textureFarmland");
        this.textureFarmlandSide = Objects.requireNonNull(textureFarmlandSide,
                "textureFarmlandSide");
        this.textureMycelium = Objects.requireNonNull(textureMycelium,
                "textureMycelium");
        this.texturePodzol = Objects.requireNonNull(texturePodzol,
                "texturePodzol");
        this.textureCrimsonNylium = Objects.requireNonNull(
                textureCrimsonNylium, "textureCrimsonNylium");
        this.textureWarpedNylium = Objects.requireNonNull(
                textureWarpedNylium, "textureWarpedNylium");
        this.textureSnow = Objects.requireNonNull(textureSnow, "textureSnow");
    }

    /**
     * Returns the effective snapshot for the current config.
     */
    public static BetterGrassRules current(CinderConfig config) {
        if (!config.betterGrassIgnoreResourcePack()) {
            BetterGrassRules pack = PACK_RULES.get();
            if (pack != null) {
                return pack;
            }
        }
        BetterGrassRules rules = cachedConfigRules;
        if (rules != null && cachedConfig == config) {
            return rules;
        }
        BetterGrassRules rebuilt = fromConfig(config);
        cachedConfig = config;
        cachedConfigRules = rebuilt;
        return rebuilt;
    }

    /**
     * Publishes a resource-pack override. Pass {@code null} to clear it.
     */
    public static void replaceResourcePackRules(BetterGrassRules rules) {
        PACK_RULES.set(rules);
    }

    /**
     * Returns {@code true} when a resource pack currently overrides family
     * flags and textures.
     */
    public static boolean hasResourcePackRules() {
        return PACK_RULES.get() != null;
    }

    /**
     * Builds the no-resource-pack fallback from Cinder's config toggles.
     */
    public static BetterGrassRules fromConfig(CinderConfig config) {
        return new BetterGrassRules(
                config.betterGrassGrassBlock(),
                config.betterGrassSnowyGrassBlock(),
                config.betterGrassDirtPath(),
                config.betterGrassFarmland(),
                config.betterGrassMycelium(),
                config.betterGrassSnowyGrassBlock(),
                config.betterGrassPodzol(),
                config.betterGrassSnowyGrassBlock(),
                config.betterGrassCrimsonNylium(),
                config.betterGrassWarpedNylium(),
                false,
                GRASS_TEXTURE,
                GRASS_SIDE_TEXTURE,
                DIRT_PATH_TEXTURE,
                DIRT_PATH_SIDE_TEXTURE,
                FARMLAND_TEXTURE,
                FARMLAND_SIDE_TEXTURE,
                MYCELIUM_TEXTURE,
                PODZOL_TEXTURE,
                CRIMSON_NYLIUM_TEXTURE,
                WARPED_NYLIUM_TEXTURE,
                SNOW_TEXTURE);
    }

    /**
     * Returns {@code true} when the family is enabled.
     */
    public boolean enabled(BetterGrassFamily family) {
        return switch (family) {
            case GRASS -> grass;
            case GRASS_SNOW -> grassSnow;
            case DIRT_PATH -> dirtPath;
            case FARMLAND -> farmland;
            case MYCELIUM -> mycelium;
            case MYCELIUM_SNOW -> myceliumSnow;
            case PODZOL -> podzol;
            case PODZOL_SNOW -> podzolSnow;
            case CRIMSON_NYLIUM -> crimsonNylium;
            case WARPED_NYLIUM -> warpedNylium;
        };
    }

    /**
     * Returns the target texture for the family.
     */
    public NamespaceId texture(BetterGrassFamily family) {
        return switch (family) {
            case GRASS -> textureGrass;
            case GRASS_SNOW, MYCELIUM_SNOW, PODZOL_SNOW -> textureSnow;
            case DIRT_PATH -> textureDirtPath;
            case FARMLAND -> textureFarmland;
            case MYCELIUM -> textureMycelium;
            case PODZOL -> texturePodzol;
            case CRIMSON_NYLIUM -> textureCrimsonNylium;
            case WARPED_NYLIUM -> textureWarpedNylium;
        };
    }

    public boolean grassMultilayer() {
        return grassMultilayer;
    }

    public NamespaceId textureGrass() {
        return textureGrass;
    }

    public NamespaceId textureGrassSide() {
        return textureGrassSide;
    }

    public NamespaceId textureDirtPath() {
        return textureDirtPath;
    }

    public NamespaceId textureDirtPathSide() {
        return textureDirtPathSide;
    }

    public NamespaceId textureFarmland() {
        return textureFarmland;
    }

    public NamespaceId textureFarmlandSide() {
        return textureFarmlandSide;
    }

    public NamespaceId textureSnow() {
        return textureSnow;
    }
}
