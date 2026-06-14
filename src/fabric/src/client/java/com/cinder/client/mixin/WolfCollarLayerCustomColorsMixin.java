package com.cinder.client.mixin;

import com.cinder.fabric.customcolors.CustomColorsRuntime;
import net.minecraft.client.renderer.entity.layers.WolfCollarLayer;
import net.minecraft.world.item.DyeColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Target: {@link WolfCollarLayer#submit}.
 *
 * <p>Purpose: applies {@code collar.*} custom colors to wolf collars while
 * preserving vanilla model, texture and render-layer behaviour.
 *
 * <p>Risk: low. Local color value only.
 */
@Mixin(WolfCollarLayer.class)
public abstract class WolfCollarLayerCustomColorsMixin {

    @Redirect(method = "submit",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/DyeColor;getTextureDiffuseColor()I"))
    private int cinder$customWolfCollarColor(DyeColor color) {
        return CustomColorsRuntime.collarColor(color,
                color.getTextureDiffuseColor());
    }
}
