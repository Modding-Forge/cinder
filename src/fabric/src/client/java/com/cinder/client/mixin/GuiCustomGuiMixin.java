package com.cinder.client.mixin;

import com.cinder.fabric.customgui.CustomGuiRuntime;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Custom GUI screen lifecycle hook.
 *
 * <p>Target: {@link Gui#setScreen(Screen)}. Purpose: evaluate Custom GUI
 * rules once after Minecraft has selected the final active screen.
 *
 * <p>Preserved behaviour: no vanilla state is changed; Cinder only snapshots
 * the screen for later texture lookup.
 *
 * <p>Compatibility: risk is low because the hook is tail-only and optional
 * for rendering correctness.
 */
@Mixin(Gui.class)
public abstract class GuiCustomGuiMixin {

    @Inject(method = "setScreen", at = @At("TAIL"), require = 0)
    private void cinder$customGuiScreenChanged(@Nullable Screen screen,
                                               CallbackInfo ci) {
        CustomGuiRuntime.screenChanged(screen);
    }
}
