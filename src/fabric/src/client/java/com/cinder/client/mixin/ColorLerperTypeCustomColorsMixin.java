package com.cinder.client.mixin;

import com.cinder.fabric.customcolors.CustomColorsRuntime;
import net.minecraft.client.color.ColorLerper;
import net.minecraft.world.item.DyeColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Target: {@code ColorLerper.Type}.
 *
 * <p>Purpose: applies {@code sheep.*} custom colors to the sheep color lerper,
 * including jeb sheep interpolation. Vanilla lerping and non-sheep color
 * tables are preserved.
 *
 * <p>Risk: low. Single return-value hook, client color only.
 */
@Mixin(targets = "net.minecraft.client.color.ColorLerper$Type")
public abstract class ColorLerperTypeCustomColorsMixin {

    @Inject(method = "getColor", at = @At("RETURN"), cancellable = true)
    private void cinder$customSheepColor(DyeColor dyeColor,
                                         CallbackInfoReturnable<Integer> cir) {
        if ((Object) this == ColorLerper.Type.SHEEP) {
            cir.setReturnValue(CustomColorsRuntime.sheepColor(
                    dyeColor, cir.getReturnValue()));
        }
    }
}
