package com.cinder.client.mixin;

import com.cinder.fabric.customsky.CustomSkyRuntime;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.world.level.MoonPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Target: {@link SkyRenderer#renderSunMoonAndStars}.
 *
 * <p>Purpose: render Cinder Custom Sky layers after vanilla sun/moon/stars and
 * before the dark-disc pass in Mojang's 26.2 sky FrameGraph path. Vanilla
 * sky rendering is preserved; missing hooks fail safe because this injection
 * is additive and does not overwrite the method.
 *
 * <p>Risk: low-medium. The method is renderer-facing and may move between
 * Minecraft snapshots, but the injection is a TAIL hook with no local capture.
 */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererCustomSkyMixin {

    @Inject(method = "renderSunMoonAndStars", at = @At("TAIL"), require = 0)
    private void cinder$renderCustomSky(PoseStack poseStack,
                                        float sunAngle,
                                        float moonAngle,
                                        float starAngle,
                                        MoonPhase moonPhase,
                                        float rainBrightness,
                                        float starBrightness,
                                        CallbackInfo ci) {
        CustomSkyRuntime.renderOverworld(poseStack, rainBrightness);
    }
}
