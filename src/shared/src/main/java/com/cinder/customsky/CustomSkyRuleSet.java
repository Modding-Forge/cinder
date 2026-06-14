package com.cinder.customsky;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Immutable collection of parsed custom-sky layers.
 *
 * <p>Threading: atomically published by loader code and read concurrently by
 * the renderer. Performance: layers are sorted once at reload time; world
 * filtering is a compact array scan because custom-sky layer counts are tiny.
 */
public final class CustomSkyRuleSet {

    private static final CustomSkyRuleSet EMPTY =
            new CustomSkyRuleSet(new CustomSkyLayer[0]);

    private final CustomSkyLayer[] layers;

    public CustomSkyRuleSet(CustomSkyLayer[] layers) {
        CustomSkyLayer[] copy = layers == null
                ? new CustomSkyLayer[0]
                : layers.clone();
        Arrays.sort(copy, Comparator
                .comparingInt((CustomSkyLayer l) -> l.world().id())
                .thenComparingInt(CustomSkyLayer::layerIndex)
                .thenComparing(CustomSkyLayer::sourceFile));
        this.layers = copy;
    }

    public static CustomSkyRuleSet empty() {
        return EMPTY;
    }

    public static CustomSkyRuleSet of(List<CustomSkyLayer> layers) {
        if (layers == null || layers.isEmpty()) {
            return EMPTY;
        }
        return new CustomSkyRuleSet(layers.toArray(CustomSkyLayer[]::new));
    }

    public boolean isEmpty() {
        return layers.length == 0;
    }

    public CustomSkyLayer[] all() {
        return layers.clone();
    }

    public CustomSkyLayer[] layersForWorld(int worldId) {
        ArrayList<CustomSkyLayer> out = new ArrayList<>();
        for (CustomSkyLayer layer : layers) {
            if (layer.world().id() == worldId) {
                out.add(layer);
            }
        }
        return out.toArray(CustomSkyLayer[]::new);
    }
}
