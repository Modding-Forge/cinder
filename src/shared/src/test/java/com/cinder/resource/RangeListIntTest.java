package com.cinder.resource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RangeListIntTest {

    @Test
    void emptyInput_parsesToEmpty() {
        assertTrue(RangeListInt.parse("").isEmpty());
        assertTrue(RangeListInt.parse("   ").isEmpty());
        assertTrue(RangeListInt.parse(null).isEmpty());
    }

    @Test
    void singleValue() {
        RangeListInt r = RangeListInt.parse("5");
        assertTrue(r.contains(5));
        assertFalse(r.contains(4));
        assertFalse(r.contains(6));
    }

    @Test
    void simpleRange() {
        RangeListInt r = RangeListInt.parse("3-7");
        for (int v = 3; v <= 7; v++) {
            assertTrue(r.contains(v), "should contain " + v);
        }
        assertFalse(r.contains(2));
        assertFalse(r.contains(8));
    }

    @Test
    void negativeValues() {
        RangeListInt r = RangeListInt.parse("-5");
        assertTrue(r.contains(-5));
        assertFalse(r.contains(-4));
    }

    @Test
    void negativeRange_bothEnds() {
        RangeListInt r = RangeListInt.parse("(-10)-(-3)");
        for (int v = -10; v <= -3; v++) {
            assertTrue(r.contains(v), "should contain " + v);
        }
        assertFalse(r.contains(-11));
        assertFalse(r.contains(-2));
    }

    @Test
    void mixedNegativeAndPositive() {
        RangeListInt r = RangeListInt.parse("(-5)-5");
        for (int v = -5; v <= 5; v++) {
            assertTrue(r.contains(v));
        }
    }

    @Test
    void multipleRanges_merged() {
        RangeListInt r = RangeListInt.parse("1-3 5-7");
        assertTrue(r.contains(1));
        assertTrue(r.contains(2));
        assertTrue(r.contains(3));
        assertFalse(r.contains(4));
        assertTrue(r.contains(5));
        assertTrue(r.contains(6));
        assertTrue(r.contains(7));
    }

    @Test
    void touchingRanges_merged() {
        // "1-3 4-6" touches at 3-4; merged behaviour keeps it contiguous.
        RangeListInt r = RangeListInt.parse("1-3 4-6");
        for (int v = 1; v <= 6; v++) {
            assertTrue(r.contains(v));
        }
    }

    @Test
    void overlappingRanges_merged() {
        RangeListInt r = RangeListInt.parse("1-5 3-7");
        // Merged: 1-7
        for (int v = 1; v <= 7; v++) {
            assertTrue(r.contains(v));
        }
    }

    @Test
    void startGreaterThanEnd_throws() {
        assertThrows(IllegalArgumentException.class, () -> RangeListInt.parse("5-3"));
    }

    @Test
    void malformedInteger_throws() {
        // The parser is strict: any non-numeric input that does not
        // match the documented "value" or "value-value" grammar
        // (optionally parenthesised) produces an
        // IllegalArgumentException, not a NumberFormatException, so
        // that the caller can produce a single uniform error.
        assertThrows(IllegalArgumentException.class, () -> RangeListInt.parse("abc"));
    }

    @Test
    void all_containsEverything() {
        assertTrue(RangeListInt.ALL.contains(Integer.MIN_VALUE));
        assertTrue(RangeListInt.ALL.contains(0));
        assertTrue(RangeListInt.ALL.contains(Integer.MAX_VALUE));
        assertTrue(RangeListInt.ALL.isAll());
    }

    @Test
    void empty_containsNothing() {
        assertFalse(RangeListInt.EMPTY.contains(0));
        assertFalse(RangeListInt.EMPTY.isAll());
    }

    @Test
    void ranges_returnsCopy() {
        RangeListInt r = RangeListInt.parse("1-3");
        int[] a = r.ranges();
        int[] b = r.ranges();
        a[0] = 999;
        assertEquals(1, b[0]);
    }

    @Test
    void toString_representative() {
        assertEquals("RangeListInt[1-3 5]", RangeListInt.parse("1-3 5").toString());
        assertEquals("RangeListInt[]", RangeListInt.EMPTY.toString());
        assertEquals("RangeListInt[all]", RangeListInt.ALL.toString());
    }
}
