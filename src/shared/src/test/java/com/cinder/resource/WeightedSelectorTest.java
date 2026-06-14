package com.cinder.resource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeightedSelectorTest {

    @Test
    void uniform_singleOutcome() {
        WeightedSelector s = new WeightedSelector(new int[]{1});
        for (long seed = 0; seed < 100; seed++) {
            assertEquals(0, s.sample(seed));
        }
    }

    @Test
    void uniform_fairDistribution() {
        WeightedSelector s = new WeightedSelector(new int[]{1, 1, 1, 1});
        int[] hits = new int[4];
        int n = 100_000;
        for (long seed = 0; seed < n; seed++) {
            hits[s.sample(seed)]++;
        }
        // 25% each +/- 1%.
        for (int i = 0; i < 4; i++) {
            double ratio = hits[i] / (double) n;
            assertTrue(Math.abs(ratio - 0.25) < 0.01,
                    "Bin " + i + " had " + ratio + ", expected ~0.25");
        }
    }

    @Test
    void weighted_skewsTowardsHigher() {
        WeightedSelector s = new WeightedSelector(new int[]{1, 1, 1, 100});
        int high = 0;
        int n = 100_000;
        for (long seed = 0; seed < n; seed++) {
            if (s.sample(seed) == 3) {
                high++;
            }
        }
        double ratio = high / (double) n;
        // Expected: 100/103 ~= 0.97
        assertTrue(ratio > 0.95, "High-weight bin should dominate, got " + ratio);
    }

    @Test
    void zeroTotal_returnsZero() {
        WeightedSelector s = new WeightedSelector(new int[]{0, 0, 0});
        for (long seed = 0; seed < 10; seed++) {
            assertEquals(0, s.sample(seed));
        }
    }

    @Test
    void mixedZeroAndPositive_onlyZerosAreNotSampled() {
        WeightedSelector s = new WeightedSelector(new int[]{0, 1, 0, 1});
        for (long seed = 0; seed < 1000; seed++) {
            int v = s.sample(seed);
            assertTrue(v == 1 || v == 3, "got " + v);
        }
    }

    @Test
    void negativeWeight_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new WeightedSelector(new int[]{1, -1}));
    }

    @Test
    void emptyWeights_throws() {
        assertThrows(IllegalArgumentException.class, () -> new WeightedSelector(new int[]{}));
    }

    @Test
    void deterministic_sameInputSameOutput() {
        WeightedSelector s = new WeightedSelector(new int[]{2, 3, 5});
        for (long seed = 0; seed < 1000; seed++) {
            assertEquals(s.sample(seed), s.sample(seed));
        }
    }

    @Test
    void largeWeights_noOverflow() {
        // Test that we use long arithmetic internally.
        WeightedSelector s = new WeightedSelector(new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE});
        long total = s.totalWeight();
        assertEquals((long) Integer.MAX_VALUE * 2, total);
    }
}
