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
        if (state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.FERN)
                || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.POTTED_FERN)
                || state.is(Blocks.BUSH)
                || state.is(Blocks.SUGAR_CANE)) {
            return BiomeColors.getAverageGrassColor(level, pos);
        }
        if (state.is(Blocks.OAK_LEAVES)
                || state.is(Blocks.JUNGLE_LEAVES)
                || state.is(Blocks.ACACIA_LEAVES)
                || state.is(Blocks.DARK_OAK_LEAVES)
                || state.is(Blocks.VINE)
                || state.is(Blocks.MANGROVE_LEAVES)) {
            return BiomeColors.getAverageFoliageColor(level, pos);
        }
        if (state.is(Blocks.WATER) || state.is(Blocks.BUBBLE_COLUMN)
                || state.is(Blocks.WATER_CAULDRON)) {
            return BiomeColors.getAverageWaterColor(level, pos);
        }
        return CustomColorsRuntime.overrideColor("lilypad", -9321636);
    }
}
