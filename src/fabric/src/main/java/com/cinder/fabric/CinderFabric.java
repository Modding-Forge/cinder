package com.cinder.fabric;

import com.cinder.Constants;
import com.cinder.platform.Platforms;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common-side (logical + physical) entrypoint. Runs on every environment
 * the mod is loaded into. Phase 0 only logs a startup line; feature work
 * will hang off the registered {@link com.cinder.platform.Platform}.
 */
public final class CinderFabric implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[{}] initialized (common)", Constants.MOD_NAME);
        // Phase 4.6: the mod is client-only functional; the
        // dedicated server has no Platform service registered.
        // We log the absence and continue rather than crash.
        Platforms.tryGet().ifPresentOrElse(
                p -> LOGGER.info("[{}] active loader: {}",
                        Constants.MOD_NAME, p.id()),
                () -> LOGGER.info(
                        "[{}] no Platform implementation on this "
                                + "environment (server or unsplit jar); "
                                + "running as a no-op.",
                        Constants.MOD_NAME));
    }
}
