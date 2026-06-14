package com.cinder.platform;

import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Helpers around the loader-agnostic {@link Platform} interface.
 *
 * <p>Each loader module is expected to register exactly one
 * {@link Platform} implementation through {@code META-INF/services}.
 * The first successful lookup wins; if none is registered,
 * the holder is empty and {@link #tryGet()} returns
 * {@link Optional#empty()}.
 *
 * <h2>Test escape hatch</h2>
 *
 * <p>Tests that need a controllable Platform use
 * {@link #setForTest(Platform)}; the previous value is
 * returned so the test can restore it. Production code never
 * calls this method; the public Javadoc for it makes that
 * contract explicit.
 *
 * <p>Performance: the holder is a single
 * {@link AtomicReference}, so reads are lock-free. The
 * ServiceLoader lookup runs at most once per process, on
 * the first call to {@link #tryGet()} or {@link #get()}.
 */
public final class Platforms {

    private static final AtomicReference<Platform> INSTANCE = new AtomicReference<>();

    static {
        // Eagerly load the first platform via ServiceLoader
        // on class init. If no service is registered, the
        // holder stays null and the tryGet()/get() methods
        // behave accordingly.
        Platform p = loadFirst();
        if (p != null) {
            INSTANCE.set(p);
        }
    }

    private Platforms() {
    }

    /**
     * Returns the active {@link Platform} implementation.
     * Throws {@link IllegalStateException} if no
     * implementation is registered.
     */
    public static Platform get() {
        Platform p = INSTANCE.get();
        if (p == null) {
            throw new IllegalStateException(
                    "No Platform implementation found on the classpath. "
                            + "Did you forget to include a loader module?");
        }
        return p;
    }

    /**
     * Returns the active platform wrapped in an {@link Optional}.
     */
    public static Optional<Platform> tryGet() {
        return Optional.ofNullable(INSTANCE.get());
    }

    /**
     * Test-only: installs a fresh {@link Platform} and
     * returns the previous value. Production code must not
     * call this method; the Javadoc is the contract.
     */
    public static Platform setForTest(Platform newPlatform) {
        if (newPlatform == null) {
            return INSTANCE.getAndSet(null);
        }
        return INSTANCE.getAndSet(newPlatform);
    }

    private static Platform loadFirst() {
        try {
            for (Platform candidate : ServiceLoader.load(Platform.class)) {
                return candidate;
            }
        } catch (ServiceConfigurationError err) {
            throw new IllegalStateException(
                    "Failed to load a Platform implementation via ServiceLoader", err);
        }
        return null;
    }
}
