package com.cinder.client.mixin;

import net.minecraft.client.renderer.texture.atlas.SpriteSources;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor mixin for {@link SpriteSources} that exposes the
 * private static {@code ID_MAPPER} so that the
 * {@code cinder:tiles} codec can be registered.
 *
 * <h2>Target</h2>
 *
 * <p>{@code SpriteSources.ID_MAPPER} (private static final
 * {@code LateBoundIdMapper<Identifier, MapCodec<? extends
 * SpriteSource>>}).
 *
 * <h2>Purpose</h2>
 *
 * <p>The {@code LateBoundIdMapper} is private; we cannot
 * reach it from outside the Mojang package without an
 * accessor or reflection. The accessor gives us a clean,
 * type-safe reference that we use to register
 * {@code cinder:tiles -> CinderTileSpriteSource.MAP_CODEC}
 * at the tail of {@code SpriteSources.bootstrap()}.
 *
 * <h2>Risk</h2>
 *
 * <p>Low. We are not modifying the {@code ID_MAPPER}'s
 * behaviour, only adding a single entry. The field exists
 * in 26.2-rc-1 and is unlikely to change shape.
 */
@Mixin(SpriteSources.class)
public interface SpriteSourcesAccessor {

    /**
     * Returns the {@code ID_MAPPER} field of
     * {@link SpriteSources}.
     */
    @Accessor("ID_MAPPER")
    static ExtraCodecs.LateBoundIdMapper<Identifier, ?> getIdMapper() {
        throw new AssertionError("mixin untransformed");
    }
}
