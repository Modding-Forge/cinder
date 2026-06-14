package com.cinder.fabric.ctm;

import com.cinder.ctm.NeighborCache;
import com.cinder.ctm.NeighborView;
import com.cinder.resource.NamespaceId;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Fabric-side adapter that fills a {@link NeighborCache} from a
 * Minecraft {@link BlockAndTintGetter} and a {@link TextureAtlas}.
 *
 * <p>This is the only class in the {@code fabric} module's
 * {@code client} source set that knows about Minecraft's renderer.
 * It bridges the loader-agnostic {@link NeighborView} contract to
 * the Mojang-mapped types.
 *
 * <h2>Coordinate model</h2>
 *
 * <p>The 3x3x3 cube is centred on the block being rendered. Cells are
 * addressed by their (dx, dy, dz) offset within {@code {-1, 0, 1}^3}.
 * The {@link NeighborCache} stores them as a flat 27-element array.
 *
 * <h2>Per-face sprites</h2>
 *
 * <p>The {@link com.cinder.ctm.NeighborView#sprite(int, int, int, int)}
 * method takes a face ordinal (DOWN, UP, N, S, W, E). Different faces
 * of the same block can have different rendered sprites (logs, grass,
 * stairs), and the CTM engine cares which sprite is actually
 * rendered. The renderer populates the per-face sprites lazily via
 * {@link #setSpriteForFace} as it iterates the bakes.
 */
public final class FabricNeighborView implements NeighborView {

    private static final int CUBE_SIZE = 27;
    private static final int FACE_COUNT = 6;

    private final BlockAndTintGetter source;
    private final NeighborCache cache = new NeighborCache();
    /** Cached Mojang sprites per (cell, face). */
    private final TextureAtlasSprite @Nullable [] spriteTable =
            new TextureAtlasSprite[CUBE_SIZE * FACE_COUNT];
    @Nullable
    private BlockPos centerPos;

    public FabricNeighborView(BlockAndTintGetter source) {
        this.source = source;
    }

    /**
     * Recentre the cache on a new block. Must be called before any
     * per-cell setSprite calls.
     */
    public void reset(BlockPos centerPos) {
        this.centerPos = centerPos.immutable();
        this.cache.reset();
        for (int i = 0; i < spriteTable.length; i++) {
            spriteTable[i] = null;
        }
        BlockState center = this.source.getBlockState(centerPos);
        this.cache.set(0, 0, 0, blockIdOf(center), center.isSolidRender());
    }

    /**
     * Records the rendered sprite of one face of a single cell.
     * Repeated calls for the same cell and face are last-wins
     * (so a per-face hook can overwrite the default).
     */
    public void setSpriteForFace(int dx, int dy, int dz, int face, TextureAtlasSprite sprite) {
        spriteTable[spriteIndexOf(dx, dy, dz, face)] = sprite;
    }

    /**
     * Populates the block-id and full-block flag for one neighbour
     * cell. Skips the centre cell (which is filled in {@link #reset}).
     */
    public void setNeighbourBlock(int dx, int dy, int dz) {
        if (centerPos == null) {
            throw new IllegalStateException("reset() must be called first");
        }
        BlockPos p = this.centerPos.offset(dx, dy, dz);
        BlockState state = this.source.getBlockState(p);
        this.cache.set(dx, dy, dz, blockIdOf(state), state.isSolidRender());
    }

    /**
     * Convenience: populate the 26 neighbour cells (skipping the
     * centre) for the block at the centre position.
     */
    public void fillNeighbours() {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    setNeighbourBlock(dx, dy, dz);
                }
            }
        }
    }

    public NeighborCache cache() {
        return this.cache;
    }

    public BlockPos centerPos() {
        if (centerPos == null) {
            throw new IllegalStateException("reset() must be called first");
        }
        return centerPos;
    }

    // --- NeighborView -------------------------------------------------

    @Override
    public @Nullable NamespaceId sprite(int dx, int dy, int dz, int face) {
        TextureAtlasSprite s = spriteTable[spriteIndexOf(dx, dy, dz, face)];
        if (s == null) {
            return null;
        }
        return namespaceIdOf(s);
    }

    @Override
    public String blockId(int dx, int dy, int dz) {
        return this.cache.blockId(dx, dy, dz);
    }

    @Override
    public boolean isFullBlock(int dx, int dy, int dz) {
        return this.cache.isFullBlock(dx, dy, dz);
    }

    // --- helpers ------------------------------------------------------

    private static int spriteIndexOf(int dx, int dy, int dz, int face) {
        int cell = (dx + 1) * 9 + (dy + 1) * 3 + (dz + 1);
        return cell * FACE_COUNT + face;
    }

    private static String blockIdOf(BlockState state) {
        Identifier id = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(state.getBlock());
        if (id == null) {
            return null;
        }
        return id.toString();
    }

    private static NamespaceId namespaceIdOf(TextureAtlasSprite sprite) {
        Identifier id = sprite.contents().name();
        return new NamespaceId(id.getNamespace(), id.getPath());
    }
}
