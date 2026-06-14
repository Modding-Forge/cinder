package com.cinder.client.sodium;

import com.cinder.bettergrass.BetterGrassFamily;
import com.cinder.bettergrass.BetterGrassRules;
import com.cinder.config.BetterGrassMode;
import com.cinder.config.CinderConfig;
import com.cinder.config.CinderConfigHolder;
import com.cinder.resource.NamespaceId;
import net.caffeinemc.mods.sodium.client.render.model.MutableQuadViewImpl;
import net.caffeinemc.mods.sodium.client.render.texture.SpriteFinderCache;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sodium meshing adapter for Cinder's Better Grass mode.
 *
 * <p>Behaviour target: OptiFine-style Better Grass for grass-like full-side
 * blocks. Fast mode maps every enabled full side face to that block family's
 * top texture. Fancy mode does the same only when the block below the
 * neighbouring side position is the same family, matching the visible
 * "surface continues over this edge" rule. Lowered families such as dirt path
 * and farmland also let a dirt block directly below them reuse the same side
 * texture when the family block above would receive Better Grass on that side.
 *
 * <p>Threading: owned by a Sodium {@code BlockRenderer} instance. It reads the
 * immutable config snapshot and the read-only chunk slice supplied by Sodium.
 *
 * <p>Performance: HOT PATH. Allocation policy: no per-quad object allocation;
 * the block atlas lookup currently follows the existing CTM lookup adapter and
 * can be reload-cached later if profiling points at it.
 */
public final class CinderSodiumBetterGrass {

    private static final float FULL_FACE_EPSILON = 0.001F;
    private static final Logger LOGGER =
            LoggerFactory.getLogger("cinder/sodium-better-grass");
    private static final int MULTILAYER_FALLBACK_LOG_LIMIT = 8;
    private static final AtomicInteger MULTILAYER_FALLBACK_LOGS =
            new AtomicInteger();
    private static final NamespaceId MOIST_FARMLAND =
            new NamespaceId("minecraft", "block/farmland_moist");
    private static final NamespaceId VANILLA_SNOWY_SIDE =
            new NamespaceId("minecraft", "block/grass_block_snow");
    private static final BlockFamily[] BLOCK_FAMILIES = {
            new BlockFamily(Blocks.GRASS_BLOCK,
                    BetterGrassFamily.GRASS,
                    BetterGrassFamily.GRASS_SNOW, true, 1.0F, false),
            new BlockFamily(Blocks.DIRT_PATH,
                    BetterGrassFamily.DIRT_PATH,
                    null, false, 0.9375F, true),
            new BlockFamily(Blocks.FARMLAND,
                    BetterGrassFamily.FARMLAND,
                    null, false, 0.9375F, true),
            new BlockFamily(Blocks.MYCELIUM,
                    BetterGrassFamily.MYCELIUM,
                    BetterGrassFamily.MYCELIUM_SNOW, false, 1.0F, false),
            new BlockFamily(Blocks.PODZOL,
                    BetterGrassFamily.PODZOL,
                    BetterGrassFamily.PODZOL_SNOW, false, 1.0F, false),
            new BlockFamily(Blocks.CRIMSON_NYLIUM,
                    BetterGrassFamily.CRIMSON_NYLIUM,
                    null, false, 1.0F, false),
            new BlockFamily(Blocks.WARPED_NYLIUM,
                    BetterGrassFamily.WARPED_NYLIUM,
                    null, false, 1.0F, false)
    };

    private final CtmSodiumSpriteLookup spriteLookup =
            new CtmSodiumSpriteLookup();

