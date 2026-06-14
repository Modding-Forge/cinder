package com.cinder.client.mixin;

import com.cinder.fabric.customgui.CustomGuiRuntime;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Custom GUI texture replacement hook.
 *
 * <p>Target: {@link GuiGraphicsExtractor}'s direct texture blit path. Purpose:
 * replace container background texture identifiers selected by the current
 * Custom GUI screen snapshot.
 *
 * <p>Preserved behaviour: coordinates, UVs, color, render pipeline, and
 * texture sampling are unchanged; only the texture identifier may change.
 *
 * <p>Compatibility: risk is low to medium. The hook targets the private
 * direct-texture blit funnel in Minecraft 26.2 and uses {@code require = 0}
 * so missing signatures fail safe.
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorCustomGuiMixin {

    @ModifyVariable(
            method = "innerBlit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;"
                    + "Lnet/minecraft/resources/Identifier;IIIIFFFFI)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0)
    private Identifier cinder$customGuiTexture(Identifier original) {
        return CustomGuiRuntime.override(original);
    }
}
