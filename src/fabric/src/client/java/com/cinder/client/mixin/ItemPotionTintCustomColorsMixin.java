package com.cinder.client.mixin;

import com.cinder.fabric.customcolors.CustomColorsRuntime;
import net.minecraft.client.color.item.Potion;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Target: {@link Potion#calculate(ItemStack, ClientLevel, LivingEntity)}.
 *
 * <p>Purpose: applies {@code potion.*} color overrides to Mojang's item tint
 * source. Custom item component colors still win over resource-pack defaults.
 *
 * <p>Risk: low. Item tint source result only.
 */
@Mixin(Potion.class)
public abstract class ItemPotionTintCustomColorsMixin {

    @Inject(method = "calculate", at = @At("RETURN"), cancellable = true)
    private void cinder$customPotionColor(ItemStack itemStack,
                                          @Nullable ClientLevel level,
                                          @Nullable LivingEntity owner,
                                          CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(CustomColorsRuntime.potionColor(
                itemStack, cir.getReturnValue()));
    }
}
