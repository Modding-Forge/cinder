package com.cinder.fabric.atlas;

import com.cinder.Constants;
import com.cinder.bettergrass.BetterGrassFamily;
import com.cinder.bettergrass.BetterGrassProperties;
import com.cinder.bettergrass.BetterGrassRules;
import com.cinder.ctm.CompactCtmTiles;
import com.cinder.ctm.CtmProperties;
import com.cinder.ctm.CtmMethod;
import com.cinder.ctm.CtmRule;
import com.cinder.ctm.CtmTileSpec;
import com.cinder.ctm.CtmTileResolver;
import com.cinder.ctm.TileIndexTable;
import com.cinder.emissive.EmissiveProperties;
import com.cinder.emissive.EmissiveSettings;
import com.cinder.resource.NamespaceId;
import com.cinder.resource.PropertiesFile;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;

/**
 * Custom Mojang {@link SpriteSource} that injects OptiFine
 * CTM tile PNGs into the vanilla block atlas.
 *
 * <h2>Why a custom SpriteSource?</h2>
 *
 * <p>The OptiFine pack layout places tile PNGs at
 * {@code assets/minecraft/optifine/ctm/<family>/<index>.png},
 * which is <b>outside</b> the {@code textures/} root that
 * vanilla's {@code SingleFile} and {@code DirectoryLister}
 * SpriteSources assume. The vanilla sources would
 * silently drop those PNGs on the floor. Our custom source
 * reads them via {@code getResource} directly, bypassing
 * the {@code textures/} prefix.
 *
 * <h2>Why a separate Source from {@code CtmReloadListener}?</h2>
 *
 * <p>{@code CtmReloadListener} populates the
 * {@code CtmTileAtlas} (used by the renderer to look up
 * sprite ids) and runs in parallel with the atlas stitch.
 * A SpriteSource that depends on the live atlas would race
 * with the reload and is not allowed by Mojang's reload
 * API anyway. The SpriteSource is therefore
 * <b>self-contained</b>: it walks the resource pack for
 * {@code .properties} files and the parsed rules
 * determine the exact tile PNG paths via the
 * {@code tiles=...} list.
 *
 * <p>This duplicates the .properties parse work that
 * {@code CtmReloadListener} does, but it keeps the two
 * listeners independent and reload-order-safe. Future
 * phases can cache the parsed result behind a singleton
 * if profiling shows the duplicate parse is too expensive.
 *
 * <h2>Threading</h2>
 *
 * <p>{@link #run(ResourceManager, SpriteSource.Output)} is
 * called on the resource-reload thread by the
 * {@code SpriteSourceList.load} path. The listResources
 * and getResource calls are synchronous and allocation-
 * bounded.
 *
 * <h2>Performance</h2>
 *
 * <p>O(n_files + n_tiles) per reload. The listResources
 * call returns all entries under
 * {@code optifine/ctm} (including ContinUIty's
 * {@code continuity/ctm} tree) in one go.
 *
 * <p>Performance: NOT on hot path. Runs once per resource
 * reload.
 */
public record CinderTileSpriteSource() implements SpriteSource {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(Constants.MOD_ID + "/tile-source");

    /** Mod-namespace-prefixed SpriteSource type id. */
    public static final Identifier TYPE_ID =
            Identifier.fromNamespaceAndPath(
                    Constants.MOD_ID, "tiles");

    /**
     * A unit codec: this source has no per-instance JSON
     * parameters (the source's behaviour is determined at
     * reload time by reading the resource pack), so a
     * {@link MapCodec#unit} is appropriate.
     */
    public static final MapCodec<CinderTileSpriteSource> MAP_CODEC =
            MapCodec.unit(CinderTileSpriteSource::new);

    private static final String OPTIFINE_CTM = "optifine/ctm";
    private static final String CONTINUITY_CTM = "continuity/ctm";
    private static final Identifier BETTER_GRASS_PROPERTIES =
            Identifier.fromNamespaceAndPath(
                    "minecraft", "optifine/bettergrass.properties");
    private static final Identifier EMISSIVE_PROPERTIES =
            Identifier.fromNamespaceAndPath(
                    "minecraft", "optifine/emissive.properties");

