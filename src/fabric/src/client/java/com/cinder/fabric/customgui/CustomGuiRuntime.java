package com.cinder.fabric.customgui;

import com.cinder.config.CinderConfigHolder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Atomic holder and render-hook front door for Fabric Custom GUI.
 *
 * <p>Purpose: keeps reload, screen-open matching, and GUI blit replacement
 * separated. Resource-pack conditions are evaluated once per screen, not per
 * blit.
 *
 * <p>Threading: snapshots and screen override tables are atomically replaced.
 * Screen matching is called from the client render thread.
 *
 * <p>Performance: HOT PATH in {@link #override(Identifier)}. It performs only
 * config/compat checks and one immutable map lookup.
 */
public final class CustomGuiRuntime {

    private static final AtomicReference<CustomGuiClientSnapshot> SNAPSHOT =
            new AtomicReference<>(CustomGuiClientSnapshot.empty());
    private static final AtomicReference<CustomGuiScreenOverrides> ACTIVE =
            new AtomicReference<>(CustomGuiScreenOverrides.EMPTY);
    private static final AtomicReference<String> PENDING_SHULKER_COLOR =
            new AtomicReference<>();

    private CustomGuiRuntime() {
    }

    public static void replace(CustomGuiClientSnapshot snapshot) {
        SNAPSHOT.set(snapshot != null ? snapshot : CustomGuiClientSnapshot.empty());
        ACTIVE.set(CustomGuiScreenOverrides.EMPTY);
    }

    public static CustomGuiClientSnapshot snapshot() {
        return SNAPSHOT.get();
    }

    /**
     * Stores the color of a shulker box that is about to open a screen.
     *
     * <p>Threading: called on the client thread from the interaction hook.
     * The value is atomically consumed during the next screen classification.
     *
     * <p>Performance: one atomic write outside render hot paths.
     */
    public static void rememberPendingShulkerColor(String color) {
        PENDING_SHULKER_COLOR.set(color);
    }

    /**
     * Clears stale shulker context when an interaction does not open a shulker
     * screen.
     *
     * <p>Threading: client-thread interaction/screen lifecycle helper.
     *
     * <p>Performance: one atomic write outside render hot paths.
     */
    public static void clearPendingShulkerColor() {
        PENDING_SHULKER_COLOR.set(null);
    }

    /**
     * Consumes the remembered shulker color for the current screen match.
     *
     * <p>Threading: called on the client render thread during screen changes.
     *
     * <p>Performance: one atomic get-and-clear per shulker screen open.
     */
    static String consumePendingShulkerColor() {
        return PENDING_SHULKER_COLOR.getAndSet(null);
    }

    public static void screenChanged(Screen screen) {
        if (!enabled()) {
            ACTIVE.set(CustomGuiScreenOverrides.EMPTY);
            clearPendingShulkerColor();
            return;
        }
        CustomGuiClientSnapshot snapshot = SNAPSHOT.get();
        if (snapshot.isEmpty()) {
            ACTIVE.set(CustomGuiScreenOverrides.EMPTY);
            clearPendingShulkerColor();
            return;
        }
        CustomGuiScreenContext context =
                CustomGuiScreenClassifier.classify(screen);
        if (context == null) {
            clearPendingShulkerColor();
        }
        ACTIVE.set(snapshot.resolve(context));
    }

    public static Identifier override(Identifier original) {
        if (original == null || !enabled()) {
            return original;
        }
        return ACTIVE.get().override(original);
    }

    private static boolean enabled() {
        return CinderConfigHolder.get().customGuiActive()
                && !FabricLoader.getInstance().isModLoaded("optigui");
    }
}
