package com.cinder.client.mixin;

import com.cinder.fabric.customcolors.CustomColorsRuntime;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ColorResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Target: {@link ClientLevel#calculateBlockTint(BlockPos, ColorResolver)}.
 *
 * <p>Purpose: route vanilla-registered biome tint resolvers through Cinder's
 * immutable Custom Colors snapshot. Preserved behaviour: non-Cinder resolvers
 * and disabled/missing custom colors continue through vanilla unchanged.
 * Compatibility: narrow HEAD injection, no custom resolvers are registered or
 * passed into Minecraft's resolver cache. Risk: low; the hook only affects
 * registered grass, foliage, dry foliage, and water resolver calls.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelCustomColorsMixin {

    private static final int NO_CUSTOM_COLOR = Integer.MIN_VALUE;

    @Inject(method = "calculateBlockTint", at = @At("HEAD"), cancellable = true)
    private void cinder$customBiomeTint(BlockPos pos,
                                        ColorResolver colorResolver,
                                        CallbackInfoReturnable<Integer> cir) {
        int color = CustomColorsRuntime.registeredBiomeColor(colorResolver,
                (ClientLevel) (Object) this, pos, NO_CUSTOM_COLOR);
        if (color != NO_CUSTOM_COLOR) {
            cir.setReturnValue(color);
        }
    }
}
