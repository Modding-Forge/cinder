package com.cinder.customsky;

/**
 * Immutable custom-sky rotation settings.
 *
 * <p>Threading: safe to share between reload and render snapshots.
 * Performance: renderer reads primitive fields directly.
 */
public record CustomSkyRotation(
        boolean rotate,
        float speed,
        float axisX,
        float axisY,
        float axisZ) {

    public static final CustomSkyRotation DEFAULT =
            new CustomSkyRotation(true, 1.0F, 0.0F, 0.0F, 1.0F);

    public CustomSkyRotation {
        if (!Float.isFinite(speed)) {
            speed = 1.0F;
        }
        if (!Float.isFinite(axisX) || !Float.isFinite(axisY)
                || !Float.isFinite(axisZ)
                || (axisX == 0.0F && axisY == 0.0F && axisZ == 0.0F)) {
            axisX = 0.0F;
            axisY = 0.0F;
            axisZ = 1.0F;
        }
    }
}
