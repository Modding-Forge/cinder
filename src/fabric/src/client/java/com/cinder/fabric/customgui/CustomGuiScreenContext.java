package com.cinder.fabric.customgui;

import com.cinder.condition.ConditionContext;
import com.cinder.condition.ConditionKey;
import com.cinder.resource.NamespaceId;

import java.util.Map;
import java.util.Objects;

/**
 * Fabric-side Custom GUI condition context.
 *
 * <p>Purpose: exposes the facts known at screen-open time to shared
 * {@code ConditionSet} rules without leaking Minecraft classes into shared
 * code.
 *
 * <p>Threading: immutable and used on the render thread after construction.
 *
 * <p>Performance: built once per active screen. Blit hooks do not create this
 * object.
 */
final class CustomGuiScreenContext implements ConditionContext {

    private final String container;
    private final String name;
    private final int height;
    private final Map<String, Boolean> booleanFlags;
    private final Map<String, String> stringFlags;
    private final Map<String, Integer> intFlags;

    CustomGuiScreenContext(String container,
                           String name,
                           int height,
                           Map<String, Boolean> booleanFlags,
                           Map<String, String> stringFlags,
                           Map<String, Integer> intFlags) {
        this.container = Objects.requireNonNull(container, "container");
        this.name = name;
        this.height = height;
        this.booleanFlags = Map.copyOf(booleanFlags);
        this.stringFlags = Map.copyOf(stringFlags);
        this.intFlags = Map.copyOf(intFlags);
    }

    String container() {
        return container;
    }

    @Override
    public boolean has(ConditionKey key, String qualifier) {
        return switch (key) {
            case SCREEN_ID -> true;
            case CUSTOM_NAME -> name != null;
            case HEIGHT -> height != Integer.MIN_VALUE;
            case CONTAINER_FLAG -> hasFlag(qualifier);
            default -> false;
        };
    }

    @Override
    public String stringValue(ConditionKey key, String qualifier) {
        return switch (key) {
            case SCREEN_ID -> container;
            case CUSTOM_NAME -> name;
            case CONTAINER_FLAG -> stringFlags.get(qualifier);
            default -> null;
        };
    }

    @Override
    public int intValue(ConditionKey key, String qualifier, int fallback) {
        if (key == ConditionKey.HEIGHT) {
            return height == Integer.MIN_VALUE ? fallback : height;
        }
        if (key == ConditionKey.CONTAINER_FLAG) {
            Integer value = intFlags.get(qualifier);
            return value != null ? value : fallback;
        }
        return fallback;
    }

    @Override
    public boolean booleanValue(ConditionKey key,
                                String qualifier,
                                boolean fallback) {
        if (key != ConditionKey.CONTAINER_FLAG) {
            return fallback;
        }
        Boolean value = booleanFlags.get(qualifier);
        return value != null ? value : fallback;
    }

    @Override
    public boolean contains(ConditionKey key,
                            String qualifier,
                            NamespaceId value) {
        if (key == ConditionKey.BIOME) {
            return false;
        }
        return ConditionContext.super.contains(key, qualifier, value);
    }

    private boolean hasFlag(String qualifier) {
        return booleanFlags.containsKey(qualifier)
                || stringFlags.containsKey(qualifier)
                || intFlags.containsKey(qualifier);
    }
}
