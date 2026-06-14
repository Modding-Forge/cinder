package com.cinder.condition;

import com.cinder.resource.ComponentMatchers;
import com.cinder.resource.NamespaceId;
import com.cinder.resource.PropertiesFile;
import com.cinder.resource.RangeListInt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Shared parser helpers for OptiFine-style feature condition keys.
 *
 * <p>Purpose: Phase D does not implement CIT or Custom GUI rendering, but it
 * centralises the condition keys those future features will parse so they do
 * not grow incompatible matcher implementations.
 *
 * <p>Unknown keys are ignored here. Feature-specific parsers remain
 * responsible for warnings, required texture/model fields, priority, and
 * resource-path validation.
 *
 * <p>Threading: stateless utility class. Returned {@link ConditionSet}
 * instances are immutable.
 *
 * <p>Performance: parsing runs at resource reload. Evaluation cost is encoded
 * in each compiled condition and sorted by {@link ConditionSet}.
 */
public final class ConditionPropertiesReader {

    private static final String NBT_PREFIX = "nbt.";
    private static final String COMPONENT_PREFIX = "components.";

    private ConditionPropertiesReader() {
    }

    /**
     * Reads CIT-relevant condition keys from {@code props}.
     */
    public static ConditionSet readCitConditions(PropertiesFile props) {
        Objects.requireNonNull(props, "props");
        List<Condition> out = new ArrayList<>();

        String matchItems = firstPresent(props, "matchItems", "items");
        if (matchItems != null) {
            out.add(namespaceSet("items", matchItems, ConditionKey.ITEM_ID,
                    null, ConditionCost.CONSTANT));
        }
        addRange(out, props, "damage", ConditionKey.DAMAGE, null);
        addRange(out, props, "damageMask", ConditionKey.DAMAGE_MASK, null);
        addRange(out, props, "damagePercent",
                ConditionKey.DAMAGE_PERCENT, null);
        addRange(out, props, "stackSize", ConditionKey.STACK_SIZE, null);
        addNamespaceSet(out, props, "enchantments",
                ConditionKey.ENCHANTMENT_ID, null, ConditionCost.CHEAP);
        addNamespaceSet(out, props, "enchantmentIDs",
                ConditionKey.ENCHANTMENT_ID, null, ConditionCost.CHEAP);
        addRange(out, props, "enchantmentLevels",
                ConditionKey.ENCHANTMENT_LEVEL, null);
        addStringSet(out, props, "hand", ConditionKey.HAND, null,
                ConditionCost.CHEAP);

        for (Map.Entry<String, String> entry : props.entries().entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(NBT_PREFIX) && key.length() > NBT_PREFIX.length()) {
                out.add(stringMatcher(key, entry.getValue(),
                        ConditionKey.NBT_RAW,
                        key.substring(NBT_PREFIX.length()),
                        ConditionCost.EXPENSIVE));
            } else if (key.startsWith(COMPONENT_PREFIX)
                    && key.length() > COMPONENT_PREFIX.length()) {
                out.add(stringMatcher(key, entry.getValue(),
                        ConditionKey.COMPONENT,
                        unescapeComponentKey(
                                key.substring(COMPONENT_PREFIX.length())),
                        ConditionCost.EXPENSIVE));
            }
        }

