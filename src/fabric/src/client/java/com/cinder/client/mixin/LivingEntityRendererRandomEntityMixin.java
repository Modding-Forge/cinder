package com.cinder.client.mixin;

import com.cinder.fabric.randomentity.CinderRandomEntityState;
import com.cinder.fabric.randomentity.RandomEntityRuntime;
import com.cinder.randomentity.RandomEntityContext;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Target: {@link LivingEntityRenderer#getRenderType}.
 *
 * <p>Purpose: remap the base living-entity texture selected by vanilla to an
 * OptiFine/ETF-style Random Entity variant.
 *
 * <p>Preserved behavior: vanilla still chooses visibility, translucency,
 * outline, model render type, lighting, and state submission. Cinder changes
 * only the texture identifier passed into the same vanilla render-type path.
 *
 * <p>Compatibility: {@code require = 0} lets the mod fail safe if Mojang
 * reshapes the render type method.
 *
 * <p>Risk: medium; the hook targets a renderer method shared by all living
 * entity renderers.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererRandomEntityMixin {
    @ModifyExpressionValue(method = "getRenderType",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/"
                            + "LivingEntityRenderer;getTextureLocation"
                            + "(Lnet/minecraft/client/renderer/entity/state/"
                            + "LivingEntityRenderState;)"
                            + "Lnet/minecraft/resources/Identifier;"),
            require = 0)
    private Identifier cinder$randomEntityTexture(Identifier base,
            LivingEntityRenderState state) {
        if (state instanceof CinderRandomEntityState cinderState) {
            RandomEntityContext context =
                    cinderState.cinder$getRandomEntityContext();
            int variant = RandomEntityRuntime.resolveIndex(base, context);
            cinderState.cinder$setRandomEntityVariantIndex(variant);
            return RandomEntityRuntime.remap(base, variant);
        }
        return base;
    }
}
