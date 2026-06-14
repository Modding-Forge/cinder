package com.cinder.fabric.randomentity;

import com.cinder.config.CinderConfigHolder;
import com.cinder.randomentity.RandomEntityContext;
import com.cinder.resource.NamespaceId;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Random Entity texture runtime bridge.
 *
 * <p>Purpose: stores the active immutable resource-pack snapshot and creates a
 * loader-agnostic context during entity render-state extraction.
 *
 * <p>Threading: snapshot publication is atomic. Context objects are immutable
 * and stored on per-render states, never in global mutable current-entity
 * fields.
 *
 * <p>Performance: HOT PATH in {@link #remap(Identifier, RandomEntityContext)}.
 * The method performs config/compat gates, an empty-snapshot check, and one
 * O(1) rule lookup.
 */
public final class RandomEntityRuntime {
    private static final Logger LOGGER =
            LoggerFactory.getLogger("cinder/random-entities");
    private static final AtomicReference<RandomEntityClientSnapshot> SNAPSHOT =
            new AtomicReference<>(RandomEntityClientSnapshot.empty());
    private static final AtomicInteger VERSION = new AtomicInteger();
    private static volatile boolean warnedEtf;

    private RandomEntityRuntime() {
    }

    public static int nextVersion() {
        return VERSION.incrementAndGet();
    }

    public static void replace(RandomEntityClientSnapshot snapshot) {
        SNAPSHOT.set(snapshot == null ? RandomEntityClientSnapshot.empty()
                : snapshot);
    }

    public static RandomEntityContext capture(Entity entity) {
        if (entity == null) {
            return null;
        }
        Level level = entity.level();
        BlockPos pos = entity.blockPosition();
        NamespaceId biome = biomeId(level, pos);
        NamespaceId block = blockBelowId(level, pos);
        String name = entity.hasCustomName() && entity.getCustomName() != null
                ? entity.getCustomName().getString() : null;
        boolean baby = entity instanceof LivingEntity living && living.isBaby();
        int health = 0;
        int maxHealth = 0;
        if (entity instanceof LivingEntity living) {
            health = Math.round(living.getHealth());
            maxHealth = Math.round(living.getMaxHealth());
        }
        String weather = weather(level);
        String color = color(entity);
        long day = level == null ? 0L : level.getOverworldClockTime();
        int dayTime = (int) Math.floorMod(day, 24000L);
        int moonPhase = (int) Math.floorMod(day / 24000L, 8L);
        Entity vehicle = entity.getVehicle();
        return new RandomEntityContext(seed(entity),
                vehicle == null ? seed(entity) : seed(vehicle),
                biome,
                pos.getY(),
                name,
                null,
                0,
                color,
                baby,
                health,
                maxHealth,
                moonPhase,
                dayTime,
                weather,
                Math.max(1, Math.round(entity.getBbWidth() * 100.0f)),
                block);
    }

    public static Identifier remap(Identifier texture,
                                   RandomEntityContext context) {
        if (!active()) {
            return texture;
        }
        RandomEntityClientSnapshot snapshot = SNAPSHOT.get();
        if (snapshot.isEmpty()) {
            return texture;
        }
        return snapshot.remap(texture, context);
    }

    public static int resolveIndex(Identifier texture,
                                   RandomEntityContext context) {
        if (!active()) {
            return 1;
        }
        RandomEntityClientSnapshot snapshot = SNAPSHOT.get();
        if (snapshot.isEmpty()) {
            return 1;
        }
        return snapshot.resolveIndex(texture, context);
    }

    public static Identifier remap(Identifier texture, int variantIndex) {
        if (!active()) {
            return texture;
        }
        RandomEntityClientSnapshot snapshot = SNAPSHOT.get();
        if (snapshot.isEmpty()) {
            return texture;
        }
        return snapshot.remap(texture, variantIndex);
    }

    public static boolean active() {
        if (!CinderConfigHolder.get().randomEntitiesActive()) {
            return false;
        }
        if (FabricLoader.getInstance().isModLoaded("entity_texture_features")) {
            if (!warnedEtf) {
                warnedEtf = true;
                LOGGER.warn("[Cinder] ETF detected; Cinder Random Entities "
                        + "runtime selection is disabled");
            }
            return false;
        }
        return true;
    }

    private static long seed(Entity entity) {
        UUID uuid = entity.getUUID();
        return uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits()
                ^ Integer.toUnsignedLong(entity.getId());
    }

    private static NamespaceId biomeId(Level level, BlockPos pos) {
        if (level == null) {
            return null;
        }
        Optional<ResourceKey<Biome>> key = level.getBiome(pos).unwrapKey();
        return key.map(value -> new NamespaceId(
                value.identifier().getNamespace(),
                value.identifier().getPath())).orElse(null);
    }

    private static NamespaceId blockBelowId(Level level, BlockPos pos) {
        if (level == null) {
            return null;
        }
        Identifier id = BuiltInRegistries.BLOCK.getKey(
                level.getBlockState(pos.below()).getBlock());
        return new NamespaceId(id.getNamespace(), id.getPath());
    }

    private static String weather(Level level) {
        if (level == null) {
            return "clear";
        }
        if (level.isThundering()) {
            return "thunder";
        }
        return level.isRaining() ? "rain" : "clear";
    }

    private static String color(Entity entity) {
        DyeColor color = null;
        if (entity instanceof Sheep sheep) {
            color = sheep.getColor();
        } else if (entity instanceof Wolf wolf) {
            color = wolf.getCollarColor();
        }
        return color == null ? null : color.getName();
    }
}
