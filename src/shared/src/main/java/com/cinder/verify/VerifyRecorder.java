package com.cinder.verify;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Records compare-off vs compare-on results for the
 * debug-verify mode.
 *
 * <p>The class is a thread-safe singleton: the renderer (which
 * may run on a render thread) increments counters via
 * {@link #recordMatch}, {@link #recordMismatch}, or
 * {@link #recordSkip}, while a config-reload thread can call
 * {@link #reset} to start a fresh window. The
 * {@link #snapshot()} method returns an immutable copy of the
 * current counters.
 *
 * <p>Why an explicit recorder instead of a logger call per
 * mismatch? The recorder is O(1) per comparison; logging would
 * also be O(1) but would clutter the log file. The recorder
 * supports an aggregate readout at any time and is testable
 * without capturing log output.
 *
 * <p>Performance: lock-free, allocation-free on the hot path.
 * The {@code snapshot()} method allocates one record per call.
 */
public final class VerifyRecorder {

    private static final VerifyRecorder INSTANCE = new VerifyRecorder();

    private final AtomicLong matches = new AtomicLong();
    private final AtomicLong mismatches = new AtomicLong();
    private final AtomicLong skips = new AtomicLong();

    private VerifyRecorder() {
    }

    /**
     * Returns the process-wide singleton instance.
     */
    public static VerifyRecorder get() {
        return INSTANCE;
    }

    /**
     * Records a comparison where Cinder and the simulated
     * vanilla path produced the same result.
     */
    public void recordMatch() {
        matches.incrementAndGet();
    }

    /**
     * Records a comparison where Cinder and the simulated
     * vanilla path produced different results.
     */
    public void recordMismatch() {
        mismatches.incrementAndGet();
    }

    /**
     * Records a comparison that was skipped (e.g. no rule
     * matched at all, or the position was filtered out).
     */
    public void recordSkip() {
        skips.incrementAndGet();
    }

    /**
     * Returns an immutable snapshot of the current counters.
     */
    public VerifyStats snapshot() {
        return new VerifyStats(
                matches.get(), mismatches.get(), skips.get());
    }

    /**
     * Resets all counters to zero. Used when a config reload
     * starts a new verify window.
     */
    public void reset() {
        matches.set(0);
        mismatches.set(0);
        skips.set(0);
    }

    /**
     * Records an integer value (e.g. a tile index) as a string,
     * for inclusion in log messages. Provided here to centralise
     * the formatting and to keep the {@code VerifyRecorder}
     * public surface narrow.
     */
    public static String describe(int value) {
        if (value == Integer.MIN_VALUE) {
            return "<min>";
        }
        Objects.requireNonNull(value, "value");
        return Integer.toString(value);
    }
}
