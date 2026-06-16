package com.cinder.fabric.customcolors;

import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Small tint-source adapter installed into Mojang's {@code BlockColors}.
 *
 * <p>Performance: HOT PATH. Allocation policy: enum singleton only. The
 * common fallback path calls the matching vanilla tint function directly.
 */
public enum CinderCustomBlockTintSource implements BlockTintSource {
    GRASS,
    GRASS_BLOCK,
    FOLIAGE,
    WATER,
    WATER_PARTICLES,
    REDSTONE,
    STEM,
    PALETTE;

    @Override
    public int color(BlockState state) {
        return switch (this) {
            case REDSTONE -> CustomColorsRuntime.redstoneColor(state);
            case STEM -> CustomColorsRuntime.stemColor(state);
            case PALETTE -> CustomColorsRuntime.blockPaletteColor(
                    state, null, null, -1);
            default -> -1;
        };
    }

    @Override
    public int colorInWorld(BlockState state,
                            BlockAndTintGetter level,
                            BlockPos pos) {
        if (level == null || pos == null) {
            return color(state);
        }
        return switch (this) {
            case GRASS, GRASS_BLOCK -> CustomColorsRuntime.grassColor(
                    state, level, pos);
            case FOLIAGE -> CustomColorsRuntime.foliageColor(
                    state, level, pos);
            case WATER -> CustomColorsRuntime.waterColor(state, level, pos);
            case PALETTE -> paletteOrVanilla(state, level, pos);
            default -> color(state);
        };
    }

    @Override
    public int colorAsTerrainParticle(BlockState state,
                                      BlockAndTintGetter level,
                                      BlockPos pos) {
        if (this == WATER_PARTICLES) {
            return CustomColorsRuntime.waterColor(state, level, pos);
        }
        if (this == GRASS_BLOCK) {
            return -1;
        }
        return colorInWorld(state, level, pos);
    }

    private static int paletteOrVanilla(BlockState state,
                                        BlockAndTintGetter level,
                                        BlockPos pos) {
        int palette = CustomColorsRuntime.blockPaletteColor(
                state, level, pos, Integer.MIN_VALUE);
        if (palette != Integer.MIN_VALUE) {
            return palette;
        }
        if (isGrassTintedState(state)) {
            return BiomeColors.getAverageGrassColor(level, pos);
        }
        if (isFoliageTintedState(state)) {
            return BiomeColors.getAverageFoliageColor(level, pos);
        }
        if (isWaterTintedState(state)) {
            return BiomeColors.getAverageWaterColor(level, pos);
        }
        return state.is(Blocks.LILY_PAD)
                ? CustomColorsRuntime.overrideColor("lilypad", -9321636)
                : -1;
    }

    /**
     * Returns whether palette tint is meaningful for a CTM overlay target.
     *
     * <p>OptiFine-style overlay rules often specify {@code tintBlock} for
     * blocks like sand, snow, or clay even though those blocks are not vanilla
     * tinted. If Custom Colors installed a block palette for such a block,
     * blindly applying that palette to already-colored overlay PNGs darkens the
     * result. CTM overlays should therefore inherit palette tint only for block
     * families that are actually tintable in vanilla-style rendering.
     */
    public static boolean isVanillaTintedState(BlockState state) {
        return isGrassTintedState(state)
                || isFoliageTintedState(state)
                || isWaterTintedState(state)
                || state.is(Blocks.LILY_PAD);
    }

    private static boolean isGrassTintedState(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.FERN)
                || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.POTTED_FERN)
                || state.is(Blocks.BUSH)
                || state.is(Blocks.SUGAR_CANE);
    }

    private static boolean isFoliageTintedState(BlockState state) {
        return state.is(Blocks.OAK_LEAVES)
                || state.is(Blocks.JUNGLE_LEAVES)
                || state.is(Blocks.ACACIA_LEAVES)
                || state.is(Blocks.DARK_OAK_LEAVES)
                || state.is(Blocks.VINE)
                || state.is(Blocks.MANGROVE_LEAVES);
    }

    private static boolean isWaterTintedState(BlockState state) {
        return state.is(Blocks.WATER)
                || state.is(Blocks.BUBBLE_COLUMN)
                || state.is(Blocks.WATER_CAULDRON);
    }
}
