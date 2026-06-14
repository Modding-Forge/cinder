package com.cinder.ctm;

import com.cinder.resource.ComponentMatchers;
import com.cinder.resource.NamespaceId;
import com.cinder.resource.PropertiesFile;
import com.cinder.resource.RangeListInt;
import com.cinder.resource.ResourcePath;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parser for a single OptiFine-style CTM {@code .properties} file.
 *
 * <p>The output is a single {@link CtmRule} (the {@code .properties}
 * format describes one rule per file) plus a {@link CtmRuleSet}
 * convenience that contains just that rule.
 *
 * <p>Behaviour is derived from the OF documentation at
 * {@code optifine/OptiFineDoc/doc/ctm.properties}. Where the doc
 * describes defaults and parsing rules, those are reproduced here.
 * Where the OF code makes a choice (e.g. the order in which
 * duplicate keys are handled), we make our own.
 */
public final class CtmProperties {

    private CtmProperties() {
    }

    /**
     * Parses a CTM properties file.
     *
     * @param props    the parsed key/value store
     * @param parent   the parent directory identifier, used for
     *                 resolving relative tile paths
     * @param src      a free-form source label, used in error messages
     *                 and as the rule's {@code sourceFile} for priority
     *                 ordering
     * @return a parsed rule
     * @throws IllegalArgumentException on malformed input
     */
    public static CtmRule parse(PropertiesFile props, NamespaceId parent, String src) {
        String methodKey = props.get("method");
        if (methodKey == null) {
            throw new IllegalArgumentException("missing 'method' in " + src);
        }
        CtmMethod method = CtmMethod.fromKey(methodKey);
        if (method == null) {
            throw new IllegalArgumentException(
                    "unknown CTM method '" + methodKey + "' in " + src);
        }

        CtmRule.Builder b = CtmRule.builder()
                .method(method)
                .sourceFile(src)
                .sourceLine(0);

        // matchTiles / matchBlocks
        for (String s : props.getTokens("matchTiles")) {
            b.addMatchTile(parseMatchTile(s, parent));
        }
        for (String s : props.getTokens("matchBlocks")) {
            b.addMatchBlock(BlockSpec.parse(s));
        }
        // Default-inference from filename:
        //   - "optifine/ctm/<dir>/<name>.properties"   -> matchTiles=<name>
        //   - "optifine/ctm/<dir>/block_<name>.properties" -> matchBlocks=<name>
        if (b.matchTiles().isEmpty() && b.matchBlocks().isEmpty() && src != null) {
            int slash = src.lastIndexOf('/');
            String base = slash < 0 ? src : src.substring(slash + 1);
            if (base.endsWith(".properties")) {
                base = base.substring(0, base.length() - ".properties".length());
            }
            if (base.startsWith("block_")) {
                b.addMatchBlock(BlockSpec.parse(
                        NamespaceId.DEFAULT_NAMESPACE + ":" + base.substring("block_".length())));
            } else {
                b.addMatchTile(new NamespaceId(NamespaceId.DEFAULT_NAMESPACE, "block/" + base));
            }
        }

        // connect
        String connectKey = props.get("connect", "block");
        b.connect(parseConnect(connectKey, b));
        for (String s : props.getTokens("connectTiles")) {
            b.addConnectTile(parseConnectTile(s));
        }
        for (String s : props.getTokens("connectBlocks")) {
            b.addConnectBlock(BlockSpec.parse(s));
        }

        // faces
        String faces = props.get("faces", "all");
        b.facesMask(parseFaces(faces));

        // biomes. OptiFine uses comma-separated biome lists
        // (e.g. "plains,forest,desert"); whitespace-only
        // splitting would leave the commas in place and the
        // selector's contains() check would never match. We
        // therefore split on either commas or whitespace here.
        String biomesSpec = props.get("biomes");
        if (biomesSpec != null) {
            for (String s : biomesSpec.split("[,\\s]+")) {
                if (!s.isEmpty()) {
                    b.addBiome(s);
                }
            }
        }

        // heights
        String heightsSpec = props.get("heights");
        if (heightsSpec != null) {
            b.heights(RangeListInt.parse(heightsSpec));
        }

        // tiles
        String tilesSpec = props.get("tiles");
        if (tilesSpec == null) {
            throw new IllegalArgumentException("missing 'tiles' in " + src);
        }
        for (String tok : tilesSpec.trim().split("\\s+")) {
            if (tok.isEmpty()) {
                continue;
            }
            // Special tokens <skip> and <default> start with '<'
            // and must always be forwarded to CtmTileSpec.fromSpec,
            // not to the numeric-range path. The range path
            // would otherwise treat a token like "<default>" as
            // a range (its '-' is at position 1) and silently
            // fall back to a sprite name, breaking the skip /
            // default sentinels.
            if (tok.charAt(0) == '<') {
                b.addTile(CtmTileSpec.fromSpec(
                        tok, NamespaceId.DEFAULT_NAMESPACE, parent));
                continue;
            }
            // Expand a numeric range like "0-46" into a sequence of
            // individual numeric CtmTileSpec entries. A range token
            // contains exactly one '-' that is not at either end and
            // is not surrounded by parens; it is recognised by
            // attempting Integer.parseInt on each half.
            int dash = findRangeDash(tok);
            if (dash > 0) {
                int start;
                int end;
                try {
                    start = Integer.parseInt(tok.substring(0, dash));
                    end = Integer.parseInt(tok.substring(dash + 1));
                } catch (NumberFormatException ignored) {
                    // Not a numeric range; treat as a sprite name.
                    b.addTile(CtmTileSpec.fromSpec(
                            tok, NamespaceId.DEFAULT_NAMESPACE, parent));
                    continue;
                }
                if (start > end) {
                    throw new IllegalArgumentException(
                            "tiles range has start > end: " + tok);
                }
                for (int i = start; i <= end; i++) {
                    b.addTile(CtmTileSpec.numeric(i));
                }
            } else {
                b.addTile(CtmTileSpec.fromSpec(
                        tok, NamespaceId.DEFAULT_NAMESPACE, parent));
            }
        }

        // weight
        String weightSpec = props.get("weight");
        if (weightSpec != null) {
            b.weight(Integer.parseInt(weightSpec));
        }

        // width / height (REPEAT)
        if (method == CtmMethod.REPEAT) {
            String w = props.get("width");
            String h = props.get("height");
            if (w == null || h == null) {
                throw new IllegalArgumentException(
                        "method=repeat requires width and height in " + src);
            }
            b.width(Integer.parseInt(w));
            b.height(Integer.parseInt(h));
        }

        // weights (RANDOM)
        if (method == CtmMethod.RANDOM) {
            String w = props.get("weights");
            if (w != null) {
                List<Integer> ws = new ArrayList<>();
                for (String tok : w.trim().split("\\s+")) {
                    ws.add(Integer.parseInt(tok));
                }
                b.randomWeights(ws);
            }
        }

        // innerSeams
        b.innerSeams(Boolean.parseBoolean(props.get("innerSeams", "false")));

        String tintIndexSpec = props.get("tintIndex");
        if (tintIndexSpec != null) {
            b.tintIndex(Integer.parseInt(tintIndexSpec.trim()));
        }
        String tintBlockSpec = props.get("tintBlock");
        if (tintBlockSpec != null && !tintBlockSpec.trim().isEmpty()) {
            b.tintBlock(BlockSpec.parse(tintBlockSpec.trim()));
        }

        // ctm.<n>=<tileIndex> overrides (CTM_COMPACT)
        if (method == CtmMethod.CTM_COMPACT) {
            int[] overrides = new int[47];
            for (int i = 0; i < overrides.length; i++) {
                overrides[i] = -1;
            }
            for (var entry : props.entries().entrySet()) {
                String k = entry.getKey();
                if (k != null && k.startsWith("ctm.")) {
                    int idx;
                    try {
                        idx = Integer.parseInt(k.substring(4));
                    } catch (NumberFormatException ignored) {
                        continue;
                    }
                    if (idx >= 0 && idx < overrides.length) {
                        overrides[idx] = Integer.parseInt(entry.getValue());
                    }
                }
            }
            b.ctmOverrides(overrides);
        }

        // name (block-entity custom name matcher; parse as plain string
        // matchers for now, deferred to fabric/ adapter for NBT walk).
        String nameSpec = props.get("name");
        if (nameSpec != null) {
            for (ComponentMatchers.Compiled m : ComponentMatchers.parseList(nameSpec)) {
                b.addNbtMatcher(m);
            }
        }

        return b.build();
    }

