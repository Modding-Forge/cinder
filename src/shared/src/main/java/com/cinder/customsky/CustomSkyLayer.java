package com.cinder.customsky;

import com.cinder.resource.NamespaceId;
import com.cinder.resource.RangeListInt;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable OptiFine custom-sky layer rule.
 *
 * <p>Threading: instances are built during reload and shared read-only with
 * the client renderer. Performance: render matching uses primitive fields and
 * arrays with no allocation.
 */
public final class CustomSkyLayer {

    public static final int WEATHER_CLEAR = 1;
    public static final int WEATHER_RAIN = 1 << 1;
    public static final int WEATHER_THUNDER = 1 << 2;

    private final String sourceFile;
    private final CustomSkyWorld world;
    private final int layerIndex;
    private final NamespaceId source;
    private final boolean hasFade;
    private final int startFadeIn;
    private final int endFadeIn;
    private final int startFadeOut;
    private final int endFadeOut;
    private final CustomSkyBlendMode blend;
    private final CustomSkyRotation rotation;
    private final RangeListInt days;
    private final int daysLoop;
    private final int weatherMask;
    private final NamespaceId[] biomes;
    private final RangeListInt heights;
    private final int transitionTicks;

    public CustomSkyLayer(String sourceFile,
                          CustomSkyWorld world,
                          int layerIndex,
                          NamespaceId source,
                          boolean hasFade,
                          int startFadeIn,
                          int endFadeIn,
                          int startFadeOut,
                          int endFadeOut,
                          CustomSkyBlendMode blend,
                          CustomSkyRotation rotation,
                          RangeListInt days,
                          int daysLoop,
                          int weatherMask,
                          NamespaceId[] biomes,
                          RangeListInt heights,
                          int transitionTicks) {
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
        this.world = Objects.requireNonNull(world, "world");
        this.layerIndex = layerIndex;
        this.source = Objects.requireNonNull(source, "source");
        this.hasFade = hasFade;
        this.startFadeIn = normalizeTick(startFadeIn);
        this.endFadeIn = normalizeTick(endFadeIn);
        this.startFadeOut = normalizeTick(startFadeOut);
        this.endFadeOut = normalizeTick(endFadeOut);
        this.blend = Objects.requireNonNull(blend, "blend");
        this.rotation = Objects.requireNonNull(rotation, "rotation");
        this.days = days == null ? RangeListInt.ALL : days;
        this.daysLoop = Math.max(1, daysLoop);
        this.weatherMask = weatherMask == 0 ? WEATHER_CLEAR : weatherMask;
        this.biomes = biomes == null ? new NamespaceId[0] : biomes.clone();
        this.heights = heights == null ? RangeListInt.ALL : heights;
        this.transitionTicks = Math.max(0, transitionTicks);
    }

    public String sourceFile() {
        return sourceFile;
    }

    public CustomSkyWorld world() {
        return world;
    }

    public int layerIndex() {
        return layerIndex;
    }

    public NamespaceId source() {
        return source;
    }

    public boolean hasFade() {
        return hasFade;
    }

    public int startFadeIn() {
        return startFadeIn;
    }

    public int endFadeIn() {
        return endFadeIn;
    }

    public int startFadeOut() {
        return startFadeOut;
    }

    public int endFadeOut() {
        return endFadeOut;
    }

    public CustomSkyBlendMode blend() {
        return blend;
    }

    public CustomSkyRotation rotation() {
        return rotation;
    }

    public RangeListInt days() {
        return days;
    }

    public int daysLoop() {
        return daysLoop;
    }

    public int weatherMask() {
        return weatherMask;
    }

    public NamespaceId[] biomes() {
        return biomes.clone();
    }

    public RangeListInt heights() {
        return heights;
    }

    public int transitionTicks() {
        return transitionTicks;
    }

    /**
     * Computes the time fade alpha for a Minecraft day tick in [0, 23999].
     */
    public float fadeAlpha(int dayTime) {
        if (!hasFade) {
            return 1.0F;
        }
        int tick = normalizeTick(dayTime);
        if (inWrappedRange(tick, startFadeIn, endFadeIn)) {
            int length = wrappedDistance(startFadeIn, endFadeIn);
            return length == 0 ? 1.0F
                    : wrappedDistance(startFadeIn, tick) / (float) length;
        }
        if (inWrappedRange(tick, endFadeIn, startFadeOut)) {
            return 1.0F;
        }
        if (inWrappedRange(tick, startFadeOut, endFadeOut)) {
            int length = wrappedDistance(startFadeOut, endFadeOut);
            return length == 0 ? 0.0F
                    : 1.0F - wrappedDistance(startFadeOut, tick)
                    / (float) length;
        }
        return 0.0F;
    }

    /**
     * Returns true when non-time conditions match the current context.
     */
    public boolean matches(int worldId,
                           long dayIndex,
                           int weather,
                           NamespaceId biome,
                           int y) {
        if (world.id() != worldId) {
            return false;
        }
        if (!matchesDay(dayIndex)) {
            return false;
        }
        if ((weatherMask & weather) == 0) {
            return false;
        }
        if (!heights.contains(y)) {
            return false;
        }
        if (biomes.length == 0) {
            return true;
        }
        if (biome == null) {
            return false;
        }
        for (NamespaceId allowed : biomes) {
            if (allowed.equals(biome)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Computes the target alpha for non-time conditions.
     *
     * <p>Weather transitions continuously in the vanilla sky renderer. Static
     * filters gate the layer, while the weather mask contributes a smooth
     * alpha target in [0, 1].
     */
    public float conditionTargetAlpha(int worldId,
                                      long dayIndex,
                                      float clearWeight,
                                      float rainWeight,
                                      float thunderWeight,
                                      NamespaceId biome,
                                      int y) {
        if (world.id() != worldId || !matchesDay(dayIndex)
                || !heights.contains(y) || !matchesBiome(biome)) {
            return 0.0F;
        }
        float alpha = 0.0F;
        if ((weatherMask & WEATHER_CLEAR) != 0) {
            alpha += clearWeight;
        }
        if ((weatherMask & WEATHER_RAIN) != 0) {
            alpha += rainWeight;
        }
        if ((weatherMask & WEATHER_THUNDER) != 0) {
            alpha += thunderWeight;
        }
        return Math.max(0.0F, Math.min(1.0F, alpha));
    }

    private boolean matchesBiome(NamespaceId biome) {
        if (biomes.length == 0) {
            return true;
        }
        if (biome == null) {
            return false;
        }
        for (NamespaceId allowed : biomes) {
            if (allowed.equals(biome)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesDay(long dayIndex) {
        if (days.isAll()) {
            return true;
        }
        int day = (int) Math.floorMod(dayIndex, daysLoop);
        return days.contains(day);
    }

    private static boolean inWrappedRange(int tick, int start, int end) {
        if (start == end) {
            return tick == start;
        }
        if (start < end) {
            return tick >= start && tick <= end;
        }
        return tick >= start || tick <= end;
    }

    private static int wrappedDistance(int start, int end) {
        return Math.floorMod(end - start, 24000);
    }

    private static int normalizeTick(int tick) {
        return Math.floorMod(tick, 24000);
    }

    @Override
    public String toString() {
        return "CustomSkyLayer{"
                + "sourceFile='" + sourceFile + '\''
                + ", world=" + world.id()
                + ", layerIndex=" + layerIndex
                + ", source=" + source
                + ", blend=" + blend
                + ", biomes=" + Arrays.toString(biomes)
                + '}';
    }
}