    @Override
    public void run(ResourceManager resourceManager,
                    SpriteSource.Output output) {
        int added = 0;
        Set<Identifier> emitted = new HashSet<>();
        String emissiveSuffix = emissiveSuffix(resourceManager);
        added += scanTree(resourceManager, OPTIFINE_CTM, output, emitted,
                emissiveSuffix);
        added += scanTree(resourceManager, CONTINUITY_CTM, output, emitted,
                emissiveSuffix);
        added += scanBetterGrass(resourceManager, output, emitted);
        added += scanEmissive(resourceManager, output, emitted);
        if (added > 0) {
            LOGGER.info("[{}] injected {} OptiFine support sprites "
                            + "into the block atlas",
                    Constants.MOD_NAME, added);
        }
    }

    @Override
    public MapCodec<CinderTileSpriteSource> codec() {
        return MAP_CODEC;
    }

    private int scanBetterGrass(ResourceManager rm,
                                SpriteSource.Output output,
                                Set<Identifier> emitted) {
        Optional<Resource> resource = rm.getResource(BETTER_GRASS_PROPERTIES);
        if (resource.isEmpty()) {
            return 0;
        }
        BetterGrassRules rules;
        try (var in = resource.get().open();
             var reader = new InputStreamReader(
                     in, StandardCharsets.UTF_8)) {
            rules = BetterGrassProperties.parse(reader);
        } catch (Exception e) {
            return 0;
        }
        int added = 0;
        added += addBetterGrassTexture(rm, output, emitted,
                rules.texture(BetterGrassFamily.GRASS));
        added += addBetterGrassTexture(rm, output, emitted,
                rules.textureGrassSide());
        added += addBetterGrassTexture(rm, output, emitted,
                rules.texture(BetterGrassFamily.DIRT_PATH));
        added += addBetterGrassTexture(rm, output, emitted,
                rules.textureDirtPathSide());
        added += addBetterGrassTexture(rm, output, emitted,
                rules.texture(BetterGrassFamily.FARMLAND));
        added += addBetterGrassTexture(rm, output, emitted,
                rules.textureFarmlandSide());
        added += addBetterGrassTexture(rm, output, emitted,
                rules.texture(BetterGrassFamily.MYCELIUM));
        added += addBetterGrassTexture(rm, output, emitted,
                rules.texture(BetterGrassFamily.PODZOL));
        added += addBetterGrassTexture(rm, output, emitted,
                rules.texture(BetterGrassFamily.CRIMSON_NYLIUM));
        added += addBetterGrassTexture(rm, output, emitted,
                rules.texture(BetterGrassFamily.WARPED_NYLIUM));
        added += addBetterGrassTexture(rm, output, emitted,
                rules.textureSnow());
        return added;
    }

    private static int addBetterGrassTexture(ResourceManager rm,
                                             SpriteSource.Output output,
                                             Set<Identifier> emitted,
                                             NamespaceId texture) {
        Identifier spriteId = Identifier.fromNamespaceAndPath(
                texture.namespace(), texture.path());
        if (!emitted.add(spriteId)) {
            return 0;
        }
        Identifier textureId = SpriteSource.TEXTURE_ID_CONVERTER
                .idToFile(spriteId);
        Optional<Resource> resource = rm.getResource(textureId);
        if (resource.isEmpty()) {
            return 0;
        }
        output.add(spriteId, resource.get());
        return 1;
    }

