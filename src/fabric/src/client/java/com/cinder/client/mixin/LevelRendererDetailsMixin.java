package com.cinder.client.mixin;

import com.cinder.config.CinderConfig;
import com.cinder.config.CinderConfigHolder;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Target: {@link LevelRenderer#addCloudsPass}.
 *
 * <p>Purpose: implements Cinder's cloud-height control while preserving
 * Sodium's vanilla cloud visibility option as the only cloud on/off switch.
 *
 * <p>Risk: medium. The hook touches the cloud pass only and fails safe through
 * {@code require = 0} if Mojang changes the private method signature.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererDetailsMixin {

    @ModifyVariable(method = "addCloudsPass", at = @At("HEAD"),
            argsOnly = true, ordinal = 1, require = 0)
    private float cinder$cloudHeight(float cloudHeight) {
        CinderConfig cfg = CinderConfigHolder.get();
        return cfg.enabled()
                ? (float) cfg.detailsCloudHeight()
                : cloudHeight;
    }
}
