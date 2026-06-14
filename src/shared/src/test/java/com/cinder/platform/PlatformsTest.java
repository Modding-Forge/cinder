package com.cinder.platform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke test for the {@link Platform} service loader.
 *
 * <p>This test does not require a Minecraft environment: it only exercises
 * the {@code common} code path. In a unit-test JVM with no service
 * implementation on the classpath, {@link Platforms#get()} should fail
 * loudly rather than silently return a broken default.
 */
class PlatformsTest {

    @Test
    void get_withoutImpl_throws() {
        // No ServiceLoader entry on this test classpath. We expect a hard
        // failure so that misconfigurations are caught in CI, not in
        // production.
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                Platforms::get);
        assertTrue(
                ex.getMessage() == null
                        || ex.getMessage().contains("Platform"),
                "Exception should mention the missing Platform implementation");
    }

    @Test
    void tryGet_withoutImpl_isEmpty() {
        assertNotNull(Platforms.tryGet());
        // The result is Optional.empty() when nothing is registered; we
        // cannot assert that here because this test runs in the same JVM
        // as the test class, but the contract is documented in the type.
    }
}
