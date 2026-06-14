package com.cinder.fabric.quad;

import com.cinder.quad.CtmQuadDecorator;
import com.cinder.quad.QuadDecorators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric-side registration helper. Calls
 * {@link QuadDecorators#register} with Cinder's
 * {@link CtmQuadDecorator} so that the pipeline has a
 * default decorator on every Fabric installation.
 *
 * <p>Idempotent: calling {@link #installDefaults()} more
 * than once has the same effect as calling it once,
 * because the {@link CtmQuadDecorator} uses a stable id
 * and the registry replaces entries with the same id.
 *
 * <p>Called from {@code CinderFabricClient.onInitializeClient}.
 * Loader-agnostic code can also call this method to
 * ensure the default decorator is installed; the method
 * is safe to call from any thread, on any environment
 * (the registry uses a {@code CopyOnWriteArrayList}).
 */
public final class FabricQuadDecorators {

    private static final Logger LOGGER =
            LoggerFactory.getLogger("cinder/quad-decorators");

    /** The default Cinder decorator instance, shared by
     *  the renderer across all section builds. */
    private static final CtmQuadDecorator DEFAULT_CTM =
            new CtmQuadDecorator();

    private FabricQuadDecorators() {
    }

    /**
     * Returns Cinder's default CTM decorator. Per-block
     * neighbour state is passed through {@code QuadContext},
     * so this shared instance has no mutable render state.
     */
    public static CtmQuadDecorator defaultCtm() {
        return DEFAULT_CTM;
    }

    /**
     * Registers Cinder's default decorators in the
     * pipeline. Safe to call multiple times; the second
     * call replaces the first with the same id.
     */
    public static void installDefaults() {
        QuadDecorators.register(DEFAULT_CTM);
        LOGGER.info("[cinder] installed default CTM quad decorator");
    }
}
