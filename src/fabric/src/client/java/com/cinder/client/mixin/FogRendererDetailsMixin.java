package com.cinder.client.mixin;

import com.cinder.config.CinderConfig;
import com.cinder.config.CinderConfigHolder;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Target: {@link FogRenderer#setupFog}.
 *
 * <p>Purpose: implements Cinder fog distance detail toggles after vanilla has
 * selected colors and base distances. Preserved behaviour: fog color remains
 * owned by vanilla or Custom Colors; Cinder only pushes disabled fog distances
 * beyond visible range.
 *
 * <p>Risk: medium. The hook touches the 26.2 fog data object but does not alter
 * world or camera state, and fails safe if the signature changes.
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererDetailsMixin {

    private static final float FAR_FOG = 1_000_000.0F;

    @Inject(method = "setupFog", at = @At("RETURN"), cancellable = true,
            require = 0)
    private void cinder$disableFog(Camera camera,
                                   int renderDistanceInChunks,
                                   DeltaTracker deltaTracker,
                                   float darkenWorldAmount,
                                   ClientLevel level,
                                   CallbackInfoReturnable<FogData> cir) {
        CinderConfig cfg = CinderConfigHolder.get();
        FogData fog = cir.getReturnValue();
        if (fog == null || cinder$allows(cfg, camera.getFluidInCamera())) {
            return;
        }
        fog.environmentalStart = FAR_FOG;
        fog.renderDistanceStart = FAR_FOG;
        fog.environmentalEnd = FAR_FOG;
        fog.renderDistanceEnd = FAR_FOG;
        fog.skyEnd = FAR_FOG;
        fog.cloudEnd = FAR_FOG;
        cir.setReturnValue(fog);
    }

    private static boolean cinder$allows(CinderConfig cfg, FogType type) {
        if (!cfg.enabled() || !cfg.fogEnabled()) {
            return false;
        }
        return switch (type) {
            case WATER -> cfg.fogWater();
            case LAVA -> cfg.fogLava();
            case POWDER_SNOW -> cfg.fogPowderSnow();
            default -> cfg.fogAir();
        };
    }
}
