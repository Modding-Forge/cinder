package com.cinder.client.mixin;

import com.cinder.fabric.cem.CemRuntime;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.CreeperRenderer;
import net.minecraft.client.renderer.entity.state.CreeperRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Target: {@link CreeperRenderer}.
 *
 * <p>Purpose: first narrow CEM smoke hook. A parsed {@code creeper.jem}
 * switches the creeper texture and applies an exaggerated scale so the runtime
 * connection is immediately visible in-game.
 *
 * <p>Preserved behavior: vanilla animation, swelling wobble, render layers,
 * lighting, hitbox, AI and networking are unchanged. This hook only modifies
 * client-side render texture and pose scale.
 *
 * <p>Compatibility: {@code require = 0} keeps both injections optional if
 * Mojang reshapes the renderer.
 *
 * <p>Risk: low-medium; this is intentionally creeper-only until broad CEM
 * model-part replacement is implemented.
 */
@Mixin(CreeperRenderer.class)
public abstract class CreeperRendererCemMixin {
    @Inject(method = "getTextureLocation",
            at = @At("RETURN"),
            cancellable = true,
            require = 0)
    private void cinder$cemTexture(CreeperRenderState state,
                                   CallbackInfoReturnable<Identifier> cir) {
        cir.setReturnValue(CemRuntime.texture("creeper",
                cir.getReturnValue()));
    }

    @Inject(method = "scale", at = @At("TAIL"), require = 0)
    private void cinder$cemScale(CreeperRenderState state,
                                 PoseStack poseStack,
                                 CallbackInfo ci) {
        if (CemRuntime.hasModel("creeper")) {
            poseStack.scale(1.35f, 1.15f, 1.35f);
        }
    }
}
