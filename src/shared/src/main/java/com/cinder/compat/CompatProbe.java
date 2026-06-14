package com.cinder.compat;

/**
 * Loader-agnostic interface for detecting the presence of
 * specific third-party mods. The {@code common} module owns the
 * interface; the loader modules provide the implementation
 * (Fabric via {@code FabricLoader.getInstance().isModLoaded(id)}).
 *
 * <p>Performance: implementations are expected to be O(1)
 * lookups into a map populated once at startup. They must be
 * safe to call from any thread.
 */
public interface CompatProbe {

    /**
     * Returns {@code true} if the mod with the given id is
     * currently loaded.
     *
     * @param modId the mod id (e.g. "sodium", "iris")
     * @return {@code true} if loaded
     */
    boolean isLoaded(String modId);
}
