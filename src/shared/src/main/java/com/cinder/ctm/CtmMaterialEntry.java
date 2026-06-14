package com.cinder.ctm;

import com.cinder.resource.NamespaceId;

import java.util.Objects;

/**
 * Immutable renderer-facing material descriptor for one resolved CTM tile.
 *
 * <p>The entry does not describe how the backend draws the tile. It names the
 * stable CTM material id and the resource inputs a backend can upload into a
 * texture array, atlas indirection table, shader lookup table, or other GPU
 * resource.
 *
 * <h2>Threading</h2>
 *
 * <p>Instances are immutable and are published through an immutable
 * {@link CtmMaterialTable} snapshot.
 *
 * <h2>Performance</h2>
 *
 * <p>Performance: HOT PATH lookup result. Allocation policy: allocated once per
 * resource reload, never during section build.
 */
public record CtmMaterialEntry(
        int materialId,
        CtmRule rule,
        CtmMethod method,
        int tileIndex,
        NamespaceId sprite,
        String resourcePath,
        NamespaceId fallbackSourceSprite,
        int flags) {

    /** The tile references an explicit PNG resource. */
    public static final int FLAG_EXPLICIT_RESOURCE = 1;

    /** The tile must be generated from a base sprite. */
    public static final int FLAG_GENERATED_FALLBACK = 1 << 1;

    /** The tile references an already named sprite. */
    public static final int FLAG_NAMED_SPRITE = 1 << 2;

    /**
     * Canonical constructor: validates stable material invariants.
     */
    public CtmMaterialEntry {
        if (materialId <= 0) {
            throw new IllegalArgumentException(
                    "materialId 0 is reserved for pass-through");
        }
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(method, "method");
        if (tileIndex < 0) {
            throw new IllegalArgumentException("tileIndex must be >= 0");
        }
        Objects.requireNonNull(sprite, "sprite");
    }

    /**
     * Builds a material entry from a tile resolver output.
     */
    public static CtmMaterialEntry from(int materialId,
                                        CtmRule rule,
                                        CtmTileResolver.Resolution resolution) {
        Objects.requireNonNull(resolution, "resolution");
        NamespaceId sprite = resolution.resolvedSprite();
        if (sprite == null) {
            throw new IllegalArgumentException(
                    "special tile resolutions do not produce materials");
        }
        int flags = 0;
        if (resolution.resourcePath() != null) {
            flags |= FLAG_EXPLICIT_RESOURCE;
        }
        if (resolution.fallbackSourceSprite() != null) {
            flags |= FLAG_GENERATED_FALLBACK;
        }
        if (!resolution.needsInjection()) {
            flags |= FLAG_NAMED_SPRITE;
        }
        return new CtmMaterialEntry(
                materialId,
                rule,
                rule.method(),
                resolution.tileIndex(),
                sprite,
                resolution.resourcePath(),
                resolution.fallbackSourceSprite(),
                flags);
    }

    /**
     * Returns true when this material must be injected or uploaded from an
     * explicit resource.
     */
    public boolean hasExplicitResource() {
        return (flags & FLAG_EXPLICIT_RESOURCE) != 0;
    }

    /**
     * Returns true when this material is generated from a source sprite.
     */
    public boolean isGeneratedFallback() {
        return (flags & FLAG_GENERATED_FALLBACK) != 0;
    }

    /**
     * Returns true when this material refers to a named sprite that should
     * already exist in renderer resources.
     */
    public boolean isNamedSprite() {
        return (flags & FLAG_NAMED_SPRITE) != 0;
    }
}
