package com.eclipseware.imnotcheatingyouare.client.module.impl;

import com.eclipseware.imnotcheatingyouare.client.ImnotcheatingyouareClient;
import com.eclipseware.imnotcheatingyouare.client.module.Category;
import com.eclipseware.imnotcheatingyouare.client.module.Module;
import com.eclipseware.imnotcheatingyouare.client.setting.Setting;
import com.eclipseware.imnotcheatingyouare.client.setting.SettingsManager;
import com.eclipseware.imnotcheatingyouare.client.utils.RenderUtils;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.polarbear.PolarBear;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class SuspiciousChunks extends Module {

    private enum BlockKind {
        DEEPSLATE,
        COBBLED_DEEPSLATE,
        ROTATED_DEEPSLATE,
        END_STONE
    }

    private static final int MAGIC_NEIGHBOUR_LIGHT = 4;
    private static final int GEODE_MIN_Y = -64;
    private static final int GEODE_MAX_Y = 48;
    private static final int GEODE_HEIGHT = GEODE_MAX_Y - GEODE_MIN_Y + 1;

    private static final byte CELL_CRYSTAL = 1;
    private static final byte CELL_CALCITE = 2;
    private static final byte CELL_BASALT = 3;
    private static final byte CELL_AIR = 4;
    private static final byte CELL_SOLID = 5;

    private static final int[] NEIGHBOUR_X = {1, -1, 0, 0, 0, 0};
    private static final int[] NEIGHBOUR_Y = {0, 0, 1, -1, 0, 0};
    private static final int[] NEIGHBOUR_Z = {0, 0, 0, 0, 1, -1};

    private static final Set<Block> TRIAL_BLOCKS = new java.util.HashSet<>();
    static {
        TRIAL_BLOCKS.add(Blocks.TUFF_BRICKS);
        for (Block block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
            var key = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
            if (key == null) continue;
            String path = key.getPath();
            if (path.equals("waxed_copper_block") || path.equals("waxed_copper") || path.equals("waxed_oxidized_copper")) TRIAL_BLOCKS.add(block);
        }
    }

    private static final float NEAR = 0.05f;
    private static final int[][] EDGES = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
    private static final int[][] FACES = {{0, 2, 6, 4}, {1, 5, 7, 3}, {0, 1, 5, 4}, {2, 6, 7, 3}, {0, 1, 3, 2}, {4, 6, 7, 5}};

    private final SettingsManager sm;

    private final Setting detectDeepslate;
    private final Setting detectCobbledDeepslate;
    private final Setting detectRotatedDeepslate;
    private final Setting detectEndStone;
    private final Setting ignoreExposed;
    private final Setting ignoreTrialChambers;
    private final Setting trialChamberThreshold;
    private final Setting deepslateThreshold;
    private final Setting cobbledDeepslateThreshold;
    private final Setting rotatedDeepslateThreshold;
    private final Setting endStoneThreshold;
    private final Setting baseDetectors;
    private final Setting minScore;
    private final Setting baseChunkColor;
    private final Setting detCobble;
    private final Setting detDeepslateBand;
    private final Setting detObsidian;
    private final Setting detVeinInt;
    private final Setting detVertVein;
    private final Setting detNeedle;
    private final Setting detHiddenOre;
    private final Setting detPiston;
    private final Setting detMobCluster;
    private final Setting detDropped;
    private final Setting detAirPocket;
    private final Setting detStair;
    private final Setting detPolarBear;
    private final Setting detChestCluster;
    private final Setting detectAmethyst;
    private final Setting amethystSmartCheck;
    private final Setting amethystSensitivity;
    private final Setting amethystChunkColor;
    private final Setting magicAmethyst;
    private final Setting magicThreshold;
    private final Setting magicScanRadius;
    private final Setting detectStructure;
    private final Setting baseChanceThreshold;
    private final Setting structRepeater;
    private final Setting structBeehive;
    private final Setting structDeepslateFloor;
    private final Setting structVines;
    private final Setting structSeagrass;
    private final Setting structFlowers;
    private final Setting structStorage;
    private final Setting structureChunkColor;
    private final Setting renderY;
    private final Setting chunkColor;
    private final Setting thickness;
    private final Setting maxChunksToRender;
    private final Setting fillOpacity;
    private final Setting lineWidth;
    private final Setting renderFill;
    private final Setting renderOutline;
    private final Setting highlightBlocks;
    private final Setting maxBlocksToRender;
    private final Setting blockFill;
    private final Setting deepslateBlockColor;
    private final Setting cobbledDeepslateBlockColor;
    private final Setting rotatedDeepslateBlockColor;
    private final Setting endStoneBlockColor;
    private final Setting threadCount;
    private final Setting scanDelay;
    private final Setting maxConcurrentScans;
    private final Setting rescanSeconds;
    private final Setting scanRadius;
    private final Setting entityScanTicks;
    private final Setting cleanupSeconds;
    private final Setting chatAlerts;
    private final Setting soundAlerts;
    private final Setting trialChamberAlerts;
    private final Setting maxAlerts;

    private final Set<ChunkPos> flaggedChunks = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> baseFlaggedChunks = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> amethystChunks = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> structureChunks = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> inFlight = ConcurrentHashMap.newKeySet();
    private final Map<ChunkPos, Long> scannedAt = new ConcurrentHashMap<>();
    private final Map<ChunkPos, EntitySignals> entitySignals = new ConcurrentHashMap<>();
    private final Map<ChunkPos, Long> notificationTimes = new ConcurrentHashMap<>();
    private final Map<BlockPos, BlockKind> suspiciousBlocks = new ConcurrentHashMap<>();
    private final Queue<Long> recentAlerts = new ConcurrentLinkedQueue<>();
    private final AtomicInteger activeScans = new AtomicInteger(0);

    private volatile Cfg cfg;
    private volatile boolean shouldScan = false;
    private ExecutorService scannerPool;
    private long lastFeed = 0L;
    private long lastCleanup = 0L;
    private long lastCfg = 0L;
    private int entityTicks = 0;

    private final org.joml.Matrix4f matrix = new org.joml.Matrix4f();
    private final org.joml.Vector4f[] clip = new org.joml.Vector4f[8];
    {
        for (int i = 0; i < 8; i++) clip[i] = new org.joml.Vector4f();
    }
    private final float[] polyX = new float[10];
    private final float[] polyY = new float[10];
    private final float[] polyW = new float[10];
    private final float[] outX = new float[10];
    private final float[] outY = new float[10];
    private final ImVec2[] polyPoints = new ImVec2[10];
    {
        for (int i = 0; i < polyPoints.length; i++) polyPoints[i] = new ImVec2();
    }

    public SuspiciousChunks() {
        super("SuspiciousChunks", Category.Render, "Flags chunks that look like player bases, stripped geodes and other suspicious activity.");
        sm = ImnotcheatingyouareClient.INSTANCE.settingsManager;

        detectDeepslate = bool("Detect Deepslate", true);
        detectCobbledDeepslate = bool("Detect Cobbled Deepslate", true);
        detectRotatedDeepslate = bool("Detect Rotated Deepslate", true);
        detectEndStone = bool("Detect End Stone", true);
        ignoreExposed = bool("Ignore Exposed", true);
        ignoreTrialChambers = bool("Ignore Trial Chambers", true);
        trialChamberThreshold = num("Trial Chamber Threshold", 50, 1, 50);
        deepslateThreshold = num("Deepslate Threshold", 1, 1, 15);
        cobbledDeepslateThreshold = num("Cobbled Deepslate Threshold", 4, 1, 15);
        rotatedDeepslateThreshold = num("Rotated Deepslate Threshold", 3, 1, 20);
        endStoneThreshold = num("End Stone Threshold", 2, 1, 15);
        baseDetectors = bool("Base Detectors", true);
        minScore = num("Min Score", 5, 1, 30);
        baseChunkColor = color("Base Chunk Color", 0, 255, 80);
        detCobble = bool("Cobble Lines", true);
        detDeepslateBand = bool("Deepslate Band", true);
        detObsidian = bool("Obsidian Backbone", true);
        detVeinInt = bool("Vein Integrity", true);
        detVertVein = bool("Vertical Vein", true);
        detNeedle = bool("Needle Hole", true);
        detHiddenOre = bool("Hidden Ore", true);
        detPiston = bool("Piston Array", true);
        detMobCluster = bool("Mob Cluster", true);
        detDropped = bool("Dropped Items", true);
        detAirPocket = bool("Air Pocket", true);
        detStair = bool("Synthetic Stair", true);
        detPolarBear = bool("Polar Bear", true);
        detChestCluster = bool("Chest Cluster", true);
        detectAmethyst = bool("Detect Amethyst", true);
        amethystSmartCheck = bool("Amethyst Smart Check", true);
        amethystSensitivity = num("Amethyst Sensitivity", 5, 1, 10);
        amethystChunkColor = color("Amethyst Chunk Color", 180, 100, 255);
        magicAmethyst = bool("Magic Amethyst", true);
        magicThreshold = num("Magic Threshold", 12, 1, 100);
        magicScanRadius = num("Magic Scan Radius", 8, 1, 24);
        detectStructure = bool("Detect Structure", true);
        baseChanceThreshold = num("Min Base Chance", 45, 20, 99);
        structRepeater = bool("Powered Repeaters", true);
        structBeehive = bool("Occupied Beehives", true);
        structDeepslateFloor = bool("Cobbled Deepslate Floor", true);
        structVines = bool("Vine Farm", true);
        structSeagrass = bool("Seagrass Farm", true);
        structFlowers = bool("Flower Farm", true);
        structStorage = bool("Crafting Storage", true);
        structureChunkColor = color("Structure Chunk Color", 255, 120, 0);
        chunkColor = color("Chunk Color", 255, 215, 0);
        renderY = num("Render Height", 64, -64, 320);
        thickness = new Setting("Thickness", this, 0.3, 0.1, 2.0, false);
        sm.rSetting(thickness);
        maxChunksToRender = num("Max Chunks Render", 50, 10, 400);
        fillOpacity = num("Fill Opacity", 24, 0, 100);
        lineWidth = new Setting("Line Width", this, 1.5, 0.5, 4.0, false);
        sm.rSetting(lineWidth);
        renderFill = bool("Fill", true);
        renderOutline = bool("Outline", true);
        highlightBlocks = bool("Highlight Blocks", true);
        maxBlocksToRender = num("Max Blocks Render", 200, 50, 1000);
        blockFill = bool("Block Fill", false);
        deepslateBlockColor = color("Deepslate Color", 130, 130, 130);
        cobbledDeepslateBlockColor = color("Cobbled Deepslate Color", 110, 110, 110);
        rotatedDeepslateBlockColor = color("Rotated Deepslate Color", 160, 0, 160);
        endStoneBlockColor = color("End Stone Color", 255, 255, 200);
        threadCount = num("Thread Count", Math.max(1, Runtime.getRuntime().availableProcessors() / 2), 1, 4);
        scanDelay = num("Scan Delay (ms)", 100, 50, 2000);
        maxConcurrentScans = num("Max Concurrent Scans", 3, 1, 8);
        rescanSeconds = num("Rescan Seconds", 30, 5, 300);
        scanRadius = num("Scan Radius (chunks)", 12, 2, 32);
        entityScanTicks = num("Entity Scan Ticks", 20, 5, 200);
        cleanupSeconds = num("Cleanup Interval", 30, 15, 300);
        chatAlerts = bool("Chat Alerts", true);
        soundAlerts = bool("Sound Alerts", true);
        trialChamberAlerts = bool("Trial Chamber Alerts", true);
        maxAlerts = num("Max Alerts Per Minute", 5, 1, 20);

        cfg = new Cfg();
    }

    private Setting bool(String name, boolean def) {
        Setting s = new Setting(name, this, def);
        sm.rSetting(s);
        return s;
    }

    private Setting num(String name, double def, double min, double max) {
        Setting s = new Setting(name, this, def, min, max, true);
        sm.rSetting(s);
        return s;
    }

    private Setting color(String name, int r, int g, int b) {
        Setting s = new Setting(name, this, new java.awt.Color(r, g, b));
        sm.rSetting(s);
        return s;
    }

    private final class Cfg {
        final boolean detectDeepslate = SuspiciousChunks.this.detectDeepslate.getValBoolean();
        final boolean detectCobbledDeepslate = SuspiciousChunks.this.detectCobbledDeepslate.getValBoolean();
        final boolean detectRotatedDeepslate = SuspiciousChunks.this.detectRotatedDeepslate.getValBoolean();
        final boolean detectEndStone = SuspiciousChunks.this.detectEndStone.getValBoolean();
        final boolean ignoreExposed = SuspiciousChunks.this.ignoreExposed.getValBoolean();
        final boolean ignoreTrialChambers = SuspiciousChunks.this.ignoreTrialChambers.getValBoolean();
        final int trialChamberThreshold = (int) SuspiciousChunks.this.trialChamberThreshold.getValDouble();
        final int deepslateThreshold = (int) SuspiciousChunks.this.deepslateThreshold.getValDouble();
        final int cobbledDeepslateThreshold = (int) SuspiciousChunks.this.cobbledDeepslateThreshold.getValDouble();
        final int rotatedDeepslateThreshold = (int) SuspiciousChunks.this.rotatedDeepslateThreshold.getValDouble();
        final int endStoneThreshold = (int) SuspiciousChunks.this.endStoneThreshold.getValDouble();
        final boolean baseDetectors = SuspiciousChunks.this.baseDetectors.getValBoolean();
        final int minScore = (int) SuspiciousChunks.this.minScore.getValDouble();
        final boolean detCobble = SuspiciousChunks.this.detCobble.getValBoolean();
        final boolean detDeepslateBand = SuspiciousChunks.this.detDeepslateBand.getValBoolean();
        final boolean detObsidian = SuspiciousChunks.this.detObsidian.getValBoolean();
        final boolean detVeinInt = SuspiciousChunks.this.detVeinInt.getValBoolean();
        final boolean detVertVein = SuspiciousChunks.this.detVertVein.getValBoolean();
        final boolean detNeedle = SuspiciousChunks.this.detNeedle.getValBoolean();
        final boolean detHiddenOre = SuspiciousChunks.this.detHiddenOre.getValBoolean();
        final boolean detPiston = SuspiciousChunks.this.detPiston.getValBoolean();
        final boolean detMobCluster = SuspiciousChunks.this.detMobCluster.getValBoolean();
        final boolean detDropped = SuspiciousChunks.this.detDropped.getValBoolean();
        final boolean detAirPocket = SuspiciousChunks.this.detAirPocket.getValBoolean();
        final boolean detStair = SuspiciousChunks.this.detStair.getValBoolean();
        final boolean detPolarBear = SuspiciousChunks.this.detPolarBear.getValBoolean();
        final boolean detChestCluster = SuspiciousChunks.this.detChestCluster.getValBoolean();
        final boolean detectAmethyst = SuspiciousChunks.this.detectAmethyst.getValBoolean();
        final boolean amethystSmartCheck = SuspiciousChunks.this.amethystSmartCheck.getValBoolean();
        final int amethystSensitivity = (int) SuspiciousChunks.this.amethystSensitivity.getValDouble();
        final boolean magicAmethyst = SuspiciousChunks.this.magicAmethyst.getValBoolean();
        final int magicThreshold = (int) SuspiciousChunks.this.magicThreshold.getValDouble();
        final int magicScanRadius = (int) SuspiciousChunks.this.magicScanRadius.getValDouble();
        final boolean detectStructure = SuspiciousChunks.this.detectStructure.getValBoolean();
        final int baseChanceThreshold = (int) SuspiciousChunks.this.baseChanceThreshold.getValDouble();
        final boolean structRepeater = SuspiciousChunks.this.structRepeater.getValBoolean();
        final boolean structBeehive = SuspiciousChunks.this.structBeehive.getValBoolean();
        final boolean structDeepslateFloor = SuspiciousChunks.this.structDeepslateFloor.getValBoolean();
        final boolean structVines = SuspiciousChunks.this.structVines.getValBoolean();
        final boolean structSeagrass = SuspiciousChunks.this.structSeagrass.getValBoolean();
        final boolean structFlowers = SuspiciousChunks.this.structFlowers.getValBoolean();
        final boolean structStorage = SuspiciousChunks.this.structStorage.getValBoolean();
        final boolean highlightBlocks = SuspiciousChunks.this.highlightBlocks.getValBoolean();
        final int maxConcurrentScans = (int) SuspiciousChunks.this.maxConcurrentScans.getValDouble();
        final long rescanMs = (long) SuspiciousChunks.this.rescanSeconds.getValDouble() * 1000L;
        final int scanRadius = (int) SuspiciousChunks.this.scanRadius.getValDouble();
        final int scanDelay = (int) SuspiciousChunks.this.scanDelay.getValDouble();
        final int entityScanTicks = (int) SuspiciousChunks.this.entityScanTicks.getValDouble();
        final long cleanupMs = (long) SuspiciousChunks.this.cleanupSeconds.getValDouble() * 1000L;
        final boolean chatAlerts = SuspiciousChunks.this.chatAlerts.getValBoolean();
        final boolean soundAlerts = SuspiciousChunks.this.soundAlerts.getValBoolean();
        final boolean trialChamberAlerts = SuspiciousChunks.this.trialChamberAlerts.getValBoolean();
        final int maxAlerts = (int) SuspiciousChunks.this.maxAlerts.getValDouble();
    }

    @Override
    public void onEnable() {
        if (mc.level == null) return;
        clearAll();
        cfg = new Cfg();
        shouldScan = true;
        lastCleanup = System.currentTimeMillis();
        scannerPool = Executors.newFixedThreadPool((int) threadCount.getValDouble(), r -> {
            Thread t = new Thread(r, "Marlow SuspiciousChunks");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });
    }

    @Override
    public void onDisable() {
        shouldScan = false;
        if (scannerPool != null) {
            scannerPool.shutdownNow();
            scannerPool = null;
        }
        clearAll();
    }

    @Override
    public void onRenderHUD(net.minecraft.client.gui.GuiGraphicsExtractor guiGraphics, Object tickDeltaObj) {
    }

    private void clearAll() {
        flaggedChunks.clear();
        baseFlaggedChunks.clear();
        amethystChunks.clear();
        structureChunks.clear();
        inFlight.clear();
        scannedAt.clear();
        entitySignals.clear();
        notificationTimes.clear();
        suspiciousBlocks.clear();
        recentAlerts.clear();
        activeScans.set(0);
        entityTicks = 0;
    }

    @Override
    public void onTick() {
        if (!isToggled() || mc.level == null || mc.player == null) return;
        if (scannerPool == null || !shouldScan) {
            onEnable();
            if (scannerPool == null) return;
        }

        long now = System.currentTimeMillis();
        if (now - lastCfg > 250L) {
            cfg = new Cfg();
            lastCfg = now;
        }
        Cfg c = cfg;

        while (!recentAlerts.isEmpty() && now - recentAlerts.peek() > 60000L) recentAlerts.poll();

        if (c.baseDetectors && ++entityTicks >= c.entityScanTicks) {
            entityTicks = 0;
            refreshEntitySignals();
        }

        if (now - lastFeed >= c.scanDelay) {
            lastFeed = now;
            feed(now, c);
        }

        if (now - lastCleanup > c.cleanupMs) {
            performCleanup();
            lastCleanup = now;
        }
    }

    private void refreshEntitySignals() {
        Map<ChunkPos, EntitySignals> tally = new HashMap<>();

        for (Entity entity : mc.level.entitiesForRendering()) {
            ChunkPos pos = ChunkPos.containing(entity.blockPosition());
            EntitySignals signals = tally.computeIfAbsent(pos, key -> new EntitySignals());

            if (entity instanceof ItemEntity && entity.getY() < 0.0) signals.droppedItems++;
            if (entity instanceof PolarBear) signals.polarBears += 3;
            if (!(entity instanceof Player)) signals.mobs++;
        }

        entitySignals.keySet().retainAll(tally.keySet());
        entitySignals.putAll(tally);
    }

    private void feed(long now, Cfg c) {
        int free = c.maxConcurrentScans - activeScans.get();
        if (free <= 0) return;

        int radius = Math.min(c.scanRadius, mc.options.renderDistance().get() + 1);
        ChunkPos centre = mc.player.chunkPosition();
        List<LevelChunk> due = new ArrayList<>();

        for (int cx = centre.x() - radius; cx <= centre.x() + radius; cx++) {
            for (int cz = centre.z() - radius; cz <= centre.z() + radius; cz++) {
                ChunkPos pos = new ChunkPos(cx, cz);
                if (inFlight.contains(pos)) continue;
                Long at = scannedAt.get(pos);
                if (at != null && now - at < c.rescanMs) continue;

                LevelChunk chunk = mc.level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk != null) due.add(chunk);
            }
        }

        if (due.isEmpty()) return;

        due.sort(Comparator.comparingInt(ch -> {
            int dx = ch.getPos().x() - centre.x();
            int dz = ch.getPos().z() - centre.z();
            return dx * dx + dz * dz;
        }));

        for (int i = 0; i < Math.min(free, due.size()); i++) {
            LevelChunk chunk = due.get(i);
            ChunkPos pos = chunk.getPos();
            if (!inFlight.add(pos)) continue;
            scannedAt.put(pos, now);
            final Cfg snapshot = c;
            scannerPool.submit(() -> {
                activeScans.incrementAndGet();
                try {
                    analyzeChunk(chunk, snapshot);
                } catch (Throwable ignored) {
                } finally {
                    activeScans.decrementAndGet();
                    inFlight.remove(pos);
                }
            });
        }
    }

    private void analyzeChunk(LevelChunk chunk, Cfg c) {
        if (!shouldScan) return;
        ChunkPos pos = chunk.getPos();
        ChunkAnalysis analysis = new ChunkAnalysis();
        Map<BlockPos, BlockKind> found = new HashMap<>();

        scanBlockSignals(chunk, analysis, c, found);
        if (c.baseDetectors) scanBaseSignals(chunk, analysis);
        if (c.detectAmethyst) analysis.geode = scanGeode(chunk, c);
        if (c.magicAmethyst) analysis.magicSpots = scanMagicAmethyst(chunk, c);
        if (c.detectStructure) scanStructure(chunk, analysis);

        if (c.highlightBlocks) {
            suspiciousBlocks.keySet().removeIf(bp -> (bp.getX() >> 4) == pos.x() && (bp.getZ() >> 4) == pos.z());
            suspiciousBlocks.putAll(found);
        }

        evaluate(pos, analysis, c);
    }

    private void scanBlockSignals(LevelChunk chunk, ChunkAnalysis analysis, Cfg c, Map<BlockPos, BlockKind> found) {
        LevelChunkSection[] sections = chunk.getSections();
        int originX = chunk.getPos().getMinBlockX();
        int originZ = chunk.getPos().getMinBlockZ();
        int bottom = chunk.getMinY();
        boolean inEnd = mc.level != null && mc.level.dimension() == Level.END;

        for (int index = 0; index < sections.length; index++) {
            if (!shouldScan) return;

            LevelChunkSection section = sections[index];
            if (section == null || section.hasOnlyAir()) continue;

            int sectionBottom = bottom + index * 16;
            if (sectionBottom > 128 || sectionBottom + 15 < 0) continue;

            for (int y = 0; y < 16; y++) {
                int worldY = sectionBottom + y;
                if (worldY < 0 || worldY > 128) continue;

                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (state.isAir()) continue;

                        classifyBlock(new BlockPos(originX + x, worldY, originZ + z), state, analysis, inEnd, c, found);
                    }
                }
            }
        }
    }

    private void classifyBlock(BlockPos blockPos, BlockState state, ChunkAnalysis analysis, boolean inEnd, Cfg c, Map<BlockPos, BlockKind> found) {
        if (c.ignoreTrialChambers && isTrialChamberBlock(state)) analysis.trialChamber++;

        boolean interesting = state.is(Blocks.DEEPSLATE) || state.is(Blocks.COBBLED_DEEPSLATE) || state.is(Blocks.END_STONE);
        if (!interesting) return;

        if (c.ignoreExposed && isExposed(blockPos)) return;

        BlockKind kind = null;

        if (c.detectDeepslate && isPlainDeepslate(state) && !isInLongDeepslateRun(blockPos, blockPos.getY())) {
            analysis.deepslate++;
            kind = BlockKind.DEEPSLATE;
        }

        if (c.detectRotatedDeepslate && isRotatedDeepslate(state)) {
            analysis.rotatedDeepslate++;
            kind = BlockKind.ROTATED_DEEPSLATE;
        }

        if (c.detectCobbledDeepslate && state.is(Blocks.COBBLED_DEEPSLATE)) {
            analysis.cobbledDeepslate++;
            kind = BlockKind.COBBLED_DEEPSLATE;
        }

        if (c.detectEndStone && state.is(Blocks.END_STONE) && !inEnd) {
            analysis.endStone++;
            kind = BlockKind.END_STONE;
        }

        if (kind != null && c.highlightBlocks) found.put(blockPos, kind);
    }

    private boolean isExposed(BlockPos pos) {
        if (mc.level == null) return false;

        for (Direction dir : Direction.values()) {
            BlockPos offset = pos.relative(dir);
            if (offset.getY() < mc.level.getMinY() || offset.getY() >= mc.level.getMaxY()) continue;

            BlockState neighbour = mc.level.getBlockState(offset);
            if (neighbour.isAir()) return true;

            FluidState fluid = neighbour.getFluidState();
            if (fluid != null && !fluid.isEmpty()) return true;
        }

        return false;
    }

    private boolean isInLongDeepslateRun(BlockPos pos, int worldY) {
        if (mc.level == null) return false;

        int limit = worldY > -8 ? 50 : 20;

        if (runLength(pos, Direction.EAST, Direction.WEST, limit) >= limit) return true;
        if (runLength(pos, Direction.SOUTH, Direction.NORTH, limit) >= limit) return true;

        return worldY > 0 && runLength(pos, Direction.UP, Direction.DOWN, limit) >= limit;
    }

    private int runLength(BlockPos pos, Direction forward, Direction back, int limit) {
        int total = 1;

        for (Direction dir : new Direction[]{forward, back}) {
            for (int i = 1; i < limit; i++) {
                BlockPos next = pos.relative(dir, i);
                if (next.getY() < mc.level.getMinY() || next.getY() >= mc.level.getMaxY()) break;
                if (!isPlainDeepslate(mc.level.getBlockState(next))) break;
                total++;
            }
        }

        return total;
    }

    private void scanBaseSignals(LevelChunk chunk, ChunkAnalysis analysis) {
        ChunkPos pos = chunk.getPos();
        int originX = pos.getMinBlockX();
        int originZ = pos.getMinBlockZ();
        int bottom = chunk.getMinY();
        int top = Math.min(chunk.getMinY() + chunk.getHeight() - 1, 60);

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                if (!shouldScan) return;

                int worldX = originX + x;
                int worldZ = originZ + z;

                int airRun = 0;
                int deepAirRun = 0;
                int oreColumn = 0;

                for (int y = bottom; y <= top; y++) {
                    cursor.set(worldX, y, worldZ);
                    BlockState state = chunk.getBlockState(cursor);
                    Block block = state.getBlock();

                    if (block == Blocks.AIR || block == Blocks.CAVE_AIR) {
                        airRun++;
                        if (y < 0) deepAirRun++;
                    } else {
                        if (airRun >= 12 && y < 20) analysis.airPocket += airRun / 4;
                        airRun = 0;
                        if (y < 0) deepAirRun = 0;
                    }

                    if (y < 0) {
                        if (block == Blocks.COBBLESTONE || block == Blocks.COBBLED_DEEPSLATE || block == Blocks.MOSSY_COBBLESTONE) {
                            cursor.set(worldX + 1, y, worldZ);
                            if (worldX + 1 < originX + 16 && chunk.getBlockState(cursor).getBlock() == block) analysis.cobbleLines++;
                            cursor.set(worldX, y, worldZ);
                        }

                        if (block == Blocks.POLISHED_DEEPSLATE || block == Blocks.DEEPSLATE_BRICKS
                            || block == Blocks.DEEPSLATE_TILES || block == Blocks.CHISELED_DEEPSLATE) {
                            analysis.deepslateBand++;
                        }

                        if (isStair(block)) analysis.stairs++;
                    }

                    if (y < 20) {
                        if (block == Blocks.OBSIDIAN) analysis.obsidian++;
                        if (isPiston(block)) analysis.pistons++;
                    }

                    if (y < 0 && (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST)) analysis.deepChests++;

                    if (isOre(block)) {
                        oreColumn++;

                        if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE || block == Blocks.ANCIENT_DEBRIS) {
                            analysis.valuableOre++;
                        }

                        if (isHiddenOre(chunk, worldX, y, worldZ)) analysis.hiddenOre += 2;
                    } else {
                        oreColumn = 0;
                    }

                    if (oreColumn == 4) analysis.vertVeins++;
                }

                if (deepAirRun >= 10) analysis.needles++;
            }
        }

        for (BlockEntity blockEntity : new ArrayList<>(chunk.getBlockEntities().values())) {
            if (blockEntity instanceof ChestBlockEntity || blockEntity instanceof HopperBlockEntity) analysis.storage++;
        }

        EntitySignals signals = entitySignals.get(pos);
        if (signals == null) return;

        analysis.droppedItems = signals.droppedItems;
        analysis.polarBears = signals.polarBears;
        if (signals.mobs > 20) analysis.mobCluster = signals.mobs / 5;
    }

    private int scanMagicAmethyst(LevelChunk chunk, Cfg c) {
        Level level = mc.level;
        if (level == null) return 0;

        ChunkPos pos = chunk.getPos();
        int originX = pos.getMinBlockX();
        int originZ = pos.getMinBlockZ();
        int bottom = Math.max(GEODE_MIN_Y, chunk.getMinY());
        int top = Math.min(GEODE_MAX_Y, chunk.getMinY() + chunk.getHeight() - 1);

        long sumX = 0;
        long sumY = 0;
        long sumZ = 0;
        int count = 0;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int y = bottom; y <= top; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    cursor.set(originX + x, y, originZ + z);
                    BlockState state = chunk.getBlockState(cursor);

                    if (!state.is(Blocks.AMETHYST_BLOCK) && !state.is(Blocks.BUDDING_AMETHYST)) continue;

                    sumX += cursor.getX();
                    sumY += cursor.getY();
                    sumZ += cursor.getZ();
                    count++;
                }
            }
        }

        if (count == 0) return 0;

        int centreX = (int) (sumX / count);
        int centreY = (int) (sumY / count);
        int centreZ = (int) (sumZ / count);
        int radius = c.magicScanRadius;
        int spots = 0;

        BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (!shouldScan) return spots;

                    cursor.set(centreX + dx, centreY + dy, centreZ + dz);

                    BlockState state = level.getBlockState(cursor);
                    if (!state.is(Blocks.AIR) && !state.is(Blocks.AMETHYST_CLUSTER)) continue;
                    if (level.getBrightness(LightLayer.BLOCK, cursor) != 0) continue;

                    BlockPos darkAir = null;
                    boolean lit = false;

                    for (Direction direction : Direction.values()) {
                        neighbour.set(cursor.getX() + direction.getStepX(), cursor.getY() + direction.getStepY(), cursor.getZ() + direction.getStepZ());
                        int light = level.getBrightness(LightLayer.BLOCK, neighbour);

                        if (light > MAGIC_NEIGHBOUR_LIGHT) {
                            lit = true;
                            break;
                        }

                        if (darkAir == null && light == 0 && level.getBlockState(neighbour).is(Blocks.AIR)) {
                            darkAir = neighbour.immutable();
                        }
                    }

                    if (lit || darkAir == null) continue;

                    for (Direction direction : Direction.values()) {
                        neighbour.set(darkAir.getX() + direction.getStepX(), darkAir.getY() + direction.getStepY(), darkAir.getZ() + direction.getStepZ());
                        if (level.getBrightness(LightLayer.BLOCK, neighbour) > MAGIC_NEIGHBOUR_LIGHT) {
                            lit = true;
                            break;
                        }
                    }

                    if (lit) continue;

                    spots++;
                }
            }
        }

        return spots;
    }

    private GeodeResult scanGeode(LevelChunk chunk, Cfg c) {
        LevelChunkSection[] sections = chunk.getSections();
        int bottom = chunk.getMinY();

        byte[] cells = new byte[16 * GEODE_HEIGHT * 16];
        List<int[]> crystals = new ArrayList<>();
        int matrix = 0;
        long sumX = 0;
        long sumY = 0;
        long sumZ = 0;

        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (section == null || section.hasOnlyAir()) continue;

            int sectionBottom = bottom + index * 16;
            if (sectionBottom > GEODE_MAX_Y || sectionBottom + 15 < GEODE_MIN_Y) continue;

            for (int y = 0; y < 16; y++) {
                int worldY = sectionBottom + y;
                if (worldY < GEODE_MIN_Y || worldY > GEODE_MAX_Y) continue;

                int cellY = worldY - GEODE_MIN_Y;

                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);

                        if (state.is(Blocks.BUDDING_AMETHYST)) return GeodeResult.NONE;

                        byte cell;

                        if (isCrystal(state)) {
                            cell = CELL_CRYSTAL;
                            crystals.add(new int[]{x, worldY, z});
                            sumX += x;
                            sumY += worldY;
                            sumZ += z;
                        } else if (state.is(Blocks.CALCITE)) {
                            cell = CELL_CALCITE;
                            matrix++;
                        } else if (state.is(Blocks.SMOOTH_BASALT)) {
                            cell = CELL_BASALT;
                            matrix++;
                        } else if (state.isAir()) {
                            cell = CELL_AIR;
                        } else {
                            cell = CELL_SOLID;
                        }

                        cells[cellIndex(x, cellY, z)] = cell;
                    }
                }
            }
        }

        int count = crystals.size();
        if (count == 0) return GeodeResult.NONE;

        int sens = c.amethystSensitivity;
        int minCrystals = (int) (40.0 - (sens - 1) * 3.5555555555555554);
        if (!c.amethystSmartCheck) return new GeodeResult(count >= minCrystals, count);

        double minDensity = 0.015 - (sens - 1) * 0.0013333333;
        int minMatrix = (int) (15.0 - (sens - 1) * 1.3333333333333333);
        double maxExposure = 0.08 + (sens - 1) * 0.027;
        double maxSpread = 6.0 + (sens - 1) * 0.44444445;

        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;

        for (int[] crystal : crystals) {
            lowest = Math.min(lowest, crystal[1]);
            highest = Math.max(highest, crystal[1]);
        }

        double density = count / (256.0 * Math.max(1, highest - lowest + 1));
        double centreX = (double) sumX / count;
        double centreY = (double) sumY / count;
        double centreZ = (double) sumZ / count;
        double spread = 0.0;

        for (int[] crystal : crystals) {
            double dx = crystal[0] - centreX;
            double dy = crystal[1] - centreY;
            double dz = crystal[2] - centreZ;
            spread += Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        spread /= count;

        int openFaces = 0;
        int buriedFaces = 0;

        for (int[] crystal : crystals) {
            int cx = crystal[0];
            int cy = crystal[1] - GEODE_MIN_Y;
            int cz = crystal[2];

            for (int face = 0; face < 6; face++) {
                int nx = cx + NEIGHBOUR_X[face];
                int ny = cy + NEIGHBOUR_Y[face];
                int nz = cz + NEIGHBOUR_Z[face];

                if (nx < 0 || nx > 15 || nz < 0 || nz > 15 || ny < 0 || ny >= GEODE_HEIGHT) continue;

                byte cell = cells[cellIndex(nx, ny, nz)];
                if (cell == CELL_AIR) openFaces++;
                else if (cell >= CELL_CALCITE) buriedFaces++;
            }
        }

        int faces = openFaces + buriedFaces;
        double exposure = faces > 0 ? (double) openFaces / faces : 0.0;

        boolean hit = count >= minCrystals
            && density >= minDensity
            && matrix >= minMatrix
            && spread <= maxSpread
            && exposure <= maxExposure;

        return new GeodeResult(hit, count);
    }

    private static int cellIndex(int x, int y, int z) {
        return (y * 16 + x) * 16 + z;
    }

    private void scanStructure(LevelChunk chunk, ChunkAnalysis analysis) {
        for (BlockEntity blockEntity : new ArrayList<>(chunk.getBlockEntities().values())) {
            BlockState state = chunk.getBlockState(blockEntity.getBlockPos());

            if (state.is(Blocks.BEEHIVE) || state.is(Blocks.BEE_NEST)) {
                analysis.hives++;
                if (blockEntity instanceof BeehiveBlockEntity hive) analysis.bees += Math.max(0, hive.getOccupantCount());
            } else if (isWorkstation(state.getBlock())) {
                analysis.workstations++;
            }
        }

        LevelChunkSection[] sections = chunk.getSections();
        int bottom = chunk.getMinY();

        for (int index = 0; index < sections.length; index++) {
            if (!shouldScan) return;

            LevelChunkSection section = sections[index];
            if (section == null || section.hasOnlyAir()) continue;
            if (!section.maybeHas(SuspiciousChunks::isStructureBlock)) continue;

            int sectionBottom = bottom + index * 16;

            for (int y = 0; y < 16; y++) {
                int worldY = sectionBottom + y;

                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);

                        if (state.is(Blocks.REPEATER)) {
                            analysis.repeaters++;
                            if (state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED)) {
                                analysis.poweredRepeaters++;
                            }
                        } else if (state.is(Blocks.VINE)) {
                            analysis.vines++;
                        } else if (state.is(Blocks.COBBLED_DEEPSLATE)) {
                            if (worldY >= 0 && worldY <= 20) analysis.deepslateFloor++;
                        } else if (state.is(Blocks.SEAGRASS) || state.is(Blocks.TALL_SEAGRASS)) {
                            if (worldY <= 70) analysis.seagrass++;
                        } else if (state.is(BlockTags.FLOWERS)) {
                            if (worldY <= 70) analysis.flowers++;
                        } else if (isWorkstation(state.getBlock())) {
                            analysis.workstations++;
                        }
                    }
                }
            }
        }
    }

    private static boolean isStructureBlock(BlockState state) {
        return state.is(Blocks.REPEATER)
            || state.is(Blocks.VINE)
            || state.is(Blocks.COBBLED_DEEPSLATE)
            || state.is(Blocks.SEAGRASS)
            || state.is(Blocks.TALL_SEAGRASS)
            || state.is(BlockTags.FLOWERS)
            || isWorkstation(state.getBlock());
    }

    private int structureScore(ChunkAnalysis a, Cfg c) {
        int score = 0;

        if (c.structBeehive) score += Math.min(90, a.hives * 22 + a.bees * 10);
        if (c.structDeepslateFloor) score += Math.min(65, a.deepslateFloor / 2);
        if (c.structVines) score += Math.min(55, a.vines / 4);
        if (c.structSeagrass) score += Math.min(70, a.seagrass);
        if (c.structFlowers) score += Math.min(75, a.flowers * 12);
        if (c.structRepeater) score += Math.min(90, a.repeaters * 16 + a.poweredRepeaters * 28);
        if (c.structStorage) score += Math.min(100, a.workstations * 18);

        return score;
    }

    private static int baseChance(int score) {
        return Math.max(20, Math.min(99, 25 + score / 2));
    }

    private boolean isHiddenOre(LevelChunk chunk, int x, int y, int z) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        ChunkPos pos = chunk.getPos();
        int enclosed = 0;

        for (Direction direction : Direction.values()) {
            cursor.set(x + direction.getStepX(), y + direction.getStepY(), z + direction.getStepZ());

            if (cursor.getX() < pos.getMinBlockX() || cursor.getX() > pos.getMaxBlockX()) continue;
            if (cursor.getZ() < pos.getMinBlockZ() || cursor.getZ() > pos.getMaxBlockZ()) continue;

            Block neighbour = chunk.getBlockState(cursor).getBlock();

            if (neighbour == Blocks.COBBLESTONE || neighbour == Blocks.COBBLED_DEEPSLATE || neighbour == Blocks.STONE
                || neighbour == Blocks.DEEPSLATE || neighbour == Blocks.OBSIDIAN || neighbour == Blocks.NETHERRACK) {
                enclosed++;
            }
        }

        return enclosed >= 5;
    }

    private static boolean isCrystal(BlockState state) {
        return state.is(Blocks.AMETHYST_CLUSTER)
            || state.is(Blocks.LARGE_AMETHYST_BUD)
            || state.is(Blocks.MEDIUM_AMETHYST_BUD)
            || state.is(Blocks.SMALL_AMETHYST_BUD)
            || state.is(Blocks.AMETHYST_BLOCK);
    }

    private static boolean isWorkstation(Block block) {
        return block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.BARREL
            || block == Blocks.ENDER_CHEST || block == Blocks.FURNACE || block == Blocks.BLAST_FURNACE
            || block == Blocks.SMOKER || block == Blocks.CRAFTING_TABLE || block == Blocks.ENCHANTING_TABLE
            || block == Blocks.ANVIL || block == Blocks.CHIPPED_ANVIL || block == Blocks.DAMAGED_ANVIL;
    }

    private static boolean isPiston(Block block) {
        return block == Blocks.PISTON || block == Blocks.STICKY_PISTON
            || block == Blocks.MOVING_PISTON || block == Blocks.PISTON_HEAD;
    }

    private static boolean isOre(Block block) {
        return block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE
            || block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE
            || block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE
            || block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE
            || block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE
            || block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE
            || block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE
            || block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE
            || block == Blocks.ANCIENT_DEBRIS;
    }

    private static boolean isStair(Block block) {
        return block == Blocks.STONE_STAIRS || block == Blocks.COBBLESTONE_STAIRS
            || block == Blocks.DEEPSLATE_BRICK_STAIRS || block == Blocks.POLISHED_DEEPSLATE_STAIRS
            || block == Blocks.OAK_STAIRS || block == Blocks.SPRUCE_STAIRS
            || block == Blocks.DARK_OAK_STAIRS || block == Blocks.COBBLED_DEEPSLATE_STAIRS;
    }

    private static boolean isPlainDeepslate(BlockState state) {
        if (!state.is(Blocks.DEEPSLATE) || !state.hasProperty(BlockStateProperties.AXIS)) return false;
        return state.getValue(BlockStateProperties.AXIS) == Direction.Axis.Y;
    }

    private static boolean isRotatedDeepslate(BlockState state) {
        if (!state.is(Blocks.DEEPSLATE) || !state.hasProperty(BlockStateProperties.AXIS)) return false;
        return state.getValue(BlockStateProperties.AXIS) != Direction.Axis.Y;
    }

    private static boolean isTrialChamberBlock(BlockState state) {
        return TRIAL_BLOCKS.contains(state.getBlock());
    }

    private int scoreBaseSignals(ChunkAnalysis a, List<String> tags, Cfg c) {
        int score = 0;

        if (c.detCobble && a.cobbleLines >= 3) {
            score += Math.min(a.cobbleLines, 6);
            tags.add("CobbleLine(" + a.cobbleLines + ")");
        }

        if (c.detDeepslateBand && a.deepslateBand >= 4) {
            score += Math.min(a.deepslateBand / 2, 5);
            tags.add("Deepslate(" + a.deepslateBand + ")");
        }

        if (c.detObsidian && a.obsidian >= 3) {
            score += Math.min(a.obsidian, 6);
            tags.add("Obsidian(" + a.obsidian + ")");
        }

        if (c.detVeinInt && a.valuableOre >= 1 && a.storage >= 1) {
            score += 4;
            tags.add("VeinInt");
        }

        if (c.detVertVein && a.vertVeins >= 2) {
            score += a.vertVeins;
            tags.add("VertVein(" + a.vertVeins + ")");
        }

        if (c.detNeedle && a.needles >= 2) {
            score += a.needles * 2;
            tags.add("Needle(" + a.needles + ")");
        }

        if (c.detHiddenOre && a.hiddenOre >= 2) {
            score += a.hiddenOre;
            tags.add("HiddenOre(" + a.hiddenOre + ")");
        }

        if (c.detPiston && a.pistons >= 3) {
            score += Math.min(a.pistons, 8);
            tags.add("PistonArray(" + a.pistons + ")");
        }

        if (c.detMobCluster && a.mobCluster >= 2) {
            score += a.mobCluster;
            tags.add("MobCluster(" + a.mobCluster * 5 + ")");
        }

        if (c.detDropped && a.droppedItems >= 2) {
            score += a.droppedItems * 2;
            tags.add("Dropped(" + a.droppedItems + ")");
        }

        if (c.detAirPocket && a.airPocket >= 5) {
            score += a.airPocket / 3;
            tags.add("AirPocket(" + a.airPocket + ")");
        }

        if (c.detStair && a.stairs >= 5) {
            score += a.stairs / 3;
            tags.add("Stairs(" + a.stairs + ")");
        }

        if (c.detPolarBear && a.polarBears > 0) {
            score += a.polarBears;
            tags.add("PolarBear");
        }

        if (c.detChestCluster && a.deepChests >= 10) {
            score += Math.min(a.deepChests, 12);
            tags.add("ChestCluster(" + a.deepChests + ")");
        }

        if (a.storage >= 3) {
            score += a.storage;
            tags.add("Storage(" + a.storage + ")");
        }

        return score;
    }

    private void evaluate(ChunkPos pos, ChunkAnalysis analysis, Cfg c) {
        if (c.ignoreTrialChambers && analysis.trialChamber >= c.trialChamberThreshold) {
            if (c.trialChamberAlerts) {
                notify(pos, String.format("Trial chamber - Copper/Tuff blocks: %d", analysis.trialChamber), true, c);
            }

            forget(pos);
            return;
        }

        StringBuilder reasons = new StringBuilder();

        if (c.detectDeepslate && analysis.deepslate >= c.deepslateThreshold) {
            reasons.append("Deepslate[").append(analysis.deepslate).append("] ");
        }

        if (c.detectCobbledDeepslate && analysis.cobbledDeepslate >= c.cobbledDeepslateThreshold) {
            reasons.append("CobbledDeepslate[").append(analysis.cobbledDeepslate).append("] ");
        }

        if (c.detectRotatedDeepslate && analysis.rotatedDeepslate >= c.rotatedDeepslateThreshold) {
            reasons.append("RotatedDeepslate[").append(analysis.rotatedDeepslate).append("] ");
        }

        if (c.detectEndStone && analysis.endStone >= c.endStoneThreshold) {
            reasons.append("EndStone[").append(analysis.endStone).append("] ");
        }

        if (!reasons.isEmpty()) {
            if (flaggedChunks.add(pos)) notify(pos, reasons.toString().trim(), false, c);
        } else {
            flaggedChunks.remove(pos);
        }

        if (c.baseDetectors) {
            List<String> tags = new ArrayList<>();
            int score = scoreBaseSignals(analysis, tags, c);

            if (score >= c.minScore) {
                if (baseFlaggedChunks.add(pos)) notify(pos, "Possible base, score " + score + " [" + String.join(", ", tags) + "]", false, c);
            } else {
                baseFlaggedChunks.remove(pos);
            }
        } else {
            baseFlaggedChunks.remove(pos);
        }

        boolean strippedGeode = c.detectAmethyst && analysis.geode.hit();
        boolean magicGeode = c.magicAmethyst && analysis.magicSpots >= c.magicThreshold;

        if (strippedGeode || magicGeode) {
            if (amethystChunks.add(pos)) {
                String tag = strippedGeode ? "StrippedGeode[" + analysis.geode.crystals() + "]" : "";
                if (magicGeode) tag = (tag.isEmpty() ? "" : tag + " ") + "MagicAmethyst[" + analysis.magicSpots + "]";
                notify(pos, tag, false, c);
            }
        } else {
            amethystChunks.remove(pos);
        }

        if (c.detectStructure) {
            int score = structureScore(analysis, c);
            int chance = baseChance(score);

            if (chance >= c.baseChanceThreshold && score > 0) {
                if (structureChunks.add(pos)) {
                    String tag = analysis.poweredRepeaters >= 3 ? "Repeater chunk" : "Base chance " + chance + "%";
                    notify(pos, tag + " (score " + score + ")", false, c);
                }
            } else {
                structureChunks.remove(pos);
            }
        } else {
            structureChunks.remove(pos);
        }
    }

    private void forget(ChunkPos pos) {
        flaggedChunks.remove(pos);
        baseFlaggedChunks.remove(pos);
        amethystChunks.remove(pos);
        structureChunks.remove(pos);
        notificationTimes.remove(pos);
    }

    private void notify(ChunkPos pos, String details, boolean trialChamber, Cfg c) {
        long now = System.currentTimeMillis();

        if (recentAlerts.size() >= c.maxAlerts) return;

        Long last = notificationTimes.get(pos);
        if (!trialChamber && last != null && now - last < 45000L) return;

        String message = String.format("SuspiciousChunks [%d, %d] - %s", pos.x(), pos.z(), details);
        boolean allowChat = trialChamber ? c.trialChamberAlerts : c.chatAlerts;

        recentAlerts.offer(now);
        notificationTimes.put(pos, now);

        mc.execute(() -> {
            if (allowChat && mc.player != null) mc.player.sendSystemMessage(Component.literal(message));
            if (c.soundAlerts) mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.5f));
        });
    }

    private void performCleanup() {
        if (mc.player == null) return;

        int viewDist = mc.options.renderDistance().get();
        ChunkPos centre = mc.player.chunkPosition();

        flaggedChunks.removeIf(pos -> beyond(pos, centre, viewDist + 5));
        baseFlaggedChunks.removeIf(pos -> beyond(pos, centre, viewDist + 5));
        amethystChunks.removeIf(pos -> beyond(pos, centre, viewDist + 5));
        structureChunks.removeIf(pos -> beyond(pos, centre, viewDist + 5));
        scannedAt.keySet().removeIf(pos -> beyond(pos, centre, viewDist + 3));
        entitySignals.keySet().removeIf(pos -> beyond(pos, centre, viewDist + 5));
        notificationTimes.keySet().removeIf(pos -> beyond(pos, centre, viewDist + 5));

        double limit = viewDist * 16.0 + 80.0;
        double px = mc.player.getX();
        double pz = mc.player.getZ();
        suspiciousBlocks.keySet().removeIf(pos -> Math.hypot(pos.getX() + 0.5 - px, pos.getZ() + 0.5 - pz) > limit);
    }

    private static boolean beyond(ChunkPos pos, ChunkPos centre, int radius) {
        return Math.abs(pos.x() - centre.x()) > radius || Math.abs(pos.z() - centre.z()) > radius;
    }

    public void renderImGuiOverlay() {
        if (!isToggled() || mc.player == null || mc.level == null || mc.gameRenderer == null) return;

        net.minecraft.client.Camera camera = mc.gameRenderer.mainCamera();
        net.minecraft.world.phys.Vec3 cam = camera.position();
        camera.getViewRotationProjectionMatrix(matrix);
        float dw = ImGui.getIO().getDisplaySizeX();
        float dh = ImGui.getIO().getDisplaySizeY();
        ImDrawList dl = ImGui.getBackgroundDrawList();

        boolean fill = renderFill.getValBoolean();
        boolean outline = renderOutline.getValBoolean();
        int fillAlpha = (int) (fillOpacity.getValDouble() * 2.55);
        float width = (float) lineWidth.getValDouble();

        double y = renderY.getValDouble();
        double h = thickness.getValDouble();
        int limit = (int) maxChunksToRender.getValDouble();

        drawChunks(dl, flaggedChunks, chunkColor.getValColor(), cam, y, h, limit, fill, outline, fillAlpha, width, dw, dh);
        drawChunks(dl, baseFlaggedChunks, baseChunkColor.getValColor(), cam, y, h, limit, fill, outline, fillAlpha, width, dw, dh);
        drawChunks(dl, amethystChunks, amethystChunkColor.getValColor(), cam, y, h, limit, fill, outline, fillAlpha, width, dw, dh);
        drawChunks(dl, structureChunks, structureChunkColor.getValColor(), cam, y, h, limit, fill, outline, fillAlpha, width, dw, dh);

        if (!highlightBlocks.getValBoolean() || suspiciousBlocks.isEmpty()) return;

        int blockLimit = (int) maxBlocksToRender.getValDouble();
        boolean bFill = blockFill.getValBoolean();
        double maxDist = mc.options.renderDistance().get() * 16.0;
        double maxDistSq = maxDist * maxDist;
        int drawn = 0;

        for (Map.Entry<BlockPos, BlockKind> entry : suspiciousBlocks.entrySet()) {
            if (drawn >= blockLimit) break;

            BlockPos p = entry.getKey();
            double ddx = p.getX() + 0.5 - cam.x, ddy = p.getY() + 0.5 - cam.y, ddz = p.getZ() + 0.5 - cam.z;
            if (ddx * ddx + ddy * ddy + ddz * ddz > maxDistSq) continue;

            int col = switch (entry.getValue()) {
                case DEEPSLATE -> deepslateBlockColor.getValColor();
                case COBBLED_DEEPSLATE -> cobbledDeepslateBlockColor.getValColor();
                case ROTATED_DEEPSLATE -> rotatedDeepslateBlockColor.getValColor();
                case END_STONE -> endStoneBlockColor.getValColor();
            };

            if (drawBox(dl, p.getX() - cam.x, p.getY() - cam.y, p.getZ() - cam.z, 1.0, 1.0, 1.0, col, bFill, true, fillAlpha, width, dw, dh)) drawn++;
        }
    }

    private void drawChunks(ImDrawList dl, Set<ChunkPos> chunks, int argb, net.minecraft.world.phys.Vec3 cam, double y, double h,
                            int limit, boolean fill, boolean outline, int fillAlpha, float width, float dw, float dh) {
        if (chunks.isEmpty()) return;

        int drawn = 0;
        for (ChunkPos pos : chunks) {
            if (drawn++ >= limit) break;
            drawBox(dl, pos.getMinBlockX() - cam.x, y - cam.y, pos.getMinBlockZ() - cam.z, 16.0, h, 16.0, argb, fill, outline, fillAlpha, width, dw, dh);
        }
    }

    private boolean drawBox(ImDrawList dl, double rx, double ry, double rz, double sx, double sy, double sz, int argb,
                            boolean fill, boolean outline, int fillAlpha, float width, float dw, float dh) {
        boolean anyFront = false;
        for (int i = 0; i < 8; i++) {
            clip[i].set((float) (rx + ((i & 1) != 0 ? sx : 0.0)), (float) (ry + (((i >> 1) & 1) != 0 ? sy : 0.0)),
                    (float) (rz + (((i >> 2) & 1) != 0 ? sz : 0.0)), 1f);
            matrix.transform(clip[i]);
            if (clip[i].w > NEAR) anyFront = true;
        }
        if (!anyFront) return false;

        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        int line = RenderUtils.toImGuiColor(r, g, b, 235);
        int shade = RenderUtils.toImGuiColor(r, g, b, fillAlpha);

        if (fill && fillAlpha > 0) {
            for (int f = 0; f < 6; f++) fillFace(dl, FACES[f], shade, dw, dh);
        }
        if (outline) {
            for (int[] e : EDGES) drawEdge(dl, clip[e[0]], clip[e[1]], dw, dh, line, width);
        }
        return true;
    }

    private void fillFace(ImDrawList dl, int[] q, int color, float dw, float dh) {
        int n = 0;
        for (int i = 0; i < 4; i++) {
            org.joml.Vector4f a = clip[q[i]];
            org.joml.Vector4f b = clip[q[(i + 1) & 3]];
            boolean ain = a.w > NEAR, bin = b.w > NEAR;
            if (ain) {
                polyX[n] = a.x;
                polyY[n] = a.y;
                polyW[n] = a.w;
                n++;
            }
            if (ain != bin) {
                float t = (NEAR - a.w) / (b.w - a.w);
                polyX[n] = a.x + (b.x - a.x) * t;
                polyY[n] = a.y + (b.y - a.y) * t;
                polyW[n] = NEAR;
                n++;
            }
        }
        if (n < 3) return;
        for (int i = 0; i < n; i++) {
            float px = (polyX[i] / polyW[i] + 1f) * 0.5f * dw;
            float py = (1f - polyY[i] / polyW[i]) * 0.5f * dh;
            if (!Float.isFinite(px) || !Float.isFinite(py) || Math.abs(px) > 50000f || Math.abs(py) > 50000f) return;
            polyPoints[i].set(px, py);
        }
        dl.addConvexPolyFilled(polyPoints, n, color);
    }

    private static void drawEdge(ImDrawList dl, org.joml.Vector4f a, org.joml.Vector4f b, float dw, float dh, int color, float width) {
        float ax = a.x, ay = a.y, aw = a.w, bx = b.x, by = b.y, bw = b.w;
        if (aw < NEAR && bw < NEAR) return;
        if (aw < NEAR) {
            float t = (NEAR - aw) / (bw - aw);
            ax += (bx - ax) * t;
            ay += (by - ay) * t;
            aw = NEAR;
        } else if (bw < NEAR) {
            float t = (NEAR - bw) / (aw - bw);
            bx += (ax - bx) * t;
            by += (ay - by) * t;
            bw = NEAR;
        }
        float x1 = (ax / aw + 1f) * 0.5f * dw, y1 = (1f - ay / aw) * 0.5f * dh;
        float x2 = (bx / bw + 1f) * 0.5f * dw, y2 = (1f - by / bw) * 0.5f * dh;
        if (!Float.isFinite(x1) || !Float.isFinite(y1) || !Float.isFinite(x2) || !Float.isFinite(y2)) return;
        dl.addLine(x1, y1, x2, y2, color, width);
    }

    private record GeodeResult(boolean hit, int crystals) {
        static final GeodeResult NONE = new GeodeResult(false, 0);
    }

    private static final class EntitySignals {
        int droppedItems;
        int polarBears;
        int mobs;
    }

    private static final class ChunkAnalysis {
        int deepslate;
        int cobbledDeepslate;
        int rotatedDeepslate;
        int endStone;
        int trialChamber;

        int cobbleLines;
        int deepslateBand;
        int obsidian;
        int pistons;
        int stairs;
        int airPocket;
        int needles;
        int hiddenOre;
        int vertVeins;
        int valuableOre;
        int storage;
        int mobCluster;
        int droppedItems;
        int polarBears;
        int deepChests;

        int hives;
        int bees;
        int deepslateFloor;
        int vines;
        int seagrass;
        int flowers;
        int repeaters;
        int poweredRepeaters;
        int workstations;

        GeodeResult geode = GeodeResult.NONE;
        int magicSpots;
    }
}
