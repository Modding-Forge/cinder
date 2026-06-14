package com.cinder.condition;

import com.cinder.resource.NamespaceId;

/**
 * Loader-agnostic view of the facts needed by compiled resource-pack
 * conditions.
 *
 * <p>Purpose: Fabric, later NeoForge, and pure JVM tests can expose item,
 * screen, block-entity, biome, and component facts without leaking their
 * concrete runtime types into {@code src/shared}.
 *
 * <p>Missing values are represented by {@link #has(ConditionKey, String)}
 * returning {@code false}. Other accessors must return their provided fallback
 * or {@code null}; they must not throw for missing values.
 *
 * <p>Threading: contexts are passed per evaluation. They may be immutable or
 * thread-confined mutable adapter objects, but must not be global state.
 *
 * <p>Performance: evaluation code calls these methods in sorted condition
 * order. Implementations for hot paths should avoid allocation.
 */
public interface ConditionContext {

    /**
     * Returns true when the requested fact exists.
     *
     * @param key stable fact key
     * @param qualifier optional sub-key, such as an NBT path or GUI flag name
     */
    boolean has(ConditionKey key, String qualifier);

    /**
     * Returns a string fact, or {@code null} when missing.
     */
    String stringValue(ConditionKey key, String qualifier);

    /**
     * Returns an integer fact, or {@code fallback} when missing.
     */
    int intValue(ConditionKey key, String qualifier, int fallback);

    /**
     * Returns a boolean fact, or {@code fallback} when missing.
     */
    boolean booleanValue(ConditionKey key, String qualifier, boolean fallback);

    /**
     * Returns true when a set-like fact contains {@code value}.
     *
     * <p>The default implementation falls back to a single string fact, which
     * is enough for simple contexts such as item id or biome id.
     */
    default boolean contains(ConditionKey key,
                             String qualifier,
                             NamespaceId value) {
        String actual = stringValue(key, qualifier);
        return actual != null && actual.equals(value.toString());
    }

    /**
     * Convenience overload for unqualified facts.
     */
    default boolean has(ConditionKey key) {
        return has(key, null);
    }

    /**
     * Convenience overload for unqualified string facts.
     */
    default String stringValue(ConditionKey key) {
        return stringValue(key, null);
    }

    /**
     * Convenience overload for unqualified integer facts.
     */
    default int intValue(ConditionKey key, int fallback) {
        return intValue(key, null, fallback);
    }

    /**
     * Convenience overload for unqualified boolean facts.
     */
    default boolean booleanValue(ConditionKey key, boolean fallback) {
        return booleanValue(key, null, fallback);
    }

    /**
     * Convenience overload for unqualified set-like facts.
     */
    default boolean contains(ConditionKey key, NamespaceId value) {
        return contains(key, null, value);
    }
}
