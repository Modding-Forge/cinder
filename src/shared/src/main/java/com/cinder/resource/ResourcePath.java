package com.cinder.resource;

import java.util.Objects;

/**
 * Resolves a textual OptiFine/Continuity resource path into a concrete
 * {@link NamespaceId}.
 *
 * <p>The OptiFine convention (documented in
 * {@code optifine/OptiFineDoc/doc/}) supports seven syntactic forms. We
 * implement them in a single dispatcher so that every CTM/CIT/CEM file uses
 * the same logic. The order in this file mirrors the documentation's
 * precedence; the first matching rule wins.
 *
 * <h2>Supported forms (in order of resolution)</h2>
 * <ol>
 *   <li>{@code <id>} - a {@code NamespaceId} literal in the form
 *       {@code namespace:path}, e.g. {@code minecraft:path/to/x.png}.</li>
 *   <li>{@code <mod>:<id>} - same, with a non-default namespace
 *       (e.g. {@code botania:blazeblock}).</li>
 *   <li>{@code ~/<rest>} - shorthand for the {@code minecraft} namespace
 *       under the OptiFine resource root
 *       ({@code assets/minecraft/optifine/<rest>}).</li>
 *   <li>{@code ./<rest>} or {@code <rest>} (relative to the resolving
 *       directory) - see {@link #resolve(String, String, NamespaceId)}.</li>
 *   <li>{@code <dir>/<file>} with no namespace, no leading {@code ~} or
 *       {@code ./} - resolved as
 *       {@code assets/minecraft/<dir>/<file>}.</li>
 * </ol>
 *
 * <p>Note: this class is parser-level only. It does not consult the
 * Minecraft {@code ResourceManager} - that is the
 * job of the fabric/ module's adapter in a later phase. Here we just turn
 * a string into a {@link NamespaceId} given a resolution context.
 *
 * <p>Performance: O(n) in the input length. No allocation beyond the result
 * and one or two intermediate strings.
 */
public final class ResourcePath {

    private static final String OPTIFINE_ROOT_SEGMENT = "optifine";

    private ResourcePath() {
    }

    /**
     * Resolves a resource path that lives in the
     * {@code assets/<namespace>/optifine/} tree.
     *
     * <p>This is the most common form for CTM/CIT/CEM configuration files,
     * since they almost always live under the {@code optifine} subfolder.
     *
     * @param raw           the value from a {@code .properties} file
     * @param defaultNs     the namespace to use if none is explicit
     * @param parent        the directory the {@code .properties} file was
     *                      loaded from, used as the resolution base for
     *                      relative paths; {@code null} disables relative
     *                      resolution
     * @return a parsed {@link NamespaceId}, never {@code null}
     */
    public static NamespaceId resolveOptifine(String raw, String defaultNs, NamespaceId parent) {
        Objects.requireNonNull(raw, "raw");
        String normalized = raw.replace('\\', '/');
        return resolveInternal(normalized, defaultNs, parent, true);
    }

    /**
     * Resolves a resource path that does <b>not</b> have the
     * {@code optifine} prefix injected automatically. Useful for paths that
     * appear in non-OptiFine contexts (e.g. resource-pack direct paths).
     */
    public static NamespaceId resolve(String raw, String defaultNs, NamespaceId parent) {
        Objects.requireNonNull(raw, "raw");
        String normalized = raw.replace('\\', '/');
        return resolveInternal(normalized, defaultNs, parent, false);
    }

    private static NamespaceId resolveInternal(
            String raw, String defaultNs, NamespaceId parent, boolean optifineRoot) {
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("empty resource path");
        }

        // Form 1+2: explicit namespace -> NamespaceId
        if (looksLikeNamespaced(raw)) {
            return NamespaceId.parse(raw);
        }

        // Form 3: ~/rest  -> <defaultNs>:optifine/rest
        if (raw.charAt(0) == '~' && (raw.length() == 1 || raw.charAt(1) == '/')) {
            String rest = raw.substring(raw.length() > 1 ? 2 : 1);
            String prefix = optifineRoot ? "optifine/" : "";
            return new NamespaceId(defaultNs, prefix + rest);
        }

        // Form 4: relative ./rest or just rest -> use parent's directory
        if (parent != null) {
            if (raw.startsWith("./")) {
                String rest = raw.substring(2);
                // The "parent" of a .properties file is the file's
                // directory itself (e.g. "optifine/ctm/glass"). A
                // "./subfolder/foo.png" in that file is therefore
                // "optifine/ctm/glass/subfolder/foo.png" - we do NOT
                // strip the filename with dirname here.
                String joined = parent.path() + "/" + rest;
                return new NamespaceId(parent.namespace(), joined);
            }
            if (!raw.contains("/") || raw.charAt(0) != '/') {
                // Bare filename (no '/'): place in the same directory
                // as parent. We use parent.path() directly (NOT
                // dirname), because a bare filename "1.png" inside a
                // .properties file that lives at optifine/ctm/glass
                // means "the file at optifine/ctm/glass/1.png", not
                // "the file at optifine/ctm/1.png".
                if (!raw.contains("/")) {
                    return new NamespaceId(parent.namespace(),
                            parent.path() + "/" + raw);
                }
            }
        }

        // Form 5: a/b/c with no namespace, no leading ~/ or ./
        // We interpret as <defaultNs>:<optifineRoot? optifine/ : >raw
        // Note: this is conservative; in practice a path that lives in
        // "optifine/" or "continuity/" should have come through one of the
        // relative forms above, because CTM/CIT/CEM files always live in
        // the same directory tree as the referencing .properties file.
        return new NamespaceId(defaultNs, (optifineRoot ? "optifine/" : "") + raw);
    }

    private static boolean looksLikeNamespaced(String s) {
        // The OptiFine namespace is "mod:path", where mod is a short
        // token. We require:
        //   - exactly one ':' character,
        //   - the chars before ':' form a valid namespace (letters,
        //     digits, '_', '-'),
        //   - the chars after ':' are non-empty and form a valid path.
        int colon = s.indexOf(':');
        if (colon <= 0) {
            return false;
        }
        if (s.indexOf(':', colon + 1) >= 0) {
            return false;
        }
        for (int i = 0; i < colon; i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '_' || c == '-';
            if (!ok) {
                return false;
            }
        }
        return s.length() > colon + 1;
    }

    private static String dirname(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }
}
