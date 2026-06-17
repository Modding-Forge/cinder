package com.argus.client.benchmark;

import com.argus.ctm.ConnectMode;
import com.argus.ctm.CtmCandidateScratch;
import com.argus.ctm.CtmConnectivityProfile;
import com.argus.ctm.CtmMethod;
import com.argus.ctm.CtmRule;
import com.argus.ctm.Faces;
import com.argus.resource.NamespaceId;
import com.argus.resource.RangeListInt;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Benchmark-only CTM candidate-set analyzer.
 *
 * <p>Purpose: records which face-filtered candidate arrays are actually used
 * during autopilot runs. CTM-v3 optimization work needs these facts before
 * introducing more selector fastpaths, because previous blind micro-optimizing
 * attempts were slower in real packs.
 *
 * <p>Threading: Sodium section-build workers may record concurrently. Entries
 * are immutable except for call counters.
 *
 * <p>Performance: gated by {@link ArgusBenchmark#enabled()}. Allocation is
 * accepted only for explicit dev benchmark runs.
 */
public final class CtmCandidateAnalysis {

    private static final int DEFAULT_LIMIT = 24;
    private static final ConcurrentMap<String, Entry> ENTRIES =
            new ConcurrentHashMap<>();

    private CtmCandidateAnalysis() {
    }

    /**
     * Clears candidate analysis counters for a new measured benchmark window.
     */
    public static void reset() {
        ENTRIES.clear();
    }

    /**
     * Records one positive CTM candidate lookup.
     */
    public static void record(String blockId,
                              NamespaceId baseSprite,
                              int face,
                              CtmCandidateScratch candidates) {
        if (!ArgusBenchmark.enabled() || candidates == null
                || !candidates.hasWork()) {
            return;
        }
        String key = key(blockId, baseSprite, face,
                candidates.spriteRules().length,
                candidates.blockRules().length);
        ENTRIES.computeIfAbsent(key, ignored -> new Entry(
                blockId,
                baseSprite == null ? "<none>" : baseSprite.toString(),
                faceName(face),
                candidates.spriteRules(),
                candidates.blockRules()))
                .calls
                .increment();
    }

    /**
     * Returns the most frequently used candidate sets.
     */
    public static Snapshot[] topSnapshots() {
        return topSnapshots(Integer.getInteger(
                "argus.benchmark.ctmCandidateTop", DEFAULT_LIMIT));
    }

    /**
     * Returns the most frequently used candidate sets.
     */
    public static Snapshot[] topSnapshots(int limit) {
        if (limit <= 0 || ENTRIES.isEmpty()) {
            return new Snapshot[0];
        }
        ArrayList<Snapshot> snapshots = new ArrayList<>(ENTRIES.size());
        for (Entry entry : ENTRIES.values()) {
            snapshots.add(entry.snapshot());
        }
        snapshots.sort((left, right) -> {
            int byCalls = Long.compare(right.calls(), left.calls());
            if (byCalls != 0) {
                return byCalls;
            }
            int byRules = Integer.compare(right.totalRules(),
                    left.totalRules());
            if (byRules != 0) {
                return byRules;
            }
            return left.key().compareTo(right.key());
        });
        int count = Math.min(limit, snapshots.size());
        Snapshot[] out = new Snapshot[count];
        for (int i = 0; i < count; i++) {
            out[i] = snapshots.get(i);
        }
        return out;
    }

    private static String key(String blockId,
                              NamespaceId baseSprite,
                              int face,
                              int spriteRules,
                              int blockRules) {
        return (blockId == null ? "<none>" : blockId)
                + '|'
                + (baseSprite == null ? "<none>" : baseSprite)
                + '|'
                + face
                + '|'
                + spriteRules
                + '|'
                + blockRules;
    }

    private static String faceName(int face) {
        return switch (face) {
            case Faces.DOWN -> "down";
            case Faces.UP -> "up";
            case Faces.NORTH -> "north";
            case Faces.SOUTH -> "south";
            case Faces.WEST -> "west";
            case Faces.EAST -> "east";
            default -> "unknown";
        };
    }

    private static String methodSummary(CtmRule[] spriteRules,
                                        CtmRule[] blockRules) {
        EnumMap<CtmMethod, Integer> counts = new EnumMap<>(CtmMethod.class);
        addMethods(counts, spriteRules);
        addMethods(counts, blockRules);
        return enumSummary(counts);
    }

    private static void addMethods(EnumMap<CtmMethod, Integer> counts,
                                   CtmRule[] rules) {
        for (CtmRule rule : rules) {
            counts.merge(rule.method(), 1, Integer::sum);
        }
    }

    private static String connectivitySummary(CtmRule[] spriteRules,
                                              CtmRule[] blockRules) {
        EnumMap<CtmConnectivityProfile, Integer> counts =
                new EnumMap<>(CtmConnectivityProfile.class);
        addConnectivity(counts, spriteRules);
        addConnectivity(counts, blockRules);
        return enumSummary(counts);
    }

    private static void addConnectivity(
            EnumMap<CtmConnectivityProfile, Integer> counts,
            CtmRule[] rules) {
        for (CtmRule rule : rules) {
            counts.merge(rule.runtimeProfile().connectivity(), 1,
                    Integer::sum);
        }
    }

    private static String connectSummary(CtmRule[] spriteRules,
                                         CtmRule[] blockRules) {
        EnumMap<ConnectMode, Integer> counts = new EnumMap<>(ConnectMode.class);
        addConnectModes(counts, spriteRules);
        addConnectModes(counts, blockRules);
        return enumSummary(counts);
    }

    private static void addConnectModes(EnumMap<ConnectMode, Integer> counts,
                                        CtmRule[] rules) {
        for (CtmRule rule : rules) {
            counts.merge(rule.connect(), 1, Integer::sum);
        }
    }

    private static <E extends Enum<E>> String enumSummary(EnumMap<E, Integer> counts) {
        if (counts.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(64);
        boolean first = true;
        for (java.util.Map.Entry<E, Integer> entry : counts.entrySet()) {
            if (!first) {
                out.append(',');
            }
            out.append(entry.getKey().name().toLowerCase(Locale.ROOT))
                    .append(':')
                    .append(entry.getValue());
            first = false;
        }
        return out.toString();
    }

    private static int overlayRules(CtmRule[] spriteRules,
                                    CtmRule[] blockRules) {
        return overlayRules(spriteRules) + overlayRules(blockRules);
    }

    private static int overlayRules(CtmRule[] rules) {
        int count = 0;
        for (CtmRule rule : rules) {
            if (rule.runtimeProfile().isOverlay()) {
                count++;
            }
        }
        return count;
    }

    private static String conditionSummary(CtmRule[] spriteRules,
                                           CtmRule[] blockRules) {
        ConditionCounts counts = new ConditionCounts();
        addConditions(counts, spriteRules);
        addConditions(counts, blockRules);
        return counts.summary();
    }

    private static void addConditions(ConditionCounts counts, CtmRule[] rules) {
        for (CtmRule rule : rules) {
            if (!rule.biomes().isEmpty()) {
                counts.biomes++;
            }
            if (rule.heights() != RangeListInt.ALL) {
                counts.heights++;
            }
            if (!rule.connectTiles().isEmpty()) {
                counts.connectTiles++;
            }
            if (!rule.connectBlocks().isEmpty()) {
                counts.connectBlocks++;
            }
            if (!rule.matchTiles().isEmpty()) {
                counts.matchTiles++;
            }
            if (!rule.matchBlocks().isEmpty()) {
                counts.matchBlocks++;
            }
        }
    }

    private static String firstSource(CtmRule[] spriteRules,
                                      CtmRule[] blockRules) {
        if (spriteRules.length != 0) {
            return spriteRules[0].sourceFile().orElse("");
        }
        if (blockRules.length != 0) {
            return blockRules[0].sourceFile().orElse("");
        }
        return "";
    }

    private static final class Entry {
        private final String key;
        private final String blockId;
        private final String sprite;
        private final String face;
        private final int spriteRules;
        private final int blockRules;
        private final int overlayRules;
        private final String methods;
        private final String connectivity;
        private final String connectModes;
        private final String conditions;
        private final String firstSource;
        private final LongAdder calls = new LongAdder();

        private Entry(String blockId,
                      String sprite,
                      String face,
                      CtmRule[] spriteRules,
                      CtmRule[] blockRules) {
            this.key = blockId + "|" + sprite + "|" + face + "|"
                    + spriteRules.length + "|" + blockRules.length;
            this.blockId = blockId == null ? "" : blockId;
            this.sprite = sprite;
            this.face = face;
            this.spriteRules = spriteRules.length;
            this.blockRules = blockRules.length;
            this.overlayRules = overlayRules(spriteRules, blockRules);
            this.methods = methodSummary(spriteRules, blockRules);
            this.connectivity = connectivitySummary(spriteRules, blockRules);
            this.connectModes = connectSummary(spriteRules, blockRules);
            this.conditions = conditionSummary(spriteRules, blockRules);
            this.firstSource = firstSource(spriteRules, blockRules);
        }

        private Snapshot snapshot() {
            int totalRules = spriteRules + blockRules;
            return new Snapshot(
                    key,
                    calls.sum(),
                    blockId,
                    sprite,
                    face,
                    spriteRules,
                    blockRules,
                    totalRules,
                    overlayRules,
                    totalRules == 0
                            ? 0.0D
                            : (double) overlayRules / totalRules,
                    methods,
                    connectivity,
                    connectModes,
                    conditions,
                    firstSource);
        }
    }

    private static final class ConditionCounts {
        private int biomes;
        private int heights;
        private int connectTiles;
        private int connectBlocks;
        private int matchTiles;
        private int matchBlocks;

        private String summary() {
            StringBuilder out = new StringBuilder(80);
            append(out, "biomes", biomes);
            append(out, "heights", heights);
            append(out, "connectTiles", connectTiles);
            append(out, "connectBlocks", connectBlocks);
            append(out, "matchTiles", matchTiles);
            append(out, "matchBlocks", matchBlocks);
            return out.toString();
        }

        private static void append(StringBuilder out, String name, int count) {
            if (count == 0) {
                return;
            }
            if (!out.isEmpty()) {
                out.append(',');
            }
            out.append(name).append(':').append(count);
        }
    }

    /**
     * Immutable candidate-analysis row for benchmark reports.
     */
    public record Snapshot(
            String key,
            long calls,
            String blockId,
            String sprite,
            String face,
            int spriteRules,
            int blockRules,
            int totalRules,
            int overlayRules,
            double overlayShare,
            String methods,
            String connectivity,
            String connectModes,
            String conditions,
            String firstSource) {
    }
}
