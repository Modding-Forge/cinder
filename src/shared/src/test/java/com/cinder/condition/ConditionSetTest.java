package com.cinder.condition;

import com.cinder.resource.ComponentMatchers;
import com.cinder.resource.NamespaceId;
import com.cinder.resource.RangeListInt;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionSetTest {

    @Test
    void emptySet_matchesEveryContext() {
        assertTrue(ConditionSet.empty().matches(new FakeContext()));
    }

    @Test
    void conditions_areSortedByCostWithStableTies() {
        Condition expensive = Condition.string(
                ConditionKey.CUSTOM_NAME, null,
                ComponentMatchers.parse("regex:.*"));
        Condition cheapOne = Condition.bool(
                ConditionKey.CONTAINER_FLAG, "large", true);
        Condition constant = Condition.stringSet(
                ConditionKey.SCREEN_ID, null,
                new String[]{"chest"}, ConditionCost.CONSTANT);
        Condition cheapTwo = Condition.intRange(
                ConditionKey.HEIGHT, null, RangeListInt.parse("1-3"));

        ConditionSet set = ConditionSet.of(List.of(
                expensive, cheapOne, constant, cheapTwo));

        assertEquals(List.of(constant, cheapOne, cheapTwo, expensive),
                set.conditions());
    }

    @Test
    void matching_shortCircuitsBeforeExpensiveCondition() {
        Condition expensive = Condition.string(
                ConditionKey.CUSTOM_NAME, null,
                ComponentMatchers.parse("regex:.*"));
        Condition cheapFalse = Condition.bool(
                ConditionKey.CONTAINER_FLAG, "large", true);
        ConditionSet set = ConditionSet.of(List.of(expensive, cheapFalse));

        FakeContext context = new FakeContext();
        context.throwOnString(ConditionKey.CUSTOM_NAME, null);

        assertFalse(set.matches(context));
    }

    @Test
    void stringExistsCondition_handlesMissingValues() {
        Condition existsFalse = Condition.string(
                ConditionKey.CUSTOM_NAME, null,
                ComponentMatchers.parse("exists:false"));
        Condition existsTrue = Condition.string(
                ConditionKey.CUSTOM_NAME, null,
                ComponentMatchers.parse("exists:true"));
        FakeContext context = new FakeContext();

        assertTrue(existsFalse.matches(context));
        assertFalse(existsTrue.matches(context));
    }

    @Test
    void namespaceSet_matchesSingleOrSetFacts() {
        Condition item = Condition.namespaceSet(
                ConditionKey.ITEM_ID, null,
                new NamespaceId[]{NamespaceId.parse("minecraft:diamond")},
                ConditionCost.CONSTANT);
        Condition enchant = Condition.namespaceSet(
                ConditionKey.ENCHANTMENT_ID, null,
                new NamespaceId[]{NamespaceId.parse("minecraft:mending")},
                ConditionCost.CHEAP);
        FakeContext context = new FakeContext()
                .string(ConditionKey.ITEM_ID, null, "minecraft:diamond")
                .id(ConditionKey.ENCHANTMENT_ID, null,
                        NamespaceId.parse("minecraft:mending"));

        assertTrue(item.matches(context));
        assertTrue(enchant.matches(context));
    }

    @Test
    void nullContext_throws() {
        assertThrows(NullPointerException.class,
                () -> ConditionSet.empty().matches(null));
    }

    static final class FakeContext implements ConditionContext {
        private final Map<String, String> strings = new HashMap<>();
        private final Map<String, Integer> ints = new HashMap<>();
        private final Map<String, Boolean> booleans = new HashMap<>();
        private final Map<String, Set<NamespaceId>> ids = new HashMap<>();
        private final Set<String> throwingStrings = new HashSet<>();

        FakeContext string(ConditionKey key,
                           String qualifier,
                           String value) {
            strings.put(mapKey(key, qualifier), value);
            return this;
        }

        FakeContext integer(ConditionKey key,
                            String qualifier,
                            int value) {
            ints.put(mapKey(key, qualifier), value);
            return this;
        }

        FakeContext bool(ConditionKey key,
                         String qualifier,
                         boolean value) {
            booleans.put(mapKey(key, qualifier), value);
            return this;
        }

        FakeContext id(ConditionKey key,
                       String qualifier,
                       NamespaceId value) {
            ids.computeIfAbsent(mapKey(key, qualifier),
                    ignored -> new HashSet<>()).add(value);
            return this;
        }

        FakeContext throwOnString(ConditionKey key, String qualifier) {
            throwingStrings.add(mapKey(key, qualifier));
            return this;
        }

        @Override
        public boolean has(ConditionKey key, String qualifier) {
            String k = mapKey(key, qualifier);
            return strings.containsKey(k)
                    || ints.containsKey(k)
                    || booleans.containsKey(k)
                    || ids.containsKey(k);
        }

        @Override
        public String stringValue(ConditionKey key, String qualifier) {
            String k = mapKey(key, qualifier);
            if (throwingStrings.contains(k)) {
                throw new AssertionError("expensive condition evaluated");
            }
            return strings.get(k);
        }

        @Override
        public int intValue(ConditionKey key,
                            String qualifier,
                            int fallback) {
            return ints.getOrDefault(mapKey(key, qualifier), fallback);
        }

        @Override
        public boolean booleanValue(ConditionKey key,
                                    String qualifier,
                                    boolean fallback) {
            return booleans.getOrDefault(mapKey(key, qualifier), fallback);
        }

        @Override
        public boolean contains(ConditionKey key,
                                String qualifier,
                                NamespaceId value) {
            Set<NamespaceId> set = ids.get(mapKey(key, qualifier));
            return set != null && set.contains(value)
                    || ConditionContext.super.contains(key, qualifier, value);
        }

        private static String mapKey(ConditionKey key, String qualifier) {
            return key.name() + ":" + (qualifier == null ? "" : qualifier);
        }
    }
}
