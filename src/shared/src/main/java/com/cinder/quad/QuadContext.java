package com.cinder.quad;

import com.cinder.ctm.NeighborView;
import com.cinder.resource.NamespaceId;

import java.util.Objects;

/**
 * Loader-agnostic context attached to every quad that passes
 * through the {@link QuadDecorator} pipeline.
 *
 * <p>Decoupling rationale: the {@code shared/} module is
 * forbidden from importing any Minecraft, Fabric, or
 * NeoForge type. A decorator therefore never sees a
 * {@code BakedQuad} directly. Instead, the loader-side adapter
 * builds a {@code QuadContext} from the live renderer state
 * and a {@link QuadRef} that the adapter wraps around the
 * raw quad; the decorator consults the context, decides what
 * to do, and returns either the original {@code QuadRef} or
 * a replacement.
 *
 * <h2>Performance</h2>
 *
 * <p>Instances are created per quad on the section-build
 * thread; they must therefore be cheap to allocate. The
 * record components are all primitive or {@link NamespaceId}
 * (a record itself), so a single allocation per quad is the
 * worst case. The decorator pipeline consults the context
 * only, never copies it.
 */
public record QuadContext(
        int x,
        int y,
        int z,
        int face,
        String blockId,
        NamespaceId sprite,
        NeighborView neighborView) {

    public QuadContext {
        Objects.requireNonNull(blockId, "blockId");
        Objects.requireNonNull(sprite, "sprite");
        // neighborView may be null for tests or renderers that cannot
        // expose live neighbour state.
        // x, y, z, face are primitive; no validation beyond
        // letting the caller pass any int. Decorators that
        // require a valid face ordinal should validate
        // themselves.
    }

    public QuadContext(int x, int y, int z, int face,
                       String blockId, NamespaceId sprite) {
        this(x, y, z, face, blockId, sprite, null);
    }

    /**
     * Returns the face ordinal as a {@code String} for
     * log messages. Convenience helper; not used in the
     * hot path.
     */
    public String faceName() {
        return switch (face) {
            case 0 -> "down";
            case 1 -> "up";
            case 2 -> "north";
            case 3 -> "south";
            case 4 -> "west";
            case 5 -> "east";
            default -> "face" + face;
        };
    }
}
