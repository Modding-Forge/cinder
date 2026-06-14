package com.cinder.fabric.customcolors;

import com.cinder.config.CinderConfigHolder;
import com.cinder.customcolors.ColormapImage;
import com.cinder.resource.NamespaceId;
import net.minecraft.client.multiplayer.ClientLevel;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Cursor3D;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.FogType;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Runtime access point for Custom Colors hooks.
 *
 * <p>All hooks read one immutable snapshot from an atomic reference. No hook
 * mutates global color state or allocates in the common fallback path.
 */
public final class CustomColorsRuntime {

    private static final String[] MAP_COLOR_KEYS = {
            "map.air", "map.grass", "map.sand", "map.cloth",
            "map.tnt", "map.ice", "map.iron", "map.foliage",
            "map.snow", "map.clay", "map.dirt", "map.stone",
            "map.water", "map.wood", "map.quartz", "map.adobe",
            "map.magenta", "map.lightBlue", "map.yellow",
            "map.lightGreen", "map.pink", "map.gray",
            "map.silver", "map.cyan", "map.purple", "map.blue",
            "map.brown", "map.green", "map.red", "map.black",
            "map.gold", "map.diamond", "map.lapis", "map.emerald",
            "map.obsidian", "map.netherrack", "map.white",
            "map.orange", "map.magenta", "map.lightBlue",
            "map.yellow", "map.lightGreen", "map.pink", "map.gray",
            "map.silver", "map.cyan", "map.purple", "map.blue",
            "map.brown", "map.green", "map.red", "map.black",
            "map.crimsonNylium", "map.crimsonStem", "map.crimsonHyphae",
            "map.warpedNylium", "map.warpedStem", "map.warpedHyphae",
            "map.warpedWartBlock", "map.deepslate", "map.rawIron",
            "map.glowLichen"
    };
    private static final String[] TEXT_CODE_KEYS = {
            "text.code.0", "text.code.1", "text.code.2", "text.code.3",
            "text.code.4", "text.code.5", "text.code.6", "text.code.7",
            "text.code.8", "text.code.9", "text.code.10", "text.code.11",
            "text.code.12", "text.code.13", "text.code.14", "text.code.15"
    };

    private static final AtomicReference<CustomColorsClientSnapshot> SNAPSHOT =
            new AtomicReference<>(CustomColorsClientSnapshot.empty());
    private static volatile boolean warnedColormatic;

    private CustomColorsRuntime() {
    }

    public static void replace(CustomColorsClientSnapshot snapshot) {
        SNAPSHOT.set(snapshot == null
                ? CustomColorsClientSnapshot.empty() : snapshot);
    }

    public static CustomColorsClientSnapshot snapshot() {
        return SNAPSHOT.get();
    }

    public static boolean active() {
        if (!CinderConfigHolder.get().customColorsActive()) {
            return false;
        }
        if (FabricLoader.getInstance().isModLoaded("colormatic")) {
            if (!warnedColormatic) {
                warnedColormatic = true;
                org.slf4j.LoggerFactory.getLogger("cinder/custom-colors")
                        .warn("[Cinder] Colormatic detected; Cinder Custom "
                                + "Colors runtime is disabled");
            }
            return false;
        }
        return true;
    }

    private static boolean biomeActive() {
        return active() && CinderConfigHolder.get().biomeColorsEnabled();
    }

    private static boolean skyActive() {
        return active() && CinderConfigHolder.get().skyColorsEnabled();
    }

    public static int blockPaletteColor(BlockState state,
                                        BlockAndTintGetter level,
                                        BlockPos pos,
                                        int fallback) {
        if (!biomeActive() || state == null) {
            return fallback;
        }
        CustomColorsClientSnapshot.RuntimeRule[] rules =
                SNAPSHOT.get().rulesFor(state.getBlock());
        if (rules.length == 0) {
            return fallback;
        }
        for (CustomColorsClientSnapshot.RuntimeRule runtimeRule : rules) {
            if (!runtimeRule.matches(state)) {
                continue;
            }
            return sampleRule(runtimeRule, level, pos, fallback);
        }
        return fallback;
    }

