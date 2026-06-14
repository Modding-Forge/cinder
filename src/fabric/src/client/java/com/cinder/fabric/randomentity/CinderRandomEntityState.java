package com.cinder.fabric.randomentity;

import com.cinder.randomentity.RandomEntityContext;

/**
 * Accessor attached to Minecraft entity render states by a tiny mixin.
 *
 * <p>Threading: the render state is per extracted entity render submission, so
 * storing the immutable context on it avoids global mutable entity state.
 */
public interface CinderRandomEntityState {
    void cinder$setRandomEntityContext(RandomEntityContext context);

    RandomEntityContext cinder$getRandomEntityContext();

    void cinder$setRandomEntityVariantIndex(int index);

    int cinder$getRandomEntityVariantIndex();
}
