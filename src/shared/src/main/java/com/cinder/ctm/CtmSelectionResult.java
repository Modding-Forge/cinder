package com.cinder.ctm;

import com.cinder.resource.NamespaceId;

/**
 * The result of a CTM tile-selection step.
 *
 * <p>The selection engine produces one of:
 * <ul>
 *   <li>A concrete tile index to use as the rendered face's texture.</li>
 *   <li>The sentinel {@link #SKIP_INDEX} for overlay methods where the
 *       corresponding tile in the {@code tiles} list is
 *       {@code <skip>}.</li>
 *   <li>The sentinel {@link #DEFAULT_INDEX} for overlay methods where the
 *       underlying selection engine should be queried as if this
 *       rule had not matched.</li>
 * </ul>
 *
 * <p>Immutability: instances are immutable. Use the static factory
 * methods.
 *
 * <p>Performance: the class is a small record-like immutable, suitable
 * for inlining and stack allocation in the renderer.
 */
public final class CtmSelectionResult {

    /**
     * The tile index in the rule's {@code tiles} list. For methods
     * that do not use {@code <skip>}/{@code <default>}, this is the
     * index into {@link CtmRule#tiles()}. For methods that do, the
     * renderer interprets {@link #SKIP_INDEX} and
     * {@link #DEFAULT_INDEX} specially.
     */
    private final int tileIndex;

    /**
     * Sentinel tile index: the corresponding tile in the rule's
     * {@code tiles} list was {@code <skip>}; no quad is rendered for
     * this face.
     */
    public static final int SKIP_INDEX = -1;

    /**
     * Sentinel tile index: the rule's tile was {@code <default>};
     * the renderer should fall back to the next priority rule or
     * to the vanilla tile.
     */
    public static final int DEFAULT_INDEX = -2;

    private CtmSelectionResult(int tileIndex) {
        this.tileIndex = tileIndex;
    }

    public static CtmSelectionResult ofTile(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("tile index must be >= 0");
        }
        return new CtmSelectionResult(index);
    }

    public static CtmSelectionResult skip() {
        return new CtmSelectionResult(SKIP_INDEX);
    }

    public static CtmSelectionResult useDefault() {
        return new CtmSelectionResult(DEFAULT_INDEX);
    }

    public int tileIndex() {
        return tileIndex;
    }

    public boolean isSkip() {
        return tileIndex == SKIP_INDEX;
    }

    public boolean isDefault() {
        return tileIndex == DEFAULT_INDEX;
    }

    public boolean isConcrete() {
        return tileIndex >= 0;
    }

    @Override
    public String toString() {
        if (isSkip()) {
            return "CtmSelectionResult[SKIP]";
        }
        if (isDefault()) {
            return "CtmSelectionResult[DEFAULT]";
        }
        return "CtmSelectionResult[tile=" + tileIndex + "]";
    }
}
