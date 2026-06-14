package com.cinder.fabric.compat;

import com.cinder.compat.CompatProbe;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric-side {@link CompatProbe} implementation. Probes are
 * O(1) map lookups into the FabricLoader mod table, populated
 * at mod-load time.
 *
 * <p>This is the only class in the {@code fabric} client source
 * set that imports {@code net.fabricmc.*} for the
 * compatibility-probe use case.
 */
public final class FabricCompatProbe implements CompatProbe {

    @Override
    public boolean isLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }
}
