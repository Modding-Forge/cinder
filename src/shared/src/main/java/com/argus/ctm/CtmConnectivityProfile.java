package com.argus.ctm;

/**
 * Connectivity cost class required by a CTM rule.
 *
 * <p>Purpose: lets renderer-facing resolvers group rules by the neighbour
 * facts they need before running method-specific selection. This is metadata
 * only; it does not change tile selection semantics.
 *
 * <p>Threading: immutable enum, safe to share between rule snapshots.
 *
 * <p>Performance: HOT PATH metadata. Use ordinal/switch checks in renderer
 * code; avoid allocating helper objects around this enum.
 */
public enum CtmConnectivityProfile {
    /** No neighbour connectivity is needed. */
    NONE(false, false),
    /** One block above the current block is needed. */
    TOP_1(true, false),
    /** Two horizontal neighbours in face-local coordinates are needed. */
    HORIZONTAL_2(true, false),
    /** Two vertical neighbours in face-local coordinates are needed. */
    VERTICAL_2(true, false),
    /** Four side neighbours are needed. */
    CARDINAL_4(true, false),
    /** Four side neighbours plus potentially diagonals are needed. */
    FULL_8(true, true);

    private final boolean requiresNeighbours;
    private final boolean mayRequireDiagonals;

    CtmConnectivityProfile(boolean requiresNeighbours,
                           boolean mayRequireDiagonals) {
        this.requiresNeighbours = requiresNeighbours;
        this.mayRequireDiagonals = mayRequireDiagonals;
    }

    /**
     * Returns true when the selector needs any neighbour block/sprite facts.
     */
    public boolean requiresNeighbours() {
        return requiresNeighbours;
    }

    /**
     * Returns true when this profile may need diagonal neighbour checks.
     */
    public boolean mayRequireDiagonals() {
        return mayRequireDiagonals;
    }
}
