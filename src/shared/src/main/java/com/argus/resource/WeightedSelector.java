package com.argus.resource;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable weighted selector with O(log n) sampling.
 *
 * <p>Given a list of weights (one per outcome), the selector returns the
 * index of one outcome on each {@link #sample(long)} call. Weights are
 * non-negative; a weight of zero is a valid "this outcome is never
 * selected" entry.
 *
 * <p>This is a <b>clean-room</b> implementation. It does not share any
 * data-table layout, sampling algorithm, or hash function with OptiFine's
 * closed-source counterpart. The behavior contract is what OptiFine packs
 * depend on:
 *
 * <ul>
 *   <li>Total weight of zero produces a deterministic fallback to index 0
 *       (consistent with how OptiFine resource packs treat under- or
 *       over-specified {@code weights.1} entries).</li>
 *   <li>Negative weights are rejected at construction time.</li>
 *   <li>Sampling is O(log n) using binary search over a prefix-sum
 *       table stored in {@code long[]} to avoid {@code int} overflow when
 *       pack authors use very large weights.</li>
 *   <li>The given {@code seed} is the only source of randomness; the
 *       selector itself is stateless and thread-safe.</li>
 * </ul>
 *
 * <p>Performance: construction is O(n). Sampling is O(log n) with no
 * allocation.
 *
 * <p>Thread expectations: instances are immutable; concurrent samples are
 * safe.
 */
public final class WeightedSelector {

    private final int[] weights;
    private final long[] prefix;
    private final long total;

    /**
     * @param weights the per-outcome weights, copied and validated
     * @throws IllegalArgumentException if any weight is negative
     */
    public WeightedSelector(int[] weights) {
        Objects.requireNonNull(weights, "weights");
        if (weights.length == 0) {
            throw new IllegalArgumentException("weights must be non-empty");
        }
        this.weights = Arrays.copyOf(weights, weights.length);
        long total = 0;
        for (int i = 0; i < this.weights.length; i++) {
            int w = this.weights[i];
            if (w < 0) {
                throw new IllegalArgumentException(
                        "weight at index " + i + " is negative: " + w);
            }
            total += w;
        }
        this.prefix = new long[this.weights.length];
        long acc = 0;
        for (int i = 0; i < this.weights.length; i++) {
            acc += this.weights[i];
            this.prefix[i] = acc;
        }
        this.total = total;
    }

    public WeightedSelector(Integer[] weights) {
        Objects.requireNonNull(weights, "weights");
        if (weights.length == 0) {
            throw new IllegalArgumentException("weights must be non-empty");
        }
        this.weights = new int[weights.length];
        long total = 0;
        for (int i = 0; i < weights.length; i++) {
            int w = weights[i];
            if (w < 0) {
                throw new IllegalArgumentException(
                        "weight at index " + i + " is negative: " + w);
            }
            this.weights[i] = w;
            total += w;
        }
        this.prefix = new long[weights.length];
        long acc = 0;
        for (int i = 0; i < weights.length; i++) {
            acc += this.weights[i];
            this.prefix[i] = acc;
        }
        this.total = total;
    }

    /**
     * Returns the number of outcomes.
     */
    public int size() {
        return weights.length;
    }

    /**
     * Returns the total weight (sum of all entries).
     */
    public long totalWeight() {
        return total;
    }

    /**
     * Returns the raw weight at {@code index}, primarily for diagnostics.
     */
    public int weightAt(int index) {
        return weights[index];
    }

    /**
     * Returns the index that the given {@code seed} selects.
     *
     * <p>Implementation: the seed is reduced to {@code [0, total)} via
     * multiplication with a SplitMix64-style mixer; the result is searched
     * in the prefix-sum table using binary search.
     *
     * <p>If the total weight is zero, returns {@code 0}.
     */
    public int sample(long seed) {
        if (total == 0) {
            return 0;
        }
        long mixed = mix(seed);
        // Use unsigned modulo: maps [0, 2^64) -> [0, total).
        long scaled = (mixed & Long.MAX_VALUE) % total;
        return lowerBound(prefix, scaled);
    }

    private static int lowerBound(long[] arr, long target) {
        int lo = 0;
        int hi = arr.length - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (arr[mid] <= target) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    /**
     * SplitMix64 finalizer (Steele/Lea/Flood). Public domain reference
     * implementation by Sebastiano Vigna (CC0).
     *
     * <p>We adopt this mixer because it is well-vetted, deterministic,
     * has good avalanche, and is unrelated to OptiFine's closed-source
     * hash.
     */
    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b7L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    @Override
    public String toString() {
        return "WeightedSelector[weights=" + Arrays.toString(weights) + ", total=" + total + "]";
    }
}
