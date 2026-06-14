package com.cinder.fabric.emissive;

import com.cinder.Constants;
import com.cinder.emissive.EmissiveProperties;
import com.cinder.emissive.EmissiveSettings;
import com.cinder.emissive.EmissiveSpriteTable;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Fabric resource reload bridge for OptiFine emissive textures.
 *
 * <p>Threading: file discovery and parsing happen on the prepare executor;
 * publication is one atomic table swap after the reload barrier.
 */
public final class EmissiveReloadListener implements
        PreparableReloadListener,
        IdentifiableResourceReloadListener {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(Constants.MOD_ID + "/emissive-reload");
    private static final Identifier EMISSIVE_PROPERTIES =
            Identifier.fromNamespaceAndPath(
                    "minecraft", "optifine/emissive.properties");
    private static final String OPTIFINE_CTM = "optifine/ctm";
    private static final String CONTINUITY_CTM = "continuity/ctm";
    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(
                    Constants.MOD_ID, "emissive_reload");

    /**
     * Registers the emissive reload listener.
     */
    public static void register() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)
                .registerReloadListener(new EmissiveReloadListener());
        LOGGER.info("[{}] Emissive reload listener registered",
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
                .thenAcceptAsync(EmissiveReloadListener::publish,
                        reloadExecutor);
    }

    private static EmissiveSpriteTable load(ResourceManager resourceManager) {
        Optional<Resource> resource =
                resourceManager.getResource(EMISSIVE_PROPERTIES);
        if (resource.isEmpty()) {
            return EmissiveSpriteTable.empty();
        }
        EmissiveSettings settings;
        try (var in = resource.get().open();
             var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            settings = EmissiveProperties.parse(reader);
        } catch (Exception e) {
            LOGGER.warn("[{}] failed to read {}; emissive disabled: {}",
                    Constants.MOD_NAME, EMISSIVE_PROPERTIES,
                    e.getMessage());
            return EmissiveSpriteTable.empty();
        }
        Map<NamespaceId, NamespaceId> mappings = collectMappings(
                resourceManager, settings.suffix());
        LOGGER.info("[{}] emissive reload: suffix={} mappings={}",
                Constants.MOD_NAME, settings.suffix(), mappings.size());
        return EmissiveSpriteTable.of(mappings);
    }

    private static Map<NamespaceId, NamespaceId> collectMappings(
            ResourceManager resourceManager,
            String suffix) {
        Map<NamespaceId, NamespaceId> mappings = new LinkedHashMap<>();
        Map<Identifier, Resource> textures;
        try {
            textures = resourceManager.listResources("textures",
                    id -> id.getPath().endsWith(suffix + ".png"));
        } catch (RuntimeException e) {
            return mappings;
        }
        for (Identifier textureResource : textures.keySet()) {
            String path = textureResource.getPath();
            String noPrefix = stripTexturePrefix(path);
            String noPng = stripPng(noPrefix);
            if (!noPng.endsWith(suffix)) {
                continue;
            }
            String basePath = noPng.substring(0,
                    noPng.length() - suffix.length());
            if (basePath.isEmpty()) {
                continue;
            }
            Identifier baseTexture = Identifier.fromNamespaceAndPath(
                    textureResource.getNamespace(),
                    "textures/" + basePath + ".png");
            if (resourceManager.getResource(baseTexture).isEmpty()) {
                continue;
            }
            mappings.put(
                    new NamespaceId(textureResource.getNamespace(), basePath),
                    new NamespaceId(textureResource.getNamespace(), noPng));
        }
        collectCtmMappings(resourceManager, suffix, OPTIFINE_CTM, mappings);
        collectCtmMappings(resourceManager, suffix, CONTINUITY_CTM, mappings);
        return mappings;
    }

    private static void collectCtmMappings(
            ResourceManager resourceManager,
            String suffix,
            String tree,
            Map<NamespaceId, NamespaceId> mappings) {
        Map<Identifier, Resource> resources;
        try {
            resources = resourceManager.listResources(tree,
                    id -> id.getPath().endsWith(suffix + ".png"));
        } catch (RuntimeException e) {
            return;
        }
        for (Identifier resource : resources.keySet()) {
            String noPng = stripPng(resource.getPath());
            if (!noPng.endsWith(suffix)) {
                continue;
            }
            String basePath = noPng.substring(0,
                    noPng.length() - suffix.length());
            if (basePath.isEmpty()) {
                continue;
            }
            Identifier baseResource = Identifier.fromNamespaceAndPath(
                    resource.getNamespace(), basePath + ".png");
            if (resourceManager.getResource(baseResource).isEmpty()) {
                continue;
            }
            mappings.put(
                    new NamespaceId("cinder", basePath),
                    new NamespaceId("cinder", noPng));
            if (isCompactSourceTile(basePath)) {
                addGeneratedCompactMappings(basePath, suffix, mappings);
            }
        }
    }

    private static boolean isCompactSourceTile(String basePath) {
        int slash = basePath.lastIndexOf('/');
        if (slash < 0 || slash == basePath.length() - 1) {
            return false;
        }
        String name = basePath.substring(slash + 1);
        return name.length() == 1 && name.charAt(0) >= '0'
                && name.charAt(0) <= '4';
    }

    private static void addGeneratedCompactMappings(
            String compactSourcePath,
            String suffix,
            Map<NamespaceId, NamespaceId> mappings) {
        int slash = compactSourcePath.lastIndexOf('/');
        String dir = compactSourcePath.substring(0, slash);
        for (int face = 0; face < 6; face++) {
            for (int tile = 1; tile < 47; tile++) {
                String generated = dir + "/generated_face_" + face
                        + "/" + tile;
                mappings.put(
                        new NamespaceId("cinder", generated),
                        new NamespaceId("cinder", generated + suffix));
            }
        }
    }

    private static void publish(EmissiveSpriteTable table) {
        EmissiveSpriteTable.replace(table);
        if (table == null || table.isEmpty()) {
            LOGGER.info("[{}] no emissive texture mappings active",
                    Constants.MOD_NAME);
        } else {
            LOGGER.info("[{}] emissive texture table installed: {} mappings",
                    Constants.MOD_NAME, table.size());
        }
        requestTerrainRebuild();
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
    }

    private static String stripTexturePrefix(String path) {
        return path.startsWith("textures/")
                ? path.substring("textures/".length())
                : path;
    }

    private static String stripPng(String path) {
        return path.endsWith(".png")
                ? path.substring(0, path.length() - 4)
                : path;
    }
}
