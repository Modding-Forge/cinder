package com.argus.client.render;

import com.argus.ctm.NeighborCache;
import com.argus.ctm.NeighborView;
import com.argus.resource.NamespaceId;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Minecraft-client adapter that fills a {@link NeighborCache} from a render
 * world view.
 *
 * <p>The class intentionally has no Fabric imports. It can be reused by Sodium
 * integrations on any loader that exposes the same Minecraft client rendering
 * types. Loader-specific source sets may decide how to construct and wire it.
 *
 * <h2>Threading</h2>
 *
 * <p>One instance is owned by one renderer/section-build worker context and is
 * recentered for each block. It is not thread-safe.
 *
 * <h2>Performance</h2>
 *
 * <p>Performance: HOT PATH. Allocation policy: no per-neighbour allocation
 * after construction. Neighbour block states are loaded lazily by offset.
 */
public final class CtmMinecraftNeighborView implements NeighborView {

    private static final int CUBE_SIZE = 27;
    private static final int FACE_COUNT = 6;

    private final BlockAndTintGetter source;
    private final NeighborCache cache = new NeighborCache();
    private final NamespaceId @Nullable [] spriteTable =
            new NamespaceId[CUBE_SIZE * FACE_COUNT];
    private final boolean[] loaded = new boolean[CUBE_SIZE];
    private final BlockPos.MutableBlockPos scratchPos =
            new BlockPos.MutableBlockPos();
    private int centerX;
    private int centerY;
    private int centerZ;
    private boolean centered;

    private static final ConcurrentMap<Block, String> BLOCK_IDS =
            new ConcurrentHashMap<>();
    private static final ConcurrentMap<Identifier, NamespaceId> SPRITE_IDS =
            new ConcurrentHashMap<>();

    public CtmMinecraftNeighborView(BlockAndTintGetter source) {
        this.source = source;
    }

    public void reset(BlockPos centerPos) {
        beginReset(centerPos);
        BlockState center = source.getBlockState(centerPos);
        setCenterState(center);
    }

    public void reset(BlockPos centerPos, BlockState centerState) {
        beginReset(centerPos);
        setCenterState(centerState);
    }

    private void beginReset(BlockPos centerPos) {
        this.centerX = centerPos.getX();
        this.centerY = centerPos.getY();
        this.centerZ = centerPos.getZ();
        this.centered = true;
        cache.reset();
        Arrays.fill(loaded, false);
        Arrays.fill(spriteTable, null);
    }

    private void setCenterState(BlockState centerState) {
        cache.set(0, 0, 0, blockIdOf(centerState), centerState.isSolidRender());
        loaded[cellIndexOf(0, 0, 0)] = true;
    }

    public void fillNeighbours() {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        setNeighbourBlock(dx, dy, dz);
                    }
                }
            }
        }
    }

    public void setSpriteForFace(int dx, int dy, int dz,
                                 int face,
                                 TextureAtlasSprite sprite) {
        setSpriteIdForFace(dx, dy, dz, face, namespaceIdOf(sprite));
    }

    public void setSpriteIdForFace(int dx, int dy, int dz,
                                   int face,
                                   NamespaceId sprite) {
        spriteTable[spriteIndexOf(dx, dy, dz, face)] = sprite;
    }

    private void setNeighbourBlock(int dx, int dy, int dz) {
        if (!centered) {
            throw new IllegalStateException("reset() must be called first");
        }
        scratchPos.set(centerX + dx, centerY + dy, centerZ + dz);
        BlockState state = source.getBlockState(scratchPos);
        cache.set(dx, dy, dz, blockIdOf(state), state.isSolidRender());
        loaded[cellIndexOf(dx, dy, dz)] = true;
    }

    @Override
    public @Nullable NamespaceId sprite(int dx, int dy, int dz, int face) {
        ensureLoaded(dx, dy, dz);
        int index = spriteIndexOf(dx, dy, dz, face);
        NamespaceId sprite = spriteTable[index];
        if (sprite == null) {
            sprite = fallbackSprite(dx, dy, dz, face);
            if (sprite != null) {
                spriteTable[index] = sprite;
            }
        }
        return sprite;
    }

    @Override
    public String blockId(int dx, int dy, int dz) {
        ensureLoaded(dx, dy, dz);
        return cache.blockId(dx, dy, dz);
    }

    @Override
    public boolean isFullBlock(int dx, int dy, int dz) {
        ensureLoaded(dx, dy, dz);
        return cache.isFullBlock(dx, dy, dz);
    }

    private static int spriteIndexOf(int dx, int dy, int dz, int face) {
        return cellIndexOf(dx, dy, dz) * FACE_COUNT + face;
    }

    private static int cellIndexOf(int dx, int dy, int dz) {
        return (dx + 1) * 9 + (dy + 1) * 3 + (dz + 1);
    }

    private void ensureLoaded(int dx, int dy, int dz) {
        int index = cellIndexOf(dx, dy, dz);
        if (!loaded[index]) {
            setNeighbourBlock(dx, dy, dz);
        }
    }

    private static String blockIdOf(BlockState state) {
        Block block = state.getBlock();
        String cached = BLOCK_IDS.get(block);
        if (cached != null) {
            return cached;
        }
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        if (id == null) {
            return null;
        }
        String created = id.toString();
        String previous = BLOCK_IDS.putIfAbsent(block, created);
        return previous == null ? created : previous;
    }

    private static NamespaceId namespaceIdOf(TextureAtlasSprite sprite) {
        Identifier id = sprite.contents().name();
        NamespaceId cached = SPRITE_IDS.get(id);
        if (cached != null) {
            return cached;
        }
        NamespaceId created = new NamespaceId(id.getNamespace(), id.getPath());
        NamespaceId previous = SPRITE_IDS.putIfAbsent(id, created);
        return previous == null ? created : previous;
    }

    private @Nullable NamespaceId fallbackSprite(int dx, int dy, int dz,
                                                 int face) {
        String blockId = cache.blockId(dx, dy, dz);
        if (blockId == null || blockId.isEmpty()
                || "minecraft:air".equals(blockId)) {
            return null;
        }
        int colon = blockId.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : blockId.substring(0, colon);
        String name = colon < 0 ? blockId : blockId.substring(colon + 1);
        String path = switch (name) {
            case "grass_block" -> switch (face) {
                case 0 -> "block/dirt";
                case 1 -> "block/grass_block_top";
                default -> "block/grass_block_side";
            };
            case "snow_block", "snow" -> "block/snow";
            default -> "block/" + name;
        };
        return new NamespaceId(namespace, path);
    }
}
