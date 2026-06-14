package com.cinder.fabric.client;

import com.cinder.Constants;
import com.cinder.platform.Platforms;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-only entrypoint. Renderer hooks and any client-resource reload
 * listeners will be registered from here in later phases.
 */
public final class CinderFabricClient implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID + "/client");

    @Override
    public void onInitializeClient() {
        LOGGER.info("[{}] initialized (client, loader={})",
                Constants.MOD_NAME, Platforms.get().id());
        if (net.fabricmc.loader.api.FabricLoader.getInstance()
                .isModLoaded("sodium")) {
            LOGGER.info("[{}] Sodium detected; Sodium CTM mesh path is active "
                            + "and vanilla terrain CTM hooks are disabled",
                    Constants.MOD_NAME);
        }

        // Phase 5: load the config file
        // (config/cinder.properties) into the shared holder.
        // The path uses the standard Fabric config dir; if the
        // file is missing we silently use the defaults.
        try {
            java.nio.file.Path configDir = net.fabricmc.loader.api.FabricLoader
                    .getInstance().getConfigDir();
            com.cinder.fabric.config.FabricConfigLoader
                    .loadAndInstall(configDir);
        } catch (RuntimeException e) {
            LOGGER.warn("[{}] config load failed; using defaults: {}",
                    Constants.MOD_NAME, e.getMessage());
        }

        // Phase 3: register the CTM resource reload listener. The
        // listener walks assets/*/optifine/ctm/*.properties and
        // assets/*/continuity/ctm/*.properties, parses them, and
        // atomically swaps the result into Platforms.get().ctmRegistry().
        com.cinder.fabric.ctm.CtmReloadListener.register();
        com.cinder.fabric.bettergrass.BetterGrassReloadListener.register();
        com.cinder.fabric.emissive.EmissiveReloadListener.register();
        com.cinder.fabric.cit.CitReloadListener.register();
        com.cinder.fabric.customgui.CustomGuiReloadListener.register();
        com.cinder.fabric.animation.CustomAnimationReloadListener.register();
        com.cinder.fabric.customcolors.CustomColorsReloadListener.register();
        com.cinder.client.command.CinderClientCommands.register();

        // The old CPU quad-swap CTM path is intentionally not
        // installed by default. Cinder's production CTM renderer is
        // being moved toward backend-native terrain material data
        // instead of per-quad sprite mutation on section-build threads.
        com.cinder.fabric.quad.BlockAtlasTracker.register();

        // Phase 7: tile injection is wired through a
        // custom SpriteSource registered via a Mixin into
        // SpriteSources.bootstrap() and consumed by the
        // vanilla block atlas definition shipped at
        // assets/minecraft/atlases/blocks.json. No
        // listener registration is needed here: the
        // SpriteSource is consulted on every atlas
        // stitch automatically.
    }
}
