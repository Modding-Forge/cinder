package com.cinder.client.mixin;

import com.cinder.config.CinderConfigHolder;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Target: {@link WeatherEffectRenderer#render}.
 *
 * <p>Purpose: implements the Rain/Snow detail toggle without cancelling
 * {@link net.minecraft.client.renderer.LevelRenderer}'s weather pass, so world
 * border rendering in that pass remains preserved.
 *
 * <p>Risk: low. Cancels one leaf renderer method when disabled.
 */
@Mixin(WeatherEffectRenderer.class)
public abstract class WeatherEffectRendererDetailsMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true,
            require = 0)
    private void cinder$hideWeather(Vec3 cameraPos,
                                    WeatherRenderState renderState,
                                    CallbackInfo ci) {
        if (!CinderConfigHolder.get().detailsRainSnowEnabled()) {
            ci.cancel();
        }
    }
}
