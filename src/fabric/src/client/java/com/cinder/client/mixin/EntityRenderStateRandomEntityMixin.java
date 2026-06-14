package com.cinder.client.mixin;

import com.cinder.fabric.randomentity.CinderRandomEntityState;
import com.cinder.randomentity.RandomEntityContext;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Target: {@link EntityRenderState}.
 *
 * <p>Purpose: attach the immutable Random Entity evaluation context to the
 * per-entity render state so later texture hooks do not need global mutable
 * current-entity state.
 *
 * <p>Preserved behavior: no vanilla fields or methods are changed.
 *
 * <p>Compatibility: adds only one private field and an interface.
 *
 * <p>Risk: low.
 */
@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateRandomEntityMixin
        implements CinderRandomEntityState {
    @Unique
    private RandomEntityContext cinder$randomEntityContext;

    @Unique
    private int cinder$randomEntityVariantIndex = -1;

    @Override
    public void cinder$setRandomEntityContext(RandomEntityContext context) {
        this.cinder$randomEntityContext = context;
    }

    @Override
    public RandomEntityContext cinder$getRandomEntityContext() {
        return cinder$randomEntityContext;
    }

    @Override
    public void cinder$setRandomEntityVariantIndex(int index) {
        this.cinder$randomEntityVariantIndex = index;
    }

    @Override
    public int cinder$getRandomEntityVariantIndex() {
        return cinder$randomEntityVariantIndex;
    }
}
