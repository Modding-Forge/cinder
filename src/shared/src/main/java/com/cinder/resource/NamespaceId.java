package com.cinder.resource;

import java.util.Objects;

/**
 * Loader-agnostic reference to a resource, encoded as {@code namespace:path}.
 *
 * <p>This is the parser-level representation used by the
 * {@code com.cinder.resource} package. It deliberately does not depend on
 * {@code net.minecraft.resources.Identifier} so that the
 * {@code com.cinder.common} module can be unit-tested in pure JVM context
 * (no Minecraft on the classpath).
 *
 * <p>Both fields are non-null, non-empty, and use only the character set
 * {@code [a-z0-9_./-]} for the path and {@code [a-z0-9_-]} for the
 * namespace. The constructor validates these rules so that invalid input
 * fails fast.
 *
 * <p>Instances are immutable and therefore safe to share.
 */
public record NamespaceId(String namespace, String path) {

    /** Default namespace for the vanilla resource pack. */
    public static final String DEFAULT_NAMESPACE = "minecraft";

    public NamespaceId {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(path, "path");
        if (namespace.isEmpty()) {
            throw new IllegalArgumentException("namespace is empty");
        }
        if (path.isEmpty()) {
            throw new IllegalArgumentException("path is empty");
        }
        validateChars("namespace", namespace, isNamespaceChar);
        validateChars("path", path, isPathChar);
    }

    /**
     * Convenience: returns {@code "namespace:path"} in the canonical form.
     */
    @Override
    public String toString() {
        return namespace + ":" + path;
    }

    /**
     * Parses a resource identifier from one of two textual forms:
     * <ul>
     *   <li>{@code minecraft:path/to/x.png} - explicit namespace.</li>
     *   <li>{@code path/to/x.png} - defaults to the {@code minecraft}
     *       namespace; this form is rejected if it contains a
     *       backslash because the OptiFine convention requires forward
     *       slashes.</li>
     * </ul>
     * The path is normalised to forward slashes.
     */
    public static NamespaceId parse(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("identifier is null");
        }
        // Normalise separators: any backslash becomes a forward slash,
        // and consecutive slashes are collapsed into a single slash.
        // This handles Windows-style paths coming through the OF format
        // and avoids the "path//to//x" double-slash trap.
        StringBuilder sb = new StringBuilder(raw.length());
        boolean lastWasSlash = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\\' || c == '/') {
                if (!lastWasSlash) {
                    sb.append('/');
                    lastWasSlash = true;
                }
            } else {
                sb.append(c);
                lastWasSlash = false;
            }
        }
        String normalized = sb.toString();
        int colon = normalized.indexOf(':');
        if (colon < 0) {
            return new NamespaceId(DEFAULT_NAMESPACE, normalized);
        }
        String ns = normalized.substring(0, colon);
        String path = normalized.substring(colon + 1);
        if (path.isEmpty()) {
            throw new IllegalArgumentException("path is empty in: " + raw);
        }
        return new NamespaceId(ns, path);
    }

    private interface CharTest {
        boolean isValid(char c);
    }

    private static final CharTest isNamespaceChar = c ->
            (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '_' || c == '-';

    private static final CharTest isPathChar = c ->
            isNamespaceChar.isValid(c) || c == '/' || c == '.';

    private static void validateChars(String what, String value, CharTest test) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!test.isValid(c)) {
                throw new IllegalArgumentException(
                        "Invalid character '" + c + "' in " + what + ": " + value);
            }
        }
    }
}
