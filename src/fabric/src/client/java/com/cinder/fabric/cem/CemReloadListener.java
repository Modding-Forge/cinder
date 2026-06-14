package com.cinder.fabric.cem;

import com.cinder.Constants;
import com.cinder.cem.CemModel;
import com.cinder.cem.CemParseResult;
import com.cinder.cem.CemParser;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Fabric resource reload bridge for Custom Entity Models.
 *
 * <p>Purpose: scans active packs for OptiFine/EMF-style {@code .jem} files,
 * delegates parsing to shared clean-room code, and publishes an immutable
 * smoke-ready client snapshot.
 *
 * <p>Threading: resource I/O and parsing run on the prepare executor;
 * publication happens after the reload barrier.
 */
public final class CemReloadListener
        implements PreparableReloadListener, IdentifiableResourceReloadListener {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(Constants.MOD_ID + "/cem");
    private static final String ROOT = "optifine/cem";

    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(Constants.MOD_ID, "cem_reload");

    public static void register() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)
                .registerReloadListener(new CemReloadListener());
        LOGGER.info("[{}] CEM reload listener registered", Constants.MOD_NAME);
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
        ResourceManager manager = currentReload.resourceManager();
        return CompletableFuture
                .supplyAsync(() -> load(manager), taskExecutor)
                .thenCompose(preparationBarrier::wait)
                .thenAcceptAsync(CemReloadListener::publish, reloadExecutor);
    }

    private static CemRuntime.Snapshot load(ResourceManager manager) {
        ArrayList<CemParser.Source> sources = new ArrayList<>();
        for (Identifier id : manager.listResources(ROOT,
                loc -> loc.getPath().endsWith(".jem")).keySet()) {
            try (var in = manager.getResource(id).orElseThrow().open();
                 var reader = new InputStreamReader(in,
                         StandardCharsets.UTF_8)) {
                sources.add(new CemParser.Source(
                        id.getNamespace() + ":" + id.getPath(),
                        readAll(reader)));
            } catch (Exception e) {
                LOGGER.warn("[{}] failed to read CEM file {}: {}",
                        Constants.MOD_NAME, id, e.getMessage());
            }
        }

        CemParseResult result = CemParser.parseAll(sources);
        Map<String, CemModel> models = new LinkedHashMap<>();
        for (CemModel model : result.models()) {
            String key = key(model.sourcePath());
            if (!key.isBlank()) {
                models.put(key, model);
            }
        }
        for (CemParseResult.Error error : result.errors()) {
            LOGGER.warn("[{}] skipping malformed CEM file {}: {}",
                    Constants.MOD_NAME, error.sourcePath(), error.message());
        }
        LOGGER.info("[{}] CEM reload: {} models, {} errors",
                Constants.MOD_NAME, models.size(), result.errors().size());
        return new CemRuntime.Snapshot(models);
    }

    private static void publish(CemRuntime.Snapshot snapshot) {
        CemRuntime.replace(snapshot);
        if (FabricLoader.getInstance().isModLoaded("entity_model_features")) {
            LOGGER.warn("[{}] EMF detected; CEM snapshot was loaded but "
                            + "runtime selection is disabled",
                    Constants.MOD_NAME);
        }
        LOGGER.info("[{}] CEM snapshot installed: {} models, active={}",
                Constants.MOD_NAME, snapshot.models().size(),
                !snapshot.models().isEmpty());
    }

    private static String key(String sourcePath) {
        int slash = sourcePath.lastIndexOf('/');
        String name = slash < 0 ? sourcePath : sourcePath.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    private static String readAll(InputStreamReader reader) throws Exception {
        StringBuilder out = new StringBuilder();
        char[] buffer = new char[4096];
        int read;
        while ((read = reader.read(buffer)) >= 0) {
            out.append(buffer, 0, read);
        }
        return out.toString();
    }
}