    /**
     * Locates the dash that separates two integers in a token such
     * as {@code "0-46"}. Returns {@code -1} if the token is not a
     * numeric range.
     *
     * <p>Heuristics: a dash at position 0 (negative number) or at
     * the very end is not a range separator. The token is a range
     * only if both halves are valid integer strings.
     */
    private static int findRangeDash(String tok) {
        if (tok.isEmpty() || tok.equals("-")) {
            return -1;
        }
        int dash = tok.indexOf('-');
        if (dash <= 0 || dash == tok.length() - 1) {
            return -1;
        }
        return dash;
    }

    private static ConnectMode parseConnect(String spec, CtmRule.Builder b) {
        switch (spec.toLowerCase(Locale.ROOT)) {
            case "block": return ConnectMode.BLOCK;
            case "tile":  return ConnectMode.TILE;
            case "state": return ConnectMode.STATE;
            default:
                throw new IllegalArgumentException("unknown connect value: " + spec);
        }
    }

    private static NamespaceId parseConnectTile(String spec) {
        String trimmed = spec.trim();
        if (!trimmed.contains("/")
                && !trimmed.contains(":")
                && !trimmed.contains(".")
                && !trimmed.startsWith("textures/")) {
            return new NamespaceId(NamespaceId.DEFAULT_NAMESPACE,
                    "block/" + trimmed);
        }
        CtmTileSpec tile = CtmTileSpec.fromSpec(spec,
                NamespaceId.DEFAULT_NAMESPACE, null);
        NamespaceId sprite = tile.resolvedSprite();
        if (sprite != null) {
            return sprite;
        }
        return new NamespaceId(NamespaceId.DEFAULT_NAMESPACE, "block/" + spec);
    }