    private int scanEmissive(ResourceManager rm,
                             SpriteSource.Output output,
                             Set<Identifier> emitted) {
        Optional<Resource> resource = rm.getResource(EMISSIVE_PROPERTIES);
        if (resource.isEmpty()) {
            return 0;
        }
        EmissiveSettings settings;
        try (var in = resource.get().open();
             var reader = new InputStreamReader(
                     in, StandardCharsets.UTF_8)) {
            settings = EmissiveProperties.parse(reader);
        } catch (Exception e) {
            return 0;
        }
        Map<Identifier, Resource> textures;
        try {
            textures = rm.listResources("textures",
                    id -> id.getPath().endsWith(
                            settings.suffix() + ".png"));
        } catch (RuntimeException e) {
            return 0;
        }
        int added = 0;
        for (Map.Entry<Identifier, Resource> entry : textures.entrySet()) {
            Identifier textureResource = entry.getKey();
            String spritePath = stripPng(stripTexturePrefix(
                    textureResource.getPath()));
            if (!spritePath.endsWith(settings.suffix())) {
                continue;
            }
            String basePath = spritePath.substring(0,
                    spritePath.length() - settings.suffix().length());
            Identifier baseTexture = Identifier.fromNamespaceAndPath(
                    textureResource.getNamespace(),
                    "textures/" + basePath + ".png");
            if (rm.getResource(baseTexture).isEmpty()) {
                continue;
            }
            Identifier spriteId = Identifier.fromNamespaceAndPath(
                    textureResource.getNamespace(), spritePath);
            if (!emitted.add(spriteId)) {
                continue;
            }
            output.add(spriteId, entry.getValue());
            added++;
        }
        return added;
    }

    private String emissiveSuffix(ResourceManager rm) {
        Optional<Resource> resource = rm.getResource(EMISSIVE_PROPERTIES);
        if (resource.isEmpty()) {
            return null;
        }
        try (var in = resource.get().open();
             var reader = new InputStreamReader(
                     in, StandardCharsets.UTF_8)) {
            return EmissiveProperties.parse(reader).suffix();
        } catch (Exception e) {
            return null;
        }
    }

    private int scanTree(ResourceManager rm, String tree,
                         SpriteSource.Output output,
                         Set<Identifier> emitted,
                         String emissiveSuffix) {
        // listResources returns the full Identifier map of
        // every file in the tree (no extension filter
        // available at this layer; we filter to .properties
        // below).
        Map<Identifier, Resource> all;
        try {
            all = rm.listResources(tree, id -> true);
        } catch (RuntimeException e) {
            // Some loader-specific pack types may throw
            // when listing; bail silently.
            return 0;
        }
        if (all == null || all.isEmpty()) {
            return 0;
        }
        int added = 0;
        // Identify all .properties files in the tree, parse
        // them, and resolve their tile PNGs.
        for (Map.Entry<Identifier, Resource> e : all.entrySet()) {
            Identifier loc = e.getKey();
            String path = loc.getPath();
            if (!path.endsWith(".properties")) {
                continue;
            }
            // We only handle the .properties files
            // themselves; tile PNGs are added via
            // output.add(...) from the parsed .properties.
            // (The all-map may also contain tile PNGs that
            // match the suffix ".properties" filter on the
            // first call; we filter them out above.)
            String body;
            try (var in = e.getValue().open();
                 var reader = new InputStreamReader(
                         in, StandardCharsets.UTF_8)) {
                body = readAll(reader);
            } catch (Exception ex) {
                continue;
            }
            // Compose a NamespaceId for the parent of the
            // .properties file so that the parser can
            // resolve relative tile paths correctly.
            String dirPath = parentDirectory(loc);
            NamespaceId parent = toNamespaceId(dirPath);
            CtmRule rule;
            try {
                PropertiesFile props = PropertiesFile.parse(
                        new java.io.StringReader(body));
                rule = CtmProperties.parse(
                        props, parent, loc.toString());
            } catch (Exception ex) {
                // Malformed .properties - skip.
                continue;
            }
            // The resolver needs the original resource
            // path string ("ns:optifine/ctm/.../foo.properties").
            String tileDirPath = CtmTileResolver.propertiesDirectoryPath(
                    loc.toString());
            int dirColon = tileDirPath.indexOf(':');
            String dirNs = dirColon < 0
                    ? NamespaceId.DEFAULT_NAMESPACE
                    : tileDirPath.substring(0, dirColon);
            String dirSlash = dirColon < 0
                    ? tileDirPath
                    : tileDirPath.substring(dirColon + 1);
            java.util.function.IntPredicate tileExists = n -> {
                Identifier tileId = Identifier.fromNamespaceAndPath(
                        dirNs, dirSlash + "/" + n + ".png");
                return rm.getResource(tileId).isPresent();
            };
            List<CtmTileResolver.Resolution> resolutions =
                    CtmTileResolver.resolve(
                            rule, loc.toString(), tileExists);
            Resource[] compactTileResources = null;
            for (CtmTileResolver.Resolution r : resolutions) {
                if (!r.needsInjection()) {
                    continue;
                }
                Identifier spriteId = parseIdentifier(
                        r.resolvedSprite().toString());
                if (!emitted.add(spriteId)) {
                    continue;
                }
                if (r.resourcePath() != null) {
                    Optional<Resource> tileRes =
                            rm.getResource(parseIdentifier(r.resourcePath()));
                    if (tileRes.isEmpty()) {
                        continue;
                    }
                    output.add(spriteId, tileRes.get());
                    added++;
                    added += addExplicitCtmEmissiveCompanion(
                            rm, output, emitted, spriteId,
                            r.resourcePath(), emissiveSuffix);
                    continue;
                }
                if (r.fallbackSourceSprite() == null) {
                    continue;
                }
                Identifier baseTextureId = SpriteSource.TEXTURE_ID_CONVERTER
                        .idToFile(parseIdentifier(
                                r.fallbackSourceSprite().toString()));
                Optional<Resource> baseRes = rm.getResource(baseTextureId);
                if (baseRes.isEmpty()) {
                    continue;
                }
                int generatedTileIndex = CtmTileResolver
                        .isCompactFullTileIndex(r.tileIndex())
                        ? CtmTileResolver.compactFullTileIndex(r.tileIndex())
                        : r.tileIndex();
                if (CtmTileResolver.isCompactFullTileIndex(r.tileIndex())
                        && rule.method() == CtmMethod.CTM_COMPACT) {
                    if (compactTileResources == null) {
                        compactTileResources = compactTileResources(
                                rm, rule, dirNs, dirSlash);
                    }
                    if (compactTileResources != null) {
                        int generatedFace = CtmTileResolver
                                .compactFullTileFace(r.tileIndex());
                        output.add(spriteId, new GeneratedCompactCtmTile(
                                spriteId, compactTileResources,
                                generatedTileIndex, generatedFace));
                        added++;
                        added += addGeneratedCompactEmissiveCompanion(
                                rm, output, emitted, spriteId, rule,
                                dirNs, dirSlash, generatedTileIndex,
                                generatedFace, emissiveSuffix);
                        continue;
                    }
                }
                output.add(spriteId, new GeneratedCtmTile(
                        spriteId, baseTextureId, baseRes.get(),
                        generatedTileIndex,
                        rule.method().isOverlay()));
                added++;
            }
        }
        return added;
    }

