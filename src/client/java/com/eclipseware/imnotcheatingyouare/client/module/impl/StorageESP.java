package com.eclipseware.imnotcheatingyouare.client.module.impl;

import com.eclipseware.imnotcheatingyouare.client.ImnotcheatingyouareClient;
import com.eclipseware.imnotcheatingyouare.client.module.Category;
import com.eclipseware.imnotcheatingyouare.client.module.Module;
import com.eclipseware.imnotcheatingyouare.client.setting.Setting;
import com.eclipseware.imnotcheatingyouare.client.setting.SettingsManager;
import com.eclipseware.imnotcheatingyouare.client.utils.RenderUtils;
import imgui.ImGui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.DropperBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.TrappedChestBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StorageESP extends Module {

    private enum Kind {
        CHEST("Chest", "Chest Color", 255, 165, 0),
        TRAPPED("Trapped Chest", "Trapped Chest Color", 255, 0, 0),
        ENDER("Ender Chest", "Ender Chest Color", 138, 43, 226),
        BARREL("Barrel", "Barrel Color", 139, 90, 43),
        SHULKER("Shulker Box", "Shulker Color", 130, 90, 130),
        HOPPER("Hopper", "Hopper Color", 100, 100, 100),
        DISPENSER("Dispenser", "Dispenser Color", 128, 128, 128),
        DROPPER("Dropper", "Dropper Color", 169, 169, 169),
        FURNACE("Furnaces", "Furnace Color", 160, 160, 160);

        final String toggle;
        final String colorName;
        final int r, g, b;

        Kind(String toggle, String colorName, int r, int g, int b) {
            this.toggle = toggle;
            this.colorName = colorName;
            this.r = r;
            this.g = g;
            this.b = b;
        }
    }

    private record Hit(BlockPos pos, int color, int openFaces) {}

    private final Map<Block, Kind> kinds = new HashMap<>();

    private final java.util.concurrent.ExecutorService scanner = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Marlow StorageESP");
        t.setDaemon(true);
        return t;
    });
    private volatile List<Hit> hits = List.of();
    private volatile boolean scanning = false;
    private long lastScan = 0;

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

    public StorageESP() {
        super("StorageESP", Category.Render, "Highlights storage blocks like chests, barrels, and shulker boxes via ImGui.");

        SettingsManager sm = ImnotcheatingyouareClient.INSTANCE.settingsManager;
        sm.rSetting(new Setting("Max Blocks", this, 1500.0, 100.0, 5000.0, true));
        sm.rSetting(new Setting("Fill Opacity", this, 18.0, 0.0, 60.0, true));
        sm.rSetting(new Setting("Line Width", this, 1.5, 0.5, 4.0, false));

        for (Block block : BuiltInRegistries.BLOCK) {
            Kind kind = classify(block);
            if (kind != null) kinds.put(block, kind);
        }
    }

    private static Kind classify(Block block) {
        if (block instanceof TrappedChestBlock) return Kind.TRAPPED;
        if (block instanceof EnderChestBlock) return Kind.ENDER;
        if (block instanceof ChestBlock) return Kind.CHEST;
        if (block instanceof BarrelBlock) return Kind.BARREL;
        if (block instanceof ShulkerBoxBlock) return Kind.SHULKER;
        if (block instanceof HopperBlock) return Kind.HOPPER;
        if (block instanceof DropperBlock) return Kind.DROPPER;
        if (block instanceof DispenserBlock) return Kind.DISPENSER;
        if (block instanceof AbstractFurnaceBlock) return Kind.FURNACE;
        return null;
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

    private int colorOf(Kind kind) {
        Setting r = setting(kind.colorName + " R");
        Setting g = setting(kind.colorName + " G");
        Setting b = setting(kind.colorName + " B");
        int ri = r != null ? (int) r.getValDouble() : kind.r;
        int gi = g != null ? (int) g.getValDouble() : kind.g;
        int bi = b != null ? (int) b.getValDouble() : kind.b;
        return new Color(ri, gi, bi).getRGB();
    }

    @Override
    public void onDisable() {
        hits = List.of();
    }

    @Override
    public void onRenderHUD(GuiGraphicsExtractor guiGraphics, Object tickDeltaObj) {
    }

    private void scheduleScan() {
        long now = System.currentTimeMillis();
        if (scanning || now - lastScan < 400) return;
        lastScan = now;

        final Map<Block, Integer> wanted = new HashMap<>();
        Map<Kind, Integer> colorCache = new HashMap<>();
        for (Map.Entry<Block, Kind> e : kinds.entrySet()) {
            Kind kind = e.getValue();
            if (!bool(kind.toggle, true)) continue;
            wanted.put(e.getKey(), colorCache.computeIfAbsent(kind, this::colorOf));
        }
        if (wanted.isEmpty()) {
            hits = List.of();
            return;
        }

        scanning = true;
        final net.minecraft.client.multiplayer.ClientLevel level = mc.level;
        final BlockPos center = mc.player.blockPosition();
        final int range = (int) num("Range", 64);
        final int max = (int) num("Max Blocks", 1500);
        scanner.execute(() -> {
            try {
                hits = scan(level, center, range, max, wanted);
            } catch (Throwable ignored) {
            } finally {
                scanning = false;
            }
        });
    }

    private static List<Hit> scan(net.minecraft.client.multiplayer.ClientLevel level, BlockPos center, int range, int max,
                                  Map<Block, Integer> wanted) {
        List<Hit> found = new ArrayList<>();
        int rsq = range * range;
        int cx0 = (center.getX() - range) >> 4, cx1 = (center.getX() + range) >> 4;
        int cz0 = (center.getZ() - range) >> 4, cz1 = (center.getZ() + range) >> 4;
        java.util.function.Predicate<BlockState> pred = st -> wanted.containsKey(st.getBlock());
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
                                Integer color = wanted.get(b);
                                if (color == null) continue;
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
                                found.add(new Hit(pos, color, open));
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

    public void renderImGuiOverlay() {
        if (!isToggled() || mc.player == null || mc.level == null || mc.gameRenderer == null) return;
        scheduleScan();
        List<Hit> list = hits;
        if (list.isEmpty()) return;

        boolean fill = bool("Fill", true);
        boolean outline = bool("Outline", true);
        boolean tracers = bool("Tracers", false);
        float width = (float) num("Line Width", 1.5);
        int fillAlpha = (int) (num("Fill Opacity", 18) * 2.55);

        net.minecraft.client.Camera camera = mc.gameRenderer.mainCamera();
        net.minecraft.world.phys.Vec3 cam = camera.position();
        camera.getViewRotationProjectionMatrix(matrix);
        float dw = ImGui.getIO().getDisplaySizeX();
        float dh = ImGui.getIO().getDisplaySizeY();
        imgui.ImDrawList dl = ImGui.getBackgroundDrawList();
        org.joml.Vector4f center = new org.joml.Vector4f();

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
            int argb = h.color();
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
            if (tracers) {
                center.set(bx + 0.5f, by + 0.5f, bz + 0.5f, 1f);
                matrix.transform(center);
                if (center.w > 0.05f) {
                    dl.addLine(dw / 2f, dh / 2f, (center.x / center.w + 1f) * 0.5f * dw, (1f - center.y / center.w) * 0.5f * dh, line, 1f);
                }
            }
        }
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
