package com.argus.client.benchmark;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.MemoryUsage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Dev-only in-game benchmark driver for repeatable loader comparisons.
 *
 * <p>Purpose: when explicitly enabled with
 * {@code -Dargus.benchmark.autopilot=true}, opens a local world, closes menus,
 * rotates the camera, and holds movement/use/attack keys through a deterministic
 * route. Before each run it recreates one dedicated benchmark world from
 * scratch with a stable seed, so Fabric and NeoForge compare the same fresh
 * world-generation and render workload instead of inheriting previous save
 * state. By default it also closes the client after the last sample, so Gradle
 * benchmark runs return without manual cleanup.
 *
 * <p>Threading: called from the client tick event on the client thread only.
 *
 * <p>Performance: disabled cost is one static-final boolean branch. This class
 * is intended for development runs, not release benchmarking.
 */
public final class ArgusBenchmarkDriver {

    private static final boolean ENABLED =
            Boolean.getBoolean("argus.benchmark.autopilot");
    private static final Logger LOGGER =
            LoggerFactory.getLogger("argus/benchmark-driver");
    private static final int MAX_TICKS = Integer.getInteger(
            "argus.benchmark.autopilotTicks", 900);
    private static final boolean CLOSE_ON_COMPLETE = Boolean.parseBoolean(
            System.getProperty("argus.benchmark.closeOnComplete", "true"));
    private static final int CLOSE_DELAY_TICKS = Integer.getInteger(
            "argus.benchmark.closeDelayTicks", 40);
    private static final int SETTLE_TICKS = Integer.getInteger(
            "argus.benchmark.settleTicks", 100);
    private static final double TARGET_X = Double.parseDouble(
            System.getProperty("argus.benchmark.targetX", "0.0"));
    private static final double TARGET_Y = Double.parseDouble(
            System.getProperty("argus.benchmark.targetY", "125.0"));
    private static final double TARGET_Z = Double.parseDouble(
            System.getProperty("argus.benchmark.targetZ", "0.0"));
    private static final int LOG_INTERVAL_TICKS = 100;
    private static final int LOW_FPS_THRESHOLD = Integer.getInteger(
            "argus.benchmark.lowFpsThreshold", 120);
    private static final int LOW_FPS_SAMPLE_LIMIT = Integer.getInteger(
            "argus.benchmark.lowFpsSampleLimit", 24);
    private static final String DEFAULT_WORLD = "ArgusBenchmark";
    private static final long DEFAULT_SEED = 329562103L;
    private static final String METHODOLOGY =
            "Argus dev autopilot recreates a fixed-seed local world, "
                    + "enters it, teleports the player to configured benchmark "
                    + "coordinates, waits for the settle window so the player "
                    + "lands and nearby chunks can build, resets benchmark "
                    + "buckets, then drives deterministic camera and movement "
                    + "input for the configured tick count while Argus "
                    + "renderer buckets and per-tick FPS are sampled.";
    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                    .withZone(ZoneOffset.UTC);

    private static boolean openAttempted;
    private static boolean started;
    private static boolean teleported;
    private static boolean complete;
    private static boolean reportWritten;
    private static boolean closing;
    private static int closeDelayTicks;
    private static int settleTicks;
    private static int ticks;
    private static int fpsMin = Integer.MAX_VALUE;
    private static int fpsMax;
    private static long fpsSum;
    private static int fpsSamples;
    private static int[] fpsValues = new int[Math.max(1, MAX_TICKS)];
    private static int lowFpsSamples;
    private static int lowFpsSegments;
    private static boolean inLowFpsSegment;
    private static int currentLowFpsSegmentStart;
    private static int worstFpsTick;
    private static int worstFps = Integer.MAX_VALUE;
    private static int[] lowFpsTicks = new int[Math.max(1, LOW_FPS_SAMPLE_LIMIT)];
    private static int[] lowFpsValues = new int[Math.max(1, LOW_FPS_SAMPLE_LIMIT)];
    private static String[] lowFpsPositions = new String[Math.max(1, LOW_FPS_SAMPLE_LIMIT)];
    private static double[] lowFpsProcessQuadMs = new double[Math.max(1, LOW_FPS_SAMPLE_LIMIT)];
    private static double[] lowFpsCtmMs = new double[Math.max(1, LOW_FPS_SAMPLE_LIMIT)];
    private static double[] lowFpsResolveMs = new double[Math.max(1, LOW_FPS_SAMPLE_LIMIT)];
    private static long[] lowFpsGcCount = new long[Math.max(1, LOW_FPS_SAMPLE_LIMIT)];
    private static long[] lowFpsGcMillis = new long[Math.max(1, LOW_FPS_SAMPLE_LIMIT)];
    private static int lowFpsStoredSamples;
    private static long startGcCount;
    private static long startGcMillis;
    private static long startHeapUsed;
    private static long peakHeapUsed;

    private ArgusBenchmarkDriver() {
    }

