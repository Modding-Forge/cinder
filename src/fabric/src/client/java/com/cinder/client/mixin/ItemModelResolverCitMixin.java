package com.cinder.client.mixin;

import com.cinder.cit.CitRule;
import com.cinder.fabric.cit.CitRuntime;
import com.cinder.resource.NamespaceId;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * CIT hook for Mojang's 26.2 item model path.
 *
 * <p>Target: {@link ItemModelResolver#appendItemLayers}. Purpose: select a
 * Cinder CIT rule after the cheap item prefilter and realize it either by
 * delegating to a replacement model id or by remapping vanilla-generated
 * item quads to a replacement item-atlas sprite.
 *
 * <p>Compatibility: this is not a terrain hook. Sodium does not own item
 * model resolution, so this narrowly targets Mojang's item renderer only.
 */
@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverCitMixin {

    @Shadow
    private ItemModel getItemModel(Identifier modelId) {
        throw new AssertionError();
    }

    @Shadow
    private ClientItem.Properties getItemProperties(Identifier modelId) {
        throw new AssertionError();
    }

    @Inject(method = "appendItemLayers", at = @At("HEAD"), cancellable = true)
    private void cinder$replaceModel(ItemStackRenderState output,
                                     ItemStack item,
                                     ItemDisplayContext displayContext,
                                     @Nullable Level level,
                                     @Nullable ItemOwner owner,
                                     int seed,
                                     CallbackInfo ci) {
        CitRule rule = CitRuntime.select(item, hand(displayContext));
        if (rule == null || rule.replacement().model() == null) {
            return;
        }
        Identifier modelId = id(rule.replacement().model());
        output.setOversizedInGui(this.getItemProperties(modelId)
                .oversizedInGui());
        this.getItemModel(modelId).update(output, item,
                (ItemModelResolver) (Object) this, displayContext,
                level instanceof ClientLevel clientLevel ? clientLevel : null,
                owner, seed);
        ci.cancel();
    }

    @Inject(method = "appendItemLayers", at = @At("TAIL"))
    private void cinder$replaceTexture(ItemStackRenderState output,
                                       ItemStack item,
                                       ItemDisplayContext displayContext,
                                       @Nullable Level level,
                                       @Nullable ItemOwner owner,
                                       int seed,
                                       CallbackInfo ci) {
        CitRule rule = CitRuntime.select(item, hand(displayContext));
        if (rule == null || rule.replacement().texture() == null
                || rule.replacement().model() != null) {
            return;
        }
        TextureAtlasSprite target = Minecraft.getInstance()
                .getAtlasManager()
                .getAtlasOrThrow(AtlasIds.ITEMS)
                .getSprite(id(rule.replacement().texture()));
        if (target == null) {
            return;
        }
        remapOutput(output, target);
    }

    private static void remapOutput(ItemStackRenderState output,
                                    TextureAtlasSprite target) {
        ItemStackRenderStateAccessor accessor =
                (ItemStackRenderStateAccessor) output;
        ItemStackRenderState.LayerRenderState[] layers =
                accessor.cinder$layers();
        for (int i = 0; i < accessor.cinder$activeLayerCount(); i++) {
            List<BakedQuad> quads = layers[i].prepareQuadList();
            for (int q = 0; q < quads.size(); q++) {
                quads.set(q, remapQuad(quads.get(q), target));
            }
        }
    }

    private static BakedQuad remapQuad(BakedQuad quad,
                                       TextureAtlasSprite target) {
        TextureAtlasSprite source = quad.materialInfo().sprite();
        BakedQuad.MaterialInfo old = quad.materialInfo();
        BakedQuad.MaterialInfo material = new BakedQuad.MaterialInfo(
                target, layerFor(target), renderTypeFor(target),
                old.tintIndex(), old.shade(), old.lightEmission());
        return new BakedQuad(quad.position0(), quad.position1(),
                quad.position2(), quad.position3(),
                remapUv(quad.packedUV0(), source, target),
                remapUv(quad.packedUV1(), source, target),
                remapUv(quad.packedUV2(), source, target),
                remapUv(quad.packedUV3(), source, target),
                quad.direction(), material);
    }

    private static long remapUv(long packed,
                                TextureAtlasSprite source,
                                TextureAtlasSprite target) {
        float u = UVPair.unpackU(packed);
        float v = UVPair.unpackV(packed);
        float su = (u - source.getU0()) / (source.getU1() - source.getU0());
        float sv = (v - source.getV0()) / (source.getV1() - source.getV0());
        return UVPair.pack(
                target.getU0() + su * (target.getU1() - target.getU0()),
                target.getV0() + sv * (target.getV1() - target.getV0()));
    }

    private static ChunkSectionLayer layerFor(TextureAtlasSprite sprite) {
        return ChunkSectionLayer.byTransparency(sprite.transparency());
    }

    private static RenderType renderTypeFor(TextureAtlasSprite sprite) {
        return sprite.transparency().hasTranslucent()
                ? Sheets.translucentItemSheet()
                : Sheets.cutoutItemSheet();
    }

    private static Identifier id(NamespaceId id) {
        return Identifier.fromNamespaceAndPath(id.namespace(), id.path());
    }

    private static String hand(ItemDisplayContext context) {
        if (context == ItemDisplayContext.GUI) {
            return "main";
        }
        return context.leftHand() ? "off" : "main";
    }
}
