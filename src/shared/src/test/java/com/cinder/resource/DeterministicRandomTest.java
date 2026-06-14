package com.cinder.resource;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicRandomTest {

    @Test
    void sameInput_sameHash() {
        int h1 = DeterministicRandom.hash(10, 20, 30, 0);
        int h2 = DeterministicRandom.hash(10, 20, 30, 0);
        assertEquals(h1, h2);
    }

    @Test
    void differentInputs_differentHashes() {
        // Three points close in space: avalanche should make them distinct.
        Set<Integer> seen = new HashSet<>();
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                for (int z = 0; z < 8; z++) {
                    seen.add(DeterministicRandom.hash(x, y, z, 0));
                }
            }
        }
        assertEquals(8 * 8 * 8, seen.size(), "All 512 points should hash to distinct values");
    }

    @Test
    void sideChangesHash() {
        int a = DeterministicRandom.hash(10, 20, 30, 0);
        int b = DeterministicRandom.hash(10, 20, 30, 1);
        assertNotEquals(a, b);
    }

    @Test
    void nextInt_zeroBoundThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> DeterministicRandom.nextInt(0, 0, 0, 0, 0));
    }

    @Test
    void nextInt_negativeBoundThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> DeterministicRandom.nextInt(0, 0, 0, 0, -1));
    }

    @Test
    void nextInt_withinBound() {
        int bound = 7;
        for (int i = 0; i < 10_000; i++) {
            int v = DeterministicRandom.nextInt(i, i, i, 0, bound);
            assertTrue(v >= 0 && v < bound, "got " + v);
        }
    }

    @Test
    void nextInt_distribution() {
        int bound = 4;
        int[] hits = new int[bound];
        int n = 100_000;
        for (int i = 0; i < n; i++) {
            hits[DeterministicRandom.nextInt(i, 0, 0, 0, bound)]++;
        }
        for (int i = 0; i < bound; i++) {
            double ratio = hits[i] / (double) n;
            assertTrue(Math.abs(ratio - 0.25) < 0.01, "bin " + i + " ratio " + ratio);
        }
    }

    @Test
    void nextFlip_isBinary() {
        for (int i = 0; i < 1000; i++) {
            int f = DeterministicRandom.nextFlip(i, 0, 0, 0);
            assertTrue(f == 0 || f == 1);
        }
    }
}
