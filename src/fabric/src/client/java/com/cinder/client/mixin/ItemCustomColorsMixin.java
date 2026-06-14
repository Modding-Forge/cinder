package com.cinder.client.mixin;

import com.cinder.fabric.customcolors.CustomColorsRuntime;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Target: {@link Item#getBarColor(ItemStack)}.
 *
 * <p>Purpose: applies the {@code durability.png} special colormap to vanilla
 * item durability bars. Vanilla bar width/visibility and item behaviour are
 * untouched.
 *
 * <p>Risk: low. Client-visible color override only.
 */
@Mixin(Item.class)
public abstract class ItemCustomColorsMixin {

    @Inject(method = "getBarColor", at = @At("RETURN"), cancellable = true)
    private void cinder$customDurabilityColor(ItemStack stack,
                                             CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(CustomColorsRuntime.durabilityColor(
                stack, cir.getReturnValue()));
    }
}
