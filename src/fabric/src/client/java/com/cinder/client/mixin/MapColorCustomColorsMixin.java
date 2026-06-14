package com.cinder.client.mixin;

import com.cinder.fabric.customcolors.CustomColorsRuntime;
import net.minecraft.world.level.material.MapColor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Target: {@link MapColor#calculateARGBColor(MapColor.Brightness)}.
 *
 * <p>Purpose: applies {@code map.*} material color overrides while preserving
 * vanilla brightness scaling by letting vanilla calculate first and replacing
 * only the RGB source when configured.
 *
 * <p>Risk: low. Map/material tint color only; no map data changes.
 */
@Mixin(MapColor.class)
public abstract class MapColorCustomColorsMixin {

    @Shadow
    @Final
    public int id;

    @Inject(method = "calculateARGBColor", at = @At("RETURN"),
            cancellable = true)
    private void cinder$customMapColor(MapColor.Brightness brightness,
                                       CallbackInfoReturnable<Integer> cir) {
        int base = CustomColorsRuntime.mapMaterialColor(this.id,
                cir.getReturnValue());
        if (base != cir.getReturnValue()) {
            cir.setReturnValue(net.minecraft.util.ARGB.scaleRGB(base,
                    brightness.modifier));
        }
    }
}
