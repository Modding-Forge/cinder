package com.cinder.client.mixin;

import com.cinder.config.CinderConfigHolder;
import net.minecraft.client.renderer.entity.PaintingRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Target: {@link PaintingRenderer#submit}.
 *
 * <p>Purpose: gates painting render submission without touching entity state.
 *
 * <p>Risk: low. The hook is cancellable and fail-safe.
 */
@Mixin(PaintingRenderer.class)
public abstract class PaintingRendererDetailsMixin {
    @Inject(method = "submit", at = @At("HEAD"), cancellable = true,
            require = 0)
    private void cinder$hidePaintings(CallbackInfo ci) {
        if (!CinderConfigHolder.get().entityPaintings()) {
            ci.cancel();
        }
    }
}
