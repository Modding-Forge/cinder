package com.cinder.client.mixin;

import com.cinder.config.CinderConfigHolder;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.FireworkParticles;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Target: {@link ParticleEngine#createParticle},
 * {@link ParticleEngine#createTrackingEmitter}, and {@link ParticleEngine#add}.
 *
 * <p>Purpose: implements Cinder's global and category particle visibility
 * toggles. Preserved behaviour: enabled particles still go through vanilla
 * provider creation and normal limits. Tracking emitters are gated separately
 * because vanilla stores them directly instead of routing them through particle
 * provider creation first.
 *
 * <p>Risk: low. Returning {@code null} is already the vanilla no-particle
 * result when a provider is missing.
 */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineDetailsMixin {

    @Shadow
    private ClientLevel level;

    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true,
            require = 0)
    private void cinder$hideParticles(ParticleOptions options,
                                      double x,
                                      double y,
                                      double z,
                                      double xa,
                                      double ya,
                                      double za,
                                      CallbackInfoReturnable<Particle> cir) {
        if (this.level == null || !cinder$allows(options)) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void cinder$hideTrackingEmitter(Entity entity,
                                            ParticleOptions options,
                                            CallbackInfo ci) {
        if (this.level == null || !cinder$allows(options)) {
            ci.cancel();
        }
    }

    @Inject(method = "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;I)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void cinder$hideTimedTrackingEmitter(Entity entity,
                                                 ParticleOptions options,
                                                 int lifetime,
                                                 CallbackInfo ci) {
        if (this.level == null || !cinder$allows(options)) {
            ci.cancel();
        }
    }

    @Inject(method = "add", at = @At("HEAD"), cancellable = true,
            require = 0)
    private void cinder$hideQueuedFireworks(Particle particle,
                                            CallbackInfo ci) {
        var cfg = CinderConfigHolder.get();
        if (cfg.enabled() && particle instanceof FireworkParticles.Starter
                && !cfg.particleFirework()) {
            ci.cancel();
        }
    }

    private static boolean cinder$allows(ParticleOptions options) {
        var cfg = CinderConfigHolder.get();
        if (!cfg.enabled()) {
            return true;
        }
        if (options.getType() == ParticleTypes.RAIN
                || options.getType() == ParticleTypes.SPLASH) {
            return cfg.particleRainSplash();
        }
        if (options instanceof BlockParticleOption block) {
            if (block.getType() == ParticleTypes.BLOCK_MARKER) {
                return cfg.particleBlockBreaking();
            }
            return cfg.particleBlockBreak();
        }
        if (options.getType() == ParticleTypes.EXPLOSION_EMITTER
                || options.getType() == ParticleTypes.EXPLOSION
                || options.getType() == ParticleTypes.POOF) {
            return cfg.particleExplosion();
        }
        if (options.getType() == ParticleTypes.UNDERWATER) {
            return cfg.particleWater();
        }
        if (options.getType() == ParticleTypes.SMOKE
                || options.getType() == ParticleTypes.LARGE_SMOKE) {
            return cfg.particleSmoke();
        }
        if (options.getType() == ParticleTypes.ENTITY_EFFECT
                || options.getType() == ParticleTypes.EFFECT
                || options.getType() == ParticleTypes.INSTANT_EFFECT
                || options.getType() == ParticleTypes.WITCH) {
            return cfg.particlePotion();
        }
        if (options.getType() == ParticleTypes.PORTAL) {
            return cfg.particlePortal();
        }
        if (options.getType() == ParticleTypes.FLAME
                || options.getType() == ParticleTypes.SMALL_FLAME
                || options.getType() == ParticleTypes.SOUL_FIRE_FLAME) {
            return cfg.particleFlame();
        }
        if (options.getType() == ParticleTypes.DUST
                || options.getType() == ParticleTypes.DUST_COLOR_TRANSITION
                || options.getType() == ParticleTypes.DUST_PLUME
                || options.getType() == ParticleTypes.DUST_PILLAR) {
            return cfg.particleRedstone();
        }
        if (options.getType() == ParticleTypes.DRIPPING_WATER
                || options.getType() == ParticleTypes.DRIPPING_LAVA
                || options.getType() == ParticleTypes.DRIPPING_DRIPSTONE_WATER
                || options.getType() == ParticleTypes.DRIPPING_DRIPSTONE_LAVA
                || options.getType() == ParticleTypes.DRIPPING_HONEY
                || options.getType() == ParticleTypes.DRIPPING_OBSIDIAN_TEAR) {
            return cfg.particleDripping();
        }
        if (options.getType() == ParticleTypes.FIREWORK
                || options.getType() == ParticleTypes.FLASH) {
            return cfg.particleFirework();
        }
        return true;
    }
}
