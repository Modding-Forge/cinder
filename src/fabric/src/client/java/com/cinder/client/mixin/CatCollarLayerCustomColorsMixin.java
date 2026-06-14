package com.cinder.client.mixin;

import com.cinder.fabric.customcolors.CustomColorsRuntime;
import net.minecraft.client.renderer.entity.layers.CatCollarLayer;
import net.minecraft.world.item.DyeColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Target: {@link CatCollarLayer#submit}.
 *
 * <p>Purpose: applies {@code collar.*} custom colors to cat collars while
 * preserving vanilla model, texture and render-layer behaviour.
 *
 * <p>Risk: low. Local color value only.
 */
@Mixin(CatCollarLayer.class)
public abstract class CatCollarLayerCustomColorsMixin {

    @Redirect(method = "submit",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/DyeColor;getTextureDiffuseColor()I"))
    private int cinder$customCatCollarColor(DyeColor color) {
        return CustomColorsRuntime.collarColor(color,
                color.getTextureDiffuseColor());
    }
}
