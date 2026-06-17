package com.argus.client.sodium;

import com.argus.config.ArgusConfig;
import com.argus.config.ArgusConfigHolder;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Per-block Sodium render context prepared by Argus's model-emitter bridge.
 *
 * <p>The plan stores stable values that are identical for every quad emitted
 * by one block model. Per-quad scratch remains in {@link ArgusSodiumQuadPipeline}.
 *
 * <p>Threading: one instance is used only for the current section-build worker
 * call stack.
 *
 * <p>Performance: HOT PATH. Allocation policy: one small object per rendered
 * block model while the model-emitter path is active.
 */
public final class ArgusSodiumBlockRenderPlan {

    private final ArgusConfig config;
    private final @Nullable BlockAndTintGetter level;
    private final @Nullable BlockState state;
    private final @Nullable BlockPos pos;
    private final ArgusCtmBlockPlan ctmPlan;

    public ArgusSodiumBlockRenderPlan(@Nullable BlockAndTintGetter level,
                                      @Nullable BlockState state,
                                      @Nullable BlockPos pos) {
        this.config = ArgusConfigHolder.get();
        this.level = level;
        this.state = state;
        this.pos = pos;
        this.ctmPlan = new ArgusCtmBlockPlan(level, state, pos);
    }

    public ArgusConfig config() {
        return config;
    }

    public @Nullable BlockAndTintGetter level() {
        return level;
    }

    public @Nullable BlockState state() {
        return state;
    }

    public @Nullable BlockPos pos() {
        return pos;
    }

    public ArgusCtmBlockPlan ctm() {
        return ctmPlan;
    }
}
