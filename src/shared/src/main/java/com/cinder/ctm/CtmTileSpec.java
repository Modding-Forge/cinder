package com.cinder.ctm;

import com.cinder.resource.NamespaceId;
import com.cinder.resource.ResourcePath;

/**
 * One tile reference inside a CTM rule's {@code tiles=...} list.
 *
 * <p>A tile can be expressed as:
 * <ul>
 *   <li>a number, e.g. {@code 0} or {@code 8-11} (an index into the
 *       CTM tile list - for overlay methods, the special token
 *       {@code <skip>} is allowed);</li>
 *   <li>a name, e.g. {@code 1.png}, {@code glass_edge},</li>
 *   <li>an absolute path under {@code assets/...}, e.g.
 *       {@code optifine/ctm/myglass/1.png};</li>
 *   <li>the OptiFine shorthand {@code <default>} which means "use
 *       the tile that would have been selected if this rule did not
 *       exist".</li>
 * </ul>
 *
 * <p>For the parser layer, we just capture the spec and the resolved
 * form. The renderer phase will turn each {@code CtmTileSpec} into
 * a {@code GpuTextureView} layer index.
 */
public final class CtmTileSpec {

    /**
     * The original textual spec from the {@code .properties} file,
     * kept for diagnostics.
     */
    private final String rawSpec;

    /**
     * Resolved sprite ID, or {@code null} if the spec is a number
     * (numeric indices are resolved at engine time, not parse time).
     */
    private final NamespaceId resolvedSprite;

    /**
     * Numeric index for direct integer specs, or {@code -1} if this
     * is a name.
     */
    private final int numericIndex;

    private final boolean isSkip;
    private final boolean isDefault;

    private CtmTileSpec(String raw, NamespaceId sprite, int numeric,
                       boolean skip, boolean isDefault) {
        this.rawSpec = raw;
        this.resolvedSprite = sprite;
        this.numericIndex = numeric;
        this.isSkip = skip;
        this.isDefault = isDefault;
    }

    public static CtmTileSpec numeric(int index) {
        return new CtmTileSpec(Integer.toString(index), null, index, false, false);
    }

    public static CtmTileSpec name(String raw, NamespaceId sprite) {
        return new CtmTileSpec(raw, sprite, -1, false, false);
    }

    public static CtmTileSpec skip() {
        return new CtmTileSpec("<skip>", null, -1, true, false);
    }

    public static CtmTileSpec defaultTile() {
        return new CtmTileSpec("<default>", null, -1, false, true);
    }

    public static CtmTileSpec fromSpec(String raw, String defaultNs, NamespaceId parent) {
        String trimmed = raw.trim();
        if (trimmed.equals("<skip>")) {
            return skip();
        }
        if (trimmed.equals("<default>")) {
            return defaultTile();
        }
        NamespaceId textureSprite = texturePathSprite(trimmed, defaultNs);
        if (textureSprite != null) {
            return name(raw, textureSprite);
        }
        if (trimmed.endsWith(".png")) {
            NamespaceId id = ResourcePath.resolveOptifine(trimmed, defaultNs, parent);
            return name(raw, id);
        }
        if (trimmed.contains(".") || trimmed.contains("/") || trimmed.contains(":")) {
            NamespaceId id = ResourcePath.resolveOptifine(trimmed, defaultNs, parent);
            return name(raw, id);
        }
        try {
            int v = Integer.parseInt(trimmed);
            return numeric(v);
        } catch (NumberFormatException ignored) {
            NamespaceId id = ResourcePath.resolveOptifine(trimmed, defaultNs, parent);
            return name(raw, id);
        }
    }

    private static NamespaceId texturePathSprite(String raw, String defaultNs) {
        String path = raw;
        String namespace = defaultNs;
        int colon = raw.indexOf(':');
        if (colon > 0) {
            namespace = raw.substring(0, colon);
            path = raw.substring(colon + 1);
        }
        if (!path.startsWith("textures/")) {
            return null;
        }
        path = path.substring("textures/".length());
        if (path.endsWith(".png")) {
            path = path.substring(0, path.length() - ".png".length());
        }
        if (path.startsWith("blocks/")) {
            path = "block/" + path.substring("blocks/".length());
        }
        if (path.startsWith("items/")) {
            path = "item/" + path.substring("items/".length());
        }
        return new NamespaceId(namespace, path);
    }

    public String rawSpec() {
        return rawSpec;
    }

    public NamespaceId resolvedSprite() {
        return resolvedSprite;
    }

    public int numericIndex() {
        return numericIndex;
    }

    public boolean isSkip() {
        return isSkip;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public boolean isNumeric() {
        return numericIndex >= 0;
    }

    @Override
    public String toString() {
        return "CtmTileSpec[" + rawSpec + "]";
    }
}
