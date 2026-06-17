package com.argus.ctm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CtmRuleRuntimeProfileTest {

    @Test
    void fixedRandomAndRepeatNeedNoConnectivity() {
        assertConnectivity(CtmMethod.FIXED, CtmConnectivityProfile.NONE);
        assertConnectivity(CtmMethod.RANDOM, CtmConnectivityProfile.NONE);
        assertConnectivity(CtmMethod.REPEAT, CtmConnectivityProfile.NONE);
        assertConnectivity(CtmMethod.OVERLAY_FIXED,
                CtmConnectivityProfile.NONE);
        assertConnectivity(CtmMethod.OVERLAY_RANDOM,
                CtmConnectivityProfile.NONE);
        assertConnectivity(CtmMethod.OVERLAY_REPEAT,
                CtmConnectivityProfile.NONE);
    }

    @Test
    void directionalMethodsExposeNarrowConnectivity() {
        assertConnectivity(CtmMethod.TOP, CtmConnectivityProfile.TOP_1);
        assertConnectivity(CtmMethod.HORIZONTAL,
                CtmConnectivityProfile.HORIZONTAL_2);
        assertConnectivity(CtmMethod.VERTICAL,
                CtmConnectivityProfile.VERTICAL_2);
        assertConnectivity(CtmMethod.HORIZONTAL_VERTICAL,
                CtmConnectivityProfile.CARDINAL_4);
        assertConnectivity(CtmMethod.VERTICAL_HORIZONTAL,
                CtmConnectivityProfile.CARDINAL_4);
    }

    @Test
    void ctmAndConnectedOverlaysMayNeedFullEightNeighbours() {
        assertConnectivity(CtmMethod.CTM, CtmConnectivityProfile.FULL_8);
        assertConnectivity(CtmMethod.CTM_COMPACT,
                CtmConnectivityProfile.FULL_8);
        assertConnectivity(CtmMethod.OVERLAY, CtmConnectivityProfile.FULL_8);
        assertConnectivity(CtmMethod.OVERLAY_CTM,
                CtmConnectivityProfile.FULL_8);
    }

    @Test
    void methodTraitsAreExposed() {
        CtmRuleRuntimeProfile random =
                CtmRuleRuntimeProfile.of(CtmMethod.OVERLAY_RANDOM);
        assertTrue(random.isOverlay());
        assertTrue(random.isRandom());
        assertFalse(random.isRepeat());
        assertFalse(random.isLayered());

        CtmRuleRuntimeProfile layered =
                CtmRuleRuntimeProfile.of(CtmMethod.HORIZONTAL_VERTICAL);
        assertTrue(layered.isLayered());
        assertFalse(layered.isOverlay());
    }

    @Test
    void ruleCarriesRuntimeProfile() {
        CtmRule rule = CtmRule.builder()
                .method(CtmMethod.VERTICAL)
                .addTile(CtmTileSpec.numeric(0))
                .build();
        assertEquals(CtmMethod.VERTICAL, rule.runtimeProfile().method());
        assertEquals(CtmConnectivityProfile.VERTICAL_2,
                rule.runtimeProfile().connectivity());
    }

    @Test
    void weightedRandomSelectorIsCompiledWithRuleSnapshot() {
        CtmRule rule = CtmRule.builder()
                .method(CtmMethod.RANDOM)
                .addTile(CtmTileSpec.numeric(0))
                .addTile(CtmTileSpec.numeric(1))
                .randomWeights(java.util.List.of(1, 99))
                .build();

        assertNotNull(rule.randomSelector());
        assertEquals(2, rule.randomSelector().size());
    }

    @Test
    void incompleteRandomWeightsDoNotCreateCompiledSelector() {
        CtmRule rule = CtmRule.builder()
                .method(CtmMethod.RANDOM)
                .addTile(CtmTileSpec.numeric(0))
                .addTile(CtmTileSpec.numeric(1))
                .randomWeights(java.util.List.of(99))
                .build();

        assertNull(rule.randomSelector());
    }

    private static void assertConnectivity(CtmMethod method,
                                           CtmConnectivityProfile expected) {
        CtmRuleRuntimeProfile profile = CtmRuleRuntimeProfile.of(method);
        assertEquals(method, profile.method());
        assertEquals(expected, profile.connectivity());
    }
}
