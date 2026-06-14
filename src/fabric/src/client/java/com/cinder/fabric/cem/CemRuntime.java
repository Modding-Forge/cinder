package com.cinder.fabric.cem;

import com.cinder.cem.CemModel;
import com.cinder.config.CinderConfigHolder;
import com.cinder.resource.NamespaceId;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fabric-side Custom Entity Model runtime snapshot.
 *
 * <p>Purpose: stores the active immutable CEM model table and exposes tiny
 * renderer-facing lookups for the first in-game smoke hook.
 *
 * <p>Threading: snapshots are immutable and atomically replaced on resource
 * reload. Render hooks only read the current reference.
 *
 * <p>Performance: HOT PATH. Lookups are a config gate plus one map access.
 */
public final class CemRuntime {
    private static final Logger LOGGER =
            LoggerFactory.getLogger("cinder/cem");
    private static final AtomicReference<Snapshot> SNAPSHOT =
            new AtomicReference<>(Snapshot.EMPTY);
    private static volatile boolean warnedEmf;

    private CemRuntime() {
    }

    public static void replace(Snapshot snapshot) {
        SNAPSHOT.set(snapshot == null ? Snapshot.EMPTY : snapshot);
    }

    public static boolean hasModel(String entityKey) {
        return active() && SNAPSHOT.get().models.containsKey(entityKey);
    }

    public static Identifier texture(String entityKey, Identifier fallback) {
        if (!active()) {
            return fallback;
        }
        CemModel model = SNAPSHOT.get().models.get(entityKey);
        if (model == null || model.texture() == null) {
            return fallback;
        }
        NamespaceId texture = model.texture();
        return Identifier.fromNamespaceAndPath(texture.namespace(),
                texture.path());
    }

    public static boolean active() {
        if (!CinderConfigHolder.get().customEntityModelsActive()) {
            return false;
        }
        if (FabricLoader.getInstance().isModLoaded("entity_model_features")) {
            if (!warnedEmf) {
                warnedEmf = true;
                LOGGER.warn("[Cinder] EMF detected; Cinder CEM runtime "
                        + "selection is disabled");
            }
            return false;
        }
        return true;
    }

    /**
     * Immutable CEM client snapshot keyed by normalized entity/model name, for
     * example {@code creeper}.
     */
    public record Snapshot(Map<String, CemModel> models) {
        public static final Snapshot EMPTY = new Snapshot(Map.of());

        public Snapshot {
            models = models == null ? Map.of() : Map.copyOf(models);
        }
    }
}