    /**
     * Advances the benchmark driver by one client tick.
     *
     * @param minecraft active client instance
     */
    public static void tick(Minecraft minecraft) {
        if (!ENABLED || minecraft == null) {
            return;
        }
        if (complete) {
            closeAfterCompletion(minecraft);
            return;
        }
        if (minecraft.level == null || minecraft.player == null) {
            openWorldIfNeeded(minecraft);
            return;
        }

        if (!teleported) {
            teleportToBenchmarkPosition(minecraft);
            return;
        }
        if (settleTicks > 0) {
            settleBeforeBenchmark(minecraft);
            return;
        }

        if (!started) {
            started = true;
            ticks = 0;
            resetFpsSamples();
            ArgusBenchmark.resetTotals();
            captureRuntimeBaselines();
            LOGGER.info("[Argus] benchmark-driver started world={} pos={}",
                    worldLabel(minecraft), positionLabel(minecraft.player));
        }

        ticks++;
        if (minecraft.gui.screen() != null) {
            minecraft.gui.setScreen(null);
        }

        driveCamera(minecraft.player, ticks);
        driveMovement(minecraft, ticks);
        sampleFps(minecraft);

        if (ticks % LOG_INTERVAL_TICKS == 0) {
            LOGGER.info(
                    "[Argus] benchmark-driver tick={} fpsCurrent={} fpsMin={} fpsAvg={} fpsMax={} pos={}",
                    ticks,
                    minecraft.getFps(),
                    fpsMin == Integer.MAX_VALUE ? 0 : fpsMin,
                    fpsSamples == 0 ? 0 : fpsSum / fpsSamples,
                    fpsMax,
                    positionLabel(minecraft.player));
        }

        if (ticks >= MAX_TICKS) {
            releaseKeys(minecraft);
            complete = true;
            closeDelayTicks = CLOSE_DELAY_TICKS;
            writeReport(minecraft);
            LOGGER.info(
                    "[Argus] benchmark-driver complete ticks={} fpsMin={} fpsAvg={} fpsMax={} samples={} closeOnComplete={}",
                    ticks,
                    fpsMin == Integer.MAX_VALUE ? 0 : fpsMin,
                    fpsSamples == 0 ? 0 : fpsSum / fpsSamples,
                    fpsMax,
                    fpsSamples,
                    CLOSE_ON_COMPLETE);
        }
    }

    private static void closeAfterCompletion(Minecraft minecraft) {
        if (!CLOSE_ON_COMPLETE || closing) {
            return;
        }
        if (closeDelayTicks > 0) {
            closeDelayTicks--;
            return;
        }
        closing = true;
        releaseKeys(minecraft);
        LOGGER.info("[Argus] benchmark-driver requesting client stop");
        minecraft.stop();
    }

    private static void openWorldIfNeeded(Minecraft minecraft) {
        if (openAttempted) {
            return;
        }
        String world = configuredWorld();
        if (world == null || world.isBlank()) {
            openAttempted = true;
            LOGGER.warn("[Argus] benchmark-driver has no configured world id");
            return;
        }
        openAttempted = true;
        recreateBenchmarkWorld(minecraft, world);
    }

    private static void recreateBenchmarkWorld(Minecraft minecraft,
                                                String world) {
        Path saves = minecraft.getLevelSource()
                .getBaseDir()
                .toAbsolutePath()
                .normalize();
        Path target = saves.resolve(world).toAbsolutePath().normalize();
        if (!target.startsWith(saves)) {
            LOGGER.warn(
                    "[Argus] benchmark-driver rejected unsafe world id={}",
                    world);
            return;
        }
        try {
            deleteDirectory(target);
        } catch (IOException e) {
            LOGGER.warn(
                    "[Argus] benchmark-driver failed to reset world={} path={}",
                    world, target, e);
            return;
        }

        long seed = configuredSeed();
        LevelSettings settings = new LevelSettings(
                world,
                GameType.CREATIVE,
                new LevelSettings.DifficultySettings(
                        Difficulty.PEACEFUL, false, false),
                true,
                WorldDataConfiguration.DEFAULT);
        WorldOptions options = new WorldOptions(seed, true, false);
        LOGGER.info(
                "[Argus] benchmark-driver creating fresh world={} seed={} path={}",
                world, seed, target);
        minecraft.createWorldOpenFlows()
                .createFreshLevel(world, settings, options,
                        WorldPresets::createNormalWorldDimensions,
                        new TitleScreen());
    }

