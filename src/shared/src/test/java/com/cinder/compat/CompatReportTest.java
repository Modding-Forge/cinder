package com.cinder.compat;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompatReportTest {

    @Test
    void emptyReport_isCompatible() {
        CompatReport rep = CompatReport.build(id -> false);
        assertFalse(rep.isPresent(CompatDefaults.SODIUM));
        assertFalse(rep.isPresent(CompatDefaults.IRIS));
        assertTrue(rep.presentMods().isEmpty());
    }

    @Test
    void sodiumPresent_isReported() {
        CompatReport rep = CompatReport.build(
                id -> CompatDefaults.SODIUM.equals(id));
        assertTrue(rep.isPresent(CompatDefaults.SODIUM));
        assertFalse(rep.isPresent(CompatDefaults.IRIS));
    }

    @Test
    void irisPresent_isReported() {
        CompatReport rep = CompatReport.build(
                id -> CompatDefaults.IRIS.equals(id));
        assertTrue(rep.isPresent(CompatDefaults.IRIS));
        assertFalse(rep.isPresent(CompatDefaults.SODIUM));
    }

    @Test
    void multiplePresent_aggregated() {
        CompatReport rep = CompatReport.build(
                id -> CompatDefaults.SODIUM.equals(id)
                        || CompatDefaults.IRIS.equals(id));
        Set<String> present = rep.presentMods();
        assertEquals(2, present.size());
        assertTrue(present.contains(CompatDefaults.SODIUM));
        assertTrue(present.contains(CompatDefaults.IRIS));
    }

    @Test
    void raw_containsAllKnownIds() {
        CompatReport rep = CompatReport.build(id -> false);
        assertEquals(CompatDefaults.knownModIds().size(),
                rep.raw().size());
    }
}
