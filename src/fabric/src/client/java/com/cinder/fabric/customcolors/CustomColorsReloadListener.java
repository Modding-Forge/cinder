package com.cinder.fabric.customcolors;

import com.cinder.Constants;
import com.cinder.customcolors.ColormapImage;
import com.cinder.customcolors.ColormapRule;
import com.cinder.customcolors.ColorProperties;
import com.cinder.customcolors.CustomColorParseResult;
import com.cinder.customcolors.CustomColorRuleSet;
import com.cinder.customcolors.ColorOverrideTable;
import com.cinder.resource.NamespaceId;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Fabric resource reload bridge for OptiFine Custom Colors and Colormaps.
 *
 * <p>Threading: property parsing and PNG decoding happen on the prepare
 * executor; the immutable snapshot is published after the reload barrier.
 */
public final class CustomColorsReloadListener implements
        PreparableReloadListener,
        IdentifiableResourceReloadListener {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(Constants.MOD_ID + "/custom-colors-reload");
    private static final Identifier COLOR_PROPERTIES =
            Identifier.fromNamespaceAndPath("minecraft",
                    "optifine/color.properties");
    private static final String COLORMAP_FOLDER = "optifine/colormap";
    private static final String[] SPECIAL_COLORMAPS = {
            "grass", "foliage", "water", "redstone", "pumpkinstem",
            "melonstem", "lavadrop", "myceliumparticle", "xporb",
            "durability", "swampgrass", "swampfoliage", "pine", "birch",
            "underwater", "underlava", "fog0", "sky0"
    };
    private static boolean customTintSourcesInstalled;

    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(Constants.MOD_ID,
                    "custom_colors_reload");

    public static void register() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)
                .registerReloadListener(new CustomColorsReloadListener());
        LOGGER.info("[{}] Custom Colors reload listener registered",
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
                .thenAcceptAsync(CustomColorsReloadListener::publish,
                        reloadExecutor);
    }

    private static CustomColorsClientSnapshot load(
            ResourceManager resourceManager) {
        CustomColorParseResult colorProperties =
                loadColorProperties(resourceManager);
        ArrayList<ColormapRule> blockRules =
                new ArrayList<>(colorProperties.blockRules());
        for (ColormapRule rule : loadBlockColormapProperties(resourceManager)) {
            blockRules.add(rule);
        }

        LinkedHashMap<NamespaceId, ColormapImage> imagesBySource =
                new LinkedHashMap<>();
        for (ColormapRule rule : blockRules) {
            decode(resourceManager, rule.source())
                    .ifPresent(image -> imagesBySource.put(rule.source(),
                            image));
        }

        LinkedHashMap<String, ColormapImage> special = new LinkedHashMap<>();
        LinkedHashMap<String, ColormapImage> environment = new LinkedHashMap<>();
        for (String key : SPECIAL_COLORMAPS) {
            NamespaceId source = new NamespaceId("minecraft",
                    "optifine/colormap/" + key + ".png");
            Optional<ColormapImage> image = decode(resourceManager, source);
            if (image.isEmpty()) {
                continue;
            }
            if (key.startsWith("fog") || key.startsWith("sky")
                    || key.startsWith("under")) {
                environment.put(key, image.get());
            } else {
                special.put(key, image.get());
            }
        }

        CustomColorRuleSet ruleSet = new CustomColorRuleSet(
                colorProperties.overrides(),
                blockRules.toArray(ColormapRule[]::new),
                special, environment);
        CustomColorsClientSnapshot snapshot =
                CustomColorsClientSnapshot.from(ruleSet, imagesBySource);
        LOGGER.info("[{}] Custom Colors reload: {} hard colors, {} block "
                        + "rules, {} decoded block colormaps, {} special "
                        + "colormaps",
                Constants.MOD_NAME, colorProperties.overrides().size(),
                blockRules.size(), imagesBySource.size(), special.size()
                        + environment.size());
        return snapshot;
    }

    private static CustomColorParseResult loadColorProperties(
            ResourceManager resourceManager) {
        Optional<Resource> resource = resourceManager.getResource(
                COLOR_PROPERTIES);
        if (resource.isEmpty()) {
            return new CustomColorParseResult(ColorOverrideTable.empty(),
                    java.util.List.of(), java.util.List.of());
        }
        try (var in = resource.get().open();
             var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            CustomColorParseResult result =
                    ColorProperties.parseColorProperties(
                            readAll(reader), COLOR_PROPERTIES.toString());
            for (CustomColorParseResult.Error error : result.errors()) {
                LOGGER.warn("[{}] malformed Custom Colors key {} in {} "
                                + "({}): {}",
                        Constants.MOD_NAME, error.key(), error.sourceFile(),
                        error.value(), error.message());
            }
            return result;
        } catch (Exception e) {
            LOGGER.warn("[{}] failed to read {}: {}",
                    Constants.MOD_NAME, COLOR_PROPERTIES, e.getMessage());
            return new CustomColorParseResult(ColorOverrideTable.empty(),
                    java.util.List.of(), java.util.List.of());
        }
    }

    private static ArrayList<ColormapRule> loadBlockColormapProperties(
            ResourceManager resourceManager) {
        ArrayList<ColormapRule> out = new ArrayList<>();
        for (Identifier loc : resourceManager
                .listResources(COLORMAP_FOLDER,
                        id -> id.getPath().endsWith(".properties"))
                .keySet()) {
            try (var in = resourceManager.getResource(loc)
                    .orElseThrow().open();
                 var reader = new InputStreamReader(
                         in, StandardCharsets.UTF_8)) {
                ColormapRule rule = ColorProperties.parseColormapProperties(
                        readAll(reader), loc.toString());
                if (rule.hasBlocks()) {
                    out.add(rule);
                }
            } catch (Exception e) {
                LOGGER.warn("[{}] skipping malformed colormap file {}: {}",
                        Constants.MOD_NAME, loc, e.getMessage());
            }
        }
        return out;
    }

    private static Optional<ColormapImage> decode(ResourceManager manager,
                                                  NamespaceId source) {
        Identifier id = Identifier.fromNamespaceAndPath(
                source.namespace(), imagePath(source.path()));
        Optional<Resource> resource = manager.getResource(id);
        if (resource.isEmpty()) {
            return Optional.empty();
        }
        try (var in = resource.get().open();
             NativeImage image = NativeImage.read(in)) {
            return Optional.of(new ColormapImage(image.getWidth(),
                    image.getHeight(), image.getPixels()));
        } catch (Exception e) {
            LOGGER.warn("[{}] failed to decode colormap {}: {}",
                    Constants.MOD_NAME, id, e.getMessage());
            return Optional.empty();
        }
    }

    private static String imagePath(String path) {
        return path.endsWith(".png") ? path : path + ".png";
    }

    private static void publish(CustomColorsClientSnapshot snapshot) {
        CustomColorsRuntime.replace(snapshot);
        registerTintSources(snapshot);
        requestTerrainRebuild();
        LOGGER.info("[{}] Custom Colors snapshot installed: active={}",
                Constants.MOD_NAME, !snapshot.isEmpty());
    }

    private static void registerTintSources(CustomColorsClientSnapshot snapshot) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        BlockColors colors = minecraft.getBlockColors();
        if (snapshot == null || snapshot.isEmpty()) {
            if (customTintSourcesInstalled) {
                restoreVanillaTintSources(colors);
                customTintSourcesInstalled = false;
            }
            return;
        }
        customTintSourcesInstalled = true;
        colors.register(java.util.List.of(CinderCustomBlockTintSource.GRASS),
                Blocks.FERN, Blocks.SHORT_GRASS, Blocks.POTTED_FERN,
                Blocks.BUSH, Blocks.SUGAR_CANE);
        colors.register(java.util.List.of(CinderCustomBlockTintSource.GRASS_BLOCK),
                Blocks.GRASS_BLOCK, Blocks.LARGE_FERN, Blocks.TALL_GRASS);
        colors.register(java.util.List.of(CinderCustomBlockTintSource.FOLIAGE),
                Blocks.OAK_LEAVES, Blocks.JUNGLE_LEAVES,
                Blocks.ACACIA_LEAVES, Blocks.DARK_OAK_LEAVES, Blocks.VINE,
                Blocks.MANGROVE_LEAVES);
        colors.register(java.util.List.of(CinderCustomBlockTintSource.WATER),
                Blocks.WATER_CAULDRON);
        colors.register(java.util.List.of(CinderCustomBlockTintSource.WATER_PARTICLES),
                Blocks.WATER, Blocks.BUBBLE_COLUMN);
        colors.register(java.util.List.of(CinderCustomBlockTintSource.REDSTONE),
                Blocks.REDSTONE_WIRE);
        colors.register(java.util.List.of(CinderCustomBlockTintSource.STEM),
                Blocks.MELON_STEM, Blocks.PUMPKIN_STEM);
        colors.register(java.util.List.of(CinderCustomBlockTintSource.PALETTE),
                Blocks.LILY_PAD);
        for (Block block : snapshot.paletteBlocks()) {
            colors.register(java.util.List.of(CinderCustomBlockTintSource.PALETTE),
                    block);
        }
    }

    private static void restoreVanillaTintSources(BlockColors colors) {
        BlockTintSource blank = BlockTintSources.constant(-1);
        colors.register(java.util.List.of(BlockTintSources.doubleTallGrass()),
                Blocks.LARGE_FERN, Blocks.TALL_GRASS);
        colors.register(java.util.List.of(BlockTintSources.grass()),
                Blocks.FERN, Blocks.SHORT_GRASS, Blocks.POTTED_FERN,
                Blocks.BUSH);
        colors.register(java.util.List.of(BlockTintSources.grassBlock()),
                Blocks.GRASS_BLOCK);
        colors.register(java.util.List.of(blank, BlockTintSources.grass()),
                Blocks.PINK_PETALS, Blocks.WILDFLOWERS);
        colors.register(java.util.List.of(BlockTintSources.constant(-10380959)),
                Blocks.SPRUCE_LEAVES);
        colors.register(java.util.List.of(BlockTintSources.constant(-8345771)),
                Blocks.BIRCH_LEAVES);
        colors.register(java.util.List.of(BlockTintSources.foliage()),
                Blocks.OAK_LEAVES, Blocks.JUNGLE_LEAVES,
                Blocks.ACACIA_LEAVES, Blocks.DARK_OAK_LEAVES, Blocks.VINE,
                Blocks.MANGROVE_LEAVES);
        colors.register(java.util.List.of(BlockTintSources.dryFoliage()),
                Blocks.LEAF_LITTER);
        colors.register(java.util.List.of(BlockTintSources.water()),
                Blocks.WATER_CAULDRON);
        colors.register(java.util.List.of(BlockTintSources.waterParticles()),
                Blocks.WATER, Blocks.BUBBLE_COLUMN);
        colors.register(java.util.List.of(BlockTintSources.redstone()),
                Blocks.REDSTONE_WIRE);
        colors.register(java.util.List.of(BlockTintSources.sugarCane()),
                Blocks.SUGAR_CANE);
        colors.register(java.util.List.of(BlockTintSources.constant(-2046180)),
                Blocks.ATTACHED_MELON_STEM, Blocks.ATTACHED_PUMPKIN_STEM);
        colors.register(java.util.List.of(BlockTintSources.stem()),
                Blocks.MELON_STEM, Blocks.PUMPKIN_STEM);
        colors.register(java.util.List.of(BlockTintSources.constant(
                -9321636, -14647248)), Blocks.LILY_PAD);
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
