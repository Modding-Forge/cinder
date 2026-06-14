package com.cinder.client.render;

import com.cinder.ctm.NeighborCache;
import com.cinder.ctm.NeighborView;
import com.cinder.resource.NamespaceId;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

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
 * after construction, except Mojang {@link BlockPos} offsets created by the
 * current Minecraft API shape.
 */
public final class CtmMinecraftNeighborView implements NeighborView {

    private static final int CUBE_SIZE = 27;
    private static final int FACE_COUNT = 6;

    private final BlockAndTintGetter source;
    private final NeighborCache cache = new NeighborCache();
    private final NamespaceId @Nullable [] spriteTable =
            new NamespaceId[CUBE_SIZE * FACE_COUNT];
    private @Nullable BlockPos centerPos;

    public CtmMinecraftNeighborView(BlockAndTintGetter source) {
        this.source = source;
    }

    public void reset(BlockPos centerPos) {
        this.centerPos = centerPos.immutable();
        cache.reset();
        for (int i = 0; i < spriteTable.length; i++) {
            spriteTable[i] = null;
        }
        BlockState center = source.getBlockState(centerPos);
        setCenterState(center);
    }

    public void reset(BlockPos centerPos, BlockState centerState) {
        this.centerPos = centerPos.immutable();
        cache.reset();
        for (int i = 0; i < spriteTable.length; i++) {
            spriteTable[i] = null;
        }
        setCenterState(centerState);
    }

    private void setCenterState(BlockState centerState) {
        cache.set(0, 0, 0, blockIdOf(centerState), centerState.isSolidRender());
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
        Identifier id = sprite.contents().name();
        setSpriteIdForFace(dx, dy, dz, face,
                new NamespaceId(id.getNamespace(), id.getPath()));
    }

    public void setSpriteIdForFace(int dx, int dy, int dz,
                                   int face,
                                   NamespaceId sprite) {
        spriteTable[spriteIndexOf(dx, dy, dz, face)] = sprite;
    }

    private void setNeighbourBlock(int dx, int dy, int dz) {
        if (centerPos == null) {
            throw new IllegalStateException("reset() must be called first");
        }
        BlockPos p = centerPos.offset(dx, dy, dz);
        BlockState state = source.getBlockState(p);
        cache.set(dx, dy, dz, blockIdOf(state), state.isSolidRender());
    }

    @Override
    public @Nullable NamespaceId sprite(int dx, int dy, int dz, int face) {
        NamespaceId sprite =
                spriteTable[spriteIndexOf(dx, dy, dz, face)];
        if (sprite == null) {
            return fallbackSprite(dx, dy, dz, face);
        }
        return sprite;
    }

    @Override
    public String blockId(int dx, int dy, int dz) {
        return cache.blockId(dx, dy, dz);
    }

    @Override
    public boolean isFullBlock(int dx, int dy, int dz) {
        return cache.isFullBlock(dx, dy, dz);
    }

    private static int spriteIndexOf(int dx, int dy, int dz, int face) {
        int cell = (dx + 1) * 9 + (dy + 1) * 3 + (dz + 1);
        return cell * FACE_COUNT + face;
    }

    private static String blockIdOf(BlockState state) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id == null ? null : id.toString();
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
