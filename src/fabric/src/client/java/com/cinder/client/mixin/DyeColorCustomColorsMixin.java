package com.cinder.client.mixin;

import com.cinder.fabric.customcolors.CustomColorsRuntime;
import net.minecraft.world.item.DyeColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Target: {@link DyeColor}.
 *
 * <p>Purpose: applies OptiFine {@code dye.*} and sign-text color overrides at
 * the central vanilla dye-color accessors. Vanilla values are preserved when
 * Custom Colors is disabled or no override exists.
 *
 * <p>Risk: low. The hook reads an immutable Cinder snapshot and changes only
 * returned client-side colors.
 */
@Mixin(DyeColor.class)
public abstract class DyeColorCustomColorsMixin {

    @Inject(method = "getTextureDiffuseColor", at = @At("RETURN"),
            cancellable = true)
    private void cinder$customDyeColor(
            CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(CustomColorsRuntime.dyeTextureColor(
                (DyeColor) (Object) this, cir.getReturnValue()));
    }

    @Inject(method = "getTextColor", at = @At("RETURN"), cancellable = true)
    private void cinder$customDyeTextColor(
            CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(CustomColorsRuntime.dyeTextColor(
                (DyeColor) (Object) this, cir.getReturnValue()));
    }
}
