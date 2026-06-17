package com.argus.client.benchmark;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.LongAdder;

/**
 * Dev-only runtime profiler for Argus renderer hooks.
 *
 * <p>Threading: called from Sodium section-build threads and the render
 * thread. Counters are lock-free; report emission is synchronized and rare.
 *
 * <p>Performance: HOT PATH. Disabled cost is one static-final boolean branch.
 * Enable only in development with {@code -Dargus.benchmark=true}; normal
 * release/runtime launches do not log or collect samples.
 */
public final class ArgusBenchmark {

    public static final int SODIUM_PROCESS_QUAD = 0;
    public static final int SODIUM_BETTER_GRASS = 1;
    public static final int SODIUM_CTM = 2;
    public static final int SODIUM_EMISSIVE = 3;
    public static final int SODIUM_NATURAL = 4;
    public static final int SODIUM_OVERLAY_RETURN = 5;
    public static final int SODIUM_BETTER_SNOW = 6;
    public static final int CTM_PREFILTER = 7;
    public static final int CTM_NEIGHBOR_VIEW = 8;
    public static final int CTM_RESOLVE = 9;
    public static final int CTM_MATERIAL = 10;
    public static final int CTM_OVERLAY_PLAN = 11;
    public static final int CTM_UV_REMAP = 12;
    public static final int CTM_FACE_CACHE_HIT = 13;
    public static final int CTM_FACE_CACHE_MISS = 14;

    private static final boolean ENABLED =
            Boolean.getBoolean("argus.benchmark");
    private static final Logger LOGGER =
            LoggerFactory.getLogger("argus/benchmark");
    private static final String[] NAMES = {
            "sodium.process_quad",
            "sodium.better_grass",
            "sodium.ctm",
            "sodium.emissive",
            "sodium.natural",
            "sodium.overlay_return",
            "sodium.better_snow",
            "ctm.prefilter",
            "ctm.neighbor_view",
            "ctm.resolve",
            "ctm.material",
            "ctm.overlay_plan",
            "ctm.uv_remap",
            "ctm.face_cache_hit",
            "ctm.face_cache_miss"
    };
    private static final LongAdder[] COUNTS = counters();
    private static final LongAdder[] NANOS = counters();
    private static final LongAdder[] TOTAL_COUNTS = counters();
    private static final LongAdder[] TOTAL_NANOS = counters();
    private static final long REPORT_INTERVAL_NANOS = 5_000_000_000L;

    private static volatile long lastReportNanos = System.nanoTime();

    private ArgusBenchmark() {
    }

    public static boolean enabled() {
        return ENABLED;
    }

    public static long start() {
        return ENABLED ? System.nanoTime() : 0L;
    }

    public static void record(int bucket, long startNanos) {
        if (!ENABLED || startNanos == 0L) {
            return;
        }
        long now = System.nanoTime();
        COUNTS[bucket].increment();
        NANOS[bucket].add(now - startNanos);
        TOTAL_COUNTS[bucket].increment();
        TOTAL_NANOS[bucket].add(now - startNanos);
        maybeReport(now);
    }

    public static void count(int bucket) {
        if (!ENABLED) {
            return;
        }
        COUNTS[bucket].increment();
        TOTAL_COUNTS[bucket].increment();
        maybeReport(System.nanoTime());
    }

    /**
     * Returns cumulative benchmark buckets collected since JVM start.
     *
     * <p>Threading: safe to call from the render thread while section worker
     * threads are still recording. Values are approximate at the instant of
     * reading, which is sufficient for dev benchmark reports.
     */
    public static BucketSnapshot[] snapshotTotals() {
        BucketSnapshot[] out = new BucketSnapshot[NAMES.length];
        for (int i = 0; i < NAMES.length; i++) {
            out[i] = new BucketSnapshot(
                    NAMES[i],
                    TOTAL_COUNTS[i].sum(),
                    TOTAL_NANOS[i].sum());
        }
        return out;
    }

    /**
     * Clears cumulative report buckets for a fresh benchmark measurement
     * window.
     *
     * <p>Threading: intended for dev autopilot use after world setup and
     * before the measured route starts. Section worker threads may still be
     * active, so this is an approximate boundary, but it removes resource
     * reload, world creation, spawn placement and settle work from final
     * reports.
     */
    public static void resetTotals() {
        if (!ENABLED) {
            return;
        }
        for (int i = 0; i < NAMES.length; i++) {
            COUNTS[i].reset();
            NANOS[i].reset();
            TOTAL_COUNTS[i].reset();
            TOTAL_NANOS[i].reset();
        }
        lastReportNanos = System.nanoTime();
    }

    private static void maybeReport(long now) {
        if (now - lastReportNanos < REPORT_INTERVAL_NANOS) {
            return;
        }
        synchronized (ArgusBenchmark.class) {
            if (now - lastReportNanos < REPORT_INTERVAL_NANOS) {
                return;
            }
            lastReportNanos = now;
            StringBuilder line = new StringBuilder(512);
            line.append("[Argus] benchmark");
            for (int i = 0; i < NAMES.length; i++) {
                long count = COUNTS[i].sumThenReset();
                long nanos = NANOS[i].sumThenReset();
                if (count == 0L) {
                    continue;
                }
                double totalMs = nanos / 1_000_000.0D;
                double avgNs = (double) nanos / count;
                line.append(' ')
                        .append(NAMES[i])
                        .append("{count=")
                        .append(count)
                        .append(", totalMs=")
                        .append(String.format(java.util.Locale.ROOT,
                                "%.3f", totalMs))
                        .append(", avgNs=")
                        .append(String.format(java.util.Locale.ROOT,
                                "%.1f", avgNs))
                        .append('}');
            }
            LOGGER.info(line.toString());
        }
    }

    private static LongAdder[] counters() {
        LongAdder[] out = new LongAdder[NAMES.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = new LongAdder();
        }
        return out;
    }

    /**
     * Immutable benchmark bucket snapshot for final run reports.
     */
    public record BucketSnapshot(String name, long count, long nanos) {
        public double totalMillis() {
            return nanos / 1_000_000.0D;
        }

        public double averageNanos() {
            return count == 0L ? 0.0D : (double) nanos / count;
        }
    }
}
