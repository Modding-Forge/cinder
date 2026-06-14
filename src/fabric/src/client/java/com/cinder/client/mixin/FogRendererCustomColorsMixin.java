package com.cinder.client.mixin;

import com.cinder.fabric.customcolors.CustomColorsRuntime;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.util.ARGB;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Target: {@link FogRenderer#setupFog}.
 *
 * <p>Purpose: applies Custom Colors fog targets after vanilla has computed
 * darkness, water vision and night-vision adjustments. This covers
 * {@code fog.end}, {@code fog.water}, {@code fog.lava},
 * {@code underwater.png}, {@code underlava.png} and {@code fog0.png}.
 *
 * <p>Risk: medium-low. The hook changes only the final fog color vector and
 * leaves all distance/falloff data untouched.
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererCustomColorsMixin {

    @Inject(method = "computeFogColor", at = @At("RETURN"))
    private void cinder$customFogColor(Camera camera,
                                       float partialTicks,
                                       ClientLevel level,
                                       int renderDistance,
                                       float darkenWorldAmount,
                                       Vector4f dest,
                                       CallbackInfo ci) {
        int fallback = ARGB.colorFromFloat(dest.w, dest.x, dest.y, dest.z);
        int color = CustomColorsRuntime.fogColor(camera.getFluidInCamera(),
                level, fallback);
        if (color != fallback) {
            ARGB.setVector4fFromARGB32(dest, color);
        }
    }
}
