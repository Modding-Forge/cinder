package com.cinder.client.command;

import com.cinder.client.render.CtmMinecraftNeighborView;
import com.cinder.ctm.BlockSpec;
import com.cinder.ctm.CtmMaterialEntry;
import com.cinder.ctm.CtmMaterialTable;
import com.cinder.ctm.CtmOverlayTile;
import com.cinder.ctm.CtmRenderResolver;
import com.cinder.ctm.CtmRenderSelection;
import com.cinder.ctm.CtmRule;
import com.cinder.ctm.Faces;
import com.cinder.platform.Platforms;
import com.cinder.resource.NamespaceId;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.caffeinemc.mods.sodium.client.render.helper.ListStorage;
import net.caffeinemc.mods.sodium.client.services.PlatformModelAccess;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Registers Cinder client-side diagnostic commands.
 *
 * <p>The CTM texture logger inspects the currently loaded client render area
 * and prints the block positions and renderer-visible face sprites for a target
 * block type. It is intentionally a client command because it observes client
 * resource-pack models and atlas sprites, not server gameplay state.
 *
 * <h2>Threading</h2>
 *
 * <p>Commands execute on the client thread. The scan only reads client world
 * and model state.
 *
 * <h2>Performance</h2>
 *
 * <p>Debug-only path. It can scan many blocks and write many log lines; do not
 * call it during performance measurements.
 */
public final class CinderClientCommands {

    private static final Logger LOGGER =
            LoggerFactory.getLogger("cinder/ctm-command");
    private static final Direction[] DIRECTIONS = Direction.values();

    private CinderClientCommands() {
    }

