package com.cinder.ctm;

/**
 * How a CTM rule considers two adjacent blocks to be "the same".
 *
 * <p>This is the OptiFine {@code connect=block|tile|state} property,
 * documented in {@code optifine/OptiFineDoc/doc/ctm.properties}.
 *
 * <p>Behaviour:
 * <ul>
 *   <li>{@link #BLOCK}: only the block's identifier (e.g. {@code minecraft:oak_log})
 *       is compared; properties, fluid, and shape are ignored.</li>
 *   <li>{@link #TILE}: the rendered sprite of the neighbour must be
 *       the same object as the rendered sprite of the current face.
 *       Air and null are never a match.</li>
 *   <li>{@link #STATE}: full {@code BlockState} equality. The most
 *       strict mode.</li>
 * </ul>
 */
public enum ConnectMode {
    BLOCK,
    TILE,
    STATE
}
