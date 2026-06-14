package com.cinder.fabric.customgui;

import com.cinder.customgui.CustomGuiReplacement;
import com.cinder.customgui.CustomGuiRule;
import com.cinder.customgui.CustomGuiRuleSet;
import com.cinder.resource.NamespaceId;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Fabric-resolved Custom GUI rule snapshot.
 *
 * <p>Purpose: keeps shared rules immutable while resolving texture payloads to
 * Minecraft identifiers only in Fabric code.
 *
 * <p>Threading: immutable after construction and atomically published.
 *
 * <p>Performance: screen-open path evaluates only candidates for the active
 * container. Blit path consumes {@link CustomGuiScreenOverrides}.
 */
public final class CustomGuiClientSnapshot {

    private static final CustomGuiClientSnapshot EMPTY =
            new CustomGuiClientSnapshot(CustomGuiRuleSet.empty());

    private static final Map<String, Identifier> DEFAULT_TEXTURES = Map.ofEntries(
            entry("chest", "textures/gui/container/generic_54.png"),
            entry("shulker_box", "textures/gui/container/shulker_box.png"),
            entry("inventory", "textures/gui/container/inventory.png"),
            entry("crafting", "textures/gui/container/crafting_table.png"),
            entry("furnace", "textures/gui/container/furnace.png"),
            entry("blast_furnace", "textures/gui/container/blast_furnace.png"),
            entry("smoker", "textures/gui/container/smoker.png"),
            entry("dispenser", "textures/gui/container/dispenser.png"),
            entry("hopper", "textures/gui/container/hopper.png"),
            entry("brewing_stand", "textures/gui/container/brewing_stand.png"),
            entry("beacon", "textures/gui/container/beacon.png"),
            entry("anvil", "textures/gui/container/anvil.png"),
            entry("enchantment", "textures/gui/container/enchanting_table.png"),
            entry("grindstone", "textures/gui/container/grindstone.png"),
            entry("cartography", "textures/gui/container/cartography_table.png"),
            entry("loom", "textures/gui/container/loom.png"),
            entry("stonecutter", "textures/gui/container/stonecutter.png"),
            entry("smithing", "textures/gui/container/smithing.png"),
            entry("villager", "textures/gui/container/villager.png"),
            entry("horse", "textures/gui/container/horse.png"),
            entry("book", "textures/gui/book.png"));

    private final CustomGuiRuleSet ruleSet;

    private CustomGuiClientSnapshot(CustomGuiRuleSet ruleSet) {
        this.ruleSet = Objects.requireNonNull(ruleSet, "ruleSet");
    }

    public static CustomGuiClientSnapshot empty() {
        return EMPTY;
    }

    public static CustomGuiClientSnapshot from(CustomGuiRuleSet ruleSet) {
        if (ruleSet == null || ruleSet.isEmpty()) {
            return EMPTY;
        }
        return new CustomGuiClientSnapshot(ruleSet);
    }

    public boolean isEmpty() {
        return ruleSet.isEmpty();
    }

    public CustomGuiRuleSet ruleSet() {
        return ruleSet;
    }

    CustomGuiScreenOverrides resolve(CustomGuiScreenContext context) {
        if (context == null || ruleSet.isEmpty()) {
            return CustomGuiScreenOverrides.EMPTY;
        }
        CustomGuiRule[] candidates = ruleSet.candidates(context.container());
        for (CustomGuiRule rule : candidates) {
            if (!rule.matches(context)) {
                continue;
            }
            CustomGuiScreenOverrides overrides =
                    buildOverrides(context.container(), rule.replacement());
            if (!overrides.isEmpty()) {
                return overrides;
            }
        }
        return CustomGuiScreenOverrides.EMPTY;
    }

    private static CustomGuiScreenOverrides buildOverrides(
            String container,
            CustomGuiReplacement replacement) {
        HashMap<Identifier, Identifier> out = new HashMap<>();
        Identifier defaultTexture = DEFAULT_TEXTURES.get(container);
        if (replacement.defaultTexture() != null && defaultTexture != null) {
            out.put(defaultTexture, id(replacement.defaultTexture()));
        }
        for (Map.Entry<String, NamespaceId> entry
                : replacement.namedTextures().entrySet()) {
            Identifier original = originalTexture(container, entry.getKey());
            if (original != null) {
                out.put(original, id(entry.getValue()));
            }
        }
        return out.isEmpty()
                ? CustomGuiScreenOverrides.EMPTY
                : new CustomGuiScreenOverrides(out);
    }

    private static Identifier originalTexture(String container, String key) {
        String normalized = key.replace("\\:", ":")
                .toLowerCase(Locale.ROOT);
        if (normalized.indexOf(':') > 0) {
            return Identifier.tryParse(normalized);
        }
        if (normalized.contains("/") || normalized.endsWith(".png")) {
            return Identifier.withDefaultNamespace(normalized);
        }
        if (normalized.equals("default")
                || normalized.equals("container")
                || normalized.equals(container)) {
            return DEFAULT_TEXTURES.get(container);
        }
        if (normalized.equals("large") && container.equals("chest")) {
            return DEFAULT_TEXTURES.get("chest");
        }
        Identifier byName = DEFAULT_TEXTURES.get(normalized);
        if (byName != null) {
            return byName;
        }
        return null;
    }

    private static Identifier id(NamespaceId id) {
        return Identifier.fromNamespaceAndPath(id.namespace(), id.path());
    }

    private static Map.Entry<String, Identifier> entry(String key,
                                                       String path) {
        return Map.entry(key, Identifier.withDefaultNamespace(path));
    }
}
