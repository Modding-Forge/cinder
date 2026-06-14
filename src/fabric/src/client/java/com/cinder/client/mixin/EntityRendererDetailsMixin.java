package com.cinder.client.mixin;

import com.cinder.config.CinderConfigHolder;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Target: {@link EntityRenderer}.
 *
 * <p>Purpose: implements broad entity and player name-tag toggles in the base
 * renderer path. Entity shadows remain controlled by Sodium/vanilla to avoid
 * duplicate settings.
 *
 * <p>Risk: medium-low. The hooks affect common entity render-state extraction
 * but do not touch gameplay state.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererDetailsMixin {

    @Inject(method = "shouldShowName", at = @At("HEAD"), cancellable = true,
            require = 0)
    private void cinder$hideNameTags(Entity entity,
                                     double distanceToCameraSq,
                                     CallbackInfoReturnable<Boolean> cir) {
        var cfg = CinderConfigHolder.get();
        if (!cfg.entityNameTagsEnabled()
                || entity instanceof Player && !cfg.entityPlayerNameTags()) {
            cir.setReturnValue(false);
        }
    }
}
