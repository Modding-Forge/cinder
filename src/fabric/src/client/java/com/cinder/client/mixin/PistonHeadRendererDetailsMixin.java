package com.cinder.client.mixin;

import com.cinder.config.CinderConfigHolder;
import net.minecraft.client.renderer.blockentity.PistonHeadRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Target: {@link PistonHeadRenderer#submit}.
 *
 * <p>Purpose: gates moving piston block-entity render submission. Preserved
 * behaviour: piston block updates and render-state extraction are untouched.
 *
 * <p>Risk: low-medium because moving piston visuals are common but isolated.
 */
@Mixin(PistonHeadRenderer.class)
public abstract class PistonHeadRendererDetailsMixin {
    @Inject(method = "submit", at = @At("HEAD"), cancellable = true,
            require = 0)
    private void cinder$hideMovingPistons(CallbackInfo ci) {
        if (!CinderConfigHolder.get().entityPistonAnimations()) {
            ci.cancel();
        }
    }
}
