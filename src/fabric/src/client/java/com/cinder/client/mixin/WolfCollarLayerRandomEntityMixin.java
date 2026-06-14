package com.cinder.client.mixin;

import com.cinder.fabric.randomentity.CinderRandomEntityState;
import com.cinder.fabric.randomentity.RandomEntityRuntime;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.WolfCollarLayer;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Target: {@link WolfCollarLayer#submit}.
 *
 * <p>Purpose: remap wolf collar layer textures to the same Random Entity
 * variant index chosen for the wolf base texture.
 *
 * <p>Preserved behavior: vanilla still gates tame/invisible state, selects the
 * collar color, submits the parent model and uses the normal cutout render
 * type. Cinder only changes the texture identifier passed to that render type.
 *
 * <p>Compatibility: {@code require = 0} keeps the hook optional for renderer
 * refactors.
 *
 * <p>Risk: low; this is a single texture expression in a leaf layer.
 */
@Mixin(WolfCollarLayer.class)
public abstract class WolfCollarLayerRandomEntityMixin {
    @ModifyArg(method = "submit",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/rendertype/"
                            + "RenderTypes;entityCutout"
                            + "(Lnet/minecraft/resources/Identifier;)"
                            + "Lnet/minecraft/client/renderer/"
                            + "RenderType;",
                    ordinal = 0),
            index = 0,
            require = 0)
    private Identifier cinder$randomEntityCollarTexture(Identifier texture,
                                                        PoseStack poseStack,
                                                        SubmitNodeCollector nodes,
                                                        int lightCoords,
                                                        WolfRenderState state,
                                                        float yRot,
                                                        float xRot) {
        if (state instanceof CinderRandomEntityState cinderState) {
            return RandomEntityRuntime.remap(texture,
                    cinderState.cinder$getRandomEntityVariantIndex());
        }
        return texture;
    }
}