    /**
     * Registers all client commands.
     */
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
                dispatcher.register(ClientCommands.literal("cinder")
                        .then(ClientCommands.literal("ctm")
                                .then(ClientCommands.literal("log")
                                        .then(ClientCommands.literal("overlays")
                                                .executes(ctx ->
                                                        logOverlaySection(
                                                                ctx.getSource())))
                                        .then(ClientCommands.argument(
                                                        "blocktype",
                                                        StringArgumentType.word())
                                                .executes(ctx -> logBlockTextures(
                                                        ctx.getSource(),
                                                        StringArgumentType
                                                                .getString(ctx,
                                                                        "blocktype"))))))));
    }

    private static int logBlockTextures(FabricClientCommandSource source,
                                        String blockType) {
        Identifier blockId = parseBlockId(blockType);
        if (!BuiltInRegistries.BLOCK.containsKey(blockId)) {
            source.sendError(Component.literal(
                    "[Cinder] Unknown block: " + blockType
                            + " (resolved as " + blockId + ")"));
            return 0;
        }
        Block block = BuiltInRegistries.BLOCK.getValue(blockId);
        Minecraft client = source.getClient();
        ClientLevel level = source.getLevel();
        if (client.player == null || level == null) {
            source.sendError(Component.literal(
                    "[Cinder] No client level/player available"));
            return 0;
        }
        int renderDistance = client.options.renderDistance().get();
        BlockPos center = client.player.blockPosition();
        int centerChunkX = center.getX() >> 4;
        int centerChunkZ = center.getZ() >> 4;
        int minY = level.getMinY();
        int maxY = level.getMaxY();
        ScanScratch scratch = new ScanScratch();
        int found = 0;
        LOGGER.info("[cinder] CTM face texture/selection scan begin block={} "
                        + "center={} renderDistance={} y={}..{}",
                blockId, center, renderDistance, minY, maxY - 1);
        for (int chunkZ = centerChunkZ - renderDistance;
             chunkZ <= centerChunkZ + renderDistance; chunkZ++) {
            for (int chunkX = centerChunkX - renderDistance;
                 chunkX <= centerChunkX + renderDistance; chunkX++) {
                if (!level.hasChunk(chunkX, chunkZ)) {
                    continue;
                }
                int minX = chunkX << 4;
                int minZ = chunkZ << 4;
                for (int y = minY; y < maxY; y++) {
                    for (int z = minZ; z < minZ + 16; z++) {
                        for (int x = minX; x < minX + 16; x++) {
                            scratch.pos.set(x, y, z);
                            BlockState state = level.getBlockState(scratch.pos);
                            if (state.getBlock() != block) {
                                continue;
                            }
                            found++;
                            LOGGER.info("[cinder] CTM face texture/selection block={} "
                                            + "pos={} state={} faces={}",
                                    blockId,
                                    scratch.pos,
                                    state,
                                    describeFaces(client, level, scratch.pos,
                                            state, scratch));
                        }
                    }
                }
            }
        }
        LOGGER.info("[cinder] CTM face texture/selection scan end block={} found={}",
                blockId, found);
        source.sendFeedback(Component.literal(
                "[Cinder] Logged " + found + " " + blockId
                        + " blocks in render distance"));
        return found;
    }

    private static int logOverlaySection(FabricClientCommandSource source) {
        Minecraft client = source.getClient();
        ClientLevel level = source.getLevel();
        if (client.player == null || level == null) {
            source.sendError(Component.literal(
                    "[Cinder] No client level/player available"));
            return 0;
        }
        BlockPos center = client.player.blockPosition();
        int minX = (center.getX() >> 4) << 4;
        int minY = (center.getY() >> 4) << 4;
        int minZ = (center.getZ() >> 4) << 4;
        int maxY = Math.min(minY + 16, level.getMaxY());
        minY = Math.max(minY, level.getMinY());
        ScanScratch scratch = new ScanScratch();
        CtmRenderResolver resolver =
                new CtmRenderResolver(Platforms.get().ctmRegistry());
        CtmMaterialTable materialTable = CtmMaterialTable.current();
        int foundBlocks = 0;
        int foundFaces = 0;
        LOGGER.info("[cinder] CTM overlay section scan begin section=[{}..{}, "
                        + "{}..{}, {}..{}] center={}",
                minX, minX + 15,
                minY, maxY - 1,
                minZ, minZ + 15,
                center);
        for (int y = minY; y < maxY; y++) {
            for (int z = minZ; z < minZ + 16; z++) {
                for (int x = minX; x < minX + 16; x++) {
                    scratch.pos.set(x, y, z);
                    BlockState state = level.getBlockState(scratch.pos);
                    int hits = logOverlayFacesForBlock(client, level,
                            scratch.pos, state, resolver, materialTable,
                            scratch);
                    if (hits > 0) {
                        foundBlocks++;
                        foundFaces += hits;
                    }
                }
            }
        }
        LOGGER.info("[cinder] CTM overlay section scan end blocks={} faces={}",
                foundBlocks, foundFaces);
        source.sendFeedback(Component.literal(
                "[Cinder] Logged " + foundFaces
                        + " overlay faces on " + foundBlocks
                        + " blocks in current chunk section"));
        return foundFaces;
    }

    private static int logOverlayFacesForBlock(
            Minecraft client,
            ClientLevel level,
            BlockPos pos,
            BlockState state,
            CtmRenderResolver resolver,
            CtmMaterialTable materialTable,
            ScanScratch scratch) {
        CtmMinecraftNeighborView fallback =
                new CtmMinecraftNeighborView(level);
        fallback.reset(pos, state);
        fallback.fillNeighbours();
        String blockId = blockIdOf(state);
        BlockStateModel model = client.getModelManager()
                .getBlockStateModelSet()
                .get(state);
        RandomSource random = scratch.random;
        random.setSeed(state.getSeed(pos));
        List<BlockStateModelPart> parts = PlatformModelAccess.getInstance()
                .collectPartsOf(model, level, pos, state, random, scratch);
        int hits = 0;
        for (Direction direction : DIRECTIONS) {
            int face = direction.get3DDataValue();
            scratch.sprites.clear();
            scratch.spriteIds.clear();
            collectFaceSprites(level, pos, state, direction, random, parts,
                    scratch);
            if (scratch.sprites.isEmpty()) {
                NamespaceId fallbackSprite = fallback.sprite(0, 0, 0, face);
                if (fallbackSprite != null) {
                    scratch.spriteIds.add(fallbackSprite);
                    scratch.sprites.add(fallbackSprite.toString()
                            + " (fallback)");
                }
            }
            NamespaceId baseSprite = scratch.spriteIds.isEmpty()
                    ? null
                    : scratch.spriteIds.getFirst();
            if (baseSprite == null) {
                continue;
            }
            fallback.setSpriteIdForFace(0, 0, 0, face, baseSprite);
            CtmRenderSelection selection = resolver.resolve(
                    blockId, baseSprite, fallback,
                    pos.getX(), pos.getY(), pos.getZ(), face);
            if (selection == null || !selection.isOverlay()
                    || selection.overlayTiles().isEmpty()) {
                continue;
            }
            hits++;
            LOGGER.info("[cinder] CTM overlay block={} pos={} face={} "
                            + "base={} overlays={} neighbours={}",
                    blockId,
                    pos,
                    direction.getName(),
                    baseSprite,
                    describeOverlays(selection, materialTable, fallback, pos,
                            direction),
                    describeFaceNeighbours(fallback, direction));
        }
        return hits;
    }

    private static Identifier parseBlockId(String raw) {
        String value = raw.indexOf(':') < 0 ? "minecraft:" + raw : raw;
        Identifier parsed = Identifier.tryParse(value);
        return parsed == null
                ? Identifier.fromNamespaceAndPath("minecraft", raw)
                : parsed;
    }

    private static String describeFaces(Minecraft client,
                                        ClientLevel level,
                                        BlockPos pos,
                                        BlockState state,
                                        ScanScratch scratch) {
        CtmMinecraftNeighborView fallback =
                new CtmMinecraftNeighborView(level);
        fallback.reset(pos, state);
        fallback.fillNeighbours();
        CtmRenderResolver resolver =
                new CtmRenderResolver(Platforms.get().ctmRegistry());
        CtmMaterialTable materialTable = CtmMaterialTable.current();
        String blockId = blockIdOf(state);
        BlockStateModel model = client.getModelManager()
                .getBlockStateModelSet()
                .get(state);
        RandomSource random = scratch.random;
        random.setSeed(state.getSeed(pos));
        List<BlockStateModelPart> parts = PlatformModelAccess.getInstance()
                .collectPartsOf(model, level, pos, state, random, scratch);
        StringBuilder out = new StringBuilder(384);
        for (Direction direction : DIRECTIONS) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            int face = direction.get3DDataValue();
            scratch.sprites.clear();
            scratch.spriteIds.clear();
            collectFaceSprites(level, pos, state, direction, random, parts,
                    scratch);
            if (scratch.sprites.isEmpty()) {
                NamespaceId fallbackSprite = fallback.sprite(0, 0, 0, face);
                if (fallbackSprite != null) {
                    scratch.spriteIds.add(fallbackSprite);
                    scratch.sprites.add(fallbackSprite.toString()
                            + " (fallback)");
                }
            }
            NamespaceId baseSprite = scratch.spriteIds.isEmpty()
                    ? null
                    : scratch.spriteIds.getFirst();
            if (baseSprite != null) {
                fallback.setSpriteIdForFace(0, 0, 0, face, baseSprite);
            }
            CtmRenderSelection selection = baseSprite == null
                    ? null
                    : resolver.resolve(blockId, baseSprite, fallback,
                    pos.getX(), pos.getY(), pos.getZ(), face);
            out.append(direction.getName()).append('=');
            appendSprites(out, scratch.sprites);
            out.append(":ctm=");
            appendCtm(out, selection, materialTable);
        }
        return out.toString();
    }

    private static void collectFaceSprites(ClientLevel level,
                                           BlockPos pos,
                                           BlockState state,
                                           Direction direction,
                                           RandomSource random,
                                           List<BlockStateModelPart> parts,
                                           ScanScratch scratch) {
        for (BlockStateModelPart part : parts) {
            List<BakedQuad> quads = PlatformModelAccess.getInstance()
                    .getQuads(level, pos, part, state, direction, random);
            for (BakedQuad quad : quads) {
                Identifier sprite = quad.materialInfo()
                        .sprite()
                        .contents()
                        .name();
                NamespaceId spriteId = new NamespaceId(
                        sprite.getNamespace(), sprite.getPath());
                addUnique(scratch.spriteIds, spriteId);
                addUnique(scratch.sprites, spriteId.toString());
            }
        }
    }

    private static String describeOverlays(
            CtmRenderSelection selection,
            CtmMaterialTable materialTable,
            CtmMinecraftNeighborView view,
            BlockPos pos,
            Direction direction) {
        StringBuilder out = new StringBuilder(192);
        List<CtmOverlayTile> overlays = selection.overlayTiles();
        for (int i = 0; i < overlays.size(); i++) {
            if (i > 0) {
                out.append(" | ");
            }
            CtmOverlayTile overlay = overlays.get(i);
            CtmRule rule = overlay.rule();
            out.append('{')
                    .append("tile=").append(overlay.tileIndex())
                    .append(",method=").append(rule.method())
                    .append(",rule=")
                    .append(rule.sourceFile().orElse("<unknown>"));
            appendMaterial(out, "material", materialTable.find(
                    rule, overlay.tileIndex()));
            out.append(",matchBlocks=").append(rule.matchBlocks())
                    .append(",connect=").append(rule.connect())
                    .append(",connectBlocks=").append(rule.connectBlocks())
                    .append(",connectTiles=").append(rule.connectTiles())
                    .append(",tintBlock=")
                    .append(rule.tintBlock()
                            .map(Object::toString)
                            .orElse("<none>"))
                    .append(",sources=")
                    .append(describeOverlaySources(rule, view, pos, direction))
                    .append('}');
        }
        return out.toString();
    }

    private static String describeOverlaySources(
            CtmRule rule,
            CtmMinecraftNeighborView view,
            BlockPos pos,
            Direction direction) {
        StringBuilder out = new StringBuilder(160);
        int face = direction.get3DDataValue();
        int[][] sides = overlaySideOffsets(face);
        out.append("sides[");
        boolean wrote = false;
        for (int i = 0; i < sides.length; i++) {
            int[] d = sides[i];
            if (!overlaySourceMatches(rule, view, d[0], d[1], d[2], face)) {
                continue;
            }
            if (wrote) {
                out.append(';');
            }
            appendSource(out, "side", i, pos, d, view, face);
            wrote = true;
        }
        if (!wrote) {
            out.append("<none>");
        }
        out.append("],diags[");
        wrote = false;
        int[][] diagonals = overlayEdgeOffsets(face);
        for (int i = 0; i < diagonals.length; i++) {
            int[] d = diagonals[i];
            if (!overlaySourceMatches(rule, view, d[0], d[1], d[2], face)) {
                continue;
            }
            if (wrote) {
                out.append(';');
            }
            appendSource(out, "diag", i, pos, d, view, face);
            wrote = true;
        }
        if (!wrote) {
            out.append("<none>");
        }
        out.append("],baseSides[");
        wrote = false;
        for (int i = 0; i < sides.length; i++) {
            int[] d = sides[i];
            if (!overlayBaseMatches(rule, view, d[0], d[1], d[2], face)) {
                continue;
            }
            if (wrote) {
                out.append(';');
            }
            appendSource(out, "base", i, pos, d, view, face);
            wrote = true;
        }
        if (!wrote) {
            out.append("<none>");
        }
        out.append(']');
        return out.toString();
    }

    private static void appendSource(StringBuilder out,
                                     String kind,
                                     int index,
                                     BlockPos pos,
                                     int[] offset,
                                     CtmMinecraftNeighborView view,
                                     int face) {
        out.append(kind)
                .append(index)
                .append('@')
                .append(pos.getX() + offset[0])
                .append(',')
                .append(pos.getY() + offset[1])
                .append(',')
                .append(pos.getZ() + offset[2])
                .append('=')
                .append(view.blockId(offset[0], offset[1], offset[2]));
        NamespaceId sprite = view.sprite(offset[0], offset[1], offset[2], face);
        if (sprite != null) {
            out.append('/').append(sprite);
        }
    }

    private static boolean overlaySourceMatches(CtmRule rule,
                                                CtmMinecraftNeighborView view,
                                                int dx,
                                                int dy,
                                                int dz,
                                                int face) {
        if (!view.isFullBlock(dx, dy, dz)) {
            return false;
        }
        String blockId = view.blockId(dx, dy, dz);
        NamespaceId sprite = view.sprite(dx, dy, dz, face);
        if (!rule.connectTiles().isEmpty()
                && !matchesAnyConnectTile(rule, sprite, blockId)) {
            return false;
        }
        if (!rule.connectBlocks().isEmpty()
                && !matchesAnyConnectBlock(rule, blockId)) {
            return false;
        }
        if (rule.connectTiles().isEmpty()
                && rule.connectBlocks().isEmpty()
                && !sameBlock(blockId, view.blockId(0, 0, 0))) {
            return false;
        }
        int[] normal = Faces.delta(face);
        if (view.isFullBlock(dx + normal[0], dy + normal[1],
                dz + normal[2])) {
            return false;
        }
        return !sameAsBaseUnderRule(rule, view, dx, dy, dz, face);
    }

    private static boolean overlayBaseMatches(CtmRule rule,
                                              CtmMinecraftNeighborView view,
                                              int dx,
                                              int dy,
                                              int dz,
                                              int face) {
        String blockId = view.blockId(dx, dy, dz);
        if (blockId == null) {
            return false;
        }
        if (!rule.matchBlocks().isEmpty()
                && !matchesAnyBlockSpec(rule.matchBlocks(), blockId)) {
            return false;
        }
        NamespaceId sprite = view.sprite(dx, dy, dz, face);
        if (!rule.matchTiles().isEmpty()
                && (sprite == null || !rule.matchTiles().contains(sprite))) {
            return false;
        }
        int[] normal = Faces.delta(face);
        return !view.isFullBlock(dx + normal[0], dy + normal[1],
                dz + normal[2]);
    }

    private static boolean sameAsBaseUnderRule(CtmRule rule,
                                               CtmMinecraftNeighborView view,
                                               int dx,
                                               int dy,
                                               int dz,
                                               int face) {
        String blockId = view.blockId(dx, dy, dz);
        String center = view.blockId(0, 0, 0);
        return switch (rule.connect()) {
            case BLOCK -> sameBlock(blockId, center);
            case STATE -> sameBlock(blockId, center)
                    && view.isFullBlock(dx, dy, dz)
                    == view.isFullBlock(0, 0, 0);
            case TILE -> {
                NamespaceId sprite = view.sprite(dx, dy, dz, face);
                NamespaceId centerSprite = view.sprite(0, 0, 0, face);
                yield sprite != null && sprite.equals(centerSprite);
            }
        };
    }

    private static String describeFaceNeighbours(
            CtmMinecraftNeighborView view,
            Direction direction) {
        int face = direction.get3DDataValue();
        int[] out = Faces.delta(face);
        StringBuilder text = new StringBuilder(192);
        text.append("out=").append(view.blockId(out[0], out[1], out[2]));
        int[][] sides = overlaySideOffsets(face);
        for (int i = 0; i < sides.length; i++) {
            int[] d = sides[i];
            text.append(",side").append(i).append('=')
                    .append(view.blockId(d[0], d[1], d[2]));
        }
        int[][] diagonals = overlayEdgeOffsets(face);
        for (int i = 0; i < diagonals.length; i++) {
            int[] d = diagonals[i];
            text.append(",diag").append(i).append('=')
                    .append(view.blockId(d[0], d[1], d[2]));
        }
        return text.toString();
    }

    private static int[][] overlaySideOffsets(int face) {
        return switch (face) {
            case Faces.DOWN -> new int[][] {
                    { -1, 0, 0 }, { 1, 0, 0 },
                    { 0, 0, -1 }, { 0, 0, 1 }
            };
            case Faces.UP -> new int[][] {
                    { -1, 0, 0 }, { 1, 0, 0 },
                    { 0, 0, 1 }, { 0, 0, -1 }
            };
            case Faces.NORTH -> new int[][] {
                    { 1, 0, 0 }, { -1, 0, 0 },
                    { 0, -1, 0 }, { 0, 1, 0 }
            };
            case Faces.SOUTH -> new int[][] {
                    { -1, 0, 0 }, { 1, 0, 0 },
                    { 0, -1, 0 }, { 0, 1, 0 }
            };
            case Faces.WEST -> new int[][] {
                    { 0, 0, -1 }, { 0, 0, 1 },
                    { 0, -1, 0 }, { 0, 1, 0 }
            };
            case Faces.EAST -> new int[][] {
                    { 0, 0, 1 }, { 0, 0, -1 },
                    { 0, -1, 0 }, { 0, 1, 0 }
            };
            default -> throw new IllegalArgumentException("bad face: " + face);
        };
    }

    private static int[][] overlayEdgeOffsets(int face) {
        return switch (face) {
            case Faces.DOWN -> new int[][] {
                    { 1, 0, -1 }, { -1, 0, -1 },
                    { 1, 0, 1 }, { -1, 0, 1 }
            };
            case Faces.UP -> new int[][] {
                    { 1, 0, 1 }, { -1, 0, 1 },
                    { 1, 0, -1 }, { -1, 0, -1 }
            };
            case Faces.NORTH -> new int[][] {
                    { -1, -1, 0 }, { 1, -1, 0 },
                    { -1, 1, 0 }, { 1, 1, 0 }
            };
            case Faces.SOUTH -> new int[][] {
                    { 1, -1, 0 }, { -1, -1, 0 },
                    { 1, 1, 0 }, { -1, 1, 0 }
            };
            case Faces.WEST -> new int[][] {
                    { 0, -1, 1 }, { 0, -1, -1 },
                    { 0, 1, 1 }, { 0, 1, -1 }
            };
            case Faces.EAST -> new int[][] {
                    { 0, -1, -1 }, { 0, -1, 1 },
                    { 0, 1, -1 }, { 0, 1, 1 }
            };
            default -> throw new IllegalArgumentException("bad face: " + face);
        };
    }

    private static void addUnique(List<String> values, String value) {
        for (String existing : values) {
            if (existing.equals(value)) {
                return;
            }
        }
        values.add(value);
    }

    private static void addUnique(List<NamespaceId> values, NamespaceId value) {
        for (NamespaceId existing : values) {
            if (existing.equals(value)) {
                return;
            }
        }
        values.add(value);
    }

    private static void appendSprites(StringBuilder out, List<String> sprites) {
        if (sprites.isEmpty()) {
            out.append("[]");
            return;
        }
        out.append('[');
        for (int i = 0; i < sprites.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(sprites.get(i));
        }
        out.append(']');
    }

    private static void appendCtm(StringBuilder out,
                                  @Nullable CtmRenderSelection selection,
                                  CtmMaterialTable materialTable) {
        if (selection == null) {
            out.append("none");
            return;
        }
        out.append(selection.method())
                .append("{tile=")
                .append(selection.primaryTileIndex())
                .append(",flags=")
                .append(selection.flags());
        appendMaterial(out, "primary", materialTable.find(
                selection.rule(), selection.primaryTileIndex()));
        if (selection.hasSecondaryTile()) {
            appendMaterial(out, "secondary", materialTable.find(
                    selection.rule(), selection.secondaryTileIndex()));
        }
        if (selection.isOverlay()) {
            out.append(",overlays=[");
            List<CtmOverlayTile> overlays = selection.overlayTiles();
            for (int i = 0; i < overlays.size(); i++) {
                if (i > 0) {
                    out.append(';');
                }
                CtmOverlayTile overlay = overlays.get(i);
                out.append(overlay.tileIndex());
                appendMaterial(out, "", materialTable.find(
                        overlay.rule(), overlay.tileIndex()));
            }
            out.append(']');
        }
        out.append('}');
    }

    private static void appendMaterial(StringBuilder out,
                                       String label,
                                       Optional<CtmMaterialEntry> material) {
        if (!label.isEmpty()) {
            out.append(',').append(label).append('=');
        } else {
            out.append(':');
        }
        if (material.isEmpty()) {
            out.append("missing");
            return;
        }
        CtmMaterialEntry entry = material.get();
        out.append('#')
                .append(entry.materialId())
                .append('@')
                .append(entry.sprite());
        if (entry.hasExplicitResource()) {
            out.append("+png");
        }
        if (entry.isGeneratedFallback()) {
            out.append("+generated");
        }
    }

    private static boolean matchesAnyConnectTile(CtmRule rule,
                                                 @Nullable NamespaceId sprite,
                                                 @Nullable String blockId) {
        if (sprite != null && rule.connectTiles().contains(sprite)) {
            return true;
        }
        if (blockId == null) {
            return false;
        }
        String namespace = namespaceOf(blockId);
        String name = nameOf(blockId);
        String basePath = "block/" + name;
        String compactBlockPath = name.endsWith("_block")
                ? "block/" + name.substring(0, name.length() - 6)
                : null;
        for (NamespaceId tile : rule.connectTiles()) {
            if (!tile.namespace().equals(namespace)) {
                continue;
            }
            String path = tile.path();
            if (path.equals(basePath)
                    || path.equals(compactBlockPath)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesAnyConnectBlock(CtmRule rule,
                                                  @Nullable String blockId) {
        return matchesAnyBlockSpec(rule.connectBlocks(), blockId);
    }

    private static boolean matchesAnyBlockSpec(List<BlockSpec> specs,
                                               @Nullable String blockId) {
        if (blockId == null) {
            return false;
        }
        String namespace = namespaceOf(blockId);
        String name = nameOf(blockId);
        for (BlockSpec spec : specs) {
            if (spec.namespace().equals(namespace)
                    && spec.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameBlock(@Nullable String left,
                                     @Nullable String right) {
        return left != null && left.equals(right);
    }

    private static String namespaceOf(String blockId) {
        int colon = blockId.indexOf(':');
        return colon < 0 ? "minecraft" : blockId.substring(0, colon);
    }

    private static String nameOf(String blockId) {
        int colon = blockId.indexOf(':');
        return colon < 0 ? blockId : blockId.substring(colon + 1);
    }

    private static String blockIdOf(BlockState state) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id == null ? "" : id.toString();
    }

    private static final class ScanScratch implements ListStorage {
        private final BlockPos.MutableBlockPos pos =
                new BlockPos.MutableBlockPos();
        private final RandomSource random = RandomSource.create(42L);
        private final ArrayList<String> sprites = new ArrayList<>(4);
        private final ArrayList<NamespaceId> spriteIds = new ArrayList<>(4);
        private final ArrayList<BlockStateModelPart> parts =
                new ArrayList<>(8);

        @Override
        public List<BlockStateModelPart> clearAndGet() {
            parts.clear();
            return parts;
        }
    }
}
