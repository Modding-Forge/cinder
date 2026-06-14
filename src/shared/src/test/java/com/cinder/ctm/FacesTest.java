package com.cinder.ctm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FacesTest {

    @Test
    void delta_returnsUnitVector() {
        assertArrayEquals(new int[]{0, -1, 0}, Faces.delta(Faces.DOWN));
        assertArrayEquals(new int[]{0, 1, 0}, Faces.delta(Faces.UP));
        assertArrayEquals(new int[]{0, 0, -1}, Faces.delta(Faces.NORTH));
        assertArrayEquals(new int[]{0, 0, 1}, Faces.delta(Faces.SOUTH));
        assertArrayEquals(new int[]{-1, 0, 0}, Faces.delta(Faces.WEST));
        assertArrayEquals(new int[]{1, 0, 0}, Faces.delta(Faces.EAST));
    }

    @Test
    void orthogonalSides_isLength4() {
        for (int f = 0; f < 6; f++) {
            int[] sides = Faces.orthogonalSides(f);
            assertEquals(4, sides.length, "face " + f + " must have 4 sides");
        }
    }

    @Test
    void diagonals_isLength4() {
        for (int f = 0; f < 6; f++) {
            int[][] d = Faces.diagonals(f);
            assertEquals(4, d.length, "face " + f + " must have 4 diagonals");
        }
    }

    @Test
    void diagonals_areUniqueOffsets() {
        for (int f = 0; f < 6; f++) {
            int[][] d = Faces.diagonals(f);
            // Each diagonal must be unique in (dx, dy, dz).
            for (int i = 0; i < d.length; i++) {
                for (int j = i + 1; j < d.length; j++) {
                    boolean same = d[i][0] == d[j][0]
                            && d[i][1] == d[j][1]
                            && d[i][2] == d[j][2];
                    if (same) {
                        throw new AssertionError(
                                "face " + f + ": duplicate diagonal at i=" + i + " j=" + j);
                    }
                }
            }
        }
    }

    @Test
    void invalidFace_throws() {
        assertThrows(IllegalArgumentException.class, () -> Faces.delta(6));
        assertThrows(IllegalArgumentException.class, () -> Faces.orthogonalSides(6));
        assertThrows(IllegalArgumentException.class, () -> Faces.diagonals(6));
    }
}
