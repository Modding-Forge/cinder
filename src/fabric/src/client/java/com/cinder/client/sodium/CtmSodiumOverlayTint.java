package com.cinder.client.sodium;

import com.cinder.ctm.BlockSpec;
import com.cinder.ctm.CtmRule;
import net.caffeinemc.mods.sodium.api.util.ColorARGB;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Computes the base vertex color for Sodium CTM overlay quads.
 *
 * <p>Overlay quads are rendered as their own Sodium mesh quads. Their color
 * must come from the overlay rule's optional {@code tintBlock}, not from the
 * block face they are laid over. This keeps snow overlays white on grass while
 * still allowing grass-like overlays to receive biome tinting.
 *
 * <p>This class has no Fabric imports; it is intended to stay reusable by any
 * loader that can provide the same Minecraft client classes and Sodium hook.
 *
 * <h2>Threading</h2>
 *
 * <p>Called from Sodium section-build workers. The Minecraft block color
 * registry is read-only after client bootstrap. The world slice is the same
 * immutable render slice Sodium uses for terrain meshing.
 *
 * <h2>Performance</h2>
 *
 * <p>Performance: HOT PATH. Allocation policy: none on untinted rules; one
 * registry lookup and one default block state lookup for tinted overlay rules.
 */
public final class CtmSodiumOverlayTint {

    /**
     * Returns an ARGB base color for the overlay quad.
     */
    public int color(CtmRule rule,
                     BlockState baseState,
                     BlockAndTintGetter level,
                     BlockPos pos) {
        if (rule == null || rule.tintIndex() < 0) {
            return -1;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return -1;
        }
        BlockColors blockColors = minecraft.getBlockColors();
        if (blockColors == null) {
            return -1;
        }
        BlockState tintState = tintState(rule, baseState);
        BlockTintSource tintSource = blockColors.getTintSource(
                tintState, rule.tintIndex());
        if (tintSource == null) {
            return -1;
        }
        int rgb = tintSource.colorInWorld(tintState, level, pos);
        return ColorARGB.withAlpha(rgb, 255);
    }

    private static BlockState tintState(CtmRule rule, BlockState baseState) {
        return rule.tintBlock()
                .map(CtmSodiumOverlayTint::blockStateForSpec)
                .orElse(baseState);
    }

    private static BlockState blockStateForSpec(BlockSpec spec) {
        Identifier id = Identifier.fromNamespaceAndPath(
                spec.namespace(), spec.name());
        Block block = BuiltInRegistries.BLOCK.getValue(id);
        return block == null
                ? Blocks.AIR.defaultBlockState()
                : block.defaultBlockState();
    }
}
