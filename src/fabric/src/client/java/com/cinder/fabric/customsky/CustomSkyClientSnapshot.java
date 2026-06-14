package com.cinder.fabric.customsky;

import com.cinder.customsky.CustomSkyLayer;
import com.cinder.customsky.CustomSkyRuleSet;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

import java.util.ArrayList;

/**
 * Fabric runtime snapshot for Custom Sky.
 *
 * <p>Threading: immutable after reload publication. Performance: render code
 * reads compact arrays and pre-resolved texture identifiers only.
 */
public final class CustomSkyClientSnapshot {

    private static final CustomSkyClientSnapshot EMPTY =
            new CustomSkyClientSnapshot(CustomSkyRuleSet.empty(),
                    new RuntimeLayer[0]);

    private final CustomSkyRuleSet ruleSet;
    private final RuntimeLayer[] layers;

    private CustomSkyClientSnapshot(CustomSkyRuleSet ruleSet,
                                    RuntimeLayer[] layers) {
        this.ruleSet = ruleSet == null ? CustomSkyRuleSet.empty() : ruleSet;
        this.layers = layers == null ? new RuntimeLayer[0] : layers.clone();
    }

    public static CustomSkyClientSnapshot empty() {
        return EMPTY;
    }

    public static CustomSkyClientSnapshot from(CustomSkyRuleSet ruleSet,
                                               ResourceManager manager,
                                               Logger logger) {
        if (ruleSet == null || ruleSet.isEmpty()) {
            return EMPTY;
        }
        ArrayList<RuntimeLayer> layers = new ArrayList<>();
        for (CustomSkyLayer layer : ruleSet.all()) {
            Identifier id = Identifier.fromNamespaceAndPath(
                    layer.source().namespace(), layer.source().path());
            if (manager.getResource(id).isEmpty()) {
                if (logger != null) {
                    logger.warn("[Cinder] skipping Custom Sky layer {}: "
                            + "missing source {}", layer.sourceFile(), id);
                }
                continue;
            }
            layers.add(new RuntimeLayer(layer, id));
        }
        if (layers.isEmpty()) {
            return EMPTY;
        }
        return new CustomSkyClientSnapshot(ruleSet,
                layers.toArray(RuntimeLayer[]::new));
    }

    public CustomSkyRuleSet ruleSet() {
        return ruleSet;
    }

    public boolean isEmpty() {
        return layers.length == 0;
    }

    public int size() {
        return layers.length;
    }

    public RuntimeLayer[] layers() {
        return layers.clone();
    }

    /**
     * Immutable resolved layer consumed by the renderer.
     */
    public record RuntimeLayer(CustomSkyLayer rule, Identifier texture) {
    }
}
