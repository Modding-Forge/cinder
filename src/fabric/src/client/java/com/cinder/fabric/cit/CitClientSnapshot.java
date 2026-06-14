package com.cinder.fabric.cit;

import com.cinder.cit.CitRule;
import com.cinder.cit.CitRuleSet;
import com.cinder.resource.NamespaceId;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Fabric-resolved CIT snapshot for hot item-render lookup.
 *
 * <p>Purpose: converts shared {@link NamespaceId} item ids to actual
 * Minecraft {@link Item} instances once at reload, then exposes O(1)
 * identity lookups during rendering.
 *
 * <p>Threading: immutable after construction and published atomically by
 * {@link CitRuntime}.
 *
 * <p>Performance: HOT PATH via {@link #rulesFor(Item)}. It performs one
 * identity-map lookup and returns the immutable per-item array.
 */
public final class CitClientSnapshot {

    private static final CitRule[] EMPTY_RULES = new CitRule[0];
    private static final CitClientSnapshot EMPTY =
            new CitClientSnapshot(CitRuleSet.empty(), new IdentityHashMap<>());

    private final CitRuleSet ruleSet;
    private final IdentityHashMap<Item, CitRule[]> byItem;

    private CitClientSnapshot(CitRuleSet ruleSet,
                              IdentityHashMap<Item, CitRule[]> byItem) {
        this.ruleSet = Objects.requireNonNull(ruleSet, "ruleSet");
        this.byItem = new IdentityHashMap<>(byItem);
    }

    public static CitClientSnapshot empty() {
        return EMPTY;
    }

    public static CitClientSnapshot from(CitRuleSet ruleSet) {
        Objects.requireNonNull(ruleSet, "ruleSet");
        if (ruleSet.isEmpty()) {
            return EMPTY;
        }
        IdentityHashMap<Item, CitRule[]> index = new IdentityHashMap<>();
        for (Map.Entry<NamespaceId, CitRule[]> entry
                : ruleSet.byItem().entrySet()) {
            Identifier id = Identifier.fromNamespaceAndPath(
                    entry.getKey().namespace(), entry.getKey().path());
            Item item = BuiltInRegistries.ITEM.getValue(id);
            if (item != null) {
                index.put(item, entry.getValue());
            }
        }
        if (index.isEmpty()) {
            return EMPTY;
        }
        return new CitClientSnapshot(ruleSet, index);
    }

    public CitRuleSet ruleSet() {
        return ruleSet;
    }

    public boolean isEmpty() {
        return byItem.isEmpty();
    }

    public CitRule[] rulesFor(Item item) {
        CitRule[] rules = byItem.get(item);
        return rules == null ? EMPTY_RULES : rules;
    }
}
