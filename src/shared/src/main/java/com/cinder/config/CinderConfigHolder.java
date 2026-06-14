package com.cinder.config;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Process-wide holder for the active {@link CinderConfig}. The
 * holder is a single atomic reference that can be replaced
 * atomically on config reload; readers see either the old or
 * the new config, never a half-updated state.
 *
 * <p>The class is loader-agnostic. Loaders initialise the
 * holder on mod start (and on every config-reload event) by
 * calling {@link #replace(CinderConfig)}; the rest of the
 * codebase reads via {@link #get()}.
 *
 * <p>Performance: {@link #get()} is a single volatile read; the
 * hot paths (selector, registry) can cache the returned
 * reference locally and avoid the call entirely.
 */
public final class CinderConfigHolder {

    private static final AtomicReference<CinderConfig> INSTANCE =
            new AtomicReference<>(CinderConfigDefaults.defaults());

    private CinderConfigHolder() {
    }

    /**
     * Returns the active configuration, or the default if no
     * loader has set one.
     */
    public static CinderConfig get() {
        return INSTANCE.get();
    }

    /**
     * Atomically replaces the active configuration.
     */
    public static void replace(CinderConfig newConfig) {
        INSTANCE.set(newConfig);
    }

    /**
     * Resets the configuration to the defaults. Test-only.
     */
    public static void reset() {
        INSTANCE.set(CinderConfigDefaults.defaults());
    }
}
