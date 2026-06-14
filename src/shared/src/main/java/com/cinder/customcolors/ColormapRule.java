package com.cinder.customcolors;

import com.cinder.ctm.BlockSpec;
import com.cinder.resource.NamespaceId;

import java.util.Arrays;
import java.util.Objects;

/**
 * Loader-agnostic custom-colormap rule.
 *
 * <p>Block matching is represented as parser-level {@link BlockSpec}s. Fabric
 * resolves these into real blocks/states when it builds its runtime snapshot.
 *
 * <p>Threading: immutable. Performance: rule arrays are built at reload time;
 * renderer adapters should pre-index rules by block for O(1) lookup.
 */
public final class ColormapRule {

    private final String sourceFile;
    private final NamespaceId source;
    private final ColormapFormat format;
    private final BlockSpec[] blocks;
    private final int fixedColor;
    private final boolean hasFixedColor;
    private final int yVariance;
    private final int yOffset;
    private final int itemColor;
    private final boolean hasItemColor;

    public ColormapRule(String sourceFile,
                        NamespaceId source,
                        ColormapFormat format,
                        BlockSpec[] blocks,
                        Integer fixedColor,
                        int yVariance,
                        int yOffset,
                        Integer itemColor) {
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
        this.source = Objects.requireNonNull(source, "source");
        this.format = Objects.requireNonNull(format, "format");
        this.blocks = blocks == null ? new BlockSpec[0]
                : Arrays.copyOf(blocks, blocks.length);
        this.fixedColor = fixedColor == null ? 0 : fixedColor;
        this.hasFixedColor = fixedColor != null;
        this.yVariance = Math.max(0, yVariance);
        this.yOffset = yOffset;
        this.itemColor = itemColor == null ? 0 : itemColor;
        this.hasItemColor = itemColor != null;
    }

    public String sourceFile() {
        return sourceFile;
    }

    public NamespaceId source() {
        return source;
    }

    public ColormapFormat format() {
        return format;
    }

    public BlockSpec[] blocks() {
        return Arrays.copyOf(blocks, blocks.length);
    }

    public boolean hasBlocks() {
        return blocks.length > 0;
    }

    public boolean hasFixedColor() {
        return hasFixedColor;
    }

    public int fixedColor() {
        return fixedColor;
    }

    public int yVariance() {
        return yVariance;
    }

    public int yOffset() {
        return yOffset;
    }

    public boolean hasItemColor() {
        return hasItemColor;
    }

    public int itemColor() {
        return itemColor;
    }

    public int sample(ColormapImage image,
                      double temperature,
                      double rainfall,
                      int biomeColumn,
                      int y,
                      int x,
                      int z) {
        if (format == ColormapFormat.FIXED || hasFixedColor) {
            return fixedColor;
        }
        if (format == ColormapFormat.GRID) {
            return image.sampleGrid(biomeColumn, y, yOffset, yVariance, x, z);
        }
        return image.sampleVanilla(temperature, rainfall);
    }
}