    private static int addExplicitCtmEmissiveCompanion(
            ResourceManager rm,
            SpriteSource.Output output,
            Set<Identifier> emitted,
            Identifier baseSprite,
            String baseResourcePath,
            String emissiveSuffix) {
        if (emissiveSuffix == null || emissiveSuffix.isEmpty()
                || !baseResourcePath.endsWith(".png")) {
            return 0;
        }
        String emissiveResourcePath = baseResourcePath.substring(
                0, baseResourcePath.length() - 4) + emissiveSuffix + ".png";
        Optional<Resource> emissiveResource = rm.getResource(
                parseIdentifier(emissiveResourcePath));
        if (emissiveResource.isEmpty()) {
            return 0;
        }
        Identifier emissiveSprite = Identifier.fromNamespaceAndPath(
                baseSprite.getNamespace(),
                baseSprite.getPath() + emissiveSuffix);
        if (!emitted.add(emissiveSprite)) {
            return 0;
        }
        output.add(emissiveSprite, emissiveResource.get());
        return 1;
    }

    private static int addGeneratedCompactEmissiveCompanion(
            ResourceManager rm,
            SpriteSource.Output output,
            Set<Identifier> emitted,
            Identifier baseSprite,
            CtmRule rule,
            String dirNs,
            String dirSlash,
            int fullTileIndex,
            int face,
            String emissiveSuffix) {
        if (emissiveSuffix == null || emissiveSuffix.isEmpty()) {
            return 0;
        }
        Resource[] emissiveResources = compactTileResources(
                rm, rule, dirNs, dirSlash, emissiveSuffix);
        if (emissiveResources == null) {
            return 0;
        }
        Identifier emissiveSprite = Identifier.fromNamespaceAndPath(
                baseSprite.getNamespace(),
                baseSprite.getPath() + emissiveSuffix);
        if (!emitted.add(emissiveSprite)) {
            return 0;
        }
        output.add(emissiveSprite, new GeneratedCompactCtmTile(
                emissiveSprite, emissiveResources, fullTileIndex, face));
        return 1;
    }

