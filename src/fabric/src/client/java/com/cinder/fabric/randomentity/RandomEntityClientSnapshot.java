package com.cinder.fabric.randomentity;

import com.cinder.randomentity.RandomEntityContext;
import com.cinder.randomentity.RandomEntityRuleSet;
import com.cinder.resource.NamespaceId;
import net.minecraft.resources.Identifier;

/**
 * Fabric-side immutable Random Entity texture snapshot.
 *
 * <p>Purpose: converts Minecraft {@link Identifier} texture locations into the
 * loader-agnostic shared rule set and converts selected replacements back to
 * Minecraft identifiers.
 *
 * <p>Threading: immutable after construction; read concurrently by render
 * state submission.
 *
 * <p>Performance: HOT PATH. One map lookup in the shared rule set plus the
 * small selected rule list. No allocation when the snapshot is empty or no
 * entry exists for the base texture.
 */
public final class RandomEntityClientSnapshot {
    private static final RandomEntityClientSnapshot EMPTY =
            new RandomEntityClientSnapshot(RandomEntityRuleSet.empty(), 0);

    private final RandomEntityRuleSet ruleSet;
    private final int version;

    public RandomEntityClientSnapshot(RandomEntityRuleSet ruleSet,
                                      int version) {
        this.ruleSet = ruleSet == null ? RandomEntityRuleSet.empty() : ruleSet;
        this.version = version;
    }

    public static RandomEntityClientSnapshot empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return ruleSet.isEmpty();
    }

    public int version() {
        return version;
    }

    public Identifier remap(Identifier baseTexture,
                            RandomEntityContext context) {
        int index = resolveIndex(baseTexture, context);
        return remap(baseTexture, index);
    }

    public int resolveIndex(Identifier baseTexture,
                            RandomEntityContext context) {
        if (baseTexture == null || context == null || ruleSet.isEmpty()) {
            return 1;
        }
        NamespaceId base = new NamespaceId(baseTexture.getNamespace(),
                baseTexture.getPath());
        RandomEntityRuleSet.Entry entry = ruleSet.entry(base);
        if (entry == null) {
            return 1;
        }
        return entry.resolveIndex(context);
    }

    public Identifier remap(Identifier baseTexture, int variantIndex) {
        if (baseTexture == null || variantIndex <= 1 || ruleSet.isEmpty()) {
            return baseTexture;
        }
        NamespaceId base = new NamespaceId(baseTexture.getNamespace(),
                baseTexture.getPath());
        RandomEntityRuleSet.Entry entry = ruleSet.entry(base);
        if (entry == null) {
            return baseTexture;
        }
        NamespaceId selected = entry.textureForIndex(variantIndex);
        if (selected.equals(base)) {
            return baseTexture;
        }
        return Identifier.fromNamespaceAndPath(selected.namespace(),
                selected.path());
    }
}
