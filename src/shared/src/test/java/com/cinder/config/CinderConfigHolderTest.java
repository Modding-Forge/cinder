package com.cinder.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Phase 5 tests for the {@link CinderConfigHolder}.
 *
 * <p>The holder is the process-wide singleton that loaders
 * populate and the rest of the codebase reads. The tests
 * verify the atomic-replace contract and the default fallback.
 */
class CinderConfigHolderTest {

    @AfterEach
    void resetHolder() {
        // Each test must leave the holder at its default so
        // other tests in the same JVM do not observe stale
        // state.
        CinderConfigHolder.reset();
    }

    @Test
    void get_returnsDefaultBeforeAnyReplace() {
        CinderConfig cfg = CinderConfigHolder.get();
        assertNotNull(cfg);
        assertEquals(CinderConfigDefaults.ENABLED, cfg.enabled());
    }

    @Test
    void replace_isVisibleToSubsequentGet() {
        CinderConfig custom = new CinderConfig(
                false, true, true, false, true, BetterGrassMode.FANCY);
        CinderConfigHolder.replace(custom);
        assertEquals(custom, CinderConfigHolder.get());
    }

    @Test
    void replace_isAtomic() {
        // The holder uses an AtomicReference; a single thread
        // sees either the old or the new value, never a
        // half-updated state. We cannot directly test
        // atomicity from a single thread; we test the
        // contract that the returned reference is the one
        // we just put.
        CinderConfig first = new CinderConfig(true, false, false, true,
                false, BetterGrassMode.FAST);
        CinderConfig second = new CinderConfig(false, true, true, false,
                true, BetterGrassMode.OFF);
        CinderConfigHolder.replace(first);
        CinderConfigHolder.replace(second);
        assertEquals(second, CinderConfigHolder.get());
    }

    @Test
    void reset_returnsToDefault() {
        CinderConfigHolder.replace(
                new CinderConfig(false, false, false, false, false,
                        BetterGrassMode.OFF));
        CinderConfigHolder.reset();
        assertEquals(CinderConfigDefaults.defaults(), CinderConfigHolder.get());
    }
}
