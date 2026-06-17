package com.argus.client.sodium;

import net.caffeinemc.mods.sodium.client.render.model.MutableQuadViewImpl;
import net.caffeinemc.mods.sodium.client.services.DefaultModelEmitter;
import net.caffeinemc.mods.sodium.client.services.PlatformModelAccess;
import net.caffeinemc.mods.sodium.client.services.PlatformModelEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Predicate;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sodium model-emitter bridge for Argus terrain features.
 *
 * <p>Sodium discovers this class through its {@link PlatformModelEmitter}
 * service. The bridge prepares one block-level plan and then routes each model
 * quad through {@link ArgusSodiumQuadPipeline} before delegating to Sodium's
 * original quad sink.
 *
 * <p>Threading: Sodium may call the service from multiple section-build
 * workers. All mutable feature state is kept in a {@link ThreadLocal}.
 *
 * <p>Performance: HOT PATH. Allocation policy mirrors Sodium's default model
 * emitter, plus one small block plan per rendered model.
 */
public final class ArgusSodiumModelEmitter implements PlatformModelEmitter {

    private static final Logger LOGGER =
            LoggerFactory.getLogger("argus/sodium-emitter");
    private static final boolean DISABLED =
            Boolean.getBoolean("argus.sodium.modelEmitter.disabled");
    private static final ThreadLocal<ArgusSodiumQuadPipeline> PIPELINES =
            ThreadLocal.withInitial(ArgusSodiumQuadPipeline::new);
    private static final AtomicBoolean LOGGED = new AtomicBoolean();

    private static volatile boolean active;
    private static volatile ArgusSodiumModelEmitter overrideInstance;

    private final PlatformModelEmitter fallback = new DefaultModelEmitter();

    public ArgusSodiumModelEmitter() {
        active = true;
        if (overrideInstance == null) {
            overrideInstance = this;
        }
        if (LOGGED.compareAndSet(false, true)) {
            if (DISABLED) {
                LOGGER.info("Argus Sodium ModelEmitter active, pipeline disabled");
            } else {
                LOGGER.info("Argus Sodium ModelEmitter active");
            }
        }
    }

    /**
     * Returns true when Sodium loaded this service and Argus should suppress
     * the legacy processQuad feature path.
     */
    public static boolean featurePipelineActive() {
        return active && !DISABLED;
    }

    /**
     * Returns Argus's emitter when Sodium's ServiceLoader cannot see mod
     * service descriptors in the active loader classpath.
     *
     * <p>Threading: may be called by Sodium render workers after bootstrap.
     * Instance creation is synchronized and the instance itself keeps mutable
     * pipeline state thread-confined.
     */
    public static PlatformModelEmitter overrideInstance() {
        if (DISABLED) {
            return null;
        }
        ArgusSodiumModelEmitter instance = overrideInstance;
        if (instance != null) {
            return instance;
        }
        synchronized (ArgusSodiumModelEmitter.class) {
            instance = overrideInstance;
            if (instance == null) {
                instance = new ArgusSodiumModelEmitter();
                overrideInstance = instance;
            }
            return instance;
        }
    }

    @Override
    public void emitModel(BlockStateModel model,
                          Predicate<Direction> cullTest,
                          MutableQuadViewImpl quad,
                          RandomSource random,
                          BlockAndTintGetter blockView,
                          BlockPos pos,
                          BlockState state,
                          Bufferer defaultBuffer) {
        if (DISABLED) {
            fallback.emitModel(model, cullTest, quad, random, blockView, pos,
                    state, defaultBuffer);
            return;
        }
        ArgusSodiumBlockRenderPlan plan =
                new ArgusSodiumBlockRenderPlan(blockView, state, pos);
        ArgusSodiumQuadPipeline pipeline = PIPELINES.get();
        List<BlockStateModelPart> parts =
                PlatformModelAccess.getInstance()
                        .collectPartsOf(model, blockView, pos, state, random,
                                quad);
        for (int i = 0; i < parts.size(); i++) {
            BlockStateModelPart part = parts.get(i);
            defaultBuffer.emit(part, cullTest,
                    emittedQuad -> pipeline.emit(
                            emittedQuad,
                            MutableQuadViewImpl::emitDirectly,
                            plan));
        }
    }
}