    public static int grassColor(BlockState state,
                                 BlockAndTintGetter level,
                                 BlockPos pos) {
        int palette = blockPaletteColor(state, level, pos, Integer.MIN_VALUE);
        if (palette != Integer.MIN_VALUE) {
            return palette;
        }
        return BiomeColors.getAverageGrassColor(level, pos);
    }

    public static int foliageColor(BlockState state,
                                   BlockAndTintGetter level,
                                   BlockPos pos) {
        int palette = blockPaletteColor(state, level, pos, Integer.MIN_VALUE);
        if (palette != Integer.MIN_VALUE) {
            return palette;
        }
        return BiomeColors.getAverageFoliageColor(level, pos);
    }

    public static int waterColor(BlockState state,
                                 BlockAndTintGetter level,
                                 BlockPos pos) {
        int palette = blockPaletteColor(state, level, pos, Integer.MIN_VALUE);
        if (palette != Integer.MIN_VALUE) {
            return palette;
        }
        return BiomeColors.getAverageWaterColor(level, pos);
    }

    /**
     * Overrides Minecraft's registered biome color resolvers from a
     * ClientLevel mixin.
     *
     * <p>Performance: HOT PATH. Allocation policy mirrors vanilla's blend
     * code and allocates only the cursor/mutable position when biome blending
     * is enabled. The hook never passes custom ColorResolver instances back
     * into ClientLevel, because 26.2 only registers vanilla resolvers.
     */
    public static int registeredBiomeColor(ColorResolver resolver,
                                           ClientLevel level,
                                           BlockPos pos,
                                           int fallback) {
        if (!biomeActive() || resolver == null || level == null || pos == null) {
            return fallback;
        }
        ColormapImage image = specialForResolver(resolver);
        if (image == null) {
            return fallback;
        }
        int dist = Minecraft.getInstance().options.biomeBlendRadius().get();
        if (dist == 0) {
            return sampleBiome(level, pos, image);
        }
        int count = (dist * 2 + 1) * (dist * 2 + 1);
        int totalRed = 0;
        int totalGreen = 0;
        int totalBlue = 0;
        Cursor3D cursor = new Cursor3D(pos.getX() - dist, pos.getY(),
                pos.getZ() - dist, pos.getX() + dist, pos.getY(),
                pos.getZ() + dist);
        BlockPos.MutableBlockPos nextPos = new BlockPos.MutableBlockPos();
        while (cursor.advance()) {
            nextPos.set(cursor.nextX(), cursor.nextY(), cursor.nextZ());
            int color = sampleBiome(level, nextPos, image);
            totalRed += ARGB.red(color);
            totalGreen += ARGB.green(color);
            totalBlue += ARGB.blue(color);
        }
        return ARGB.color(totalRed / count, totalGreen / count,
                totalBlue / count);
    }

    /**
     * Overrides Sodium's cached biome color source before Sodium applies its
     * own section-local color cache and blur.
     *
     * <p>Performance: HOT PATH during Sodium color-cache population. The
     * method is allocation-free and returns RGB to match vanilla
     * {@link ColorResolver} semantics.
     */
    public static int sodiumBiomeColor(ColorResolver resolver,
                                       Biome biome,
                                       int fallback) {
        if (!biomeActive() || resolver == null || biome == null) {
            return fallback;
        }
        ColormapImage image = specialForResolver(resolver);
        if (image == null) {
            return fallback;
        }
        return sampleBiome(biome, image) & 0xFFFFFF;
    }

    public static int redstoneColor(BlockState state) {
        if (active()) {
            ColormapImage image = SNAPSHOT.get().special("redstone");
            if (image != null) {
                int power = state.getValue(RedStoneWireBlock.POWER);
                return image.pixel(power, 0);
            }
        }
        return RedStoneWireBlock.getColorForPower(
                state.getValue(RedStoneWireBlock.POWER));
    }

    public static int stemColor(BlockState state) {
        if (active()) {
            String key = blockPath(state.getBlock()).contains("pumpkin")
                    ? "pumpkinstem" : "melonstem";
            ColormapImage image = SNAPSHOT.get().special(key);
            if (image != null) {
                return image.pixel(state.getValue(StemBlock.AGE), 0);
            }
        }
        int age = state.getValue(StemBlock.AGE);
        return ARGB.color(age * 32, 255 - age * 8, age * 4);
    }

