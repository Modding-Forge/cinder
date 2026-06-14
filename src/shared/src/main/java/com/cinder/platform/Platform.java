package com.cinder.platform;

/**
 * Loader-agnostic abstraction over loader-specific concerns.
 *
 * <p>The {@code common} module owns the interface and the data model; each
 * loader module provides an implementation that is discovered through
 * {@link java.util.ServiceLoader}. Code in {@code common} MUST go through
 * {@link Platform} for any operation that depends on the running loader
 * (entrypoint dispatch, resource-reload registration, config path resolution,
 * event bus interaction, ...).
 *
 * <p><b>Phase 0 surface</b> is intentionally minimal. Methods will be added
 * in Phase 1 (reload-listener registration) and Phase 3 (event hooks,
 * config path) as features that need them are implemented.
 *
 * <p>Implementations are expected to be safe to call from the loader's main
 * thread only. Calls from render threads, worker threads, or async tasks are
 * out of scope for Phase 0.
 *
 * <p>Thread expectations: callers must invoke methods on the loader main
 * thread; implementations are not required to be thread-safe.
 *
 * <p>Performance: all methods are O(1) and have no allocation in the hot path
 * (none of them <i>are</i> in a hot path during Phase 0).
 */
public interface Platform {

    /**
     * A short, stable, human-readable identifier of the running loader.
     *
     * <p>Examples: {@code "fabric"}, {@code "neoforge"}, {@code "forge"}.
     *
     * @return the loader id, never {@code null}
     */
    String id();

    /**
     * The mod id under which the running instance of Cinder is registered.
     *
     * <p>For Phase 0 this is always {@code "cinder"} (see
     * {@code gradle.properties} -> {@code mod_id=cinder}). The method exists
     * so that future split-fork scenarios can keep the same {@code common}
     * code path.
     *
     * @return the active mod id, never {@code null}
     */
    String modId();

    /**
     * Whether the current logical side is the client.
     *
     * <p>Returns {@code true} for any dedicated client process and for the
     * integrated server's host. Renderers and HUD/render hooks in
     * {@code common} that need client-side data should gate on this flag.
     *
     * @return {@code true} if running on a client, {@code false} otherwise
     */
    boolean isClient();

    /**
     * Returns the registry of CTM rules. The registry is loader-agnostic;
     * loaders are responsible for populating it on resource reload.
     * The returned reference is the same across calls within a process;
     * its internal state changes atomically on reload.
     *
     * <p>For Phase 3 the registry is an optional surface: a loader that
     * does not implement CTM (e.g. a server-only build) returns
     * a registry whose rule set is always empty. The caller should not
     * need to null-check.
     */
    com.cinder.ctm.CtmRegistry ctmRegistry();
}
