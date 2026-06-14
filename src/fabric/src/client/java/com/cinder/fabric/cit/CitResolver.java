package com.cinder.fabric.cit;

import com.cinder.cit.CitRule;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Snapshot-local CIT selector with content-based caching.
 *
 * <p>Purpose: avoids repeated condition evaluation for identical rendered item
 * content while never consulting wall-clock time. The cache is discarded on
 * resource reload together with the snapshot.
 *
 * <p>Threading: item rendering is client-thread oriented today. The tiny LRU is
 * synchronized to remain safe if Mojang renders items from worker contexts in
 * the future.
 */
public final class CitResolver {

    private static final int MAX_CACHE_ENTRIES = 512;

    private final CitClientSnapshot snapshot;
    private final Map<CitCacheKey, CitRule> cache =
            new LinkedHashMap<>(64, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<CitCacheKey, CitRule> eldest) {
                    return size() > MAX_CACHE_ENTRIES;
                }
            };

    public CitResolver(CitClientSnapshot snapshot) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
    }

    public CitClientSnapshot snapshot() {
        return snapshot;
    }

    public CitRule select(ItemStack stack, String hand) {
        if (snapshot.isEmpty()) {
            return null;
        }
        CitRule[] rules = snapshot.rulesFor(stack.getItem());
        if (rules.length == 0) {
            return null;
        }
        CitCacheKey key = CitCacheKey.from(stack, hand);
        synchronized (cache) {
            if (cache.containsKey(key)) {
                return cache.get(key);
            }
        }
        CitConditionContext context = new CitConditionContext(stack, hand);
        CitRule selected = null;
        for (CitRule rule : rules) {
            if (rule.conditions().matches(context)) {
                selected = rule;
                break;
            }
        }
        synchronized (cache) {
            cache.put(key, selected);
        }
        return selected;
    }
}
