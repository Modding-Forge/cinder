package com.cinder.fabric.customcolors;

import com.cinder.ctm.BlockSpec;
import com.cinder.customcolors.ColormapImage;
import com.cinder.customcolors.ColormapRule;
import com.cinder.customcolors.ColorOverrideTable;
import com.cinder.customcolors.CustomColorRuleSet;
import com.cinder.resource.NamespaceId;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Fabric runtime snapshot derived from the shared Custom Colors rule set.
 *
 * <p>Threading: immutable after publication through
 * {@link CustomColorsRuntime}. Performance: block palette lookups are an
 * identity-map block prefilter followed by a small fixed rule array.
 */
public final class CustomColorsClientSnapshot {

    private static final CustomColorsClientSnapshot EMPTY =
            new CustomColorsClientSnapshot(CustomColorRuleSet.empty(),
                    new IdentityHashMap<>(), Map.of());

    private final CustomColorRuleSet ruleSet;
    private final IdentityHashMap<Block, RuntimeRule[]> blockRules;
    private final Map<NamespaceId, ColormapImage> imagesBySource;

    private CustomColorsClientSnapshot(CustomColorRuleSet ruleSet,
                                       IdentityHashMap<Block, RuntimeRule[]> blockRules,
                                       Map<NamespaceId, ColormapImage> imagesBySource) {
        this.ruleSet = ruleSet;
        this.blockRules = blockRules;
        this.imagesBySource = Map.copyOf(imagesBySource);
    }

    public static CustomColorsClientSnapshot empty() {
        return EMPTY;
    }

    public static CustomColorsClientSnapshot from(CustomColorRuleSet ruleSet,
                                                 Map<NamespaceId, ColormapImage> imagesBySource) {
        if (ruleSet == null || ruleSet.isEmpty()) {
            return empty();
        }
        IdentityHashMap<Block, ArrayList<RuntimeRule>> building =
                new IdentityHashMap<>();
        for (ColormapRule rule : ruleSet.blockRules()) {
            ColormapImage image = imagesBySource.get(rule.source());
            if (image == null && !rule.hasFixedColor()) {
                continue;
            }
            for (BlockSpec spec : rule.blocks()) {
                Block block = resolveBlock(spec);
                if (block == null) {
                    continue;
                }
                building.computeIfAbsent(block, ignored -> new ArrayList<>())
                        .add(new RuntimeRule(rule, image, spec));
            }
        }
        IdentityHashMap<Block, RuntimeRule[]> indexed = new IdentityHashMap<>();
        for (Map.Entry<Block, ArrayList<RuntimeRule>> entry : building.entrySet()) {
            indexed.put(entry.getKey(), entry.getValue()
                    .toArray(RuntimeRule[]::new));
        }
        return new CustomColorsClientSnapshot(ruleSet, indexed,
                imagesBySource);
    }

    public boolean isEmpty() {
        return ruleSet.isEmpty() && blockRules.isEmpty();
    }

    public ColorOverrideTable overrides() {
        return ruleSet.overrides();
    }

    public Map<NamespaceId, ColormapImage> imagesBySource() {
        return imagesBySource;
    }

    public RuntimeRule[] rulesFor(Block block) {
        RuntimeRule[] rules = blockRules.get(block);
        return rules == null ? RuntimeRule.EMPTY : rules;
    }

    public ColormapImage special(String key) {
        return ruleSet.special(key);
    }

    public ColormapImage environment(String key) {
        return ruleSet.environment(key);
    }

    public Iterable<Block> paletteBlocks() {
        return blockRules.keySet();
    }

    private static Block resolveBlock(BlockSpec spec) {
        Identifier id = Identifier.fromNamespaceAndPath(
                spec.namespace(), spec.name());
        Block block = BuiltInRegistries.BLOCK.getValue(id);
        return block == null || BuiltInRegistries.BLOCK.getKey(block) == null
                ? null : block;
    }

    public record RuntimeRule(ColormapRule rule,
                              ColormapImage image,
                              BlockSpec spec) {
        static final RuntimeRule[] EMPTY = new RuntimeRule[0];

        boolean matches(BlockState state) {
            if (spec.properties().isEmpty()) {
                return true;
            }
            for (Map.Entry<String, java.util.Set<String>> expected
                    : spec.properties().entrySet()) {
                Property<?> property = findProperty(state, expected.getKey());
                if (property == null) {
                    return false;
                }
                String actual = propertyName(state, property);
                if (!expected.getValue().contains(actual)) {
                    return false;
                }
            }
            return true;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static String propertyName(BlockState state,
                                           Property property) {
            return property.getName(state.getValue(property));
        }

        private static Property<?> findProperty(BlockState state,
                                                String name) {
            for (Property<?> property : state.getProperties()) {
                if (property.getName().equals(name)) {
                    return property;
                }
            }
            return null;
        }
    }
}
