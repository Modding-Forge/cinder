package com.cinder.client.mixin;

import com.cinder.fabric.customcolors.CustomColorsRuntime;
import net.minecraft.client.color.item.GrassColorSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Target: {@link GrassColorSource#calculate(ItemStack, ClientLevel, LivingEntity)}.
 *
 * <p>Purpose: makes grass-tinted block items consume the same Cinder grass
 * colormap as terrain. Vanilla item tinting is preserved when no custom
 * colormap exists.
 *
 * <p>Risk: low. Item tint source result only.
 */
@Mixin(GrassColorSource.class)
public abstract class ItemGrassTintCustomColorsMixin {

    @Inject(method = "calculate", at = @At("RETURN"), cancellable = true)
    private void cinder$customGrassItemColor(ItemStack itemStack,
                                             @Nullable ClientLevel level,
                                             @Nullable LivingEntity owner,
                                             CallbackInfoReturnable<Integer> cir) {
        GrassColorSource source = (GrassColorSource) (Object) this;
        cir.setReturnValue(CustomColorsRuntime.itemGrassColor(
                source.temperature(), source.downfall(), cir.getReturnValue()));
    }
}