        return ConditionSet.of(out);
    }

    /**
     * Reads Custom GUI-relevant condition keys from {@code props}.
     */
    public static ConditionSet readGuiConditions(PropertiesFile props) {
        Objects.requireNonNull(props, "props");
        List<Condition> out = new ArrayList<>();

        addStringSet(out, props, "container", ConditionKey.SCREEN_ID, null,
                ConditionCost.CONSTANT);
        addStringMatcher(out, props, "name", ConditionKey.CUSTOM_NAME, null);
        addNamespaceSet(out, props, "biomes", ConditionKey.BIOME, null,
                ConditionCost.CHEAP);
        addRange(out, props, "heights", ConditionKey.HEIGHT, null);

        addBooleanFlag(out, props, "large");
        addBooleanFlag(out, props, "trapped");
        addBooleanFlag(out, props, "christmas");
        addBooleanFlag(out, props, "ender");
        addRange(out, props, "levels", ConditionKey.CONTAINER_FLAG,
                "levels");
        addStringSet(out, props, "professions", ConditionKey.CONTAINER_FLAG,
                "professions", ConditionCost.CHEAP);
        addStringSet(out, props, "variants", ConditionKey.CONTAINER_FLAG,
                "variants", ConditionCost.CHEAP);
        addStringSet(out, props, "colors", ConditionKey.CONTAINER_FLAG,
                "colors", ConditionCost.CHEAP);

        return ConditionSet.of(out);
    }

    private static void addStringMatcher(List<Condition> out,
                                         PropertiesFile props,
                                         String propertyKey,
                                         ConditionKey key,
                                         String qualifier) {
        String value = props.get(propertyKey);
        if (value != null) {
            out.add(stringMatcher(propertyKey, value, key, qualifier, null));
        }
    }

    private static Condition stringMatcher(String propertyKey,
                                           String value,
                                           ConditionKey key,
                                           String qualifier,
                                           ConditionCost cost) {
        try {
            ComponentMatchers.Compiled matcher =
                    ComponentMatchers.parse(value);
            if (cost == null) {
                return Condition.string(key, qualifier, matcher);
            }
            return Condition.string(key, qualifier, matcher, cost);
        } catch (RuntimeException e) {
            throw new ConditionParseException(propertyKey, value, e);
        }
    }

    private static void addRange(List<Condition> out,
                                 PropertiesFile props,
                                 String propertyKey,
                                 ConditionKey key,
                                 String qualifier) {
        String value = props.get(propertyKey);
        if (value == null) {
            return;
        }
        try {
            out.add(Condition.intRange(
                    key, qualifier, RangeListInt.parse(value)));
        } catch (RuntimeException e) {
            throw new ConditionParseException(propertyKey, value, e);
        }
    }

    private static void addBooleanFlag(List<Condition> out,
                                       PropertiesFile props,
                                       String propertyKey) {
        String value = props.get(propertyKey);
        if (value == null) {
            return;
        }
        String trimmed = value.trim();
        if (!trimmed.equals("true") && !trimmed.equals("false")) {
            throw new ConditionParseException(propertyKey, value,
                    "expected true or false");
        }
        out.add(Condition.bool(ConditionKey.CONTAINER_FLAG,
                propertyKey, Boolean.parseBoolean(trimmed)));
    }

    private static void addNamespaceSet(List<Condition> out,
                                        PropertiesFile props,
                                        String propertyKey,
                                        ConditionKey key,
                                        String qualifier,
                                        ConditionCost cost) {
        String value = props.get(propertyKey);
        if (value != null) {
            out.add(namespaceSet(propertyKey, value, key, qualifier, cost));
        }
    }

    private static Condition namespaceSet(String propertyKey,
                                          String value,
                                          ConditionKey key,
                                          String qualifier,
                                          ConditionCost cost) {
        String[] tokens = splitList(value);
        if (tokens.length == 0) {
            throw new ConditionParseException(propertyKey, value,
                    "expected at least one id");
        }
        NamespaceId[] ids = new NamespaceId[tokens.length];
        try {
            for (int i = 0; i < tokens.length; i++) {
                ids[i] = NamespaceId.parse(tokens[i]);
            }
        } catch (RuntimeException e) {
            throw new ConditionParseException(propertyKey, value, e);
        }
        return Condition.namespaceSet(key, qualifier, ids, cost);
    }

    private static void addStringSet(List<Condition> out,
                                     PropertiesFile props,
                                     String propertyKey,
                                     ConditionKey key,
                                     String qualifier,
                                     ConditionCost cost) {
        String value = props.get(propertyKey);
        if (value == null) {
            return;
        }
        String[] tokens = splitList(value);
        if (tokens.length == 0) {
            throw new ConditionParseException(propertyKey, value,
                    "expected at least one value");
        }
        out.add(Condition.stringSet(key, qualifier, tokens, cost));
    }

    private static String firstPresent(PropertiesFile props,
                                       String first,
                                       String second) {
        String value = props.get(first);
        return value != null ? value : props.get(second);
    }

    private static String[] splitList(String value) {
        if (value == null || value.isBlank()) {
            return new String[0];
        }
        return value.trim().split("[,\\s]+");
    }

    private static String unescapeComponentKey(String key) {
        if (key.equals("~")) {
            return "minecraft:custom_name";
        }
        return key.replace("\\:", ":");
    }
}
