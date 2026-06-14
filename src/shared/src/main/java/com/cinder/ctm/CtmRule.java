package com.cinder.ctm;

import com.cinder.resource.ComponentMatchers;
import com.cinder.resource.NamespaceId;
import com.cinder.resource.RangeListInt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * One parsed CTM rule. Immutable. The renderer-side engine (Phase 2/3)
 * is responsible for taking a rule and a position+face and producing
 * a tile index; this class only carries the data.
 *
 * <p>Field semantics follow {@code optifine/OptiFineDoc/doc/ctm.properties}:
 * <ul>
 *   <li>{@code matchTiles}: list of sprite IDs; this rule applies to
 *       faces whose rendered sprite is in the list. The renderer
 *       resolves "rendered sprite" per face.</li>
 *   <li>{@code matchBlocks}: list of {@link BlockSpec}; the rule applies
 *       to faces of blocks matching any of the specs.</li>
 *   <li>{@code connect}: how "matching" is determined for the
 *       neighbour check.</li>
 *   <li>{@code faces}: which faces of the block this rule applies to
 *       (top/bottom/north/south/east/west/sides/all).</li>
 *   <li>{@code biomes}: optional biome matcher; if non-empty, the
 *       rule applies only when the block's biome matches one of the
 *       entries.</li>
 *   <li>{@code heights}: optional Y range filter; if non-empty, the
 *       rule applies only when the block's Y is in the range.</li>
 *   <li>{@code tiles}: ordered list of {@link CtmTileSpec}; the
 *       engine indexes into this list.</li>
 *   <li>{@code weight}: higher weight wins on a tie. Resource packs
 *       conventionally use 0 as a default and raise the value to
 *       override other rules.</li>
 *   <li>{@code width}/{@code height}: required for {@link CtmMethod#REPEAT}.</li>
 *   <li>{@code weights}: per-tile weights for {@link CtmMethod#RANDOM}.</li>
 *   <li>{@code innerSeams}: enables the inner-edge check on
 *       {@code ctm}/{@code ctm_compact}.</li>
 *   <li>{@code ctmOverrides}: {@code ctm.<n>=<tileIndex>} entries for
 *       {@link CtmMethod#CTM_COMPACT}.</li>
 *   <li>{@code tintIndex}/{@code tintBlock}: optional overlay tint metadata.
 *       Renderers may use this to apply Vanilla block tint sources to overlay
 *       layers without changing the base block's vertex color.</li>
 * </ul>
 *
 * <p>Mutability: immutable.
 *
 * <p>Performance: a single rule is small (a few hundred bytes at most)
 * and the data is read on every section rebuild. Use this class
 * directly; do not wrap it in caches unless profiling demands it.
 */
public final class CtmRule {

    private final CtmMethod method;
    private final List<NamespaceId> matchTiles;
    private final List<BlockSpec> matchBlocks;
    private final ConnectMode connect;
    private final List<NamespaceId> connectTiles;
    private final List<BlockSpec> connectBlocks;
    private final int facesMask;          // bit 0..5: D/U/N/S/W/E, or 0 for "all"
    private final List<String> biomes;
    private final RangeListInt heights;
    private final List<CtmTileSpec> tiles;
    private final int weight;
    private final int width;              // 0 if not REPEAT
    private final int height;             // 0 if not REPEAT
    private final List<Integer> randomWeights;  // null if not RANDOM
    private final boolean innerSeams;
    private final int[] ctmOverrides;     // length 47, -1 = no override
    private final int tintIndex;
    private final BlockSpec tintBlock;
    private final List<ComponentMatchers.Compiled> nbtMatchers;
    private final String sourceFile;
    private final int sourceLine;

    private CtmRule(Builder b) {
        this.method = b.method;
        this.matchTiles = List.copyOf(b.matchTiles);
        this.matchBlocks = List.copyOf(b.matchBlocks);
        this.connect = b.connect;
        this.connectTiles = List.copyOf(b.connectTiles);
        this.connectBlocks = List.copyOf(b.connectBlocks);
        this.facesMask = b.facesMask;
        this.biomes = List.copyOf(b.biomes);
        this.heights = b.heights;
        this.tiles = List.copyOf(b.tiles);
        this.weight = b.weight;
        this.width = b.width;
        this.height = b.height;
        this.randomWeights = b.randomWeights;
        this.innerSeams = b.innerSeams;
        this.ctmOverrides = b.ctmOverrides;
        this.tintIndex = b.tintIndex;
        this.tintBlock = b.tintBlock;
        this.nbtMatchers = List.copyOf(b.nbtMatchers);
        this.sourceFile = b.sourceFile;
        this.sourceLine = b.sourceLine;
    }

    public CtmMethod method() {
        return method;
    }

    public List<NamespaceId> matchTiles() {
        return matchTiles;
    }

    public List<BlockSpec> matchBlocks() {
        return matchBlocks;
    }

    public ConnectMode connect() {
        return connect;
    }

    public List<NamespaceId> connectTiles() {
        return connectTiles;
    }

    public List<BlockSpec> connectBlocks() {
        return connectBlocks;
    }

    public int facesMask() {
        return facesMask;
    }

    public List<String> biomes() {
        return biomes;
    }

    public RangeListInt heights() {
        return heights;
    }

    public List<CtmTileSpec> tiles() {
        return tiles;
    }

    public int weight() {
        return weight;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public List<Integer> randomWeights() {
        return randomWeights;
    }

    public boolean innerSeams() {
        return innerSeams;
    }

    public int[] ctmOverrides() {
        return ctmOverrides;
    }

    public int tintIndex() {
        return tintIndex;
    }

    public Optional<BlockSpec> tintBlock() {
        return Optional.ofNullable(tintBlock);
    }

    public List<ComponentMatchers.Compiled> nbtMatchers() {
        return nbtMatchers;
    }

    public Optional<String> sourceFile() {
        return Optional.ofNullable(sourceFile);
    }

    public int sourceLine() {
        return sourceLine;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link CtmRule}. Not thread-safe.
     */
    public static final class Builder {
        private CtmMethod method = CtmMethod.CTM;
        private final List<NamespaceId> matchTiles = new ArrayList<>();
        private final List<BlockSpec> matchBlocks = new ArrayList<>();
        private ConnectMode connect = ConnectMode.BLOCK;
        private final List<NamespaceId> connectTiles = new ArrayList<>();
        private final List<BlockSpec> connectBlocks = new ArrayList<>();
        private int facesMask; // 0 means "all faces"
        private final List<String> biomes = new ArrayList<>();
        private RangeListInt heights = RangeListInt.ALL;
        private final List<CtmTileSpec> tiles = new ArrayList<>();
        private int weight;
        private int width;
        private int height;
        private List<Integer> randomWeights;
        private boolean innerSeams;
        private int[] ctmOverrides;
        private int tintIndex = -1;
        private BlockSpec tintBlock;
        private final List<ComponentMatchers.Compiled> nbtMatchers = new ArrayList<>();
        private String sourceFile;
        private int sourceLine;

        public Builder method(CtmMethod m) { this.method = m; return this; }
        public Builder addMatchTile(NamespaceId id) { this.matchTiles.add(id); return this; }
        public Builder addMatchBlock(BlockSpec spec) { this.matchBlocks.add(spec); return this; }
        public Builder connect(ConnectMode m) { this.connect = m; return this; }
        public Builder addConnectTile(NamespaceId id) { this.connectTiles.add(id); return this; }
        public Builder addConnectBlock(BlockSpec spec) { this.connectBlocks.add(spec); return this; }
        public java.util.List<NamespaceId> matchTiles() { return this.matchTiles; }
        public java.util.List<BlockSpec> matchBlocks() { return this.matchBlocks; }
        public Builder facesMask(int mask) { this.facesMask = mask; return this; }
        public Builder addBiome(String b) { this.biomes.add(b); return this; }
        public Builder heights(RangeListInt r) { this.heights = r; return this; }
        public Builder addTile(CtmTileSpec t) { this.tiles.add(t); return this; }
        public Builder weight(int w) { this.weight = w; return this; }
        public Builder width(int w) { this.width = w; return this; }
        public Builder height(int h) { this.height = h; return this; }
        public Builder randomWeights(List<Integer> ws) { this.randomWeights = ws; return this; }
        public Builder innerSeams(boolean b) { this.innerSeams = b; return this; }
        public Builder ctmOverrides(int[] overrides) { this.ctmOverrides = overrides; return this; }
        public Builder tintIndex(int index) { this.tintIndex = index; return this; }
        public Builder tintBlock(BlockSpec spec) { this.tintBlock = spec; return this; }
        public Builder addNbtMatcher(ComponentMatchers.Compiled c) { this.nbtMatchers.add(c); return this; }
        public Builder sourceFile(String f) { this.sourceFile = f; return this; }
        public Builder sourceLine(int n) { this.sourceLine = n; return this; }

        public CtmRule build() {
            if (method == null) {
                throw new IllegalStateException("method not set");
            }
            if (tiles.isEmpty()) {
                throw new IllegalStateException("tiles not set");
            }
            if (method == CtmMethod.REPEAT && (width <= 0 || height <= 0)) {
                throw new IllegalStateException(
                        "repeat method requires positive width and height");
            }
            return new CtmRule(this);
        }
    }
}
