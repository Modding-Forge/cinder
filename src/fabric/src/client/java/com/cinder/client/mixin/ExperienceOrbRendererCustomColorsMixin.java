package com.cinder.client.mixin;

import com.cinder.fabric.customcolors.CustomColorsRuntime;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ExperienceOrbRenderer;
import net.minecraft.client.renderer.entity.state.ExperienceOrbRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Target: {@link ExperienceOrbRenderer#submit}.
 *
 * <p>Purpose: applies the {@code xporb.png} special colormap to the XP orb
 * vertex color while preserving the vanilla texture, geometry and alpha.
 *
 * <p>Risk: medium-low. The hook touches four identical vertex calls but only
 * changes color arguments.
 */
@Mixin(ExperienceOrbRenderer.class)
public abstract class ExperienceOrbRendererCustomColorsMixin {

    private static final ThreadLocal<Float> CINDER_XP_ORB_AGE =
            ThreadLocal.withInitial(() -> 0.0F);

    @Inject(method = "submit", at = @At("HEAD"))
    private void cinder$captureXpOrbAge(ExperienceOrbRenderState state,
                                        PoseStack poseStack,
                                        SubmitNodeCollector submitNodeCollector,
                                        CameraRenderState camera,
                                        CallbackInfo ci) {
        CINDER_XP_ORB_AGE.set(state.ageInTicks);
    }

    @Redirect(method = "submit",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/ExperienceOrbRenderer;vertex(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lcom/mojang/blaze3d/vertex/PoseStack$Pose;FFIIIFFI)V"),
            require = 0)
    private void cinder$customXpOrbVertex(VertexConsumer buffer,
                                          PoseStack.Pose pose,
                                          float x,
                                          float y,
                                          int red,
                                          int green,
                                          int blue,
                                          float u,
                                          float v,
                                          int lightCoords) {
        int fallback = ARGB.color(128, red, green, blue);
        int color = CustomColorsRuntime.xpOrbColor(
                CINDER_XP_ORB_AGE.get(), fallback);
        buffer.addVertex(pose, x, y, 0.0F)
                .setColor(ARGB.red(color), ARGB.green(color),
                        ARGB.blue(color), ARGB.alpha(color))
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(lightCoords)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    @Inject(method = "submit", at = @At("RETURN"))
    private void cinder$clearXpOrbAge(ExperienceOrbRenderState state,
                                      PoseStack poseStack,
                                      SubmitNodeCollector submitNodeCollector,
                                      CameraRenderState camera,
                                      CallbackInfo ci) {
        CINDER_XP_ORB_AGE.remove();
    }
}