    /**
     * Applies Cinder's Better-Grass-owned snow side cover to full solid side
     * faces. This deliberately stays separate from Better Snow, which emits
     * OptiFine-style snow layer geometry for non-solid blocks.
     *
     * @return {@code true} when the quad was remapped
     */
    public boolean applySnowSideRemap(MutableQuadViewImpl quad,
                                      BlockAndTintGetter level,
                                      BlockState state,
                                      BlockPos pos) {
        CinderConfig config = CinderConfigHolder.get();
        if (!config.betterGrassActive()
                || level == null || state == null || pos == null) {
            return false;
        }
        BetterGrassRules rules = BetterGrassRules.current(config);
        Direction direction = faceDirection(quad);
        if (direction == null || direction.getAxis() == Direction.Axis.Y) {
            return false;
        }
        boolean realSnowCover = isSnowyBlock(level.getBlockState(pos.above()));
        boolean fakeSnowCover = hasFakeSnowCoverAbove(level, state, pos);
        if (!snowSideEligible(state)
                || (!realSnowCover && !fakeSnowCover)
                || !isFullExteriorFace(quad, direction, 1.0F)) {
            return false;
        }
        TextureAtlasSprite source = sourceSprite(quad);
        NamespaceId targetId =
                rules.enabled(BetterGrassFamily.GRASS_SNOW)
                        ? rules.texture(BetterGrassFamily.GRASS_SNOW)
                        : vanillaSnowySideTarget(state, fakeSnowCover);
        if (targetId == null) {
            return false;
        }
        TextureAtlasSprite target = spriteLookup.sprite(targetId);
        if (source == null || target == null
                || source.contents().name().equals(target.contents().name())) {
            return false;
        }
        CtmSodiumQuadProcessor.remapSprite(quad, source, target);
        quad.setTintIndex(-1);
        return true;
    }

    /**
     * Applies Better Grass to one Sodium quad if the active mode wants it.
     *
     * @return {@code true} when the quad was remapped
     */
    public boolean apply(MutableQuadViewImpl quad,
                         BlockAndTintGetter level,
                         BlockState state,
                         BlockPos pos) {
        CinderConfig config = CinderConfigHolder.get();
        BetterGrassMode mode = config.betterGrassMode();
        if (!config.betterGrassActive()
                || mode == BetterGrassMode.OFF
                || level == null || state == null || pos == null) {
            return false;
        }
        BetterGrassRules rules = BetterGrassRules.current(config);
        Direction direction = faceDirection(quad);
        if (direction == null || direction.getAxis() == Direction.Axis.Y) {
            return false;
        }
        BlockFamily family = familyFor(rules, level, pos, state);
        BlockState textureSourceState = state;
        boolean supportFace = false;
        float sideHeight = family == null ? 1.0F : family.sideHeight();
        if (family == null) {
            SupportMatch support = supportFamilyAbove(rules, level, state,
                    pos, direction, mode);
            if (support == null) {
                return false;
            }
            family = support.family();
            textureSourceState = support.state();
            supportFace = true;
        }
        if (!isFullExteriorFace(quad, direction, sideHeight)) {
            return false;
        }
        if (!supportFace
                && mode == BetterGrassMode.FANCY
                && !continuesFamily(level, pos, direction, family)) {
            return false;
        }
        TextureAtlasSprite source = sourceSprite(quad);
        TextureAtlasSprite target = spriteLookup.sprite(
                family.topSprite(rules, level, pos, textureSourceState));
        if (source == null || target == null
                || source.contents().name().equals(target.contents().name())) {
            return false;
        }
        CtmSodiumQuadProcessor.remapSprite(quad, source, target);
        if (!supportFace && family.usesMultilayerFallback(rules, level, pos,
                textureSourceState)) {
            logMultilayerFallback();
        }
        quad.setTintIndex(family.tinted(level, pos, textureSourceState)
                ? 0 : -1);
        return true;
    }

    private static @Nullable BlockFamily familyFor(BetterGrassRules rules,
                                                   BlockAndTintGetter level,
                                                   BlockPos pos,
                                                   BlockState state) {
        for (BlockFamily family : BLOCK_FAMILIES) {
            if (state.is(family.block())
                    && family.enabled(rules, level, pos, state)) {
                return family;
            }
        }
        return null;
    }

    private static boolean continuesFamily(BlockAndTintGetter level,
                                           BlockPos pos,
                                           Direction direction,
                                           BlockFamily family) {
        BlockPos continuation = pos.below().relative(direction);
        return family.matchesVariant(level, pos, continuation);
    }

    private static @Nullable SupportMatch supportFamilyAbove(
            BetterGrassRules rules,
            BlockAndTintGetter level,
            BlockState state,
            BlockPos pos,
            Direction direction,
            BetterGrassMode mode) {
        if (!state.is(Blocks.DIRT)) {
            return null;
        }
        BlockPos abovePos = pos.above();
        BlockState aboveState = level.getBlockState(abovePos);
        BlockFamily family = familyFor(rules, level, abovePos, aboveState);
        if (family == null || !family.extendsToDirtSupport()) {
            return null;
        }
        if (mode == BetterGrassMode.FANCY
                && !continuesFamily(level, abovePos, direction, family)) {
            return null;
        }
        return new SupportMatch(family, aboveState);
    }

