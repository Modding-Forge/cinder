package com.cinder.cem;

/**
 * Immutable CEM box primitive parsed from a {@code .jem/.jpm} model.
 *
 * <p>Threading: immutable and shared safely after reload.
 *
 * <p>Performance: reload-time data only in Phase K; renderer adapters may
 * later convert these primitives into Minecraft model parts once per reload.
 */
public record CemBox(float x, float y, float z,
                     float width, float height, float depth) {
}
