package com.cinder.client.mixin;

import com.cinder.config.CinderConfigHolder;
import net.minecraft.client.renderer.blockentity.EnchantTableRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Target: {@link EnchantTableRenderer#submit}.
 *
 * <p>Purpose: gates enchanting table book render submission. Preserved
 * behaviour: block entity state and screen behaviour are untouched.
 *
 * <p>Risk: low.
 */
@Mixin(EnchantTableRenderer.class)
public abstract class EnchantTableRendererDetailsMixin {
    @Inject(method = "submit", at = @At("HEAD"), cancellable = true,
            require = 0)
    private void cinder$hideBook(CallbackInfo ci) {
        if (!CinderConfigHolder.get().entityEnchantingTableBook()) {
            ci.cancel();
        }
    }
}
