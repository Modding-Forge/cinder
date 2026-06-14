package com.cinder.fabric.bettergrass;

import com.cinder.Constants;
import com.cinder.bettergrass.BetterGrassFamily;
import com.cinder.bettergrass.BetterGrassProperties;
import com.cinder.bettergrass.BetterGrassRules;
import com.cinder.resource.NamespaceId;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Fabric resource reload bridge for OptiFine {@code bettergrass.properties}.
 *
 * <p>Threading: parsing runs on the reload prepare executor; publication runs
 * after the preparation barrier through atomic shared state replacement.
 */
public final class BetterGrassReloadListener implements
        PreparableReloadListener,
        IdentifiableResourceReloadListener {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(Constants.MOD_ID + "/better-grass-reload");
    private static final Identifier BETTER_GRASS_PROPERTIES =
            Identifier.fromNamespaceAndPath(
                    "minecraft", "optifine/bettergrass.properties");
    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(
                    Constants.MOD_ID, "better_grass_reload");

    /**
     * Registers the Better Grass resource reload listener.
     */
    public static void register() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)
                .registerReloadListener(new BetterGrassReloadListener());
        LOGGER.info("[{}] Better Grass reload listener registered",
                Constants.MOD_NAME);
    }

    @Override
    @SuppressWarnings("deprecation")
    public Identifier getFabricId() {
        return ID;
    }

    @Override
    public CompletableFuture<Void> reload(
            SharedState currentReload,
            Executor taskExecutor,
            PreparationBarrier preparationBarrier,
            Executor reloadExecutor) {
        ResourceManager resourceManager = currentReload.resourceManager();
        return CompletableFuture
                .supplyAsync(() -> load(resourceManager), taskExecutor)
                .thenCompose(preparationBarrier::wait)
                .thenAcceptAsync(BetterGrassReloadListener::publish,
                        reloadExecutor);
    }

    private static BetterGrassRules load(ResourceManager resourceManager) {
        Optional<Resource> resource =
                resourceManager.getResource(BETTER_GRASS_PROPERTIES);
        if (resource.isEmpty()) {
            return null;
        }
        try (var in = resource.get().open();
             var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            BetterGrassRules rules = validateTextures(
                    BetterGrassProperties.parse(reader), resourceManager);
            LOGGER.info("[{}] loaded {}", Constants.MOD_NAME,
                    BETTER_GRASS_PROPERTIES);
            return rules;
        } catch (Exception e) {
            LOGGER.warn("[{}] failed to read {}; using Cinder defaults: {}",
                    Constants.MOD_NAME, BETTER_GRASS_PROPERTIES,
                    e.getMessage());
            return null;
        }
    }

    private static void publish(BetterGrassRules rules) {
        BetterGrassRules.replaceResourcePackRules(rules);
        if (rules == null) {
            LOGGER.info("[{}] no bettergrass.properties active; using Cinder "
                    + "Better Grass config", Constants.MOD_NAME);
        } else {
            LOGGER.info("[{}] Better Grass resource-pack rules installed",
                    Constants.MOD_NAME);
        }
        requestTerrainRebuild();
    }

    private static BetterGrassRules validateTextures(
            BetterGrassRules rules,
            ResourceManager resourceManager) {
        return new BetterGrassRules(
                rules.enabled(BetterGrassFamily.GRASS),
                rules.enabled(BetterGrassFamily.GRASS_SNOW),
                rules.enabled(BetterGrassFamily.DIRT_PATH),
                rules.enabled(BetterGrassFamily.FARMLAND),
                rules.enabled(BetterGrassFamily.MYCELIUM),
                rules.enabled(BetterGrassFamily.MYCELIUM_SNOW),
                rules.enabled(BetterGrassFamily.PODZOL),
                rules.enabled(BetterGrassFamily.PODZOL_SNOW),
                rules.enabled(BetterGrassFamily.CRIMSON_NYLIUM),
                rules.enabled(BetterGrassFamily.WARPED_NYLIUM),
                rules.grassMultilayer(),
                validateTexture(resourceManager, rules.textureGrass(),
                        BetterGrassRules.GRASS_TEXTURE, "texture.grass"),
                validateTexture(resourceManager, rules.textureGrassSide(),
                        BetterGrassRules.GRASS_SIDE_TEXTURE,
                        "texture.grass_side"),
                validateTexture(resourceManager,
                        rules.texture(BetterGrassFamily.DIRT_PATH),
                        BetterGrassRules.DIRT_PATH_TEXTURE,
                        "texture.dirt_path"),
                validateTexture(resourceManager, rules.textureDirtPathSide(),
                        BetterGrassRules.DIRT_PATH_SIDE_TEXTURE,
                        "texture.dirt_path_side"),
                validateTexture(resourceManager,
                        rules.texture(BetterGrassFamily.FARMLAND),
                        BetterGrassRules.FARMLAND_TEXTURE,
                        "texture.farmland"),
                validateTexture(resourceManager, rules.textureFarmlandSide(),
                        BetterGrassRules.FARMLAND_SIDE_TEXTURE,
                        "texture.farmland_side"),
                validateTexture(resourceManager,
                        rules.texture(BetterGrassFamily.MYCELIUM),
                        BetterGrassRules.MYCELIUM_TEXTURE,
                        "texture.mycelium"),
                validateTexture(resourceManager,
                        rules.texture(BetterGrassFamily.PODZOL),
                        BetterGrassRules.PODZOL_TEXTURE,
                        "texture.podzol"),
                validateTexture(resourceManager,
                        rules.texture(BetterGrassFamily.CRIMSON_NYLIUM),
                        BetterGrassRules.CRIMSON_NYLIUM_TEXTURE,
                        "texture.crimson_nylium"),
                validateTexture(resourceManager,
                        rules.texture(BetterGrassFamily.WARPED_NYLIUM),
                        BetterGrassRules.WARPED_NYLIUM_TEXTURE,
                        "texture.warped_nylium"),
                validateTexture(resourceManager, rules.textureSnow(),
                        BetterGrassRules.SNOW_TEXTURE, "texture.snow"));
    }

    private static NamespaceId validateTexture(ResourceManager resourceManager,
                                               NamespaceId candidate,
                                               NamespaceId fallback,
                                               String key) {
        if (textureExists(resourceManager, candidate)) {
            return candidate;
        }
        LOGGER.warn("[{}] {}={} does not resolve to a texture; using {}",
                Constants.MOD_NAME, key, candidate, fallback);
        return fallback;
    }

    private static boolean textureExists(ResourceManager resourceManager,
                                         NamespaceId texture) {
        Identifier resourceId = Identifier.fromNamespaceAndPath(
                texture.namespace(), "textures/" + texture.path() + ".png");
        return resourceManager.getResource(resourceId).isPresent();
    }

    private static void requestTerrainRebuild() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        minecraft.levelRenderer.invalidateCompiledGeometry(
                minecraft.level,
                minecraft.options,
                minecraft.gameRenderer.mainCamera(),
                minecraft.getBlockColors());
        LOGGER.info("[{}] requested terrain rebuild after Better Grass reload",
                Constants.MOD_NAME);
    }
}
