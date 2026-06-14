package com.cinder.fabric.natural;

import com.cinder.natural.NaturalTextureRuleSet;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Atomic client snapshot holder for Natural Textures.
 *
 * <p>Threading: immutable snapshots are swapped atomically during resource
 * reload and read by Sodium meshing threads.
 */
public final class NaturalTexturesRuntime {

    private static final AtomicReference<NaturalTextureRuleSet> SNAPSHOT =
            new AtomicReference<>(NaturalTextureRuleSet.empty());

    private NaturalTexturesRuntime() {
    }

    public static void replace(NaturalTextureRuleSet ruleSet) {
        SNAPSHOT.set(ruleSet == null ? NaturalTextureRuleSet.empty()
                : ruleSet);
    }

    public static NaturalTextureRuleSet snapshot() {
        return SNAPSHOT.get();
    }
}