    private static Resource[] compactTileResources(ResourceManager rm,
                                                   CtmRule rule,
                                                   String dirNs,
                                                   String dirSlash) {
        return compactTileResources(rm, rule, dirNs, dirSlash, "");
    }

    private static Resource[] compactTileResources(ResourceManager rm,
                                                   CtmRule rule,
                                                   String dirNs,
                                                   String dirSlash,
                                                   String suffix) {
        if (rule.tiles().size() < 5) {
            return null;
        }
        Resource[] resources = new Resource[5];
        for (int i = 0; i < resources.length; i++) {
            Optional<Resource> resource = compactTileResource(
                    rm, rule.tiles().get(i), dirNs, dirSlash, suffix);
            if (resource.isEmpty()) {
                return null;
            }
            resources[i] = resource.get();
        }
        return resources;
    }

    private static Optional<Resource> compactTileResource(
            ResourceManager rm,
            CtmTileSpec spec,
            String dirNs,
            String dirSlash) {
        return compactTileResource(rm, spec, dirNs, dirSlash, "");
    }

    private static Optional<Resource> compactTileResource(
            ResourceManager rm,
            CtmTileSpec spec,
            String dirNs,
            String dirSlash,
            String suffix) {
        if (spec.isSkip() || spec.isDefault()) {
            return Optional.empty();
        }
        if (spec.isNumeric()) {
            Identifier id = Identifier.fromNamespaceAndPath(
                    dirNs, dirSlash + "/" + spec.numericIndex()
                            + suffix + ".png");
            return rm.getResource(id);
        }
        if (spec.resolvedSprite() == null) {
            return Optional.empty();
        }
        Identifier textureId = SpriteSource.TEXTURE_ID_CONVERTER
                .idToFile(parseIdentifier(spec.resolvedSprite().toString()));
        return rm.getResource(textureId);
    }

    /**
     * Generates a transitional atlas sprite for an OptiFine numeric CTM
     * tile whose explicit PNG is absent. The public CTM template marks
     * missing side neighbours as edge strokes and missing diagonal
     * neighbours as inner-corner strokes; this loader copies those
     * regions from the rule's base texture into a transparent tile.
     *
     * <p>Performance: NOT on hot path. Runs during atlas stitch only.
     */
    private static final class GeneratedCtmTile
            implements SpriteSource.DiscardableLoader {
        private final Identifier spriteId;
        private final Identifier baseTextureId;
        private final Resource baseResource;
        private final int tileIndex;
        private final boolean overlay;

        private GeneratedCtmTile(Identifier spriteId,
                                 Identifier baseTextureId,
                                 Resource baseResource,
                                 int tileIndex,
                                 boolean overlay) {
            this.spriteId = spriteId;
            this.baseTextureId = baseTextureId;
            this.baseResource = baseResource;
            this.tileIndex = tileIndex;
            this.overlay = overlay;
        }

        @Override
        public SpriteContents get(SpriteResourceLoader loader) {
            try (InputStream stream = baseResource.open();
                 NativeImage base = NativeImage.read(stream)) {
                NativeImage generated = overlay
                        ? generateOverlay(base, tileIndex)
                        : generate(base, tileIndex);
                return new SpriteContents(
                        spriteId,
                        new FrameSize(
                                generated.getWidth(),
                                generated.getHeight()),
                        generated);
            } catch (Exception e) {
                LOGGER.warn("[{}] failed to generate CTM tile {} from {}: {}",
                        Constants.MOD_NAME, spriteId, baseTextureId,
                        e.getMessage());
                return MissingTextureAtlasSprite.create();
            }
        }
    }

