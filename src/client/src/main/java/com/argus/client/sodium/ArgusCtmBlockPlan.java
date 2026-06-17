package com.argus.client.sodium;

import com.argus.client.render.CtmMinecraftNeighborView;
import com.argus.ctm.Faces;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Per-block CTM cache used by the Sodium model-emitter path.
 *
 * <p>Purpose: CTM selections are geometrically stable for a block, face and
 * source sprite during one model emission. This plan caches the shared resolver
 * output so multiple quads on that face reuse the same CTM work.
 *
 * <p>Threading: one instance belongs to one Sodium model emission on one
 * section-build worker. It is never shared globally.
 *
 * <p>Performance: HOT PATH. Allocation policy: one block plan per emitted
 * model, lazy neighbor view, and tiny face-local sprite arrays.
 */
public final class ArgusCtmBlockPlan {

    private static final int FACE_COUNT = 6;

    private static final ConcurrentMap<Block, String> BLOCK_IDS =
            new ConcurrentHashMap<>();

    private final @Nullable BlockAndTintGetter level;
    private final @Nullable BlockState state;
    private final @Nullable BlockPos pos;
    private final @Nullable String blockId;
    private final ArgusCtmFacePlan[] faces =
            new ArgusCtmFacePlan[FACE_COUNT];

    private @Nullable CtmMinecraftNeighborView neighborView;

    ArgusCtmBlockPlan(@Nullable BlockAndTintGetter level,
                      @Nullable BlockState state,
                      @Nullable BlockPos pos) {
        this.level = level;
        this.state = state;
        this.pos = pos;
        this.blockId = state == null ? null : blockId(state);
    }

    public @Nullable String blockId() {
        return blockId;
    }

    public @Nullable CtmMinecraftNeighborView neighborView() {
        if (level == null || state == null || pos == null) {
            return null;
        }
        CtmMinecraftNeighborView current = neighborView;
        if (current == null) {
            current = new CtmMinecraftNeighborView(level);
            current.reset(pos, state);
            neighborView = current;
        }
        return current;
    }

    public @Nullable ArgusCtmFaceSpriteResult cached(int face,
                                                     TextureAtlasSprite sprite) {
        ArgusCtmFacePlan plan = facePlan(face, false);
        return plan == null ? null : plan.find(sprite);
    }

    public void cache(int face,
                      TextureAtlasSprite sprite,
                      ArgusCtmFaceSpriteResult result) {
        ArgusCtmFacePlan plan = facePlan(face, true);
        if (plan != null) {
            plan.put(sprite, result);
        }
    }

    private @Nullable ArgusCtmFacePlan facePlan(int face, boolean create) {
        if (face < Faces.DOWN || face > Faces.EAST) {
            return null;
        }
        ArgusCtmFacePlan plan = faces[face];
        if (plan == null && create) {
            plan = new ArgusCtmFacePlan();
            faces[face] = plan;
        }
        return plan;
    }

    private static String blockId(BlockState state) {
        Block block = state.getBlock();
        String cached = BLOCK_IDS.get(block);
        if (cached != null) {
            return cached;
        }
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        String created = id == null ? "" : id.toString();
        String previous = BLOCK_IDS.putIfAbsent(block, created);
        return previous == null ? created : previous;
    }
}
