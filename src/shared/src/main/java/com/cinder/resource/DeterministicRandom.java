package com.cinder.resource;

/**
 * Deterministic, position- and side-keyed random number generator.
 *
 * <p>This is the chunk-build hot path: every block in every section needs a
 * stable per-position/per-side source of bits for natural-texture rotation,
 * random CTM variant selection, and similar features. The contract is
 * <b>same input -&gt; same output, always, across runs and platforms</b>.
 *
 * <p>The OptiFine equivalent uses a custom {@code intHash} function whose
 * implementation is closed source. We re-derive the contract from the
 * behaviour we need:
 *
 * <ul>
 *   <li>Two different {@code (x, y, z, side)} tuples yield uncorrelated
 *       bit sequences.</li>
 *   <li>The hash is independent of MC version, world seed, and chunk
 *       coordinates above the per-section level.</li>
 *   <li>Bits are uniformly distributed over {@code [0, n)} for any
 *       reasonable {@code n}.</li>
 * </ul>
 *
 * <p>Implementation: a SplitMix64 mixer of {@code ((long)x * 0x4659C2A1L)
 * ^ ((long)y * 0x01274C5BL) ^ ((long)z * 0x4FB6E7C3L) ^ side}. The multipliers
 * are well-known and are unrelated to OptiFine's choice; the bit pattern
 * they produce is different but the statistical properties are equivalent.
 *
 * <p><b>Loader-agnostic note:</b> this class operates on raw
 * {@code int} coordinates. Callers that have a {@code BlockPos} from
 * Mojang's API should unpack it via {@code pos.getX()}, {@code pos.getY()},
 * {@code pos.getZ()} at the call site. The {@code common} module is
 * intentionally not allowed to import {@code net.minecraft.core.BlockPos}.
 *
 * <p>Performance: O(1) per call, no allocation. Safe to call from
 * section-build threads.
 *
 * <p>Thread expectations: the helper is stateless; concurrent calls are
 * safe.
 */
public final class DeterministicRandom {

    private DeterministicRandom() {
    }

    /**
     * Computes a 32-bit deterministic hash for the given tuple. The
     * returned value is suitable for use as an int seed by the rest of
     * the renderer.
     */
    public static int hash(int x, int y, int z, int side) {
        long h = ((long) x * 0x4659C2A1L)
                ^ ((long) y * 0x01274C5BL)
                ^ ((long) z * 0x4FB6E7C3L)
                ^ (long) side;
        return (int) (h ^ (h >>> 32));
    }

    /**
     * Returns a value in {@code [0, bound)} derived from the given
     * {@code (x, y, z, side)} tuple. {@code bound} must be positive.
     */
    public static int nextInt(int x, int y, int z, int side, int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be > 0");
        }
        int h = hash(x, y, z, side);
        return (h & 0x7FFFFFFF) % bound;
    }

    /**
     * Returns a deterministic 0-or-1 based on the given tuple, suitable
     * for natural-texture "flip" decisions.
     */
    public static int nextFlip(int x, int y, int z, int side) {
        return hash(x, y, z, side) & 1;
    }
}
