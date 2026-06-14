package com.cinder.client.sodium;

import com.cinder.config.CinderConfigHolder;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.TallGrassBlock;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Predicate helper for OptiFine-style Better Snow layer coverage.
 *
 * <p>This feature renders Minecraft's real snow-layer block model over
 * eligible non-solid blocks when adjacent snow and a supporting block below
 * make the layer visually plausible. Cinder's solid-side snow remap is
 * intentionally owned by Better Grass instead of this class.
 *
 * <p>Performance: HOT PATH adjacent. Called once per rendered block model and
 * exits after cheap config/block/neighbour/support checks. The renderer emits
 * the snow model only after this predicate succeeds.
 */
public final class CinderSodiumBetterSnow {

    /**
     * Returns {@code true} when the current block should receive an additional
     * snow-layer model.
     */
    public boolean shouldRenderSnowLayer(BlockAndTintGetter level,
                                         BlockState state,
                                         BlockPos pos) {
        return shouldRenderLayer(level, state, pos);
    }

    /**
     * Static form used by Better Grass to make grass sides react to Cinder's
     * fake snow layer exactly where the layer itself would render.
     */
    public static boolean shouldRenderLayer(BlockAndTintGetter level,
                                            BlockState state,
                                            BlockPos pos) {
        if (!CinderConfigHolder.get().betterSnowActive()
                || level == null || state == null || pos == null) {
            return false;
        }
        return eligibleBlock(state)
                && hasSnowNeighbour(level, pos)
                && supportBelow(level.getBlockState(pos.below()));
    }

    private static boolean hasSnowNeighbour(BlockAndTintGetter level,
                                            BlockPos pos) {
        return isSnow(level.getBlockState(pos.north()))
                || isSnow(level.getBlockState(pos.south()))
                || isSnow(level.getBlockState(pos.west()))
                || isSnow(level.getBlockState(pos.east()));
    }

    private static boolean supportBelow(BlockState state) {
        if (state.isSolidRender()) {
            return true;
        }
        Block block = state.getBlock();
        if (block instanceof StairBlock) {
            return state.getValue(StairBlock.HALF) == Half.TOP;
        }
        if (block instanceof SlabBlock) {
            return state.getValue(SlabBlock.TYPE) == SlabType.TOP;
        }
        return false;
    }

    private static boolean eligibleBlock(BlockState state) {
        if (state.isSolidRender() || state.is(Blocks.SNOW_BLOCK)) {
            return false;
        }
        Block block = state.getBlock();
        if (block instanceof VegetationBlock) {
            return block instanceof DoublePlantBlock
                    || block instanceof FlowerBlock
                    || block instanceof MushroomBlock
                    || block instanceof SaplingBlock
                    || block instanceof TallGrassBlock;
        }
        if (block instanceof FenceBlock
                || block instanceof FenceGateBlock
                || block instanceof FlowerPotBlock
                || block instanceof CrossCollisionBlock
                || block instanceof SugarCaneBlock
                || block instanceof WallBlock
                || block instanceof RedstoneTorchBlock
                || block instanceof HopperBlock
                || block instanceof LadderBlock
                || block instanceof LeverBlock
                || block instanceof TurtleEggBlock
                || block instanceof VineBlock) {
            return true;
        }
        if (block instanceof StairBlock) {
            return state.getValue(StairBlock.HALF) == Half.TOP;
        }
        if (block instanceof SlabBlock) {
            return state.getValue(SlabBlock.TYPE) == SlabType.TOP;
        }
        if (block instanceof ButtonBlock) {
            return state.getValue(ButtonBlock.FACE) != AttachFace.FLOOR;
        }
        return false;
    }

    private static boolean isSnow(BlockState state) {
        return state.is(Blocks.SNOW);
    }
}