    public static int overrideColor(String key, int fallback) {
        if (!biomeActive()) {
            return fallback;
        }
        return SNAPSHOT.get().overrides().colorOr(key, fallback);
    }

    public static int overrideArgb(String key, int fallback) {
        int rgb = overrideColor(key, fallback & 0xFFFFFF);
        return ARGB.opaque(rgb);
    }

    public static int dyeTextureColor(DyeColor color, int fallback) {
        if (color == null) {
            return fallback;
        }
        return overrideArgb("dye." + color.getName(), fallback);
    }

    public static int dyeTextColor(DyeColor color, int fallback) {
        if (color == null) {
            return fallback;
        }
        return overrideArgb("text.sign." + color.getName(),
                overrideArgb("text.sign", fallback));
    }

    public static int sheepColor(DyeColor color, int fallback) {
        if (color == null) {
            return fallback;
        }
        return overrideArgb("sheep." + color.getName(), fallback);
    }

    public static int collarColor(DyeColor color, int fallback) {
        if (color == null) {
            return fallback;
        }
        return overrideArgb("collar." + color.getName(), fallback);
    }

    public static int potionColor(ItemStack stack, int fallback) {
        if (!active() || stack == null) {
            return fallback;
        }
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null || contents.customColor().isPresent()) {
            return fallback;
        }
        return contents.potion()
                .flatMap(holder -> holder.unwrapKey()
                        .map(key -> key.identifier().getPath()))
                .map(path -> overrideArgb("potion." + path, fallback))
                .orElseGet(() -> overrideArgb("potion.water", fallback));
    }

    public static int itemGrassColor(float temperature,
                                     float downfall,
                                     int fallback) {
        if (!skyActive()) {
            return fallback;
        }
        ColormapImage image = SNAPSHOT.get().special("grass");
        return image == null ? fallback : ARGB.opaque(
                image.sampleVanilla(temperature, downfall));
    }

    public static int mapMaterialColor(int id, int fallback) {
        if (id < 0 || id >= MAP_COLOR_KEYS.length) {
            return fallback;
        }
        return overrideArgb(MAP_COLOR_KEYS[id], fallback);
    }

    public static int durabilityColor(ItemStack stack, int fallback) {
        if (!active() || stack == null || stack.getMaxDamage() <= 0) {
            return fallback;
        }
        ColormapImage image = SNAPSHOT.get().special("durability");
        if (image == null) {
            return fallback;
        }
        float remaining = Math.max(0.0F,
                (float) (stack.getMaxDamage() - stack.getDamageValue())
                        / stack.getMaxDamage());
        int x = Mth.clamp(Math.round(remaining * (image.width() - 1)),
                0, image.width() - 1);
        return ARGB.opaque(image.pixel(x, 0));
    }

    public static int xpOrbColor(float ageInTicks, int fallback) {
        if (!active()) {
            return fallback;
        }
        ColormapImage image = SNAPSHOT.get().special("xporb");
        if (image == null) {
            return fallback;
        }
        int x = Math.floorMod(Mth.floor(ageInTicks), image.width());
        return ARGB.color(128, image.pixel(x, 0));
    }

    public static int textColor(String name, int fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        int codeIndex = textCodeIndex(name);
        if (codeIndex >= 0) {
            return overrideColor(TEXT_CODE_KEYS[codeIndex],
                    fallback & 0xFFFFFF);
        }
        return fallback;
    }

    public static int fogColor(FogType fogType,
                               ClientLevel level,
                               int fallback) {
        if (!active()) {
            return fallback;
        }
        if (fogType == FogType.WATER) {
            return environmentColor("underwater",
                    overrideArgb("fog.water", fallback));
        }
        if (fogType == FogType.LAVA) {
            return environmentColor("underlava",
                    overrideArgb("fog.lava", fallback));
        }
        if (level != null && level.dimension() == Level.END) {
            return environmentColor("fog0",
                    overrideArgb("fog.end", fallback));
        }
        return fallback;
    }

    private static int environmentColor(String key, int fallback) {
        ColormapImage image = SNAPSHOT.get().environment(key);
        return image == null ? fallback : ARGB.opaque(image.pixel(0, 0));
    }

    private static int textCodeIndex(String name) {
        return switch (name) {
            case "black" -> 0;
            case "dark_blue" -> 1;
            case "dark_green" -> 2;
            case "dark_aqua" -> 3;
            case "dark_red" -> 4;
            case "dark_purple" -> 5;
            case "gold" -> 6;
            case "gray" -> 7;
            case "dark_gray" -> 8;
            case "blue" -> 9;
            case "green" -> 10;
            case "aqua" -> 11;
            case "red" -> 12;
            case "light_purple" -> 13;
            case "yellow" -> 14;
            case "white" -> 15;
            default -> -1;
        };
    }

    private static int sampleRule(CustomColorsClientSnapshot.RuntimeRule rule,
                                  BlockAndTintGetter level,
                                  BlockPos pos,
                                  int fallback) {
        if (rule.rule().hasFixedColor()) {
            return rule.rule().fixedColor();
        }
        if (level == null || pos == null) {
            return fallback;
        }
        ColormapImage image = rule.image();
        if (image == null) {
            return fallback;
        }
        if (rule.rule().format() == com.cinder.customcolors.ColormapFormat.GRID) {
            int biomeColumn = Math.floorMod(pos.getX() * 31 + pos.getZ(),
                    image.width());
            return rule.rule().sample(image, 0.0D, 0.0D, biomeColumn,
                    pos.getY(), pos.getX(), pos.getZ());
        }
        if (level instanceof ClientLevel clientLevel) {
            return sampleImageInClientLevel(clientLevel, pos, image);
        }
        return fallback;
    }

    private static ColormapImage specialForResolver(ColorResolver resolver) {
        CustomColorsClientSnapshot snapshot = SNAPSHOT.get();
        if (resolver == BiomeColors.GRASS_COLOR_RESOLVER) {
            return snapshot.special("grass");
        }
        if (resolver == BiomeColors.FOLIAGE_COLOR_RESOLVER
                || resolver == BiomeColors.DRY_FOLIAGE_COLOR_RESOLVER) {
            return snapshot.special("foliage");
        }
        if (resolver == BiomeColors.WATER_COLOR_RESOLVER) {
            return snapshot.special("water");
        }
        return null;
    }

    private static int sampleImageInClientLevel(ClientLevel level,
                                                BlockPos pos,
                                                ColormapImage image) {
        int dist = Minecraft.getInstance().options.biomeBlendRadius().get();
        if (dist == 0) {
            return sampleBiome(level, pos, image);
        }
        int count = (dist * 2 + 1) * (dist * 2 + 1);
        int totalRed = 0;
        int totalGreen = 0;
        int totalBlue = 0;
        Cursor3D cursor = new Cursor3D(pos.getX() - dist, pos.getY(),
                pos.getZ() - dist, pos.getX() + dist, pos.getY(),
                pos.getZ() + dist);
        BlockPos.MutableBlockPos nextPos = new BlockPos.MutableBlockPos();
        while (cursor.advance()) {
            nextPos.set(cursor.nextX(), cursor.nextY(), cursor.nextZ());
            int color = sampleBiome(level, nextPos, image);
            totalRed += ARGB.red(color);
            totalGreen += ARGB.green(color);
            totalBlue += ARGB.blue(color);
        }
        return ARGB.color(totalRed / count, totalGreen / count,
                totalBlue / count);
    }

    private static int sampleBiome(ClientLevel level,
                                   BlockPos pos,
                                   ColormapImage image) {
        Biome biome = level.getBiome(pos).value();
        return sampleBiome(biome, image);
    }

    private static int sampleBiome(Biome biome,
                                   ColormapImage image) {
        Biome.ClimateSettings climate = biome.climateSettings;
        return image.sampleVanilla(climate.temperature, climate.downfall);
    }

    private static String blockPath(Block block) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        return id == null ? "" : id.getPath();
    }
}
