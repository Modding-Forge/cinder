package com.cinder.verify;

/**
 * Aggregate counts produced by {@link VerifyRecorder}. The
 * recorder accumulates {@code matches} and {@code mismatches}
 * for the duration of a session; {@link VerifyRecorder#snapshot()} returns
 * an immutable copy.
 *
 * <p>The class is a record so that the snapshot is automatically
 * immutable and value-equal - tests assert on the snapshot
 * directly.
 *
 * <p>Performance: no allocation on the hot path. The recorder
 * uses two {@code long} counters internally; the snapshot is
 * created on demand only.
 */
public record VerifyStats(long matches, long mismatches, long skips) {

    public VerifyStats {
        // All components are non-negative longs; no defensive
        // copy is needed.
    }

    public static VerifyStats empty() {
        return new VerifyStats(0L, 0L, 0L);
    }

    public long total() {
        return matches + mismatches + skips;
    }
}
