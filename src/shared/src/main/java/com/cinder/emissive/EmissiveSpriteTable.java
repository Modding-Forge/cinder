package com.cinder.emissive;

import com.cinder.resource.NamespaceId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Immutable runtime lookup from a base block-atlas sprite to its emissive
 * companion sprite.
 *
 * <p>Threading: tables are immutable. The current snapshot is atomically
 * replaced during resource reload and read lock-free by renderer workers.
 *
 * <p>Performance: HOT PATH. Lookup is a single map read from pre-parsed
 * {@link NamespaceId} values.
 */
public final class EmissiveSpriteTable {

    private static final EmissiveSpriteTable EMPTY =
            new EmissiveSpriteTable(Collections.emptyMap());
    private static final AtomicReference<EmissiveSpriteTable> CURRENT =
            new AtomicReference<>(EMPTY);

    private final Map<NamespaceId, NamespaceId> emissiveByBase;

    private EmissiveSpriteTable(Map<NamespaceId, NamespaceId> emissiveByBase) {
        this.emissiveByBase = Collections.unmodifiableMap(emissiveByBase);
    }

    /**
     * Returns an empty table.
     */
    public static EmissiveSpriteTable empty() {
        return EMPTY;
    }

    /**
     * Builds an immutable table from the given mappings.
     */
    public static EmissiveSpriteTable of(Map<NamespaceId, NamespaceId> mappings) {
        Objects.requireNonNull(mappings, "mappings");
        if (mappings.isEmpty()) {
            return EMPTY;
        }
        return new EmissiveSpriteTable(new LinkedHashMap<>(mappings));
    }

    /**
     * Publishes the current table. Passing {@code null} clears emissive data.
     */
    public static void replace(EmissiveSpriteTable table) {
        CURRENT.set(table == null ? EMPTY : table);
    }

    /**
     * Returns the current table snapshot.
     */
    public static EmissiveSpriteTable current() {
        return CURRENT.get();
    }

    /**
     * Returns {@code true} when no emissive mappings are active.
     */
    public boolean isEmpty() {
        return emissiveByBase.isEmpty();
    }

    /**
     * Returns the emissive sprite for a base sprite, or {@code null}.
     */
    public NamespaceId emissiveSprite(NamespaceId baseSprite) {
        return emissiveByBase.get(baseSprite);
    }

    /**
     * Number of active mappings.
     */
    public int size() {
        return emissiveByBase.size();
    }
}
