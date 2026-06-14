package com.cinder.client.mixin;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor for post-model CIT texture remapping.
 *
 * <p>Target: {@link ItemStackRenderState}. Purpose: lets Cinder inspect the
 * vanilla-produced item render layers after {@code ItemModel.update} and
 * replace only sprite/material references for texture-only CIT rules.
 *
 * <p>Risk: low. Read-only access to layer array/count; Cinder mutates each
 * layer through the public {@code prepareQuadList()} API.
 */
@Mixin(ItemStackRenderState.class)
public interface ItemStackRenderStateAccessor {

    @Accessor("activeLayerCount")
    int cinder$activeLayerCount();

    @Accessor("layers")
    ItemStackRenderState.LayerRenderState[] cinder$layers();
}
