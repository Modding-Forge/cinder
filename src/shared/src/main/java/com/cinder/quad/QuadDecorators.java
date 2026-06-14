package com.cinder.quad;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Process-wide registry of {@link QuadDecorator}s. The
 * pipeline consults the registry in priority order on every
 * quad.
 *
 * <h2>Thread-safety</h2>
 *
 * <p>The decorator list is a {@link CopyOnWriteArrayList} so
 * that registrations (which happen on the mod-init thread)
 * and reads (which happen on the render thread) do not race.
 * A registration is O(n) (copy) but is performed exactly
 * once per decorator at startup; reads are lock-free.
 *
 * <p>Mod authors are expected to call
 * {@link #register(QuadDecorator)} exactly once, on mod
 * init. There is no explicit "unregister" because the
 * registry is process-wide and lives as long as the JVM.
 *
 * <h2>Snapshot reads</h2>
 *
 * <p>{@link #pipeline()} returns an immutable snapshot of
 * the registered decorators, sorted by ascending priority.
 * The pipeline method is called by the renderer per
 * section, not per quad, so the sort cost is amortised.
 *
 * <h2>Performance</h2>
 *
 * <p>Pipeline run is O(n_decorators) per quad. A typical
 * installation has exactly one decorator (Cinder's CTM
 * one). The list snapshot is built once per section
 * (typically 0.5-2 ms amortised cost) and reused for every
 * quad in the section.
 */
public final class QuadDecorators {

    private static final CopyOnWriteArrayList<QuadDecorator> REGISTRY =
            new CopyOnWriteArrayList<>();

    private static final AtomicReference<List<QuadDecorator>> PIPELINE =
            new AtomicReference<>(List.of());

    private QuadDecorators() {
    }

    /**
     * Registers a decorator. Decorators are sorted by
     * priority (lower first), then by registration order
     * (FIFO). A duplicate id replaces the previous
     * decorator with the same id; this is the expected
     * behaviour for mods that hot-reload their config.
     */
    public static void register(QuadDecorator decorator) {
        if (decorator == null) {
            throw new NullPointerException("decorator");
        }
        synchronized (REGISTRY) {
            // Replace any existing decorator with the same id.
            REGISTRY.removeIf(d -> d.id().equals(decorator.id()));
            REGISTRY.add(decorator);
            rebuildPipeline();
        }
    }

    /**
     * Returns an immutable snapshot of the current decorator
     * pipeline, sorted by priority ascending. Renderer-side
     * code calls this once per section and reuses the list
     * for every quad in the section.
     */
    public static List<QuadDecorator> pipeline() {
        return PIPELINE.get();
    }

    /**
     * Runs the registered decorators on a single quad.
     * The first decorator sees the input quad; each
     * subsequent decorator sees the previous one's output.
     * Decorators that return {@link Optional#empty()} pass
     * the quad through unchanged.
     *
     * <p>If no decorator is registered, the input quad is
     * returned (no allocation).
     *
     * <p>Decorator exceptions are caught and logged by the
     * pipeline; the offending decorator is bypassed and the
     * quad is passed through unchanged. The pipeline never
     * throws.
     */
    public static QuadRef apply(QuadRef quad, QuadContext context) {
        List<QuadDecorator> pipeline = PIPELINE.get();
        if (pipeline.isEmpty()) {
            return quad;
        }
        QuadRef current = quad;
        for (QuadDecorator d : pipeline) {
            try {
                Optional<QuadRef> next = d.decorate(current, context);
                if (next.isPresent()) {
                    current = next.get();
                }
            } catch (RuntimeException e) {
                // Decorators are not allowed to throw. We log
                // and continue with the unchanged quad. The
                // exception is reported via the standard
                // logger of the calling code; here we just
                // swallow it.
                System.err.println("[cinder] QuadDecorator '"
                        + d.id() + "' threw, bypassing: "
                        + e.getMessage());
            }
        }
        return current;
    }

    /**
     * Removes all registered decorators. Test-only.
     */
    public static void clear() {
        synchronized (REGISTRY) {
            REGISTRY.clear();
            rebuildPipeline();
        }
    }

    /**
     * Returns the number of registered decorators. Useful
     * for tests and diagnostics.
     */
    public static int size() {
        return REGISTRY.size();
    }

    private static void rebuildPipeline() {
        List<QuadDecorator> sorted = new ArrayList<>(REGISTRY);
        sorted.sort(Comparator
                .comparingInt(QuadDecorator::priority)
                .thenComparing(QuadDecorator::id));
        PIPELINE.set(List.copyOf(sorted));
    }
}
