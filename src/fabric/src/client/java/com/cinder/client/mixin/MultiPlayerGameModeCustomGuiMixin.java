package com.cinder.client.mixin;

import com.cinder.fabric.customgui.CustomGuiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Custom GUI container context hook for block interactions.
 *
 * <p>Target: {@link MultiPlayerGameMode#useItemOn(LocalPlayer,
 * InteractionHand, BlockHitResult)}. Purpose: capture client-visible facts
 * that are not present on the opened screen instance, currently shulker box
 * color for OptiFine {@code colors=} GUI rules.
 *
 * <p>Preserved behaviour: no interaction result or packet state is changed.
 * Cinder only records a short-lived context value consumed by the next screen
 * classification.
 *
 * <p>Compatibility: low risk. The hook runs at method head, has
 * {@code require = 0}, and fails safe by clearing stale context.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeCustomGuiMixin {

    @Inject(method = "useItemOn", at = @At("HEAD"), require = 0)
    private void cinder$customGuiBlockContext(LocalPlayer player,
                                              InteractionHand hand,
                                              BlockHitResult blockHit,
                                              CallbackInfoReturnable<?> cir) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || blockHit == null) {
            CustomGuiRuntime.clearPendingShulkerColor();
            return;
        }
        BlockState state = level.getBlockState(blockHit.getBlockPos());
        if (!(state.getBlock() instanceof ShulkerBoxBlock shulker)) {
            CustomGuiRuntime.clearPendingShulkerColor();
            return;
        }
        DyeColor color = shulker.getColor();
        if (color == null) {
            CustomGuiRuntime.clearPendingShulkerColor();
            return;
        }
        CustomGuiRuntime.rememberPendingShulkerColor(color.getName());
    }
}
