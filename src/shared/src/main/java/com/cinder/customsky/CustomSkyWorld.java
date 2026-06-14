package com.cinder.customsky;

/**
 * Numeric OptiFine custom-sky world folder identifier.
 *
 * <p>Threading: immutable value object. Performance: parser/runtime bridge
 * uses the primitive {@link #id()} in hot lookups.
 */
public record CustomSkyWorld(int id) {
    public static final CustomSkyWorld OVERWORLD = new CustomSkyWorld(0);
    public static final CustomSkyWorld NETHER = new CustomSkyWorld(-1);
    public static final CustomSkyWorld END = new CustomSkyWorld(1);

    /**
     * Parses folder names like {@code world0}, {@code world-1}, and
     * {@code world1}.
     */
    public static CustomSkyWorld parseFolder(String folder) {
        if (folder == null || !folder.startsWith("world")) {
            throw new IllegalArgumentException("invalid sky world folder: "
                    + folder);
        }
        try {
            return new CustomSkyWorld(Integer.parseInt(folder.substring(5)));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid sky world folder: "
                    + folder, e);
        }
    }
}
