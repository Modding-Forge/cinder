package com.cinder.quad;

import java.util.Optional;

/**
 * Single point of extension for the quad-level pipeline that
 * runs after the vanilla renderer has produced a quad but
 * before it is written to the GPU buffer.
 *
 * <h2>Why a Decorator and not a Mixin?</h2>
 *
 * <p>A mixin into {@code ModelBlockRenderer.tesselateBlock} (or
 * any other vanilla renderer method) is the naive way to hook
 * into the quad stream. The problem is that
 * <ul>
 *   <li><b>Sodium</b> replaces the entire chunk renderer with
 *       its own pipeline and never calls
 *       {@code ModelBlockRenderer.tesselateBlock};</li>
 *   <li><b>Continuity</b> is itself a CTM mod that already
 *       hooks the same call, so the two mixins race;</li>
 *   <li><b>Iris</b> replaces the shader pipeline and any
 *       mixin on the per-quad output may produce quads that
 *       the Iris shader does not expect.</li>
 * </ul>
 *
 * <p>The {@link QuadDecorator} pattern avoids this by exposing
 * a stable, public, loader-agnostic interface. Cinder
 * registers a {@code CtmQuadDecorator} (which is itself a
 * {@code QuadDecorator}); other mods register their own. The
 * pipeline consults every registered decorator in
 * priority order, threads the quad through them, and emits
 * the final result. If Sodium replaces the renderer, the
 * pipeline is not run at all - and Sodium is free to consult
 * Cinder's pipeline itself via the loader-side integration
 * (the {@code fabric-rendering-api-v1} provides a similar
 * integration point).
 *
 * <h2>Decorator contract</h2>
 *
 * <ol>
 *   <li>The decorator receives a {@link QuadRef} and a
 *       {@link QuadContext}. It must not mutate the input
 *       (the pipeline shares the same quad across decorators).</li>
 *   <li>The decorator returns either
 *       {@link Optional#empty()} (meaning "I have no
 *       opinion, pass the quad through") or an
 *       {@code Optional<QuadRef>} containing the replacement
 *       quad (which the next decorator sees as its input).</li>
 *   <li>The decorator is expected to be cheap. The hot path
 *       is the CTM lookup; if the CTM registry has no
 *       matching rule, the decorator returns empty in O(1).</li>
 *   <li>The decorator is <b>not</b> allowed to throw. A buggy
 *       decorator must catch its own exceptions and return
 *       empty. The pipeline never crashes the renderer
 *       because of a third-party decorator.</li>
 * </ol>
 *
 * <h2>Priority and ordering</h2>
 *
 * <p>Decorators are sorted by ascending priority. Lower
 * numbers run first. The default priority is 100; mod authors
 * who want to run before Cinder pick a lower number, those
 * who want to run after pick a higher one. Decorators with
 * the same priority are sorted by their registration order
 * (FIFO).
 *
 * <h2>Performance</h2>
 *
 * <p>Each decorator is called once per quad. The pipeline is
 * O(n_decorators) per quad. A typical installation has
 * exactly one decorator (Cinder's CTM one), so the overhead
 * is a single virtual call per quad, comparable to a direct
 * mixin hook.
 */
public interface QuadDecorator {

    /**
     * A short, stable identifier for this decorator. Used
     * for log messages and for diagnostics (e.g. the
     * debug-verify mode reports which decorator produced a
     * quad).
     */
    String id();

    /**
     * Priority. Lower numbers run first. The pipeline
     * consults decorators in ascending priority order.
     */
    default int priority() {
        return 100;
    }

    /**
     * Inspects the quad and context, returns the quad to be
     * passed to the next decorator. Returning
     * {@link Optional#empty()} leaves the quad unchanged.
     */
    Optional<QuadRef> decorate(QuadRef quad, QuadContext context);
}
