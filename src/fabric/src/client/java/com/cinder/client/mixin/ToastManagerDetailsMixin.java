package com.cinder.client.mixin;

import com.cinder.config.CinderConfig;
import com.cinder.config.CinderConfigHolder;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.gui.components.toasts.RecipeToast;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.components.toasts.TutorialToast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Target: {@link ToastManager#addToast}.
 *
 * <p>Purpose: filters vanilla toast categories. Preserved behaviour: allowed
 * toasts queue and render through the normal vanilla manager.
 *
 * <p>Risk: low. Cancelling a toast only prevents a client notification.
 */
@Mixin(ToastManager.class)
public abstract class ToastManagerDetailsMixin {
    @Inject(method = "addToast", at = @At("HEAD"), cancellable = true,
            require = 0)
    private void cinder$filterToasts(Toast toast, CallbackInfo ci) {
        CinderConfig cfg = CinderConfigHolder.get();
        if (toast instanceof AdvancementToast && !cfg.toastAdvancement()
                || toast instanceof RecipeToast && !cfg.toastRecipe()
                || toast instanceof SystemToast && !cfg.toastSystem()
                || toast instanceof TutorialToast && !cfg.toastTutorial()) {
            ci.cancel();
        }
    }
}
