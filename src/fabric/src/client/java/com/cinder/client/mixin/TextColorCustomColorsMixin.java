package com.cinder.client.mixin;

import com.cinder.fabric.customcolors.CustomColorsRuntime;
import net.minecraft.network.chat.TextColor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Target: {@link TextColor#getValue()}.
 *
 * <p>Purpose: applies OptiFine {@code text.code.0-15} overrides for named
 * legacy formatting colors. Explicit custom RGB text colors are left intact.
 *
 * <p>Risk: low. Immutable snapshot lookup, client text color only.
 */
@Mixin(TextColor.class)
public abstract class TextColorCustomColorsMixin {

    @Shadow
    @Final
    private String name;

    @Inject(method = "getValue", at = @At("RETURN"), cancellable = true)
    private void cinder$customTextCodeColor(
            CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(CustomColorsRuntime.textColor(
                this.name, cir.getReturnValue()));
    }
}
