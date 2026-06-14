package com.cinder.fabric.natural;

import com.cinder.Constants;
import com.cinder.natural.NaturalTextureParseResult;
import com.cinder.natural.NaturalTextureProperties;
import com.cinder.natural.NaturalTextureRuleSet;
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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Fabric reload bridge for OptiFine Natural Textures.
 *
 * <p>Threading: parse on prepare executor, publish immutable snapshot after
 * reload barrier.
 */
public final class NaturalTexturesReloadListener implements
        PreparableReloadListener,
        IdentifiableResourceReloadListener {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(Constants.MOD_ID + "/natural-reload");
    private static final Identifier NATURAL_PROPERTIES =
            Identifier.fromNamespaceAndPath("minecraft",
                    "optifine/natural.properties");

    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(Constants.MOD_ID,
                    "natural_textures_reload");

    public static void register() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)
                .registerReloadListener(new NaturalTexturesReloadListener());
        LOGGER.info("[{}] Natural Textures reload listener registered",
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
        ResourceManager manager = currentReload.resourceManager();
        return CompletableFuture.supplyAsync(() -> load(manager), taskExecutor)
                .thenCompose(preparationBarrier::wait)
                .thenAcceptAsync(NaturalTexturesRuntime::replace,
                        reloadExecutor);
    }

    private static NaturalTextureRuleSet load(ResourceManager manager) {
        Optional<Resource> resource = manager.getResource(NATURAL_PROPERTIES);
        if (resource.isEmpty()) {
            NaturalTextureRuleSet defaults = NaturalTextureProperties.defaults();
            LOGGER.info("[{}] Natural Textures reload: {} built-in rules",
                    Constants.MOD_NAME, defaults.size());
            return defaults;
        }
        try (var in = resource.get().open();
             var reader = new InputStreamReader(in,
                     StandardCharsets.UTF_8)) {
            NaturalTextureParseResult result = NaturalTextureProperties.parse(
                    readAll(reader), NATURAL_PROPERTIES.toString());
            for (NaturalTextureParseResult.Error error : result.errors()) {
                LOGGER.warn("[{}] malformed Natural Textures key {} in {} "
                                + "({}): {}",
                        Constants.MOD_NAME, error.key(), error.sourceFile(),
                        error.value(), error.message());
            }
            NaturalTextureRuleSet ruleSet =
                    NaturalTextureRuleSet.of(result.rules());
            LOGGER.info("[{}] Natural Textures reload: {} rules",
                    Constants.MOD_NAME, ruleSet.size());
            return ruleSet;
        } catch (Exception e) {
            LOGGER.warn("[{}] failed to read {}: {}",
                    Constants.MOD_NAME, NATURAL_PROPERTIES, e.getMessage());
            return NaturalTextureRuleSet.empty();
        }
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
