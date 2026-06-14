package com.cinder.fabric.customsky;

import com.cinder.Constants;
import com.cinder.customsky.CustomSkyParseResult;
import com.cinder.customsky.CustomSkyProperties;
import com.cinder.customsky.CustomSkyRuleSet;
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
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fabric resource reload bridge for OptiFine Custom Sky layers.
 *
 * <p>Threading: discovery and parsing run on the prepare executor; a fully
 * immutable snapshot is atomically published after the reload barrier.
 */
public final class CustomSkyReloadListener implements
        PreparableReloadListener,
        IdentifiableResourceReloadListener {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(Constants.MOD_ID + "/custom-sky-reload");
    private static final String OPTIFINE_SKY = "optifine/sky";
    private static final String MCPATCHER_SKY = "mcpatcher/sky";
    private static final Pattern SKY_PATH = Pattern.compile(
            "(optifine|mcpatcher)/sky/(world-?\\d+)/sky(\\d+)\\.properties$");

    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(Constants.MOD_ID,
                    "custom_sky_reload");

    public static void register() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)
                .registerReloadListener(new CustomSkyReloadListener());
        LOGGER.info("[{}] Custom Sky reload listener registered",
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
                .thenAcceptAsync(CustomSkyReloadListener::publish,
                        reloadExecutor);
    }

    private static CustomSkyClientSnapshot load(ResourceManager manager) {
        LinkedHashMap<String, CustomSkyProperties.RuleSource> sources =
                new LinkedHashMap<>();
        collect(manager, MCPATCHER_SKY, false, sources);
        collect(manager, OPTIFINE_SKY, true, sources);
        CustomSkyParseResult parsed = CustomSkyProperties.parseAll(
                java.util.List.copyOf(sources.values()));
        for (CustomSkyParseResult.Error error : parsed.errors()) {
            LOGGER.warn("[{}] skipping malformed Custom Sky layer {}: {}",
                    Constants.MOD_NAME, error.sourceFile(), error.message());
        }
        CustomSkyRuleSet ruleSet = CustomSkyRuleSet.of(parsed.layers());
        CustomSkyClientSnapshot snapshot = CustomSkyClientSnapshot.from(
                ruleSet, manager, LOGGER);
        LOGGER.info("[{}] Custom Sky reload: {} parsed layers from {} files, "
                        + "{} runtime layers",
                Constants.MOD_NAME, ruleSet.all().length, sources.size(),
                snapshot.size());
        return snapshot;
    }

    private static void collect(ResourceManager manager,
                                String root,
                                boolean overwrite,
                                LinkedHashMap<String,
                                        CustomSkyProperties.RuleSource> out) {
        for (Identifier loc : manager
                .listResources(root,
                        id -> id.getPath().endsWith(".properties"))
                .keySet()) {
            Matcher matcher = SKY_PATH.matcher(loc.getPath());
            if (!matcher.find()) {
                continue;
            }
            String key = loc.getNamespace() + ":" + matcher.group(2)
                    + "/sky" + matcher.group(3);
            if (!overwrite && out.containsKey(key)) {
                continue;
            }
            Optional<Resource> resource = manager.getResource(loc);
            if (resource.isEmpty()) {
                continue;
            }
            try (var in = resource.get().open();
                 var reader = new InputStreamReader(
                         in, StandardCharsets.UTF_8)) {
                out.put(key, new CustomSkyProperties.RuleSource(
                        readAll(reader), loc.toString()));
            } catch (Exception e) {
                LOGGER.warn("[{}] failed to read Custom Sky file {}: {}",
                        Constants.MOD_NAME, loc, e.getMessage());
            }
        }
    }

    private static void publish(CustomSkyClientSnapshot snapshot) {
        CustomSkyRuntime.replace(snapshot);
        LOGGER.info("[{}] Custom Sky snapshot installed: active={}",
                Constants.MOD_NAME, !snapshot.isEmpty());
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
