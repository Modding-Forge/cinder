package com.cinder.client.mixin;

import com.cinder.fabric.customcolors.CustomColorsRuntime;
import net.minecraft.client.renderer.blockentity.AbstractSignRenderer;
import net.minecraft.world.level.block.entity.SignText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Target: {@link AbstractSignRenderer#getDarkColor(SignText)}.
 *
 * <p>Purpose: applies the broad {@code text.sign} override for non-glowing
 * sign text. Per-dye glowing sign text is handled by {@code DyeColor}.
 *
 * <p>Risk: low. Sign text color only.
 */
@Mixin(AbstractSignRenderer.class)
public abstract class AbstractSignRendererCustomColorsMixin {

    @Inject(method = "getDarkColor", at = @At("RETURN"), cancellable = true)
    private static void cinder$customSignText(SignText signText,
                                              CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(CustomColorsRuntime.overrideArgb(
                "text.sign", cir.getReturnValue()));
    }
}
