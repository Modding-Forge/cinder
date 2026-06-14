package com.cinder.client.mixin;

import com.cinder.fabric.customcolors.CustomColorsRuntime;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.caffeinemc.mods.sodium.client.world.biome.LevelColorCache;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Target: Sodium {@link LevelColorCache}.
 *
 * <p>Purpose: applies Cinder Custom Colors to Sodium's cached grass, foliage,
 * and water biome colors before Sodium performs section-local blur/blending.
 * Preserved behaviour: unsupported resolvers and disabled/missing custom
 * colormaps fall through to Sodium/vanilla unchanged.
 *
 * <p>Compatibility: gated by {@link CinderClientMixinPlugin}; no vanilla
 * terrain renderer dependency. Risk: low-medium because this wraps one stable
 * ColorResolver call in Sodium's cache population path.
 */
@Mixin(value = LevelColorCache.class, remap = false)
public abstract class SodiumLevelColorCacheCustomColorsMixin {

    @WrapOperation(
            method = "updateColorBuffers",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/ColorResolver;"
                            + "getColor(Lnet/minecraft/world/level/biome/Biome;DD)I",
                    remap = true))
    private int cinder$customBiomeColor(ColorResolver resolver,
                                        Biome biome,
                                        double x,
                                        double z,
                                        Operation<Integer> original) {
        int fallback = original.call(resolver, biome, x, z);
        return CustomColorsRuntime.sodiumBiomeColor(resolver, biome, fallback);
    }
}