    private static void teleportToBenchmarkPosition(Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }
        teleported = true;
        settleTicks = SETTLE_TICKS;
        releaseKeys(minecraft);
        if (minecraft.gui.screen() != null) {
            minecraft.gui.setScreen(null);
        }
        String command = String.format(Locale.ROOT, "tp @s %.3f %.3f %.3f",
                TARGET_X, TARGET_Y, TARGET_Z);
        minecraft.player.connection.sendCommand(command);
        LOGGER.info(
                "[Argus] benchmark-driver teleport command='{}' settleTicks={}",
                command, settleTicks);
    }

    private static void settleBeforeBenchmark(Minecraft minecraft) {
        releaseKeys(minecraft);
        if (minecraft.gui.screen() != null) {
            minecraft.gui.setScreen(null);
        }
        settleTicks--;
        if (settleTicks == 0 && minecraft.player != null) {
            LOGGER.info("[Argus] benchmark-driver settle complete pos={}",
                    positionLabel(minecraft.player));
        }
    }

    private static String configuredWorld() {
        String configured = System.getProperty("argus.benchmark.world", "")
                .trim();
        if (configured.isEmpty()) {
            configured = DEFAULT_WORLD;
        }
        return configured.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static long configuredSeed() {
        String configured = System.getProperty("argus.benchmark.seed", "")
                .trim();
        if (configured.isEmpty()) {
            return DEFAULT_SEED;
        }
        return WorldOptions.parseSeed(configured).orElse(DEFAULT_SEED);
    }

    private static void deleteDirectory(Path target) throws IOException {
        if (!Files.exists(target)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(target)) {
            Path[] ordered = paths
                    .sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new);
            for (Path path : ordered) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void driveCamera(LocalPlayer player, int tick) {
        float yaw = player.getYRot() + 2.0F;
        float pitch = -8.0F + (float) Math.sin(tick / 35.0D) * 18.0F;
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.setXRot(pitch);
    }

    private static void driveMovement(Minecraft minecraft, int tick) {
        boolean forward = tick < MAX_TICKS - 80;
        boolean right = tick % 240 < 120;
        boolean left = !right && tick < MAX_TICKS - 80;
        boolean jump = tick % 90 < 8;
        boolean use = tick > 100 && tick % 70 < 5;
        boolean attack = tick > 140 && tick % 110 < 5;

        minecraft.options.keyUp.setDown(forward);
        minecraft.options.keyRight.setDown(right);
        minecraft.options.keyLeft.setDown(left);
        minecraft.options.keyDown.setDown(false);
        minecraft.options.keyJump.setDown(jump);
        minecraft.options.keyUse.setDown(use);
        minecraft.options.keyAttack.setDown(attack);
    }

    private static void releaseKeys(Minecraft minecraft) {
        minecraft.options.keyUp.setDown(false);
        minecraft.options.keyRight.setDown(false);
        minecraft.options.keyLeft.setDown(false);
        minecraft.options.keyDown.setDown(false);
        minecraft.options.keyJump.setDown(false);
        minecraft.options.keyUse.setDown(false);
        minecraft.options.keyAttack.setDown(false);
    }

    private static void resetFpsSamples() {
        fpsMin = Integer.MAX_VALUE;
        fpsMax = 0;
        fpsSum = 0L;
        fpsSamples = 0;
        lowFpsSamples = 0;
        lowFpsSegments = 0;
        inLowFpsSegment = false;
        currentLowFpsSegmentStart = 0;
        worstFpsTick = 0;
        worstFps = Integer.MAX_VALUE;
        lowFpsStoredSamples = 0;
        if (fpsValues.length < Math.max(1, MAX_TICKS)) {
            fpsValues = new int[Math.max(1, MAX_TICKS)];
        }
        int lowLimit = Math.max(1, LOW_FPS_SAMPLE_LIMIT);
        if (lowFpsTicks.length < lowLimit) {
            lowFpsTicks = new int[lowLimit];
            lowFpsValues = new int[lowLimit];
            lowFpsPositions = new String[lowLimit];
            lowFpsProcessQuadMs = new double[lowLimit];
            lowFpsCtmMs = new double[lowLimit];
            lowFpsResolveMs = new double[lowLimit];
            lowFpsGcCount = new long[lowLimit];
            lowFpsGcMillis = new long[lowLimit];
        }
    }

    private static void sampleFps(Minecraft minecraft) {
        int fps = minecraft.getFps();
        if (fps <= 0) {
            return;
        }
        long heapUsed = usedHeapBytes();
        peakHeapUsed = Math.max(peakHeapUsed, heapUsed);
        if (fpsSamples == fpsValues.length) {
            fpsValues = Arrays.copyOf(fpsValues, fpsValues.length * 2);
        }
        fpsValues[fpsSamples] = fps;
        fpsMin = Math.min(fpsMin, fps);
        fpsMax = Math.max(fpsMax, fps);
        fpsSum += fps;
        fpsSamples++;
        if (fps < worstFps) {
            worstFps = fps;
            worstFpsTick = ticks;
        }
        if (fps <= LOW_FPS_THRESHOLD) {
            lowFpsSamples++;
            if (!inLowFpsSegment) {
                inLowFpsSegment = true;
                currentLowFpsSegmentStart = ticks;
                lowFpsSegments++;
            }
            rememberLowFpsSample(minecraft, fps);
            LOGGER.info(
                    "[Argus] benchmark-driver low-fps tick={} fps={} threshold={} pos={} buckets={} gcDelta={} heapUsedMiB={}",
                    ticks,
                    fps,
                    LOW_FPS_THRESHOLD,
                    minecraft.player == null ? "none" : positionLabel(minecraft.player),
                    bucketSummary(),
                    gcSummary(),
                    heapUsed / (1024L * 1024L));
        } else if (inLowFpsSegment) {
            LOGGER.info(
                    "[Argus] benchmark-driver low-fps segment end startTick={} endTick={} threshold={}",
                    currentLowFpsSegmentStart,
                    ticks - 1,
                    LOW_FPS_THRESHOLD);
            inLowFpsSegment = false;
        }
    }

    private static void captureRuntimeBaselines() {
        startGcCount = gcCollectionCount();
        startGcMillis = gcCollectionMillis();
        startHeapUsed = usedHeapBytes();
        peakHeapUsed = startHeapUsed;
    }

    private static void rememberLowFpsSample(Minecraft minecraft, int fps) {
        int slot = -1;
        if (lowFpsStoredSamples < lowFpsTicks.length) {
            slot = lowFpsStoredSamples++;
        } else {
            int weakestSlot = 0;
            int weakestFps = lowFpsValues[0];
            for (int i = 1; i < lowFpsValues.length; i++) {
                if (lowFpsValues[i] > weakestFps) {
                    weakestFps = lowFpsValues[i];
                    weakestSlot = i;
                }
            }
            if (fps < weakestFps) {
                slot = weakestSlot;
            }
        }
        if (slot < 0) {
            return;
        }
        ArgusBenchmark.BucketSnapshot[] buckets = ArgusBenchmark
                .snapshotTotals();
        lowFpsTicks[slot] = ticks;
        lowFpsValues[slot] = fps;
        lowFpsPositions[slot] = minecraft.player == null
                ? ""
                : positionLabel(minecraft.player);
        lowFpsProcessQuadMs[slot] = bucketMillis(buckets,
                "sodium.process_quad");
        lowFpsCtmMs[slot] = bucketMillis(buckets, "sodium.ctm");
        lowFpsResolveMs[slot] = bucketMillis(buckets, "ctm.resolve");
        lowFpsGcCount[slot] = gcCollectionCount();
        lowFpsGcMillis[slot] = gcCollectionMillis();
        sortLowFpsSamples();
    }

    private static void sortLowFpsSamples() {
        for (int i = 1; i < lowFpsStoredSamples; i++) {
            int tick = lowFpsTicks[i];
            int fps = lowFpsValues[i];
            String position = lowFpsPositions[i];
            double process = lowFpsProcessQuadMs[i];
            double ctm = lowFpsCtmMs[i];
            double resolve = lowFpsResolveMs[i];
            long gcCount = lowFpsGcCount[i];
            long gcMillis = lowFpsGcMillis[i];
            int j = i - 1;
            while (j >= 0 && lowFpsValues[j] > fps) {
                lowFpsTicks[j + 1] = lowFpsTicks[j];
                lowFpsValues[j + 1] = lowFpsValues[j];
                lowFpsPositions[j + 1] = lowFpsPositions[j];
                lowFpsProcessQuadMs[j + 1] = lowFpsProcessQuadMs[j];
                lowFpsCtmMs[j + 1] = lowFpsCtmMs[j];
                lowFpsResolveMs[j + 1] = lowFpsResolveMs[j];
                lowFpsGcCount[j + 1] = lowFpsGcCount[j];
                lowFpsGcMillis[j + 1] = lowFpsGcMillis[j];
                j--;
            }
            lowFpsTicks[j + 1] = tick;
            lowFpsValues[j + 1] = fps;
            lowFpsPositions[j + 1] = position;
            lowFpsProcessQuadMs[j + 1] = process;
            lowFpsCtmMs[j + 1] = ctm;
            lowFpsResolveMs[j + 1] = resolve;
            lowFpsGcCount[j + 1] = gcCount;
            lowFpsGcMillis[j + 1] = gcMillis;
        }
    }

    private static String bucketSummary() {
        ArgusBenchmark.BucketSnapshot[] buckets = ArgusBenchmark
                .snapshotTotals();
        return "processMs=" + formatDouble(bucketMillis(buckets,
                "sodium.process_quad"))
                + ",ctmMs=" + formatDouble(bucketMillis(buckets,
                "sodium.ctm"))
                + ",resolveMs=" + formatDouble(bucketMillis(buckets,
                "ctm.resolve"));
    }

    private static double bucketMillis(ArgusBenchmark.BucketSnapshot[] buckets,
                                       String name) {
        for (ArgusBenchmark.BucketSnapshot bucket : buckets) {
            if (bucket.name().equals(name)) {
                return bucket.totalMillis();
            }
        }
        return 0.0D;
    }

    private static String gcSummary() {
        return "count=" + (gcCollectionCount() - startGcCount)
                + ",ms=" + (gcCollectionMillis() - startGcMillis);
    }

    private static long gcCollectionCount() {
        long total = 0L;
        for (GarbageCollectorMXBean bean :
                ManagementFactory.getGarbageCollectorMXBeans()) {
            long count = bean.getCollectionCount();
            if (count > 0L) {
                total += count;
            }
        }
        return total;
    }

    private static long gcCollectionMillis() {
        long total = 0L;
        for (GarbageCollectorMXBean bean :
                ManagementFactory.getGarbageCollectorMXBeans()) {
            long time = bean.getCollectionTime();
            if (time > 0L) {
                total += time;
            }
        }
        return total;
    }

    private static long usedHeapBytes() {
        MemoryUsage usage = ManagementFactory.getMemoryMXBean()
                .getHeapMemoryUsage();
        return Math.max(0L, usage.getUsed());
    }

    private static void writeReport(Minecraft minecraft) {
        if (reportWritten) {
            return;
        }
        reportWritten = true;
        Instant now = Instant.now();
        String loader = property("argus.benchmark.loader", "unknown");
        String mode = Boolean.getBoolean("argus.sodium.modelEmitter.disabled")
                ? "legacy-process-quad"
                : "platform-model-emitter";
        String runLabel = property("argus.benchmark.runLabel",
                loader + "-" + mode);
        Path reportDir = reportDirectory(minecraft);
        try {
            Files.createDirectories(reportDir);
            String baseName = FILE_TIMESTAMP.format(now)
                    + "-" + sanitizeFileName(runLabel);
            String json = buildJsonReport(minecraft, now, loader, mode,
                    runLabel);
            String markdown = buildMarkdownReport(minecraft, now, loader,
                    mode, runLabel);
            Files.writeString(reportDir.resolve(baseName + ".json"), json,
                    StandardCharsets.UTF_8);
            Files.writeString(reportDir.resolve(baseName + ".md"), markdown,
                    StandardCharsets.UTF_8);
            LOGGER.info("[Argus] benchmark-driver wrote report dir={} name={}",
                    reportDir, baseName);
        } catch (IOException e) {
            LOGGER.warn("[Argus] benchmark-driver failed to write report", e);
        }
    }

    private static String buildJsonReport(Minecraft minecraft,
                                           Instant timestamp,
                                           String loader,
                                           String mode,
                                           String runLabel) {
        StringBuilder out = new StringBuilder(8192);
        out.append("{\n");
        appendJsonField(out, 1, "schema", "argus-benchmark-report-v1", true);
        appendJsonField(out, 1, "timestamp", timestamp.toString(), true);
        appendJsonField(out, 1, "runLabel", runLabel, true);
        appendJsonField(out, 1, "methodology", METHODOLOGY, true);
        appendJsonObjectStart(out, 1, "environment");
        appendJsonField(out, 2, "loader", loader, true);
        appendJsonField(out, 2, "loaderVersion",
                property("argus.benchmark.loaderVersion", "unknown"), true);
        appendJsonField(out, 2, "modVersion",
                property("argus.benchmark.modVersion", "unknown"), true);
        appendJsonField(out, 2, "minecraftVersion",
                property("argus.benchmark.minecraftVersion", "unknown"),
                true);
        appendJsonField(out, 2, "javaVersion",
                System.getProperty("java.version", "unknown"), true);
        appendJsonField(out, 2, "javaVm",
                System.getProperty("java.vm.name", "unknown"), true);
        appendJsonField(out, 2, "os",
                System.getProperty("os.name", "unknown") + " "
                        + System.getProperty("os.version", ""), true);
        appendJsonField(out, 2, "jvmArguments",
                ManagementFactory.getRuntimeMXBean()
                        .getInputArguments().toString(), false);
        appendJsonObjectEnd(out, 1, true);

        appendJsonObjectStart(out, 1, "configuration");
        appendJsonField(out, 2, "renderPath", mode, true);
        appendJsonField(out, 2, "world", configuredWorld(), true);
        appendJsonField(out, 2, "seed", Long.toString(configuredSeed()),
                true);
        appendJsonField(out, 2, "teleportTarget",
                String.format(Locale.ROOT, "%.3f %.3f %.3f",
                        TARGET_X, TARGET_Y, TARGET_Z), true);
        appendJsonField(out, 2, "settleTicks", SETTLE_TICKS, true);
        appendJsonField(out, 2, "autopilotTicks", MAX_TICKS, true);
        appendJsonField(out, 2, "closeOnComplete", CLOSE_ON_COMPLETE, true);
        appendJsonField(out, 2, "optionsTemplate",
                property("argus.benchmark.optionsFile", ""), true);
        appendJsonField(out, 2, "configTemplate",
                property("argus.benchmark.configFile", ""), true);
        appendJsonField(out, 2, "optionsSha256",
                fileHash(optionsPath(minecraft)), true);
        appendJsonField(out, 2, "configSha256",
                fileHash(configPath(minecraft)), true);
        appendJsonField(out, 2, "resourcePacks",
                optionsLine(minecraft, "resourcePacks:"), true);
        appendJsonField(out, 2, "incompatibleResourcePacks",
                optionsLine(minecraft, "incompatibleResourcePacks:"),
                false);
        appendJsonObjectEnd(out, 1, true);

        appendJsonObjectStart(out, 1, "fps");
        appendJsonField(out, 2, "samples", fpsSamples, true);
        appendJsonField(out, 2, "min",
                fpsMin == Integer.MAX_VALUE ? 0 : fpsMin, true);
        appendJsonField(out, 2, "avg",
                fpsSamples == 0 ? 0.0D : (double) fpsSum / fpsSamples,
                true);
        appendJsonField(out, 2, "max", fpsMax, true);
        appendJsonField(out, 2, "p01", percentile(0.01D), true);
        appendJsonField(out, 2, "p05", percentile(0.05D), true);
        appendJsonField(out, 2, "median", percentile(0.50D), true);
        appendJsonField(out, 2, "p95", percentile(0.95D), true);
        appendJsonField(out, 2, "p99", percentile(0.99D), true);
        appendJsonField(out, 2, "approxAvgFrameMs",
                fpsSamples == 0 ? 0.0D
                        : 1000.0D / ((double) fpsSum / fpsSamples),
                true);
        appendJsonField(out, 2, "approxP95FrameMs",
                frameMillisForFps(percentile(0.05D)), true);
        appendJsonField(out, 2, "approxP99FrameMs",
                frameMillisForFps(percentile(0.01D)), true);
        appendJsonField(out, 2, "lowFpsThreshold", LOW_FPS_THRESHOLD,
                true);
        appendJsonField(out, 2, "lowFpsSamples", lowFpsSamples, true);
        appendJsonField(out, 2, "lowFpsSegments", lowFpsSegments, true);
        appendJsonField(out, 2, "worstFpsTick", worstFpsTick, true);
        appendJsonField(out, 2, "worstFps",
                worstFps == Integer.MAX_VALUE ? 0 : worstFps,
                false);
        appendJsonObjectEnd(out, 1, true);

        appendJsonObjectStart(out, 1, "runtime");
        appendJsonField(out, 2, "gcCountDelta",
                gcCollectionCount() - startGcCount, true);
        appendJsonField(out, 2, "gcMillisDelta",
                gcCollectionMillis() - startGcMillis, true);
        appendJsonField(out, 2, "heapUsedStartMiB",
                startHeapUsed / (1024L * 1024L), true);
        appendJsonField(out, 2, "heapUsedEndMiB",
                usedHeapBytes() / (1024L * 1024L), true);
        appendJsonField(out, 2, "heapUsedPeakMiB",
                peakHeapUsed / (1024L * 1024L), false);
        appendJsonObjectEnd(out, 1, true);

        appendJsonArrayStart(out, 1, "lowFpsSamples");
        for (int i = 0; i < lowFpsStoredSamples; i++) {
            if (i > 0) {
                out.append(",\n");
            }
            indent(out, 2).append("{");
            out.append("\"tick\":").append(lowFpsTicks[i]).append(',');
            out.append("\"fps\":").append(lowFpsValues[i]).append(',');
            out.append("\"approxFrameMs\":")
                    .append(formatDouble(frameMillisForFps(lowFpsValues[i])))
                    .append(',');
            out.append("\"position\":\"")
                    .append(escapeJson(lowFpsPositions[i] == null
                            ? ""
                            : lowFpsPositions[i]))
                    .append("\",");
            out.append("\"processQuadMs\":")
                    .append(formatDouble(lowFpsProcessQuadMs[i])).append(',');
            out.append("\"ctmMs\":")
                    .append(formatDouble(lowFpsCtmMs[i])).append(',');
            out.append("\"resolveMs\":")
                    .append(formatDouble(lowFpsResolveMs[i])).append(',');
            out.append("\"gcCountDelta\":")
                    .append(lowFpsGcCount[i] - startGcCount).append(',');
            out.append("\"gcMillisDelta\":")
                    .append(lowFpsGcMillis[i] - startGcMillis);
            out.append('}');
        }
        out.append('\n');
        appendJsonArrayEnd(out, 1, true);

        appendJsonArrayStart(out, 1, "buckets");
        ArgusBenchmark.BucketSnapshot[] buckets = ArgusBenchmark
                .snapshotTotals();
        boolean wrote = false;
        for (ArgusBenchmark.BucketSnapshot bucket : buckets) {
            if (bucket.count() == 0L) {
                continue;
            }
            if (wrote) {
                out.append(",\n");
            }
            indent(out, 2).append("{");
            out.append("\"name\":\"").append(escapeJson(bucket.name()))
                    .append("\",");
            out.append("\"count\":").append(bucket.count()).append(',');
            out.append("\"totalMs\":")
                    .append(formatDouble(bucket.totalMillis())).append(',');
            out.append("\"avgNs\":")
                    .append(formatDouble(bucket.averageNanos()));
            out.append('}');
            wrote = true;
        }
        out.append('\n');
        appendJsonArrayEnd(out, 1, false);
        out.append("}\n");
        return out.toString();
    }

    private static String buildMarkdownReport(Minecraft minecraft,
                                               Instant timestamp,
                                               String loader,
                                               String mode,
                                               String runLabel) {
        StringBuilder out = new StringBuilder(8192);
        out.append("# Argus Benchmark Report\n\n");
        out.append("- Run: `").append(runLabel).append("`\n");
        out.append("- Timestamp: `").append(timestamp).append("`\n");
        out.append("- Loader: `").append(loader).append(" ")
                .append(property("argus.benchmark.loaderVersion", "unknown"))
                .append("`\n");
        out.append("- Argus: `")
                .append(property("argus.benchmark.modVersion", "unknown"))
                .append("`\n");
        out.append("- Minecraft: `")
                .append(property("argus.benchmark.minecraftVersion", "unknown"))
                .append("`\n");
        out.append("- Render path: `").append(mode).append("`\n");
        out.append("- World: `").append(configuredWorld()).append("`\n");
        out.append("- Seed: `").append(configuredSeed()).append("`\n\n");
        out.append("- Teleport target: `")
                .append(String.format(Locale.ROOT, "%.3f %.3f %.3f",
                        TARGET_X, TARGET_Y, TARGET_Z))
                .append("`\n");
        out.append("- Settle ticks: `").append(SETTLE_TICKS).append("`\n\n");
        out.append("## Methodology\n\n").append(METHODOLOGY).append("\n\n");
        out.append("## Settings\n\n");
        out.append("- Options template: `")
                .append(property("argus.benchmark.optionsFile", ""))
                .append("`\n");
        out.append("- Config template: `")
                .append(property("argus.benchmark.configFile", ""))
                .append("`\n");
        out.append("- Options SHA-256: `").append(fileHash(optionsPath(minecraft)))
                .append("`\n");
        out.append("- Config SHA-256: `").append(fileHash(configPath(minecraft)))
                .append("`\n");
        out.append("- Resource packs: `")
                .append(optionsLine(minecraft, "resourcePacks:"))
                .append("`\n\n");
        out.append("## FPS\n\n");
        out.append("| Metric | Value |\n| --- | ---: |\n");
        out.append("| Samples | ").append(fpsSamples).append(" |\n");
        out.append("| Min | ").append(fpsMin == Integer.MAX_VALUE ? 0 : fpsMin)
                .append(" |\n");
        out.append("| Avg | ")
                .append(formatDouble(fpsSamples == 0 ? 0.0D
                        : (double) fpsSum / fpsSamples))
                .append(" |\n");
        out.append("| Max | ").append(fpsMax).append(" |\n");
        out.append("| P01 | ").append(percentile(0.01D)).append(" |\n");
        out.append("| P05 | ").append(percentile(0.05D)).append(" |\n");
        out.append("| Median | ").append(percentile(0.50D)).append(" |\n");
        out.append("| P95 | ").append(percentile(0.95D)).append(" |\n");
        out.append("| P99 | ").append(percentile(0.99D)).append(" |\n\n");
        out.append("## Stall Diagnostics\n\n");
        out.append("| Metric | Value |\n| --- | ---: |\n");
        out.append("| Low-FPS threshold | ").append(LOW_FPS_THRESHOLD)
                .append(" |\n");
        out.append("| Low-FPS samples | ").append(lowFpsSamples)
                .append(" |\n");
        out.append("| Low-FPS segments | ").append(lowFpsSegments)
                .append(" |\n");
        out.append("| Worst FPS tick | ").append(worstFpsTick).append(" |\n");
        out.append("| Worst FPS | ")
                .append(worstFps == Integer.MAX_VALUE ? 0 : worstFps)
                .append(" |\n");
        out.append("| Approx P95 frame ms | ")
                .append(formatDouble(frameMillisForFps(percentile(0.05D))))
                .append(" |\n");
        out.append("| Approx P99 frame ms | ")
                .append(formatDouble(frameMillisForFps(percentile(0.01D))))
                .append(" |\n");
        out.append("| GC count delta | ")
                .append(gcCollectionCount() - startGcCount).append(" |\n");
        out.append("| GC ms delta | ")
                .append(gcCollectionMillis() - startGcMillis).append(" |\n");
        out.append("| Heap start MiB | ")
                .append(startHeapUsed / (1024L * 1024L)).append(" |\n");
        out.append("| Heap end MiB | ")
                .append(usedHeapBytes() / (1024L * 1024L)).append(" |\n");
        out.append("| Heap peak MiB | ")
                .append(peakHeapUsed / (1024L * 1024L)).append(" |\n\n");
        if (lowFpsStoredSamples > 0) {
            out.append("### Lowest FPS Samples\n\n");
            out.append("| Tick | FPS | Approx frame ms | Position | Process ms | CTM ms | Resolve ms | GC count delta | GC ms delta |\n");
            out.append("| ---: | ---: | ---: | --- | ---: | ---: | ---: | ---: | ---: |\n");
            for (int i = 0; i < lowFpsStoredSamples; i++) {
                out.append("| ").append(lowFpsTicks[i])
                        .append(" | ").append(lowFpsValues[i])
                        .append(" | ")
                        .append(formatDouble(frameMillisForFps(lowFpsValues[i])))
                        .append(" | `")
                        .append(lowFpsPositions[i] == null
                                ? ""
                                : lowFpsPositions[i])
                        .append("` | ")
                        .append(formatDouble(lowFpsProcessQuadMs[i]))
                        .append(" | ")
                        .append(formatDouble(lowFpsCtmMs[i]))
                        .append(" | ")
                        .append(formatDouble(lowFpsResolveMs[i]))
                        .append(" | ")
                        .append(lowFpsGcCount[i] - startGcCount)
                        .append(" | ")
                        .append(lowFpsGcMillis[i] - startGcMillis)
                        .append(" |\n");
            }
            out.append('\n');
        }
        out.append("## Buckets\n\n");
        out.append("| Bucket | Count | Total ms | Avg ns |\n");
        out.append("| --- | ---: | ---: | ---: |\n");
        for (ArgusBenchmark.BucketSnapshot bucket :
                ArgusBenchmark.snapshotTotals()) {
            if (bucket.count() == 0L) {
                continue;
            }
            out.append("| `").append(bucket.name()).append("` | ")
                    .append(bucket.count()).append(" | ")
                    .append(formatDouble(bucket.totalMillis())).append(" | ")
                    .append(formatDouble(bucket.averageNanos()))
                    .append(" |\n");
        }
        return out.toString();
    }

    private static Path reportDirectory(Minecraft minecraft) {
        String configured = System.getProperty("argus.benchmark.reportDir", "")
                .trim();
        if (!configured.isEmpty()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        return minecraft.gameDirectory.toPath()
                .resolve("benchmark-reports")
                .toAbsolutePath()
                .normalize();
    }

    private static Path optionsPath(Minecraft minecraft) {
        return minecraft.gameDirectory.toPath().resolve("options.txt");
    }

    private static Path configPath(Minecraft minecraft) {
        return minecraft.gameDirectory.toPath()
                .resolve("config")
                .resolve("argus.properties");
    }

    private static String optionsLine(Minecraft minecraft, String prefix) {
        Path path = optionsPath(minecraft);
        if (!Files.isRegularFile(path)) {
            return "";
        }
        try {
            for (String line : Files.readAllLines(path,
                    StandardCharsets.UTF_8)) {
                if (line.startsWith(prefix)) {
                    return line.substring(prefix.length());
                }
            }
        } catch (IOException e) {
            return "unreadable:" + e.getClass().getSimpleName();
        }
        return "";
    }

    private static String fileHash(Path path) {
        if (!Files.isRegularFile(path)) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(Files.readAllBytes(path));
            StringBuilder out = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                out.append(String.format(Locale.ROOT, "%02x", b & 0xFF));
            }
            return out.toString();
        } catch (IOException | NoSuchAlgorithmException e) {
            return "unreadable:" + e.getClass().getSimpleName();
        }
    }

    private static int percentile(double percentile) {
        if (fpsSamples == 0) {
            return 0;
        }
        int[] copy = Arrays.copyOf(fpsValues, fpsSamples);
        Arrays.sort(copy);
        int index = (int) Math.floor((copy.length - 1) * percentile);
        return copy[Math.max(0, Math.min(copy.length - 1, index))];
    }

    private static double frameMillisForFps(int fps) {
        return fps <= 0 ? 0.0D : 1000.0D / fps;
    }

    private static String property(String key, String fallback) {
        String value = System.getProperty(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String sanitizeFileName(String value) {
        return value.replaceAll("[\\\\/:*?\"<>|\\s]+", "-");
    }

    private static String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static void appendJsonObjectStart(StringBuilder out,
                                               int depth,
                                               String name) {
        indent(out, depth).append('"').append(escapeJson(name))
                .append("\": {\n");
    }

    private static void appendJsonObjectEnd(StringBuilder out,
                                             int depth,
                                             boolean comma) {
        indent(out, depth).append('}');
        out.append(comma ? ",\n" : "\n");
    }

    private static void appendJsonArrayStart(StringBuilder out,
                                              int depth,
                                              String name) {
        indent(out, depth).append('"').append(escapeJson(name))
                .append("\": [\n");
    }

    private static void appendJsonArrayEnd(StringBuilder out,
                                            int depth,
                                            boolean comma) {
        indent(out, depth).append(']');
        out.append(comma ? ",\n" : "\n");
    }

    private static void appendJsonField(StringBuilder out,
                                         int depth,
                                         String name,
                                         String value,
                                         boolean comma) {
        indent(out, depth).append('"').append(escapeJson(name)).append("\": ")
                .append('"').append(escapeJson(value)).append('"');
        out.append(comma ? ",\n" : "\n");
    }

    private static void appendJsonField(StringBuilder out,
                                         int depth,
                                         String name,
                                         int value,
                                         boolean comma) {
        indent(out, depth).append('"').append(escapeJson(name)).append("\": ")
                .append(value);
        out.append(comma ? ",\n" : "\n");
    }

    private static void appendJsonField(StringBuilder out,
                                         int depth,
                                         String name,
                                         long value,
                                         boolean comma) {
        indent(out, depth).append('"').append(escapeJson(name)).append("\": ")
                .append(value);
        out.append(comma ? ",\n" : "\n");
    }

    private static void appendJsonField(StringBuilder out,
                                         int depth,
                                         String name,
                                         boolean value,
                                         boolean comma) {
        indent(out, depth).append('"').append(escapeJson(name)).append("\": ")
                .append(value);
        out.append(comma ? ",\n" : "\n");
    }

    private static void appendJsonField(StringBuilder out,
                                         int depth,
                                         String name,
                                         double value,
                                         boolean comma) {
        indent(out, depth).append('"').append(escapeJson(name)).append("\": ")
                .append(formatDouble(value));
        out.append(comma ? ",\n" : "\n");
    }

    private static StringBuilder indent(StringBuilder out, int depth) {
        return out.append("  ".repeat(depth));
    }

    private static String escapeJson(String value) {
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format(Locale.ROOT, "\\u%04x",
                                (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    private static String worldLabel(Minecraft minecraft) {
        return minecraft.level == null
                ? "none"
                : minecraft.level.dimension().toString();
    }

    private static String positionLabel(LocalPlayer player) {
        return String.format(Locale.ROOT, "%.1f,%.1f,%.1f",
                player.getX(),
                player.getY(),
                player.getZ());
    }
}
