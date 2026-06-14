package com.cinder.cit;

import com.cinder.resource.NamespaceId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable CIT reload snapshot.
 *
 * <p>Purpose: keeps all parsed rules plus a shared item-id prefilter. Fabric
 * builds an {@code IdentityHashMap<Item, CitRule[]>} from this shape, while
 * pure JVM tests can verify ordering and parser behavior without Minecraft.
 *
 * <p>Threading: immutable and safe for atomic publication.
 */
public final class CitRuleSet {

    private static final CitRuleSet EMPTY =
            new CitRuleSet(List.of(), Map.of());

    private final List<CitRule> rules;
    private final Map<NamespaceId, CitRule[]> byItem;

    private CitRuleSet(List<CitRule> rules,
                       Map<NamespaceId, CitRule[]> byItem) {
        this.rules = rules;
        this.byItem = byItem;
    }

    public static CitRuleSet empty() {
        return EMPTY;
    }

    public static CitRuleSet of(List<CitRule> input) {
        if (input == null || input.isEmpty()) {
            return EMPTY;
        }
        ArrayList<CitRule> sorted = new ArrayList<>(input);
        Collections.sort(sorted);
        List<CitRule> immutableRules = List.copyOf(sorted);

        HashMap<NamespaceId, ArrayList<CitRule>> grouped = new HashMap<>();
        for (CitRule rule : immutableRules) {
            if (!rule.isRenderableItemRule()) {
                continue;
            }
            for (NamespaceId item : rule.items()) {
                grouped.computeIfAbsent(item, ignored -> new ArrayList<>())
                        .add(rule);
            }
        }
        HashMap<NamespaceId, CitRule[]> index = new HashMap<>();
        for (Map.Entry<NamespaceId, ArrayList<CitRule>> entry
                : grouped.entrySet()) {
            index.put(entry.getKey(),
                    entry.getValue().toArray(CitRule[]::new));
        }
        return new CitRuleSet(immutableRules, Map.copyOf(index));
    }

    public List<CitRule> all() {
        return rules;
    }

    public CitRule[] candidates(NamespaceId itemId) {
        Objects.requireNonNull(itemId, "itemId");
        CitRule[] rules = byItem.get(itemId);
        return rules == null ? new CitRule[0] : rules.clone();
    }

    public Map<NamespaceId, CitRule[]> byItem() {
        return byItem;
    }

    public boolean isEmpty() {
        return rules.isEmpty();
    }
}