    private static NamespaceId parseMatchTile(String spec,
                                              NamespaceId parent) {
        String trimmed = spec.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("empty matchTiles entry");
        }
        if (isBareSpriteName(trimmed)) {
            return new NamespaceId(NamespaceId.DEFAULT_NAMESPACE,
                    "block/" + trimmed);
        }
        int colon = trimmed.indexOf(':');
        if (colon > 0
                && trimmed.indexOf(':', colon + 1) < 0
                && trimmed.indexOf('/') < 0
                && trimmed.indexOf('.') < 0) {
            return new NamespaceId(trimmed.substring(0, colon),
                    "block/" + trimmed.substring(colon + 1));
        }
        return ResourcePath.resolveOptifine(trimmed,
                NamespaceId.DEFAULT_NAMESPACE, parent);
    }

    private static boolean isBareSpriteName(String spec) {
        return !spec.contains("/")
                && !spec.contains(":")
                && !spec.contains(".")
                && !spec.startsWith("textures/")
                && !spec.startsWith("./")
                && !spec.startsWith("~/");
    }

    private static int parseFaces(String spec) {
        // Bit positions: 0=D, 1=U, 2=N, 3=S, 4=W, 5=E.
        int mask = 0;
        for (String s : spec.toLowerCase(Locale.ROOT).split("\\s+")) {
            switch (s) {
                case "all":   return 0;
                case "top":   mask |= 1 << 1; break;
                case "bottom":mask |= 1 << 0; break;
                case "north": mask |= 1 << 2; break;
                case "south": mask |= 1 << 3; break;
                case "west":  mask |= 1 << 4; break;
                case "east":  mask |= 1 << 5; break;
                case "sides": mask |= (1 << 2) | (1 << 3) | (1 << 4) | (1 << 5); break;
                default:
                    throw new IllegalArgumentException("unknown face: " + s);
            }
        }
        return mask;
    }
}
