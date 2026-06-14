package com.cinder.fabric.cit;

import com.cinder.cit.CitRule;
import com.cinder.config.CinderConfigHolder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Atomic holder and front-door resolver for Fabric CIT.
 *
 * <p>Purpose: keeps item-render hooks tiny and safe. The hook performs only
 * cheap global checks before delegating to the resolver with a snapshot-local
 * cache.
 *
 * <p>Threading: snapshot replacement is atomic; resolver cache is owned by
 * the published snapshot wrapper and contains no weak references.
 */
public final class CitRuntime {

    private static final AtomicReference<CitResolver> RESOLVER =
            new AtomicReference<>(new CitResolver(CitClientSnapshot.empty()));

    private CitRuntime() {
    }

    public static void replace(CitClientSnapshot snapshot) {
        RESOLVER.set(new CitResolver(snapshot));
    }

    public static CitClientSnapshot snapshot() {
        return RESOLVER.get().snapshot();
    }

    /**
     * Performance: HOT PATH. Checks config, compatibility, empty stack, and
     * item-prefilter before building any condition context.
     */
    public static CitRule select(ItemStack stack, String hand) {
        if (!CinderConfigHolder.get().citActive()) {
            return null;
        }
        if (FabricLoader.getInstance().isModLoaded("citresewn")) {
            return null;
        }
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return RESOLVER.get().select(stack, hand);
    }
}
