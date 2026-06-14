package com.cinder.ctm;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Parser-level representation of an OptiFine block-spec such as
 * {@code minecraft:oak_stairs:facing=east,west:half=bottom}.
 *
 * <p>Format: {@code <namespace:><name><:prop1=val1,val2:prop2=val1>}.
 * The OptiFine convention allows omitting the namespace to default to
 * {@code minecraft}, but the {@code .properties} format always uses
 * it explicitly. Property values are stored as a {@code Map<String, Set<String>>}
 * so the matcher can answer "is this {@code BlockState} in the spec?"
 * by iterating the keys once.
 *
 * <p>This type is parser-level only; evaluation against a real
 * {@code BlockState} happens in the fabric/ adapter in a later phase.
 */
public final class BlockSpec {

    private final String namespace;
    private final String name;
    private final Map<String, Set<String>> properties;

    public BlockSpec(String namespace, String name, Map<String, Set<String>> properties) {
        this.namespace = namespace;
        this.name = name;
        this.properties = properties == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(properties);
    }

    /**
     * Parses a single block spec. Throws {@link IllegalArgumentException}
     * on malformed input.
     */
    public static BlockSpec parse(String spec) {
        if (spec == null || spec.isEmpty()) {
            throw new IllegalArgumentException("empty block spec");
        }
        // Optional namespace.
        String ns = "minecraft";
        String rest = spec;
        if (rest.startsWith("minecraft:")) {
            ns = "minecraft";
            rest = rest.substring("minecraft:".length());
        } else {
            int colon = rest.indexOf(':');
            if (colon > 0) {
                String afterColon = rest.substring(colon + 1);
                int nextColon = afterColon.indexOf(':');
                String nextSegment = nextColon < 0
                        ? afterColon
                        : afterColon.substring(0, nextColon);
                if (!nextSegment.contains("=")) {
                    ns = rest.substring(0, colon);
                    rest = afterColon;
                }
            }
        }
        // Then name[:prop=val,val:prop=val]
        String[] parts = rest.split(":");
        if (parts.length == 0 || parts[0].isEmpty()) {
            throw new IllegalArgumentException("missing block name in: " + spec);
        }
        String name = parts[0];
        Map<String, Set<String>> props = new LinkedHashMap<>();
        for (int i = 1; i < parts.length; i++) {
            String propSpec = parts[i];
            int eq = propSpec.indexOf('=');
            if (eq < 0) {
                throw new IllegalArgumentException(
                        "property spec without '=' in: " + propSpec);
            }
            String key = propSpec.substring(0, eq);
            String values = propSpec.substring(eq + 1);
            Set<String> valueSet = new java.util.LinkedHashSet<>();
            for (String v : values.split(",")) {
                if (!v.isEmpty()) {
                    valueSet.add(v);
                }
            }
            props.put(key, valueSet);
        }
        return new BlockSpec(ns, name, props);
    }

    public String namespace() {
        return namespace;
    }

    public String name() {
        return name;
    }

    public Map<String, Set<String>> properties() {
        return properties;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(namespace).append(':').append(name);
        for (Map.Entry<String, Set<String>> e : properties.entrySet()) {
            sb.append(':').append(e.getKey()).append('=');
            boolean first = true;
            for (String v : e.getValue()) {
                if (!first) {
                    sb.append(',');
                }
                sb.append(v);
                first = false;
            }
        }
        return sb.toString();
    }
}
