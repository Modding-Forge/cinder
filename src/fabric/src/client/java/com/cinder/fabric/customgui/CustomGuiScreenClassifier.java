package com.cinder.fabric.customgui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.world.inventory.ChestMenu;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps Minecraft screen instances to OptiFine Custom GUI container ids.
 *
 * <p>Purpose: keeps Minecraft class checks out of shared parser code and
 * provides the small set of screen facts Phase F can evaluate safely at
 * screen-open time.
 *
 * <p>Threading: called on the render thread during {@code Gui#setScreen}.
 *
 * <p>Performance: one ordered instanceof chain per screen open.
 */
final class CustomGuiScreenClassifier {

    private CustomGuiScreenClassifier() {
    }

    static CustomGuiScreenContext classify(Screen screen) {
        if (screen == null) {
            return null;
        }
        String container = containerId(screen);
        if (container == null) {
            return null;
        }
        HashMap<String, Boolean> bools = new HashMap<>();
        HashMap<String, String> strings = new HashMap<>();
        HashMap<String, Integer> ints = new HashMap<>();
        addChestFacts(screen, bools, ints);
        addShulkerFacts(screen, strings);
        return new CustomGuiScreenContext(container,
                screen.getTitle().getString(),
                Integer.MIN_VALUE,
                bools,
                strings,
                ints);
    }

    private static String containerId(Screen screen) {
        if (screen instanceof ContainerScreen) {
            return "chest";
        }
        if (screen instanceof ShulkerBoxScreen) {
            return "shulker_box";
        }
        if (screen instanceof InventoryScreen
                || screen instanceof CreativeModeInventoryScreen) {
            return "inventory";
        }
        if (screen instanceof CraftingScreen) {
            return "crafting";
        }
        if (screen instanceof FurnaceScreen) {
            return "furnace";
        }
        if (screen instanceof BlastFurnaceScreen) {
            return "blast_furnace";
        }
        if (screen instanceof SmokerScreen) {
            return "smoker";
        }
        if (screen instanceof DispenserScreen) {
            return "dispenser";
        }
        if (screen instanceof HopperScreen) {
            return "hopper";
        }
        if (screen instanceof BrewingStandScreen) {
            return "brewing_stand";
        }
        if (screen instanceof BeaconScreen) {
            return "beacon";
        }
        if (screen instanceof AnvilScreen) {
            return "anvil";
        }
        if (screen instanceof EnchantmentScreen) {
            return "enchantment";
        }
        if (screen instanceof GrindstoneScreen) {
            return "grindstone";
        }
        if (screen instanceof CartographyTableScreen) {
            return "cartography";
        }
        if (screen instanceof LoomScreen) {
            return "loom";
        }
        if (screen instanceof StonecutterScreen) {
            return "stonecutter";
        }
        if (screen instanceof SmithingScreen) {
            return "smithing";
        }
        if (screen instanceof MerchantScreen) {
            return "villager";
        }
        if (screen instanceof HorseInventoryScreen
                || screen instanceof NautilusInventoryScreen) {
            return "horse";
        }
        if (screen instanceof LecternScreen
                || screen instanceof BookViewScreen
                || screen instanceof BookEditScreen
                || screen instanceof BookSignScreen) {
            return "book";
        }
        return null;
    }

    private static void addChestFacts(Screen screen,
                                      Map<String, Boolean> bools,
                                      Map<String, Integer> ints) {
        if (!(screen instanceof ContainerScreen containerScreen)) {
            return;
        }
        ChestMenu menu = containerScreen.getMenu();
        int rows = menu.getRowCount();
        bools.put("large", rows >= 6);
        ints.put("levels", rows);
    }

    private static void addShulkerFacts(Screen screen,
                                        Map<String, String> strings) {
        if (!(screen instanceof ShulkerBoxScreen)) {
            return;
        }
        String color = CustomGuiRuntime.consumePendingShulkerColor();
        if (color != null && !color.isEmpty()) {
            strings.put("colors", color);
        }
    }
}
