package com.cinder.fabric.atlas;

import com.cinder.Constants;
import com.cinder.cit.CitParseResult;
import com.cinder.cit.CitReplacement;
import com.cinder.cit.CitRule;
import com.cinder.cit.CitRuleParser;
import com.cinder.resource.NamespaceId;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Item-atlas sprite source for OptiFine CIT replacement textures.
 *
 * <p>Purpose: CIT textures live under {@code optifine/cit}, outside vanilla's
 * normal {@code textures/} folder convention. This source injects them into
 * the item atlas before item rendering remaps quads.
 *
 * <p>Threading: called by Mojang's atlas reload on the resource thread.
 */
public record CinderItemSpriteSource() implements SpriteSource {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(Constants.MOD_ID + "/item-source");

    public static final Identifier TYPE_ID =
            Identifier.fromNamespaceAndPath(Constants.MOD_ID, "cit_items");

    public static final MapCodec<CinderItemSpriteSource> MAP_CODEC =
            MapCodec.unit(CinderItemSpriteSource::new);

    private static final String OPTIFINE_CIT = "optifine/cit";

    @Override
    public void run(ResourceManager resourceManager, Output output) {
        ArrayList<CitRuleParser.RuleSource> sources = new ArrayList<>();
        for (Identifier loc : resourceManager
                .listResources(OPTIFINE_CIT,
                        id -> id.getPath().endsWith(".properties"))
                .keySet()) {
            Optional<Resource> resource = resourceManager.getResource(loc);
            if (resource.isEmpty()) {
                continue;
            }
            try (var in = resource.get().open();
                 var reader = new InputStreamReader(
                         in, StandardCharsets.UTF_8)) {
                sources.add(new CitRuleParser.RuleSource(
                        readAll(reader), loc.toString()));
            } catch (Exception e) {
                LOGGER.warn("[{}] failed to read CIT sprite rule {}: {}",
                        Constants.MOD_NAME, loc, e.getMessage());
            }
        }
        CitParseResult result = CitRuleParser.parseAll(sources);
        Set<Identifier> emitted = new HashSet<>();
        int added = 0;
        for (CitRule rule : result.rules()) {
            CitReplacement replacement = rule.replacement();
            added += addTexture(resourceManager, output, emitted,
                    replacement.texture());
            for (NamespaceId texture : replacement.namedTextures().values()) {
                added += addTexture(resourceManager, output, emitted, texture);
            }
        }
        if (added > 0) {
            LOGGER.info("[{}] injected {} CIT sprites into the item atlas",
                    Constants.MOD_NAME, added);
        }
    }

    @Override
    public MapCodec<CinderItemSpriteSource> codec() {
        return MAP_CODEC;
    }

    private static int addTexture(ResourceManager rm,
                                  Output output,
                                  Set<Identifier> emitted,
                                  NamespaceId texture) {
        if (texture == null) {
            return 0;
        }
        Identifier spriteId = Identifier.fromNamespaceAndPath(
                texture.namespace(), texture.path());
        if (!emitted.add(spriteId)) {
            return 0;
        }
        Identifier directId = Identifier.fromNamespaceAndPath(
                texture.namespace(), texture.path() + ".png");
        Optional<Resource> resource = rm.getResource(directId);
        if (resource.isEmpty()) {
            Identifier textureId = SpriteSource.TEXTURE_ID_CONVERTER
                    .idToFile(spriteId);
            resource = rm.getResource(textureId);
        }
        if (resource.isEmpty()) {
            return 0;
        }
        output.add(spriteId, resource.get());
        return 1;
    }

    private static String readAll(java.io.Reader reader)
            throws java.io.IOException {
        StringBuilder out = new StringBuilder();
        char[] buf = new char[1024];
        int n;
        while ((n = reader.read(buf)) > 0) {
            out.append(buf, 0, n);
        }
        return out.toString();
    }
}
