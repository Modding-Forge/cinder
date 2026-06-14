package com.cinder.fabric.cit;

import com.cinder.Constants;
import com.cinder.cit.CitParseResult;
import com.cinder.cit.CitRuleParser;
import com.cinder.cit.CitRuleSet;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Fabric resource reload bridge for OptiFine item CIT rules.
 *
 * <p>Purpose: scans active resource packs for
 * {@code assets/<namespace>/optifine/cit/.../*.properties}, parses them in shared code,
 * resolves item ids into a Fabric hotpath snapshot, and publishes it
 * atomically.
 *
 * <p>Threading: file I/O and parsing run on the prepare executor; publication
 * runs after the reload barrier.
 */
public final class CitReloadListener implements PreparableReloadListener,
        IdentifiableResourceReloadListener {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(Constants.MOD_ID + "/cit-reload");
    private static final String OPTIFINE_CIT = "optifine/cit";

    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(Constants.MOD_ID, "cit_reload");

    public static void register() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)
                .registerReloadListener(new CitReloadListener());
        LOGGER.info("[{}] CIT reload listener registered",
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
                .thenAcceptAsync(CitReloadListener::publish, reloadExecutor);
    }

    private static CitRuleSet load(ResourceManager resourceManager) {
        ArrayList<CitRuleParser.RuleSource> sources = new ArrayList<>();
        for (Identifier loc : resourceManager
                .listResources(OPTIFINE_CIT,
                        id -> id.getPath().endsWith(".properties"))
                .keySet()) {
            Optional<Resource> resource = resourceManager.getResource(loc);
            if (resource.isEmpty()) {
                continue;
            }
            try (var in = resource.get().open();
                 var reader = new InputStreamReader(
                         in, StandardCharsets.UTF_8)) {
                sources.add(new CitRuleParser.RuleSource(
                        readAll(reader), loc.toString()));
            } catch (Exception e) {
                LOGGER.warn("[{}] failed to read CIT file {}: {}",
                        Constants.MOD_NAME, loc, e.getMessage());
            }
        }
        CitParseResult parsed = CitRuleParser.parseAll(sources);
        for (CitParseResult.Error error : parsed.errors()) {
            LOGGER.warn("[{}] skipping malformed CIT file {}: {}",
                    Constants.MOD_NAME, error.sourceFile(), error.message());
        }
        CitRuleSet ruleSet = CitRuleSet.of(parsed.rules());
        LOGGER.info("[{}] CIT reload: {} parsed rules from {} files",
                Constants.MOD_NAME, ruleSet.all().size(), sources.size());
        return ruleSet;
    }

    private static void publish(CitRuleSet ruleSet) {
        CitClientSnapshot snapshot = CitClientSnapshot.from(ruleSet);
        CitRuntime.replace(snapshot);
        if (FabricLoader.getInstance().isModLoaded("citresewn")) {
            LOGGER.warn("[{}] CIT Resewn detected; Cinder CIT snapshot was "
                            + "loaded but runtime selection is disabled",
                    Constants.MOD_NAME);
        }
        LOGGER.info("[{}] CIT snapshot installed: {} shared rules, active={}",
                Constants.MOD_NAME, ruleSet.all().size(),
                !snapshot.isEmpty());
    }

    private static String readAll(java.io.Reader reader)
            throws java.io.IOException {
        StringBuilder out = new StringBuilder();
        char[] buf = new char[1024];
        int n;
        while ((n = reader.read(buf)) > 0) {
            out.append(buf, 0, n);
        }
        return out.toString();
    }
}