    /**
     * Generates a full 47-template CTM tile from the five source tiles of a
     * {@code ctm_compact} rule. Each output quadrant independently chooses the
     * compact source tile that matches its two adjacent side connections and
     * optional diagonal connection.
     */
    private static final class GeneratedCompactCtmTile
            implements SpriteSource.DiscardableLoader {
        private final Identifier spriteId;
        private final Resource[] compactResources;
        private final int fullTileIndex;
        private final int face;

        private GeneratedCompactCtmTile(Identifier spriteId,
                                        Resource[] compactResources,
                                        int fullTileIndex,
                                        int face) {
            this.spriteId = spriteId;
            this.compactResources = compactResources;
            this.fullTileIndex = fullTileIndex;
            this.face = face;
        }

        @Override
        public SpriteContents get(SpriteResourceLoader loader) {
            NativeImage[] compact = new NativeImage[compactResources.length];
            try {
                for (int i = 0; i < compactResources.length; i++) {
                    try (InputStream stream = compactResources[i].open()) {
                        compact[i] = NativeImage.read(stream);
                    }
                }
                NativeImage generated = generateCompact(
                        compact, fullTileIndex, face);
                return new SpriteContents(
                        spriteId,
                        new FrameSize(
                                generated.getWidth(),
                                generated.getHeight()),
                        generated);
            } catch (Exception e) {
                LOGGER.warn("[{}] failed to generate compact CTM tile {}: {}",
                        Constants.MOD_NAME, spriteId, e.getMessage());
                return MissingTextureAtlasSprite.create();
            } finally {
                for (NativeImage image : compact) {
                    if (image != null) {
                        image.close();
                    }
                }
            }
        }
    }

