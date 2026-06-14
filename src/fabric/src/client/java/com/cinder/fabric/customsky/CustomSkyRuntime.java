package com.cinder.fabric.customsky;

import com.cinder.config.CinderConfigHolder;
import com.cinder.customsky.CustomSkyLayer;
import com.cinder.resource.NamespaceId;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Client-render-thread runtime for Custom Sky.
 *
 * <p>Performance: HOT PATH once per sky frame. Allocation policy: snapshot
 * arrays are reused; per-frame work is a short layer scan and texture render
 * calls only.
 */
public final class CustomSkyRuntime {

    private static final Logger LOGGER =
            LoggerFactory.getLogger("cinder/custom-sky");
    private static final AtomicReference<CustomSkyClientSnapshot> SNAPSHOT =
            new AtomicReference<>(CustomSkyClientSnapshot.empty());

    private static volatile State state = State.empty();
    private static boolean warnedCompat;

    private CustomSkyRuntime() {
    }

    public static void replace(CustomSkyClientSnapshot snapshot) {
        CustomSkyClientSnapshot next = snapshot == null
                ? CustomSkyClientSnapshot.empty()
                : snapshot;
        SNAPSHOT.set(next);
        state = State.forSnapshot(next);
    }

    public static CustomSkyClientSnapshot snapshot() {
        return SNAPSHOT.get();
    }

    public static void renderOverworld(PoseStack poseStack,
                                       float rainBrightness) {
        CustomSkyClientSnapshot snapshot = SNAPSHOT.get();
        if (snapshot.isEmpty()
                || !CinderConfigHolder.get().customSkyActive()
                || compatSkyModLoaded()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.gameRenderer == null) {
            return;
        }
        RenderContext context = RenderContext.create(minecraft, level,
                rainBrightness);
        State localState = state;
        CustomSkyClientSnapshot.RuntimeLayer[] layers = snapshot.layers();
        for (int i = 0; i < layers.length; i++) {
            CustomSkyClientSnapshot.RuntimeLayer runtime = layers[i];
            CustomSkyLayer rule = runtime.rule();
            float fade = rule.fadeAlpha(context.dayTime);
            if (fade <= 0.001F) {
                localState.step(i, 0.0F, rule.transitionTicks());
                continue;
            }
            float targetAlpha = rule.conditionTargetAlpha(context.worldId,
                    context.dayIndex, context.clearWeather,
                    context.rainWeather, context.thunderWeather,
                    context.biome, context.cameraY);
            float conditionAlpha = localState.step(i, targetAlpha,
                    rule.transitionTicks());
            float alpha = fade * conditionAlpha;
            if (alpha <= 0.001F) {
                continue;
            }
            CustomSkyRenderer.renderLayer(poseStack, runtime, context.dayTime,
                    alpha);
        }
    }

    private static boolean compatSkyModLoaded() {
        FabricLoader loader = FabricLoader.getInstance();
        boolean loaded = loader.isModLoaded("fabricskyboxes")
                || loader.isModLoaded("nuit");
        if (loaded && !warnedCompat) {
            warnedCompat = true;
            LOGGER.warn("[Cinder] another Custom Sky mod is loaded; "
                    + "Cinder Custom Sky is disabled fail-safe");
        }
        return loaded;
    }

    private record RenderContext(int worldId,
                                 int dayTime,
                                 long dayIndex,
                                 float clearWeather,
                                 float rainWeather,
                                 float thunderWeather,
                                 NamespaceId biome,
                                 int cameraY) {

        static RenderContext create(Minecraft minecraft,
                                    ClientLevel level,
                                    float rainBrightness) {
            int world = 0;
            if (level.dimension() == Level.NETHER) {
                world = -1;
            } else if (level.dimension() == Level.END) {
                world = 1;
            }
            long time = level.getOverworldClockTime();
            int dayTime = Math.floorMod((int) time, 24000);
            long day = Math.floorDiv(time, 24000L);
            float rainLevel = Math.max(0.0F,
                    Math.min(1.0F, 1.0F - rainBrightness));
            float thunderLevel = Math.max(0.0F,
                    Math.min(1.0F, level.getThunderLevel(1.0F)));
            float clearWeather = Math.max(0.0F, 1.0F - rainLevel);
            float thunderWeather = Math.min(rainLevel, thunderLevel);
            float rainWeather = Math.max(0.0F,
                    rainLevel - thunderWeather);
            BlockPos pos = BlockPos.containing(
                    minecraft.gameRenderer.mainCamera().position());
            NamespaceId biome = level.getBiome(pos).unwrapKey()
                    .map(key -> key.identifier())
                    .map(id -> new NamespaceId(id.getNamespace(),
                            id.getPath()))
                    .orElse(null);
            return new RenderContext(world, dayTime, day, clearWeather,
                    rainWeather, thunderWeather, biome, pos.getY());
        }
    }

    private static final class State {
        private final float[] conditionAlpha;

        private State(float[] conditionAlpha) {
            this.conditionAlpha = conditionAlpha;
        }

        static State empty() {
            return new State(new float[0]);
        }

        static State forSnapshot(CustomSkyClientSnapshot snapshot) {
            return new State(new float[snapshot == null ? 0
                    : snapshot.size()]);
        }

        float step(int index, float target, int transitionTicks) {
            if (index < 0 || index >= conditionAlpha.length) {
                return target;
            }
            float current = conditionAlpha[index];
            float goal = Math.max(0.0F, Math.min(1.0F, target));
            if (transitionTicks <= 0) {
                conditionAlpha[index] = goal;
                return goal;
            }
            float delta = 1.0F / transitionTicks;
            if (current < goal) {
                current = Math.min(goal, current + delta);
            } else if (current > goal) {
                current = Math.max(goal, current - delta);
            }
            conditionAlpha[index] = current;
            return current;
        }
    }
}
