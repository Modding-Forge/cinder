package com.cinder.ctm;

/**
 * Stable face-ordinal constants used across the CTM engine.
 *
 * <p>The numbers are the order documented in the OptiFine
 * documentation and used by Minecraft's
 * {@code net.minecraft.core.Direction}:
 *
 * <ul>
 *   <li>{@code 0} = DOWN</li>
 *   <li>{@code 1} = UP</li>
 *   <li>{@code 2} = NORTH</li>
 *   <li>{@code 3} = SOUTH</li>
 *   <li>{@code 4} = WEST</li>
 *   <li>{@code 5} = EAST</li>
 * </ul>
 *
 * <p>These ordinals are part of the public surface of the engine: a
 * resource pack or test that names "the WEST face" expects the value
 * {@code 4} regardless of loader. The numbers were chosen to be
 * identical to Minecraft's {@code Direction} so that adapters do not
 * have to translate.
 */
public final class Faces {
    public static final int DOWN  = 0;
    public static final int UP    = 1;
    public static final int NORTH = 2;
    public static final int SOUTH = 3;
    public static final int WEST  = 4;
    public static final int EAST  = 5;

    private Faces() {}

    /**
     * Returns the unit vector for the given face, encoded as
     * {@code (dx, dy, dz)} offsets suitable for a {@link NeighborView}.
     */
    public static int[] delta(int face) {
        return switch (face) {
            case DOWN  -> new int[] { 0, -1, 0 };
            case UP    -> new int[] { 0,  1, 0 };
            case NORTH -> new int[] { 0, 0, -1 };
            case SOUTH -> new int[] { 0, 0,  1 };
            case WEST  -> new int[] { -1, 0, 0 };
            case EAST  -> new int[] {  1, 0, 0 };
            default -> throw new IllegalArgumentException("bad face: " + face);
        };
    }

    /**
     * Returns the four orthogonal side faces for the given primary
     * face in Cinder's canonical CTM bit order:
     *
     * <ul>
     *   <li>bit 0 = local left</li>
     *   <li>bit 1 = local right</li>
     *   <li>bit 2 = local top</li>
     *   <li>bit 3 = local bottom</li>
     * </ul>
     *
     * <p>This order is deliberately face-local rather than world-global.
     * The CTM 47-template and generated fallback sprites are indexed in
     * this local order, so every face must normalize world neighbours into
     * the same bit meaning before reaching {@link TileIndexTable}.
     */
    public static int[] orthogonalSides(int face) {
        return switch (face) {
            case DOWN, UP -> new int[] { WEST, EAST, NORTH, SOUTH };
            case NORTH, SOUTH -> new int[] { WEST, EAST, UP, DOWN };
            case WEST, EAST -> new int[] { NORTH, SOUTH, UP, DOWN };
            default -> throw new IllegalArgumentException("bad face: " + face);
        };
    }

    /**
     * Returns the four diagonal neighbours for the given primary
     * face. The order matches the canonical side bit order returned by
     * {@link #orthogonalSides(int)}:
     *
     * <ul>
     *   <li>bit 0 = local left + local top</li>
     *   <li>bit 1 = local left + local bottom</li>
     *   <li>bit 2 = local right + local top</li>
     *   <li>bit 3 = local right + local bottom</li>
     * </ul>
     *
     * <p>The CTM selector and generated fallback tile masks rely on this
     * adjacency: diagonal bit 0 belongs to side bits 0 and 2, bit 1 to
     * side bits 0 and 3, bit 2 to side bits 1 and 2, and bit 3 to side
     * bits 1 and 3.
     */
    public static int[][] diagonals(int face) {
        return switch (face) {
            case DOWN, UP -> new int[][] {
                    { -1, 0, -1 }, { -1, 0,  1 },
                    {  1, 0, -1 }, {  1, 0,  1 }
            };
            case NORTH, SOUTH -> new int[][] {
                    { -1,  1, 0 }, { -1, -1, 0 },
                    {  1,  1, 0 }, {  1, -1, 0 }
            };
            case WEST, EAST -> new int[][] {
                    { 0,  1, -1 }, { 0, -1, -1 },
                    { 0,  1,  1 }, { 0, -1,  1 }
            };
            default -> throw new IllegalArgumentException("bad face: " + face);
        };
    }
}
