package com.eclipseware.imnotcheatingyouare.client.module.impl;

import com.eclipseware.imnotcheatingyouare.client.ImnotcheatingyouareClient;
import com.eclipseware.imnotcheatingyouare.client.module.Category;
import com.eclipseware.imnotcheatingyouare.client.module.Module;
import com.eclipseware.imnotcheatingyouare.client.setting.Setting;
import com.eclipseware.imnotcheatingyouare.client.setting.SettingsManager;
import com.eclipseware.imnotcheatingyouare.client.utils.RenderUtils;
import imgui.ImFont;
import imgui.ImGui;
import imgui.ImVec2;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import xyz.breadloaf.imguimc.imgui.ImguiLoader;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpawnerFinder extends Module {

    private record Hit(BlockPos pos, int openFaces) {}

    private record Label(String name, int rgb, long time) {}

    private static final int RESOLVES_PER_FRAME = 6;
    private static final long RELABEL_MS = 3000L;

    private final java.util.concurrent.ExecutorService scanner = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Marlow SpawnerFinder");
        t.setDaemon(true);
        return t;
    });
    private volatile List<Hit> hits = List.of();
    private volatile boolean scanning = false;
    private long lastScan = 0;
    private final Map<BlockPos, Label> labels = new HashMap<>();

    private final org.joml.Matrix4f matrix = new org.joml.Matrix4f();
    private final org.joml.Vector4f[] clip = new org.joml.Vector4f[8];
    {
        for (int i = 0; i < 8; i++) clip[i] = new org.joml.Vector4f();
    }
    private static final int[][] EDGES = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
    private static final int[][] FACES = {{0, 2, 6, 4}, {1, 5, 7, 3}, {0, 1, 5, 4}, {2, 6, 7, 3}, {0, 1, 3, 2}, {4, 6, 7, 5}};
    private static final net.minecraft.core.Direction[] FACE_DIRS = {
            net.minecraft.core.Direction.WEST, net.minecraft.core.Direction.EAST,
            net.minecraft.core.Direction.DOWN, net.minecraft.core.Direction.UP,
            net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.SOUTH};

    public SpawnerFinder() {
        super("SpawnerFinder", Category.Render, "Highlights mob spawners and labels which mob each one spawns.");

        SettingsManager sm = ImnotcheatingyouareClient.INSTANCE.settingsManager;
        sm.rSetting(new Setting("Range", this, 64.0, 8.0, 128.0, true));
        sm.rSetting(new Setting("Max Blocks", this, 200.0, 10.0, 1000.0, true));
        sm.rSetting(new Setting("Fill Opacity", this, 18.0, 0.0, 60.0, true));
        sm.rSetting(new Setting("Line Width", this, 1.5, 0.5, 4.0, false));
        sm.rSetting(new Setting("Fill", this, true));
        sm.rSetting(new Setting("Outline", this, true));
        sm.rSetting(new Setting("Tracers", this, false));
        sm.rSetting(new Setting("Labels", this, true));
        sm.rSetting(new Setting("Show Distance", this, true));
        sm.rSetting(new Setting("Spawner Color", this, new Color(255, 60, 60)));
    }

    private Setting setting(String name) {
        return ImnotcheatingyouareClient.INSTANCE.settingsManager.getSettingByName(this, name);
    }

    private double num(String name, double fallback) {
        Setting s = setting(name);
        return s != null ? s.getValDouble() : fallback;
    }

    private boolean bool(String name, boolean fallback) {
        Setting s = setting(name);
        return s != null ? s.getValBoolean() : fallback;
    }

    @Override
    public void onDisable() {
        hits = List.of();
        labels.clear();
    }

    @Override
    public void onRenderHUD(GuiGraphicsExtractor guiGraphics, Object tickDeltaObj) {
    }

    private void scheduleScan() {
        long now = System.currentTimeMillis();
        if (scanning || now - lastScan < 400) return;
        lastScan = now;
        scanning = true;
        final net.minecraft.client.multiplayer.ClientLevel level = mc.level;
        final BlockPos center = mc.player.blockPosition();
        final int range = (int) num("Range", 64);
        final int max = (int) num("Max Blocks", 200);
        scanner.execute(() -> {
            try {
                hits = scan(level, center, range, max);
            } catch (Throwable ignored) {
            } finally {
                scanning = false;
            }
        });
    }

    private static List<Hit> scan(net.minecraft.client.multiplayer.ClientLevel level, BlockPos center, int range, int max) {
        List<Hit> found = new ArrayList<>();
        int rsq = range * range;
        int cx0 = (center.getX() - range) >> 4, cx1 = (center.getX() + range) >> 4;
        int cz0 = (center.getZ() - range) >> 4, cz1 = (center.getZ() + range) >> 4;
        java.util.function.Predicate<BlockState> pred = st -> st.getBlock() == Blocks.SPAWNER;
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);
                if (chunk == null) continue;
                net.minecraft.world.level.chunk.LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    net.minecraft.world.level.chunk.LevelChunkSection section = sections[i];
                    if (section == null || section.hasOnlyAir() || !section.maybeHas(pred)) continue;
                    int sy = chunk.getSectionYFromSectionIndex(i) << 4;
                    if (sy + 15 < center.getY() - range || sy > center.getY() + range) continue;
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                Block b = section.getBlockState(x, y, z).getBlock();
                                if (b != Blocks.SPAWNER) continue;
                                int wx = (cx << 4) + x, wy = sy + y, wz = (cz << 4) + z;
                                int dx = wx - center.getX(), dy = wy - center.getY(), dz = wz - center.getZ();
                                if (dx * dx + dy * dy + dz * dz > rsq) continue;
                                BlockPos pos = new BlockPos(wx, wy, wz);
                                int open = 0;
                                for (int d = 0; d < 6; d++) {
                                    probe.setWithOffset(pos, FACE_DIRS[d]);
                                    if (level.getBlockState(probe).getBlock() != b) open |= 1 << d;
                                }
                                if (open == 0) continue;
                                found.add(new Hit(pos, open));
                            }
                        }
                    }
                }
            }
        }
        if (found.size() > max) {
            found.sort(java.util.Comparator.comparingDouble(h -> h.pos().distSqr(center)));
            found = new ArrayList<>(found.subList(0, max));
        }
        return found;
    }

    private int defaultRgb() {
        Setting s = setting("Spawner Color");
        return s != null ? s.getValColor() : 0xFFFF3C3C;
    }

    private static int colorForMob(String path, int fallback) {
        return switch (path) {
            case "zombie", "husk", "drowned", "zombie_villager" -> 0xFF5FD35F;
            case "skeleton", "stray", "bogged", "wither_skeleton" -> 0xFFD8D8D8;
            case "spider" -> 0xFFB23A3A;
            case "cave_spider" -> 0xFF2FC7C7;
            case "blaze" -> 0xFFFFA126;
            case "silverfish" -> 0xFF9AA5B1;
            case "creeper" -> 0xFF4CE04C;
            case "magma_cube" -> 0xFFE0602A;
            case "slime" -> 0xFF7FE07F;
            case "witch" -> 0xFFA84FD6;
            case "enderman" -> 0xFFB06BFF;
            default -> fallback;
        };
    }

    private Label resolve(BlockPos pos, long now, int fallback, int[] budget) {
        Label cached = labels.get(pos);
        if (cached != null && now - cached.time() < RELABEL_MS) return cached;
        if (budget[0] <= 0) return cached;
        budget[0]--;
        String name = "Spawner";
        int rgb = fallback;
        try {
            BlockEntity be = mc.level.getBlockEntity(pos);
            if (be instanceof SpawnerBlockEntity sbe) {
                Entity display = sbe.getSpawner().getOrCreateDisplayEntity(mc.level, pos);
                if (display != null) {
                    name = display.getType().getDescription().getString();
                    var key = BuiltInRegistries.ENTITY_TYPE.getKey(display.getType());
                    if (key != null) rgb = colorForMob(key.getPath(), fallback);
                }
            }
        } catch (Throwable ignored) {
        }
        Label label = new Label(name, rgb, now);
        labels.put(pos, label);
        return label;
    }

    public void renderImGuiOverlay() {
        if (!isToggled() || mc.player == null || mc.level == null || mc.gameRenderer == null) return;
        scheduleScan();
        List<Hit> list = hits;
        if (list.isEmpty()) return;

        boolean fill = bool("Fill", true);
        boolean outline = bool("Outline", true);
        boolean tracers = bool("Tracers", false);
        boolean showLabels = bool("Labels", true);
        boolean showDistance = bool("Show Distance", true);
        float width = (float) num("Line Width", 1.5);
        int fillAlpha = (int) (num("Fill Opacity", 18) * 2.55);
        int fallback = defaultRgb();

        net.minecraft.client.Camera camera = mc.gameRenderer.mainCamera();
        net.minecraft.world.phys.Vec3 cam = camera.position();
        camera.getViewRotationProjectionMatrix(matrix);
        float dw = ImGui.getIO().getDisplaySizeX();
        float dh = ImGui.getIO().getDisplaySizeY();
        imgui.ImDrawList dl = ImGui.getBackgroundDrawList();
        org.joml.Vector4f center = new org.joml.Vector4f();
        long now = System.currentTimeMillis();
        int[] budget = {RESOLVES_PER_FRAME};

        if (labels.size() > 512) labels.clear();

        ImFont font = showLabels ? ImguiLoader.getLabelFont() : null;
        if (font != null) ImGui.pushFont(font);
        try {
            for (Hit h : list) {
                BlockPos p = h.pos();
                float bx = (float) (p.getX() - cam.x), by = (float) (p.getY() - cam.y), bz = (float) (p.getZ() - cam.z);
                boolean anyFront = false;
                for (int i = 0; i < 8; i++) {
                    clip[i].set(bx + (i & 1), by + ((i >> 1) & 1), bz + ((i >> 2) & 1), 1f);
                    matrix.transform(clip[i]);
                    if (clip[i].w > 0.05f) anyFront = true;
                }
                if (!anyFront) continue;

                Label label = resolve(p, now, fallback, budget);
                int argb = label != null ? label.rgb() : fallback;
                int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
                int line = RenderUtils.toImGuiColor(r, g, b, 230);
                int shade = RenderUtils.toImGuiColor(r, g, b, fillAlpha);

                if (fill && fillAlpha > 0) {
                    for (int f = 0; f < 6; f++) {
                        if ((h.openFaces() & (1 << f)) == 0) continue;
                        int[] q = FACES[f];
                        if (clip[q[0]].w <= 0.05f || clip[q[1]].w <= 0.05f || clip[q[2]].w <= 0.05f || clip[q[3]].w <= 0.05f) continue;
                        dl.addQuadFilled(sx(q[0], dw), sy(q[0], dh), sx(q[1], dw), sy(q[1], dh), sx(q[2], dw), sy(q[2], dh), sx(q[3], dw), sy(q[3], dh), shade);
                    }
                }
                if (outline) {
                    for (int[] e : EDGES) {
                        if (!edgeVisible(h.openFaces(), e[0], e[1])) continue;
                        drawEdge(dl, clip[e[0]], clip[e[1]], dw, dh, line, width);
                    }
                }

                center.set(bx + 0.5f, by + 0.5f, bz + 0.5f, 1f);
                matrix.transform(center);
                if (center.w <= 0.05f) continue;
                float cx = (center.x / center.w + 1f) * 0.5f * dw;
                float cy = (1f - center.y / center.w) * 0.5f * dh;

                if (tracers) dl.addLine(dw / 2f, dh / 2f, cx, cy, line, 1f);

                if (showLabels && label != null) {
                    String text = label.name() + " Spawner";
                    if (showDistance) {
                        double dist = Math.sqrt(mc.player.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5));
                        text += "  " + (int) dist + "m";
                    }
                    drawLabel(dl, text, by + 1.35f, bx, bz, r, g, b, dw, dh);
                }
            }
        } finally {
            if (font != null) ImGui.popFont();
        }
    }

    private void drawLabel(imgui.ImDrawList dl, String text, float relTopY, float bx, float bz,
                           int r, int g, int b, float dw, float dh) {
        org.joml.Vector4f top = new org.joml.Vector4f(bx + 0.5f, relTopY, bz + 0.5f, 1f);
        matrix.transform(top);
        if (top.w <= 0.05f) return;
        float x = (top.x / top.w + 1f) * 0.5f * dw;
        float y = (1f - top.y / top.w) * 0.5f * dh;
        if (!Float.isFinite(x) || !Float.isFinite(y) || Math.abs(x) > 20000f || Math.abs(y) > 20000f) return;
        ImVec2 size = ImGui.calcTextSize(text);
        float tw = size.x, th = size.y;
        if (!Float.isFinite(tw) || !Float.isFinite(th) || tw <= 0f || th <= 0f) return;
        float padX = 6f, padY = 2f;
        float x0 = x - tw / 2f - padX;
        float y0 = y - th - padY * 2f;
        float x1 = x + tw / 2f + padX;
        float y1 = y;
        dl.addRectFilled(x0, y0, x1, y1, RenderUtils.toImGuiColor(10, 10, 14, 170));
        dl.addRectFilled(x0, y1 - 2f, x1, y1, RenderUtils.toImGuiColor(r, g, b, 230));
        dl.addText(x0 + padX + 1f, y0 + padY + 1f, RenderUtils.toImGuiColor(0, 0, 0, 200), text);
        dl.addText(x0 + padX, y0 + padY, RenderUtils.toImGuiColor(255, 255, 255, 255), text);
    }

    private static boolean edgeVisible(int open, int a, int b) {
        for (int f = 0; f < 6; f++) {
            if ((open & (1 << f)) == 0) continue;
            boolean ha = false, hb = false;
            for (int v : FACES[f]) {
                if (v == a) ha = true;
                if (v == b) hb = true;
            }
            if (ha && hb) return true;
        }
        return false;
    }

    private float sx(int i, float dw) {
        return (clip[i].x / clip[i].w + 1f) * 0.5f * dw;
    }

    private float sy(int i, float dh) {
        return (1f - clip[i].y / clip[i].w) * 0.5f * dh;
    }

    private static void drawEdge(imgui.ImDrawList dl, org.joml.Vector4f a, org.joml.Vector4f b, float dw, float dh, int color, float width) {
        float near = 0.05f;
        float ax = a.x, ay = a.y, aw = a.w, bx = b.x, by = b.y, bw = b.w;
        if (aw < near && bw < near) return;
        if (aw < near) {
            float t = (near - aw) / (bw - aw);
            ax += (bx - ax) * t;
            ay += (by - ay) * t;
            aw = near;
        } else if (bw < near) {
            float t = (near - bw) / (aw - bw);
            bx += (ax - bx) * t;
            by += (ay - by) * t;
            bw = near;
        }
        dl.addLine((ax / aw + 1f) * 0.5f * dw, (1f - ay / aw) * 0.5f * dh, (bx / bw + 1f) * 0.5f * dw, (1f - by / bw) * 0.5f * dh, color, width);
    }
}
