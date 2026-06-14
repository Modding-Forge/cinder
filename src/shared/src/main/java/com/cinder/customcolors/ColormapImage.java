package com.cinder.customcolors;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable ARGB pixel buffer for one decoded colormap image.
 *
 * <p>The shared module owns the sampling math, while loader adapters own PNG
 * decoding. Pixels are stored row-major as opaque or alpha-bearing ARGB ints.
 *
 * <p>Threading: immutable after construction. Performance: sampling is O(1)
 * and allocation-free.
 */
public final class ColormapImage {

    private final int width;
    private final int height;
    private final int[] pixels;

    public ColormapImage(int width, int height, int[] pixels) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("colormap dimensions must be positive");
        }
        Objects.requireNonNull(pixels, "pixels");
        if (pixels.length != width * height) {
            throw new IllegalArgumentException("pixel count does not match dimensions");
        }
        this.width = width;
        this.height = height;
        this.pixels = Arrays.copyOf(pixels, pixels.length);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int pixel(int x, int y) {
        int cx = clamp(x, 0, width - 1);
        int cy = clamp(y, 0, height - 1);
        return pixels[cx + cy * width];
    }

    public int sampleVanilla(double temperature, double rainfall) {
        double temp = clamp01(temperature);
        double rain = clamp01(rainfall) * temp;
        int x = (int) ((1.0D - temp) * (width - 1));
        int y = (int) ((1.0D - rain) * (height - 1));
        return pixel(x, y);
    }

    public int sampleGrid(int biomeColumn, int y, int yOffset,
                          int yVariance, int x, int z) {
        int sampleY = y + yOffset;
        if (yVariance > 0) {
            int spread = yVariance * 2 + 1;
            sampleY += positiveHash(x, z) % spread - yVariance;
        }
        return pixel(biomeColumn, sampleY);
    }

    public int[] pixelsCopy() {
        return Arrays.copyOf(pixels, pixels.length);
    }

    private static double clamp01(double v) {
        return Math.max(0.0D, Math.min(1.0D, v));
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static int positiveHash(int x, int z) {
        int h = x * 73428767 ^ z * 91227153;
        h ^= h >>> 16;
        return h & 0x7fffffff;
    }
}
