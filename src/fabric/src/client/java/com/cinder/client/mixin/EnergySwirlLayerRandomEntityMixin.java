package com.cinder.client.mixin;

import com.cinder.fabric.randomentity.CinderRandomEntityState;
import com.cinder.fabric.randomentity.RandomEntityRuntime;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EnergySwirlLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Target: {@link EnergySwirlLayer#submit}.
 *
 * <p>Purpose: remap energy-swirl layer textures, such as charged creeper
 * armor, to the same Random Entity variant index chosen for the base entity
 * texture.
 *
 * <p>Preserved behavior: vanilla still controls whether the layer renders,
 * model selection, UV scrolling, color, lighting and submit order. Cinder only
 * swaps the texture identifier when a matching numbered layer variant exists.
 *
 * <p>Compatibility: {@code require = 0} fails safe if Mojang reshapes the
 * layer submit method.
 *
 * <p>Risk: low; the hook is limited to the texture identifier returned inside
 * the existing layer render path.
 */
@Mixin(EnergySwirlLayer.class)
public abstract class EnergySwirlLayerRandomEntityMixin {
    @ModifyExpressionValue(method = "submit",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/layers/"
                            + "EnergySwirlLayer;getTextureLocation()"
                            + "Lnet/minecraft/resources/Identifier;"),
            require = 0)
    private Identifier cinder$randomEntityLayerTexture(Identifier texture,
                                                       PoseStack poseStack,
                                                       SubmitNodeCollector nodes,
                                                       int lightCoords,
                                                       EntityRenderState state,
                                                       float yRot,
                                                       float xRot) {
        if (state instanceof CinderRandomEntityState cinderState) {
            return RandomEntityRuntime.remap(texture,
                    cinderState.cinder$getRandomEntityVariantIndex());
        }
        return texture;
    }
}