    private static @Nullable TextureAtlasSprite sourceSprite(
            MutableQuadViewImpl quad) {
        TextureAtlasSprite sprite = quad.cachedSprite();
        if (sprite != null) {
            return sprite;
        }
        return quad.sprite(SpriteFinderCache.forBlockAtlas());
    }

    private static boolean snowSideEligible(BlockState state) {
        Block block = state.getBlock();
        return state.isSolidRender()
                && block != Blocks.SNOW
                && block != Blocks.SNOW_BLOCK
                && block != Blocks.ICE
                && block != Blocks.PACKED_ICE
                && block != Blocks.BLUE_ICE;
    }

    private static boolean isSnowyBlock(BlockState state) {
        return state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK);
    }

    private static boolean hasFakeSnowCoverAbove(BlockAndTintGetter level,
                                                 BlockState state,
                                                 BlockPos pos) {
        BlockPos abovePos = pos.above();
        BlockState above = level.getBlockState(abovePos);
        return isSnowDirtFamily(state)
                && CinderSodiumBetterSnow.shouldRenderLayer(level, above,
                abovePos);
    }

    private static @Nullable NamespaceId vanillaSnowySideTarget(
            BlockState state,
            boolean fakeSnowCover) {
        return fakeSnowCover && isSnowDirtFamily(state)
                ? VANILLA_SNOWY_SIDE
                : null;
    }

    private static boolean isSnowDirtFamily(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.MYCELIUM)
                || state.is(Blocks.PODZOL);
    }

    private static void logMultilayerFallback() {
        if (!CinderConfigHolder.get().ctmDebugLogging()) {
            return;
        }
        int index = MULTILAYER_FALLBACK_LOGS.getAndIncrement();
        if (index >= MULTILAYER_FALLBACK_LOG_LIMIT) {
            return;
        }
        LOGGER.info("[cinder] grass.multilayer=true requested; falling back "
                + "to replacement Better Grass until tinted overlay emission "
                + "is stabilized");
    }

    private static @Nullable Direction faceDirection(MutableQuadViewImpl quad) {
        Direction direction = quad.getCullFace();
        if (direction != null) {
            return direction;
        }
        direction = quad.getNominalFace();
        if (direction != null) {
            return direction;
        }
        return quad.getLightFace();
    }

    private static boolean isFullExteriorFace(MutableQuadViewImpl quad,
                                              Direction direction,
                                              float sideHeight) {
        float fixed = switch (direction) {
            case DOWN -> maxY(quad);
            case UP -> minY(quad);
            case NORTH -> maxZ(quad);
            case SOUTH -> minZ(quad);
            case WEST -> maxX(quad);
            case EAST -> minX(quad);
        };
        float expected = switch (direction) {
            case DOWN, NORTH, WEST -> 0.0F;
            case UP, SOUTH, EAST -> 1.0F;
        };
        if (Math.abs(fixed - expected) > FULL_FACE_EPSILON) {
            return false;
        }
        return switch (direction) {
            case DOWN, UP -> rangeFull(minX(quad), maxX(quad))
                    && rangeFull(minZ(quad), maxZ(quad));
            case NORTH, SOUTH -> rangeFull(minX(quad), maxX(quad))
                    && rangeVerticalSide(minY(quad), maxY(quad), sideHeight);
            case WEST, EAST -> rangeFull(minZ(quad), maxZ(quad))
                    && rangeVerticalSide(minY(quad), maxY(quad), sideHeight);
        };
    }

    private static boolean rangeFull(float min, float max) {
        return min <= FULL_FACE_EPSILON
                && max >= 1.0F - FULL_FACE_EPSILON;
    }

    private static boolean rangeVerticalSide(float min, float max,
                                             float expectedHeight) {
        return min <= FULL_FACE_EPSILON
                && max >= expectedHeight - FULL_FACE_EPSILON;
    }

    private static float minX(MutableQuadViewImpl quad) {
        return Math.min(Math.min(quad.getX(0), quad.getX(1)),
                Math.min(quad.getX(2), quad.getX(3)));
    }

    private static float maxX(MutableQuadViewImpl quad) {
        return Math.max(Math.max(quad.getX(0), quad.getX(1)),
                Math.max(quad.getX(2), quad.getX(3)));
    }

    private static float minY(MutableQuadViewImpl quad) {
        return Math.min(Math.min(quad.getY(0), quad.getY(1)),
                Math.min(quad.getY(2), quad.getY(3)));
    }

    private static float maxY(MutableQuadViewImpl quad) {
        return Math.max(Math.max(quad.getY(0), quad.getY(1)),
                Math.max(quad.getY(2), quad.getY(3)));
    }

    private static float minZ(MutableQuadViewImpl quad) {
        return Math.min(Math.min(quad.getZ(0), quad.getZ(1)),
                Math.min(quad.getZ(2), quad.getZ(3)));
    }

    private static float maxZ(MutableQuadViewImpl quad) {
        return Math.max(Math.max(quad.getZ(0), quad.getZ(1)),
                Math.max(quad.getZ(2), quad.getZ(3)));
    }

    private record BlockFamily(Block block,
                               BetterGrassFamily family,
                               @Nullable BetterGrassFamily snowFamily,
                               boolean tinted,
                               float sideHeight,
                               boolean extendsToDirtSupport) {
        private NamespaceId topSprite(
                BetterGrassRules rules,
                BlockAndTintGetter level,
                BlockPos pos,
                BlockState state) {
            BetterGrassFamily selected = selectedFamily(level, pos, state);
            if (selected == BetterGrassFamily.FARMLAND) {
                int moisture = intProperty(state, "moisture");
                if (moisture > 0
                        && rules.texture(BetterGrassFamily.FARMLAND)
                        .equals(BetterGrassRules.FARMLAND_TEXTURE)) {
                    return MOIST_FARMLAND;
                }
            }
            return rules.texture(selected);
        }

        private boolean tinted(BlockAndTintGetter level,
                               BlockPos pos,
                               BlockState state) {
            return tinted && selectedFamily(level, pos, state) == family;
        }

        private boolean usesMultilayerFallback(BetterGrassRules rules,
                                               BlockAndTintGetter level,
                                               BlockPos pos,
                                               BlockState state) {
            return rules.grassMultilayer()
                    && block == Blocks.GRASS_BLOCK
                    && selectedFamily(level, pos, state)
                    == BetterGrassFamily.GRASS;
        }

        private boolean matchesVariant(BlockAndTintGetter level,
                                       BlockPos sourcePos,
                                       BlockPos continuationPos) {
            BlockState source = level.getBlockState(sourcePos);
            BlockState continuation = level.getBlockState(continuationPos);
            if (!continuation.is(block)) {
                return false;
            }
            if (snowFamily != null) {
                return isSnowy(level, sourcePos, source)
                        == isSnowy(level, continuationPos, continuation);
            }
            return true;
        }

        private BetterGrassFamily selectedFamily(
                @Nullable BlockAndTintGetter level,
                @Nullable BlockPos pos,
                BlockState state) {
            if (snowFamily != null && isSnowy(level, pos, state)) {
                return snowFamily;
            }
            return family;
        }

        private boolean isSnowy(@Nullable BlockAndTintGetter level,
                                @Nullable BlockPos pos,
                                BlockState state) {
            if (boolProperty(state, "snowy")) {
                return true;
            }
            if (level == null || pos == null) {
                return false;
            }
            BlockState above = level.getBlockState(pos.above());
            return above.is(Blocks.SNOW)
                    || above.is(Blocks.SNOW_BLOCK)
                    || CinderSodiumBetterSnow.shouldRenderLayer(level, above,
                    pos.above());
        }

        private static int intProperty(BlockState state, String name) {
            for (var property : state.getProperties()) {
                if (!name.equals(property.getName())) {
                    continue;
                }
                Comparable<?> value = state.getValue(property);
                if (value instanceof Integer integer) {
                    return integer;
                }
            }
            return 0;
        }

        private static boolean boolProperty(BlockState state, String name) {
            for (var property : state.getProperties()) {
                if (!name.equals(property.getName())) {
                    continue;
                }
                Comparable<?> value = state.getValue(property);
                if (value instanceof Boolean bool) {
                    return bool;
                }
            }
            return false;
        }

        private boolean enabled(BetterGrassRules rules,
                                BlockAndTintGetter level,
                                BlockPos pos,
                                BlockState state) {
            return rules.enabled(selectedFamily(level, pos, state));
        }
    }

    private record SupportMatch(BlockFamily family, BlockState state) {
    }
}
