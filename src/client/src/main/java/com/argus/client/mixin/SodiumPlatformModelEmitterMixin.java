package com.argus.client.mixin;

import com.argus.client.sodium.ArgusSodiumModelEmitter;
import net.caffeinemc.mods.sodium.client.services.PlatformModelEmitter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Target: Sodium {@link PlatformModelEmitter#getInstance()}.
 *
 * <p>Purpose: ensures Argus can use its Sodium-native model emitter even when
 * a loader development classpath does not expose mod service descriptors to
 * Sodium's ServiceLoader lookup.
 *
 * <p>Preserved behaviour: if the Argus emitter is disabled, Sodium's original
 * service instance is returned unchanged. The fallback only replaces the
 * service object; model collection and emission still use Sodium's public
 * service interfaces.
 *
 * <p>Compatibility: narrow static-method injection, remap disabled because
 * Sodium is not Mojang-mapped Minecraft code.
 *
 * <p>Risk: low-medium. This touches Sodium service discovery, but leaves the
 * legacy Argus processQuad fallback active when the emitter is disabled.
 */
@Mixin(value = PlatformModelEmitter.class, remap = false)
public interface SodiumPlatformModelEmitterMixin {

    /**
     * Supplies Argus's emitter before Sodium returns its default service.
     */
    @Inject(method = "getInstance", at = @At("HEAD"), cancellable = true)
    private static void argus$getInstance(
            CallbackInfoReturnable<PlatformModelEmitter> cir) {
        PlatformModelEmitter emitter = ArgusSodiumModelEmitter.overrideInstance();
        if (emitter != null) {
            cir.setReturnValue(emitter);
        }
    }
}
