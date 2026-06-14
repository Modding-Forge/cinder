package com.cinder.fabric.customgui;

import com.cinder.Constants;
import com.cinder.customgui.CustomGuiParseResult;
import com.cinder.customgui.CustomGuiRuleParser;
import com.cinder.customgui.CustomGuiRuleSet;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
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
 * Fabric resource reload bridge for OptiFine Custom GUI rules.
 *
 * <p>Purpose: scans active resource packs for GUI properties files, parses
 * them in shared code, and publishes an immutable Fabric snapshot.
 *
 * <p>Threading: I/O and parsing run on the prepare executor; publication runs
 * after the reload barrier.
 */
public final class CustomGuiReloadListener implements PreparableReloadListener,
        IdentifiableResourceReloadListener {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(Constants.MOD_ID + "/custom-gui-reload");
    private static final String OPTIFINE_GUI_CONTAINER =
            "optifine/gui/container";

    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(Constants.MOD_ID,
                    "custom_gui_reload");

    public static void register() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)
                .registerReloadListener(new CustomGuiReloadListener());
        LOGGER.info("[{}] Custom GUI reload listener registered",
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
                .thenAcceptAsync(CustomGuiReloadListener::publish,
                        reloadExecutor);
    }

    private static CustomGuiRuleSet load(ResourceManager resourceManager) {
        ArrayList<CustomGuiRuleParser.RuleSource> sources = new ArrayList<>();
        for (Identifier loc : resourceManager
                .listResources(OPTIFINE_GUI_CONTAINER,
                        id -> id.getPath().endsWith(".properties"))
                .keySet()) {
            Optional<Resource> resource = resourceManager.getResource(loc);
            if (resource.isEmpty()) {
                continue;
            }
            try (var in = resource.get().open();
                 var reader = new InputStreamReader(
                         in, StandardCharsets.UTF_8)) {
                sources.add(new CustomGuiRuleParser.RuleSource(
                        readAll(reader), loc.toString()));
            } catch (Exception e) {
                LOGGER.warn("[{}] failed to read Custom GUI file {}: {}",
                        Constants.MOD_NAME, loc, e.getMessage());
            }
        }
        CustomGuiParseResult parsed = CustomGuiRuleParser.parseAll(sources);
        for (CustomGuiParseResult.Error error : parsed.errors()) {
            LOGGER.warn("[{}] skipping malformed Custom GUI file {}: {}",
                    Constants.MOD_NAME, error.sourceFile(), error.message());
        }
        CustomGuiRuleSet ruleSet = CustomGuiRuleSet.of(parsed.rules());
        LOGGER.info("[{}] Custom GUI reload: {} parsed rules from {} files",
                Constants.MOD_NAME, ruleSet.all().size(), sources.size());
        return ruleSet;
    }

    private static void publish(CustomGuiRuleSet ruleSet) {
        CustomGuiClientSnapshot snapshot = CustomGuiClientSnapshot.from(ruleSet);
        CustomGuiRuntime.replace(snapshot);
        LOGGER.info("[{}] Custom GUI snapshot installed: {} shared rules, active={}",
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
