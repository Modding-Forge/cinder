package com.cinder.client.mixin;

import com.cinder.config.CinderConfigHolder;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Target: {@link ItemFrameRenderer#submit}.
 *
 * <p>Purpose: gates item frame render submission. Preserved behaviour: entity
 * state extraction and ticking are untouched.
 *
 * <p>Risk: low. A missing target simply leaves vanilla rendering enabled.
 */
@Mixin(ItemFrameRenderer.class)
public abstract class ItemFrameRendererDetailsMixin {
    @Inject(method = "submit", at = @At("HEAD"), cancellable = true,
            require = 0)
    private void cinder$hideItemFrames(CallbackInfo ci) {
        if (!CinderConfigHolder.get().entityItemFrames()) {
            ci.cancel();
        }
    }
}
