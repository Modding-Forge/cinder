package com.cinder.natural;

import com.cinder.resource.NamespaceId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable Natural Textures lookup table.
 *
 * <p>Threading: atomically published by the loader. Performance: one map
 * lookup per candidate quad.
 */
public final class NaturalTextureRuleSet {

    private static final NaturalTextureRuleSet EMPTY =
            new NaturalTextureRuleSet(Map.of());

    private final Map<NamespaceId, NaturalTextureRule> bySprite;

    public NaturalTextureRuleSet(Map<NamespaceId, NaturalTextureRule> rules) {
        this.bySprite = Map.copyOf(rules);
    }

    public static NaturalTextureRuleSet empty() {
        return EMPTY;
    }

    public static NaturalTextureRuleSet of(List<NaturalTextureRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return EMPTY;
        }
        LinkedHashMap<NamespaceId, NaturalTextureRule> map =
                new LinkedHashMap<>();
        for (NaturalTextureRule rule : rules) {
            map.put(rule.sprite(), rule);
        }
        return new NaturalTextureRuleSet(map);
    }

    public boolean isEmpty() {
        return bySprite.isEmpty();
    }

    public NaturalTextureRule ruleFor(NamespaceId sprite) {
        return bySprite.get(sprite);
    }

    public int size() {
        return bySprite.size();
    }
}
