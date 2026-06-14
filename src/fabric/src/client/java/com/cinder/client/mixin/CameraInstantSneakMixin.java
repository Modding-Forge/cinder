package com.cinder.client.mixin;

import com.cinder.config.CinderConfig;
import com.cinder.config.CinderConfigHolder;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Target: {@link Camera#tick()}.
 *
 * <p>Purpose: removes the client camera eye-height interpolation used when the
 * player enters or leaves crouch pose. Preserved behaviour: crouch input,
 * server state, collision, movement and entity pose remain fully vanilla.
 *
 * <p>Risk: low. The hook only rewrites camera-local interpolation fields after
 * vanilla has updated them.
 */
@Mixin(Camera.class)
public abstract class CameraInstantSneakMixin {
    @Shadow
    private Entity entity;

    @Shadow
    private float eyeHeight;

    @Shadow
    private float eyeHeightOld;

    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void cinder$instantSneakCamera(CallbackInfo ci) {
        CinderConfig cfg = CinderConfigHolder.get();
        if (!cfg.enabled() || !cfg.instantSneak() || this.entity == null) {
            return;
        }

        float targetEyeHeight = this.entity.getEyeHeight();
        this.eyeHeight = targetEyeHeight;
        this.eyeHeightOld = targetEyeHeight;
    }
}
