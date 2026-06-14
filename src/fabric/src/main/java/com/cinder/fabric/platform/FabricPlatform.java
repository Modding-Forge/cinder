package com.cinder.fabric.platform;

import com.cinder.Constants;
import com.cinder.ctm.CtmRegistry;
import com.cinder.platform.Platform;

/**
 * Fabric-specific implementation of the loader-agnostic {@link Platform}
 * interface. This is the <b>only</b> class in the project allowed to import
 * anything from {@code net.fabricmc.*}.
 *
 * <p>Discovered through {@code META-INF/services}. See
 * {@code fabric/src/main/resources/META-INF/services/com.cinder.platform.Platform}.
 *
 * <p>Thread expectations: all methods are safe to call from the loader main
 * thread. The implementation is not thread-safe and is not required to be
 * in Phase 0; future phases may add stricter guarantees if needed.
 *
 * <p>Performance: O(1), no allocation.
 *
 * <p>Phase 3 change: the previous {@code FabricLoader}-based environment
 * probe was removed so that this class can live in the {@code main} source
 * set (i.e. on the dedicated server's classpath). The current
 * implementation reports {@code isClient() == true} unconditionally
 * because Phase 3 of Cinder is client-only functional (the renderer
 * hooks and the CTM registry are only meaningful on the client).
 * Phase 4 (server parity) will revisit this decision.
 */
public final class FabricPlatform implements Platform {

    private final CtmRegistry ctmRegistry;

    public FabricPlatform() {
        this.ctmRegistry = new CtmRegistry(Constants.MOD_ID);
    }

    @Override
    public String id() {
        return "fabric";
    }

    @Override
    public String modId() {
        return Constants.MOD_ID;
    }

    @Override
    public boolean isClient() {
        // The "server" flavour of the platform runs on the
        // dedicated server, which has no renderer and no CTM
        // feature. The "client" flavour (FabricPlatformClient
        // in the client source set) overrides this method and
        // reports the real environment.
        return false;
    }

    @Override
    public CtmRegistry ctmRegistry() {
        return this.ctmRegistry;
    }
}
