package com.cinder.fabric.ctm;

import com.cinder.Constants;
import com.cinder.ctm.CtmRegistry;
import com.cinder.ctm.CtmMaterialTable;
import com.cinder.ctm.CtmRule;
import com.cinder.ctm.CtmRuleParser;
import com.cinder.ctm.CtmRuleSet;
import com.cinder.ctm.CtmTileAtlas;
import com.cinder.ctm.CtmTileAtlasEntry;
import com.cinder.ctm.CtmTileResolver;
import com.cinder.platform.Platforms;
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Discovers OptiFine / Continuity CTM {@code .properties} files
 * across all loaded resource packs and feeds them into the
 * loader-agnostic {@link CtmRegistry}.
 *
 * <p>Resource paths scanned:
 * <ul>
 *   <li>{@code assets/&lt;ns&gt;/optifine/ctm/*.properties}</li>
 *   <li>{@code assets/&lt;ns&gt;/continuity/ctm/*.properties}</li>
 * </ul>
 *
 * <p>This is the only file in the project that knows about the
 * Minecraft reload-listener API; everything else about CTM lives in
 * the loader-agnostic {@code shared} module.
 *
 * <h2>Threading</h2>
 *
 * <p>The reload is split into two phases by Mojang's reload API: the
 * {@code prepare} executor does the file I/O and parsing, the
 * {@code apply} executor (render thread) does the atomic swap into
 * the registry. Both are called by the engine; we do not need to
 * synchronise beyond what the engine already provides.
 *
 * <h2>Performance</h2>
 *
 * <p>Resource discovery is O(n_files). Parsing each file is
 * O(file size). The final rule set is built in
 * {@link CtmRuleSet.Builder} which keeps an insertion-sort
 * priority; total cost is O(n log n) for n rules across all packs.
 */
public final class CtmReloadListener implements
        PreparableReloadListener,
        IdentifiableResourceReloadListener {
    // Phase 4.8: Fabric-API 0.150.3 deprecated
    // IdentifiableResourceReloadListener.getFabricId() in favour of
    // an unspecified newer API. The replacement API is not yet
    // stable across the supported Fabric versions, so we keep
    // the deprecated form and silence the warning. Migrate when
    // the replacement stabilises.

    private static final Logger LOGGER =
            LoggerFactory.getLogger(Constants.MOD_ID + "/ctm-reload");

    private static final String OPTIFINE_CTM = "optifine/ctm";
    private static final String CONTINUITY_CTM = "continuity/ctm";

    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(Constants.MOD_ID, "ctm_reload");

    /**
     * Registers this listener with the Fabric resource reload API.
     * Safe to call multiple times - it is idempotent.
     */
    public static void register() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)
                .registerReloadListener(new CtmReloadListener());
        LOGGER.info("[{}] CTM reload listener registered", Constants.MOD_NAME);
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
                .supplyAsync(() -> collectRules(resourceManager), taskExecutor)
                .thenCompose(preparationBarrier::wait)
                .thenAcceptAsync(
                        rules -> applyRules(rules, resourceManager),
                        reloadExecutor);
    }

    private List<CtmRuleParser.RuleSource> collectRules(ResourceManager resourceManager) {
        List<CtmRuleParser.RuleSource> out = new ArrayList<>();
        scan(resourceManager, OPTIFINE_CTM, out);
        scan(resourceManager, CONTINUITY_CTM, out);
        LOGGER.info("[{}] CTM reload: discovered {} rule files",
                Constants.MOD_NAME, out.size());
        return out;
    }

    private void scan(ResourceManager resourceManager, String folder,
                      List<CtmRuleParser.RuleSource> out) {
        // ResourceManager#getAllResourceLocations accepts a folder
        // prefix and a file suffix; we want the .properties files
        // in assets/<ns>/<folder>/. Mojang's API takes a path
        // like "<folder>/" and we ask for ".properties" suffix.
        // The first call returns the locations; we then read them.
        for (Identifier loc : resourceManager
                .listResources(folder, p -> p.getPath().endsWith(".properties"))
                .keySet()) {
            try {
                Resource res = resourceManager.getResource(loc).orElse(null);
                if (res == null) {
                    continue;
                }
                String body;
                try (var in = res.open();
                     var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    body = readAll(reader);
                }
                String sourceLabel = loc.toString();
                out.add(new CtmRuleParser.RuleSource(body, sourceLabel));
            } catch (IOException e) {
                LOGGER.warn("[{}] failed to read CTM rule file {}: {}",
                        Constants.MOD_NAME, loc, e.getMessage());
            }
        }
    }

    private void applyRules(List<CtmRuleParser.RuleSource> sources,
                            ResourceManager resourceManager) {
        // The parent namespace is "minecraft/optifine/ctm" - rules
        // are children of the optifine/ctm folder; the actual
        // sprite names live in the resource-pack's own namespace.
        // We use a synthetic parent for source-line reporting only.
        NamespaceId parent = new NamespaceId(Constants.MOD_ID, OPTIFINE_CTM);
        // Phase 5: per-file error isolation. A malformed
        // .properties file in a resource pack must not crash
        // the reload; the listener logs the failure and the
        // other files are still applied.
        CtmRuleSet ruleSet = CtmRuleParser.buildRuleSet(sources, parent,
                (label, message, cause) -> LOGGER.warn(
                        "[{}] skipping malformed CTM rule file {}: {}",
                        Constants.MOD_NAME, label, message));
        CtmRegistry reg = Platforms.get().ctmRegistry();
        reg.replace(ruleSet);
        LOGGER.info("[{}] CTM rule set installed: {} rules from {} files",
                Constants.MOD_NAME, ruleSet.all().size(), sources.size());
        // Phase 7: build the per-rule tile atlas (the
        // renderer consults this on each quad to map a
        // concrete tileIndex -> cinder sprite id). We
        // walk the rule set and resolve every rule's
        // tile list against its source path, asking
        // the resource manager which tile PNGs exist.
        // Missing numeric PNGs become generated cinder
        // sprites derived from the rule's base block sprite.
        buildAndPublishTileAtlas(ruleSet, sources, resourceManager);
    }

    /**
     * Resolves every rule's tile list against the source
     * path of the {@code .properties} file it was loaded
     * from, then publishes the result via
     * {@link CtmTileAtlas#replace(CtmTileAtlas)}.
     *
     * <p>The build is best-effort: a rule whose source
     * file is not in the original {@code sources} list
     * (theoretically impossible in this listener) is
     * skipped. Rules with parse errors are already
     * dropped by the rule-set builder.
     *
     * <p>Performance: O(n_rules * tiles_per_rule). Runs
     * once per resource reload.
     */
    private void buildAndPublishTileAtlas(
            CtmRuleSet ruleSet,
            List<CtmRuleParser.RuleSource> sources,
            ResourceManager resourceManager) {
        // Build a path -> sourceLabel map so we can find
        // the original resource path for each rule's
        // CtmRule.sourceFile().
        java.util.HashMap<String, String> labelByPath =
                new java.util.HashMap<>();
        for (CtmRuleParser.RuleSource s : sources) {
            labelByPath.put(s.sourceLabel(), s.sourceLabel());
        }
        java.util.ArrayList<CtmTileAtlasEntry> entries =
                new java.util.ArrayList<>();
        int numericTiles = 0;
        int namedTiles = 0;
        int generatedFallbackTiles = 0;
        for (CtmRule rule : ruleSet.all()) {
            // CtmRule.sourceFile() returns the sourceLabel
            // we passed to CtmRuleParser, which is the
            // resource path of the .properties file
            // (e.g. "minecraft:optifine/ctm/default/20_glass/glass.properties").
            String sourcePath = rule.sourceFile().orElse(null);
            if (sourcePath == null) {
                continue;
            }
            // Sanity: ensure the source path is one we
            // know about. Defensive against a rule that
            // somehow ended up with a different label.
            if (!labelByPath.containsKey(sourcePath)) {
                // Not fatal - the rule came from another
                // source (e.g. Continuity); use the
                // label as-is.
                labelByPath.put(sourcePath, sourcePath);
            }
            // Compute the rule's family directory once;
            // the existence predicate asks the resource
            // manager whether each tile PNG is present.
            String dirPath = CtmTileResolver.propertiesDirectoryPath(sourcePath);
            // Pre-parse the directory portion into a
            // namespace-aware Identifier for getResource.
            int dirColon = dirPath.indexOf(':');
            String dirNs = dirColon < 0
                    ? com.cinder.resource.NamespaceId.DEFAULT_NAMESPACE
                    : dirPath.substring(0, dirColon);
            String dirSlash = dirColon < 0
                    ? dirPath
                    : dirPath.substring(dirColon + 1);
            java.util.function.IntPredicate tileExists = n -> {
                String tilePath = dirSlash + "/" + n + ".png";
                Identifier tileId = Identifier.fromNamespaceAndPath(
                        dirNs, tilePath);
                return resourceManager.getResource(tileId).isPresent();
            };
            try {
                java.util.List<CtmTileResolver.Resolution> resolutions =
                        CtmTileResolver.resolve(
                                rule, sourcePath, tileExists);
                entries.add(new CtmTileAtlasEntry(rule, resolutions));
                for (CtmTileResolver.Resolution r : resolutions) {
                    if (r.needsInjection()) {
                        numericTiles++;
                        if (r.resourcePath() == null
                                && r.fallbackSourceSprite() != null) {
                            generatedFallbackTiles++;
                        }
                    } else if (r.isConcrete()) {
                        // Named tile, already present in an atlas.
                        namedTiles++;
                    }
                }
            } catch (RuntimeException e) {
                LOGGER.warn("[{}] tile resolution failed for rule {}: {}",
                        Constants.MOD_NAME, sourcePath, e.getMessage());
            }
        }
        CtmTileAtlas atlas = CtmTileAtlas.of(entries);
        CtmTileAtlas.replace(atlas);
        CtmMaterialTable materialTable = CtmMaterialTable.of(atlas);
        CtmMaterialTable.replace(materialTable);
        LOGGER.info("[{}] CTM tile atlas installed: {} entries, "
                        + "{} numeric (injection) tiles, "
                        + "{} generated fallback tiles, {} named tiles, "
                        + "{} material entries",
                Constants.MOD_NAME,
                entries.size(), numericTiles, generatedFallbackTiles,
                namedTiles, materialTable.size());
        requestTerrainRebuild();
    }

    private void requestTerrainRebuild() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        minecraft.levelRenderer.invalidateCompiledGeometry(
                minecraft.level,
                minecraft.options,
                minecraft.gameRenderer.mainCamera(),
                minecraft.getBlockColors());
        LOGGER.info("[{}] requested terrain rebuild after CTM material reload",
                Constants.MOD_NAME);
    }

    private static String readAll(java.io.Reader r) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[1024];
        int n;
        while ((n = r.read(buf)) > 0) {
            sb.append(buf, 0, n);
        }
        return sb.toString();
    }
}