    private static NativeImage generate(NativeImage base, int tileIndex) {
        int width = base.getWidth();
        int height = base.getHeight();
        NativeImage out = new NativeImage(
                NativeImage.Format.RGBA, width, height, true);
        int sideMask = TileIndexTable.sideMaskForTile(tileIndex);
        int diagMask = TileIndexTable.diagonalMaskForTile(tileIndex);
        int thickness = Math.max(1, Math.min(width, height) / 16);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (shouldCopyPixel(
                        x, y, width, height, thickness,
                        sideMask, diagMask)) {
                    out.setPixel(x, y, base.getPixel(x, y));
                }
            }
        }
        return out;
    }

    private static NativeImage generateOverlay(NativeImage base,
                                               int tileIndex) {
        int width = base.getWidth();
        int height = base.getHeight();
        NativeImage out = new NativeImage(
                NativeImage.Format.RGBA, width, height, true);
        int mask = overlayPixelMask(tileIndex);
        int thickness = Math.max(1, Math.min(width, height) / 4);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (shouldCopyOverlayPixel(
                        x, y, width, height, thickness, mask)) {
                    out.setPixel(x, y, base.getPixel(x, y));
                }
            }
        }
        return out;
    }

    private static NativeImage generateCompact(NativeImage[] compact,
                                               int fullTileIndex,
                                               int face) {
        int width = compact[0].getWidth();
        int height = compact[0].getHeight();
        NativeImage canonical = new NativeImage(
                NativeImage.Format.RGBA, width, height, true);
        int halfWidth = Math.max(1, width / 2);
        int halfHeight = Math.max(1, height / 2);
        for (int y = 0; y < height; y++) {
            boolean bottom = y >= halfHeight;
            for (int x = 0; x < width; x++) {
                boolean right = x >= halfWidth;
                int compactIndex = CompactCtmTiles
                        .sourceTileIndexForQuadrant(
                                fullTileIndex, quadrant(right, bottom))
                        - 1;
                NativeImage source = compact[compactIndex];
                int sx = Math.min(x, source.getWidth() - 1);
                int sy = Math.min(y, source.getHeight() - 1);
                canonical.setPixel(x, y, source.getPixel(sx, sy));
            }
        }
        return canonical;
    }

    private static int quadrant(boolean right, boolean bottom) {
        if (right) {
            return bottom
                    ? CompactCtmTiles.QUADRANT_BOTTOM_RIGHT
                    : CompactCtmTiles.QUADRANT_TOP_RIGHT;
        }
        return bottom
                ? CompactCtmTiles.QUADRANT_BOTTOM_LEFT
                : CompactCtmTiles.QUADRANT_TOP_LEFT;
    }

    private static boolean shouldCopyPixel(
            int x, int y, int width, int height, int thickness,
            int sideMask, int diagMask) {
        boolean left = x < thickness;
        boolean right = x >= width - thickness;
        boolean top = y < thickness;
        boolean bottom = y >= height - thickness;
        if (left && (sideMask & 0x1) == 0) return true;
        if (right && (sideMask & 0x2) == 0) return true;
        if (top && (sideMask & 0x4) == 0) return true;
        if (bottom && (sideMask & 0x8) == 0) return true;
        if (left && top && (sideMask & 0x5) == 0x5
                && (diagMask & 0x1) == 0) return true;
        if (left && bottom && (sideMask & 0x9) == 0x9
                && (diagMask & 0x2) == 0) return true;
        if (right && top && (sideMask & 0x6) == 0x6
                && (diagMask & 0x4) == 0) return true;
        return right && bottom && (sideMask & 0xA) == 0xA
                && (diagMask & 0x8) == 0;
    }

    private static int overlayPixelMask(int tileIndex) {
        return switch (tileIndex) {
            case 0 -> 0xA;   // right + bottom corner
            case 1 -> 0x8;   // bottom
            case 2 -> 0x9;   // left + bottom corner
            case 3 -> 0xA;   // right + bottom
            case 4 -> 0x9;   // left + bottom
            case 5 -> 0xB;   // left + right + bottom
            case 6 -> 0xD;   // left + top + bottom
            case 7 -> 0x2;   // right
            case 8 -> 0xF;   // full overlay
            case 9 -> 0x1;   // left
            case 10 -> 0x6;  // right + top
            case 11 -> 0x5;  // left + top
            case 12 -> 0xE;  // right + top + bottom
            case 13 -> 0x7;  // left + right + top
            case 14 -> 0x6;  // right + top corner
            case 15 -> 0x4;  // top
            case 16 -> 0x5;  // left + top corner
            default -> 0;
        };
    }

    private static boolean shouldCopyOverlayPixel(
            int x, int y, int width, int height, int thickness, int mask) {
        if (mask == 0xF) {
            return true;
        }
        boolean left = x < thickness;
        boolean right = x >= width - thickness;
        boolean top = y < thickness;
        boolean bottom = y >= height - thickness;
        return (left && (mask & 0x1) != 0)
                || (right && (mask & 0x2) != 0)
                || (top && (mask & 0x4) != 0)
                || (bottom && (mask & 0x8) != 0);
    }

    private static Identifier parseIdentifier(String s) {
        // Strip the leading "ns:" and split into ns + path.
        int colon = s.indexOf(':');
        if (colon < 0) {
            return Identifier.fromNamespaceAndPath(
                    NamespaceId.DEFAULT_NAMESPACE, s);
        }
        return Identifier.fromNamespaceAndPath(
                s.substring(0, colon), s.substring(colon + 1));
    }

    private static NamespaceId toNamespaceId(String nsPath) {
        int colon = nsPath.indexOf(':');
        if (colon < 0) {
            return new NamespaceId(
                    NamespaceId.DEFAULT_NAMESPACE, nsPath);
        }
        return new NamespaceId(nsPath.substring(0, colon),
                nsPath.substring(colon + 1));
    }

    private static String parentDirectory(Identifier loc) {
        String path = loc.getPath();
        int slash = path.lastIndexOf('/');
        String dir = slash < 0 ? "" : path.substring(0, slash);
        return loc.getNamespace() + ":" + dir;
    }

    private static String readAll(java.io.Reader r)
            throws java.io.IOException {
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[1024];
        int n;
        while ((n = r.read(buf)) > 0) {
            sb.append(buf, 0, n);
        }
        return sb.toString();
    }

    private static String stripTexturePrefix(String path) {
        return path.startsWith("textures/")
                ? path.substring("textures/".length())
                : path;
    }

    private static String stripPng(String path) {
        return path.endsWith(".png")
                ? path.substring(0, path.length() - 4)
                : path;
    }
}
