package com.cinder.verify;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class VerifyRecorderTest {

    @Test
    void record_match_incrementsMatches() {
        VerifyRecorder rec = VerifyRecorder.get();
        long before = rec.snapshot().matches();
        rec.recordMatch();
        rec.recordMatch();
        rec.recordMatch();
        assertEquals(before + 3, rec.snapshot().matches());
    }

    @Test
    void record_mismatch_incrementsMismatches() {
        VerifyRecorder rec = VerifyRecorder.get();
        long before = rec.snapshot().mismatches();
        rec.recordMismatch();
        assertEquals(before + 1, rec.snapshot().mismatches());
    }

    @Test
    void record_skip_incrementsSkips() {
        VerifyRecorder rec = VerifyRecorder.get();
        long before = rec.snapshot().skips();
        rec.recordSkip();
        rec.recordSkip();
        assertEquals(before + 2, rec.snapshot().skips());
    }

    @Test
    void stats_snapshot_isImmutable() {
        VerifyRecorder rec = VerifyRecorder.get();
        rec.recordMatch();
        VerifyStats s1 = rec.snapshot();
        long m1 = s1.matches();
        rec.recordMatch();
        VerifyStats s2 = rec.snapshot();
        // s1 is a snapshot taken before the second match - it
        // must not change retroactively.
        assertEquals(m1, s1.matches());
        assertNotEquals(m1, s2.matches());
    }

    @Test
    void reset_clearsAll() {
        VerifyRecorder rec = VerifyRecorder.get();
        rec.recordMatch();
        rec.recordMismatch();
        rec.recordSkip();
        rec.reset();
        VerifyStats s = rec.snapshot();
        assertEquals(0L, s.matches());
        assertEquals(0L, s.mismatches());
        assertEquals(0L, s.skips());
    }

    @Test
    void describe_returnsDecimalString() {
        assertEquals("42", VerifyRecorder.describe(42));
        assertEquals("<min>", VerifyRecorder.describe(Integer.MIN_VALUE));
    }
}
