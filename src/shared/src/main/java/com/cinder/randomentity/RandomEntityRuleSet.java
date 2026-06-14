package com.cinder.randomentity;

import com.cinder.resource.NamespaceId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable Random Entity lookup table keyed by base texture.
 *
 * <p>Performance: renderer adapters use O(1) map lookup by base texture and
 * then evaluate the small per-texture rule list.
 */
public final class RandomEntityRuleSet {
    private static final RandomEntityRuleSet EMPTY =
            new RandomEntityRuleSet(Map.of());

    private final Map<NamespaceId, Entry> entries;

    public RandomEntityRuleSet(Map<NamespaceId, Entry> entries) {
        this.entries = Map.copyOf(entries);
    }

    public static RandomEntityRuleSet empty() {
        return EMPTY;
    }

    public Entry entry(NamespaceId baseTexture) {
        return entries.get(baseTexture);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public Map<NamespaceId, Entry> entries() {
        return entries;
    }

    public record Entry(NamespaceId baseTexture,
                        List<RandomEntityVariant> variants,
                        List<RandomEntityRule> rules) {
        public Entry {
            variants = List.copyOf(variants);
            rules = List.copyOf(rules);
        }

        public NamespaceId resolve(RandomEntityContext context) {
            int selectedIndex = resolveIndex(context);
            for (RandomEntityVariant variant : variants) {
                if (variant.index() == selectedIndex) {
                    return variant.texture();
                }
            }
            return baseTexture;
        }

        public int resolveIndex(RandomEntityContext context) {
            int selectedIndex = 1;
            for (RandomEntityRule rule : rules) {
                if (rule.matches(context)) {
                    selectedIndex = rule.selectIndex(context);
                    break;
                }
            }
            if (rules.isEmpty() && variants.size() > 1) {
                selectedIndex = variants.get((int) Math.floorMod(
                        context.entitySeed(), variants.size())).index();
            }
            return hasVariant(selectedIndex) ? selectedIndex : 1;
        }

        public NamespaceId textureForIndex(int index) {
            for (RandomEntityVariant variant : variants) {
                if (variant.index() == index) {
                    return variant.texture();
                }
            }
            return baseTexture;
        }

        private boolean hasVariant(int index) {
            for (RandomEntityVariant variant : variants) {
                if (variant.index() == index) {
                    return true;
                }
            }
            return false;
        }
    }

    public static final class Builder {
        private final Map<NamespaceId, Entry> entries = new LinkedHashMap<>();

        public void add(Entry entry) {
            entries.put(entry.baseTexture(), entry);
        }

        public RandomEntityRuleSet build() {
            return entries.isEmpty() ? empty() : new RandomEntityRuleSet(entries);
        }
    }
}
