package com.cinder.randomentity;

import com.cinder.resource.ComponentMatchers;
import com.cinder.resource.NamespaceId;
import com.cinder.resource.RangeListInt;
import com.cinder.resource.WeightedSelector;

import java.util.Arrays;
import java.util.Set;

/**
 * Immutable Random Entity rule.
 *
 * <p>Rules are evaluated in file order; the first matching rule chooses one of
 * its texture indices using a deterministic selector.
 */
public final class RandomEntityRule {
    private final int[] textureIndices;
    private final WeightedSelector selector;
    private final Set<NamespaceId> biomes;
    private final RangeListInt heights;
    private final ComponentMatchers.Compiled name;
    private final Set<String> professions;
    private final Set<String> colors;
    private final Boolean baby;
    private final RangeListInt health;
    private final boolean healthPercent;
    private final RangeListInt moonPhase;
    private final RangeListInt dayTime;
    private final Set<String> weather;
    private final RangeListInt sizes;
    private final Set<NamespaceId> blocks;
    private final int seedOffset;
    private final boolean seedSourceVehicle;

    public RandomEntityRule(int[] textureIndices,
                            int[] weights,
                            Set<NamespaceId> biomes,
                            RangeListInt heights,
                            ComponentMatchers.Compiled name,
                            Set<String> professions,
                            Set<String> colors,
                            Boolean baby,
                            RangeListInt health,
                            boolean healthPercent,
                            RangeListInt moonPhase,
                            RangeListInt dayTime,
                            Set<String> weather,
                            RangeListInt sizes,
                            Set<NamespaceId> blocks,
                            int seedOffset,
                            boolean seedSourceVehicle) {
        if (textureIndices == null || textureIndices.length == 0) {
            throw new IllegalArgumentException("textures must be non-empty");
        }
        this.textureIndices = textureIndices.clone();
        int[] actualWeights = normalizeWeights(weights, textureIndices.length);
        this.selector = new WeightedSelector(actualWeights);
        this.biomes = biomes == null ? Set.of() : Set.copyOf(biomes);
        this.heights = heights;
        this.name = name;
        this.professions = professions == null ? Set.of() : Set.copyOf(professions);
        this.colors = colors == null ? Set.of() : Set.copyOf(colors);
        this.baby = baby;
        this.health = health;
        this.healthPercent = healthPercent;
        this.moonPhase = moonPhase;
        this.dayTime = dayTime;
        this.weather = weather == null ? Set.of() : Set.copyOf(weather);
        this.sizes = sizes;
        this.blocks = blocks == null ? Set.of() : Set.copyOf(blocks);
        this.seedOffset = seedOffset;
        this.seedSourceVehicle = seedSourceVehicle;
    }

    public int selectIndex(RandomEntityContext context) {
        long seed = seedSourceVehicle ? context.vehicleSeed()
                : context.entitySeed();
        seed ^= Integer.toUnsignedLong(seedOffset * 0x9E3779B9);
        return textureIndices[selector.sample(seed)];
    }

    public boolean matches(RandomEntityContext context) {
        if (!biomes.isEmpty() && (context.biome() == null
                || !biomes.contains(context.biome()))) {
            return false;
        }
        if (heights != null && !heights.contains(context.height())) {
            return false;
        }
        if (name != null && (context.name() == null
                || !name.matches(context.name()))) {
            return false;
        }
        if (!professions.isEmpty() && (context.profession() == null
                || !professions.contains(context.profession()))) {
            return false;
        }
        if (!colors.isEmpty() && (context.color() == null
                || !colors.contains(context.color()))) {
            return false;
        }
        if (baby != null && baby.booleanValue() != context.baby()) {
            return false;
        }
        if (health != null) {
            int value = healthPercent && context.maxHealth() > 0
                    ? Math.round(context.health() * 100.0f / context.maxHealth())
                    : context.health();
            if (!health.contains(value)) {
                return false;
            }
        }
        if (moonPhase != null && !moonPhase.contains(context.moonPhase())) {
            return false;
        }
        if (dayTime != null && !dayTime.contains(context.dayTime())) {
            return false;
        }
        if (!weather.isEmpty() && (context.weather() == null
                || !weather.contains(context.weather()))) {
            return false;
        }
        if (sizes != null && !sizes.contains(context.size())) {
            return false;
        }
        return blocks.isEmpty() || context.block() != null
                && blocks.contains(context.block());
    }

    public int[] textureIndices() {
        return textureIndices.clone();
    }

    private static int[] normalizeWeights(int[] weights, int count) {
        int[] out = new int[count];
        Arrays.fill(out, 1);
        if (weights != null) {
            System.arraycopy(weights, 0, out, 0, Math.min(count, weights.length));
        }
        return out;
    }
}
