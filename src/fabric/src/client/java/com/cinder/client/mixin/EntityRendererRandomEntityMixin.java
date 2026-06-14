package com.cinder.client.mixin;

import com.cinder.fabric.randomentity.CinderRandomEntityState;
import com.cinder.fabric.randomentity.RandomEntityRuntime;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Target: {@link EntityRenderer#extractRenderState(Entity, EntityRenderState, float)}.
 *
 * <p>Purpose: capture entity facts once during render-state extraction for
 * Random Entity texture matching.
 *
 * <p>Preserved behavior: vanilla state extraction runs first and is not
 * modified; Cinder only stores its own immutable side payload.
 *
 * <p>Compatibility: {@code require = 0} keeps startup safe if the method moves.
 *
 * <p>Risk: low.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererRandomEntityMixin<T extends Entity,
        S extends EntityRenderState> {
    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void cinder$captureRandomEntityContext(T entity,
                                                   S state,
                                                   float partialTicks,
                                                   CallbackInfo ci) {
        if (state instanceof CinderRandomEntityState cinderState) {
            cinderState.cinder$setRandomEntityContext(
                    RandomEntityRuntime.capture(entity));
            cinderState.cinder$setRandomEntityVariantIndex(-1);
        }
    }
}
