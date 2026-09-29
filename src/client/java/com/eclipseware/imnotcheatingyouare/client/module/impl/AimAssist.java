package com.eclipseware.imnotcheatingyouare.client.module.impl;

import com.eclipseware.imnotcheatingyouare.client.ImnotcheatingyouareClient;
import com.eclipseware.imnotcheatingyouare.client.module.Category;
import com.eclipseware.imnotcheatingyouare.client.module.Module;
import com.eclipseware.imnotcheatingyouare.client.setting.Setting;
import com.eclipseware.imnotcheatingyouare.client.setting.SettingsManager;
import com.eclipseware.imnotcheatingyouare.client.utils.FriendManager;
import com.eclipseware.imnotcheatingyouare.client.utils.MouseAimHelper;
import com.eclipseware.imnotcheatingyouare.client.utils.TargetFilterManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

public class AimAssist extends Module implements MouseAimHelper.FrameSource {
    private static final double REF_ANGLE = 45.0;
    private static final double LOCK_ANGLE = 0.06;

    private final Setting hSpeed;
    private final Setting vSpeed;
    private final Setting smoothing;
    private final Setting curve;
    private final Setting inertia;
    private final Setting reaction;
    private final Setting sway;
    private final Setting speedVariation;
    private final Setting flick;
    private final Setting flickSpeed;
    private final Setting flickOvershoot;
    private final Setting flickMinAngle;
    private final Setting flickCooldown;
    private final Setting fov;
    private final Setting range;
    private final Setting targetArea;
    private final Setting sortBy;
    private final Setting predict;
    private final Setting predictionTicks;
    private final Setting aimDeviation;
    private final Setting clickAim;
    private final Setting weaponOnly;
    private final Setting horizontalOnly;
    private final Setting teamCheck;
    private final Setting ignoreWalls;
    private final Setting players;
    private final Setting hostileMobs;
    private final Setting passiveMobs;

    private boolean registered;
    private volatile Entity target;
    private Entity lastTarget;
    private Vec3 boneOffset = Vec3.ZERO;
    private Vec3 deviation = Vec3.ZERO;
    private boolean acquired;
    private double reactionUntilMs;
    private double flickReadyAtMs;
    private boolean wantFlick;
    private boolean flicking;
    private double flickRemYaw;
    private double flickRemPitch;
    private double flickStartMs;
    private double velYaw;
    private double velPitch;
    private double phaseA;
    private double phaseB;
    private double phaseC;
    private double phaseD;
    private double phaseE;

    public AimAssist() {
        super("AimAssist", Category.Combat, "Smoothly pulls your aim toward nearby targets.");
        SettingsManager sm = ImnotcheatingyouareClient.INSTANCE.settingsManager;

        hSpeed = new Setting("Horizontal Speed", this, 35.0, 1.0, 100.0, true);
        vSpeed = new Setting("Vertical Speed", this, 30.0, 1.0, 100.0, true);
        ArrayList<String> smoothModes = new ArrayList<>(Arrays.asList("Linear", "Exponential", "Ease Out", "Smoothstep", "Sigmoid", "Custom Curve"));
        smoothing = new Setting("Smoothing", this, "Ease Out", smoothModes);
        curve = new Setting("Curve", this, 0.25, 0.1, 0.25, 1.0).visibleWhen(() -> "Custom Curve".equals(smoothing.getValString()));
        inertia = new Setting("Inertia (ms)", this, 55.0, 0.0, 250.0, true);
        reaction = new Setting("Reaction (ms)", this, 60.0, 140.0, 0.0, 400.0, true);
        sway = new Setting("Sway", this, 0.3, 0.0, 2.0, false);
        speedVariation = new Setting("Speed Variation %", this, 12.0, 0.0, 40.0, true);
        flick = new Setting("Flick", this, false);
        flickSpeed = new Setting("Flick Speed", this, 55.0, 1.0, 100.0, true).visibleWhen(() -> flick.getValBoolean());
        flickOvershoot = new Setting("Flick Overshoot %", this, 8.0, 0.0, 30.0, true).visibleWhen(() -> flick.getValBoolean());
        flickMinAngle = new Setting("Flick Min Angle", this, 25.0, 5.0, 120.0, true).visibleWhen(() -> flick.getValBoolean());
        flickCooldown = new Setting("Flick Cooldown (ms)", this, 600.0, 0.0, 3000.0, true).visibleWhen(() -> flick.getValBoolean());
        fov = new Setting("FOV", this, 60.0, 0.0, 360.0, true);
        range = new Setting("Range", this, 4.0, 1.0, 8.0, false);
        ArrayList<String> bodyTargets = new ArrayList<>(Arrays.asList("Head", "Body", "Arms", "Legs", "Nearest Part"));
        targetArea = new Setting("Target Area", this, "Body", bodyTargets);
        ArrayList<String> sortOptions = new ArrayList<>(Arrays.asList("Distance", "Angle"));
        sortBy = new Setting("Sort By", this, "Distance", sortOptions);
        predict = new Setting("Predict", this, false);
        predictionTicks = new Setting("Prediction Ticks", this, 1.0, 0.0, 5.0, false).visibleWhen(() -> predict.getValBoolean());
        aimDeviation = new Setting("Aim Deviation", this, 0.0, 0.0, 1.0, false);
        clickAim = new Setting("Click Aim", this, false);
        weaponOnly = new Setting("Weapon Only", this, false);
        horizontalOnly = new Setting("Horizontal Only", this, false);
        teamCheck = new Setting("Team Check", this, true);
        ignoreWalls = new Setting("Ignore Walls", this, false);
        players = new Setting("Players", this, true);
        hostileMobs = new Setting("Hostile Mobs", this, true);
        passiveMobs = new Setting("Passive Mobs", this, false);

        for (Setting s : new Setting[]{hSpeed, vSpeed, smoothing, curve, inertia, reaction, sway, speedVariation,
                flick, flickSpeed, flickOvershoot, flickMinAngle, flickCooldown, fov, range, targetArea, sortBy,
                predict, predictionTicks, aimDeviation, clickAim, weaponOnly, horizontalOnly, teamCheck,
                ignoreWalls, players, hostileMobs, passiveMobs}) {
            sm.rSetting(s);
        }
    }

    @Override
    public void onEnable() {
        resetState();
        MouseAimHelper.setFrameSource(this);
        registered = true;
    }

    @Override
    public void onDisable() {
        MouseAimHelper.setFrameSource(null);
        registered = false;
        resetState();
    }

    private void resetState() {
        target = null;
        lastTarget = null;
        boneOffset = Vec3.ZERO;
        deviation = Vec3.ZERO;
        acquired = false;
        flicking = false;
        wantFlick = false;
        velYaw = 0.0;
        velPitch = 0.0;
        MouseAimHelper.clearAimRate();
    }

    private void drop() {
        target = null;
        lastTarget = null;
        acquired = false;
        flicking = false;
        wantFlick = false;
        boneOffset = Vec3.ZERO;
        deviation = Vec3.ZERO;
    }

    @Override
    public boolean needsTick() {
        return false;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;

        if (!registered) {
            MouseAimHelper.setFrameSource(this);
            registered = true;
        }

        if (clickAim.getValBoolean() && !mc.options.keyAttack.isDown()) {
            drop();
            return;
        }
        if (mc.gui.screen() != null || (weaponOnly.getValBoolean() && !isHoldingWeapon())) {
            drop();
            return;
        }

        updateTarget();
        Entity t = target;

        if (t == null) {
            lastTarget = null;
            acquired = false;
            return;
        }

        boneOffset = computeBoneOffset(t);

        if (t != lastTarget) {
            lastTarget = t;
            acquired = true;
            double now = nowMs();
            double dev = aimDeviation.getValDouble();
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            deviation = dev > 0.0
                    ? new Vec3((rng.nextDouble() - 0.5) * dev, (rng.nextDouble() - 0.5) * dev, (rng.nextDouble() - 0.5) * dev)
                    : Vec3.ZERO;

            double lo = reaction.getRangeLow();
            double hi = reaction.getRangeHigh();
            reactionUntilMs = now + (hi <= lo ? lo : lo + rng.nextDouble() * (hi - lo));

            phaseA = rng.nextDouble() * Math.PI * 2.0;
            phaseB = rng.nextDouble() * Math.PI * 2.0;
            phaseC = rng.nextDouble() * Math.PI * 2.0;
            phaseD = rng.nextDouble() * Math.PI * 2.0;
            phaseE = rng.nextDouble() * Math.PI * 2.0;
            wantFlick = flick.getValBoolean() && now >= flickReadyAtMs;
            flicking = false;
        }
    }

    @Override
    public boolean active() {
        return isToggled() && mc.player != null && mc.level != null && mc.gui.screen() == null && target != null;
    }

    @Override
    public void step(double dt, double[] out) {
        Entity t = target;
        if (t == null || mc.player == null || dt <= 0.0) {
            velYaw = 0.0;
            velPitch = 0.0;
            return;
        }

        double now = nowMs();
        if (now < reactionUntilMs) {
            velYaw *= Math.exp(-dt * 25.0);
            velPitch *= Math.exp(-dt * 25.0);
            return;
        }

        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 eye = mc.player.getEyePosition(pt);
        Vec3 aim = t.getPosition(pt).add(boneOffset).add(deviation);

        if (predict.getValBoolean()) {
            Vec3 rel = t.getDeltaMovement().subtract(mc.player.getDeltaMovement());
            aim = aim.add(rel.scale(predictionTicks.getValDouble()));
        }

        double swayAmp = sway.getValDouble();
        double tSec = now / 1000.0;
        double swayYaw = swayAmp * (Math.sin(tSec * 1.7 + phaseA) * 0.6 + Math.sin(tSec * 0.63 + phaseB) * 0.4);
        double swayPitch = swayAmp * 0.6 * (Math.sin(tSec * 1.3 + phaseC) * 0.6 + Math.sin(tSec * 0.51 + phaseD) * 0.4);

        double dx = aim.x - eye.x;
        double dz = aim.z - eye.z;
        double dy = aim.y - eye.y;
        double tYaw = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        double flat = Math.sqrt(dx * dx + dz * dz);
        double tPitch = -Math.toDegrees(Math.atan2(dy, flat));

        double errYaw = Mth.wrapDegrees(tYaw - mc.player.getYRot()) + swayYaw;
        double errPitch = horizontalOnly.getValBoolean() ? 0.0 : Mth.wrapDegrees(tPitch - mc.player.getXRot()) + swayPitch;

        if (wantFlick && !flicking) {
            double mag = Math.hypot(errYaw, errPitch);
            if (mag >= flickMinAngle.getValDouble()) {
                double os = flickOvershoot.getValDouble() / 100.0;
                flicking = true;
                flickStartMs = now;
                flickRemYaw = errYaw * (1.0 + os);
                flickRemPitch = errPitch * (1.0 + os * 0.5);
            }
            wantFlick = false;
        }

        double moveYaw;
        double movePitch;

        if (flicking) {
            double rem = Math.hypot(flickRemYaw, flickRemPitch);
            if (rem < 1.2 || now - flickStartMs > 320.0) {
                flicking = false;
                flickReadyAtMs = now + flickCooldown.getValDouble();
                return;
            }
            double maxRate = 300.0 + flickSpeed.getValDouble() * 25.0;
            double rate = maxRate * Math.min(1.0, 0.25 + rem / 40.0);
            double move = Math.min(rem, rate * dt);
            moveYaw = flickRemYaw / rem * move;
            movePitch = flickRemPitch / rem * move;
            flickRemYaw -= moveYaw;
            flickRemPitch -= movePitch;
            velYaw = moveYaw / dt;
            velPitch = movePitch / dt;
            out[0] = moveYaw;
            out[1] = movePitch;
            return;
        }

        double mag = Math.hypot(errYaw, errPitch);
        double dvYaw = 0.0;
        double dvPitch = 0.0;
        if (mag > LOCK_ANGLE) {
            double x = Math.min(1.0, mag / REF_ANGLE);
            double f = Math.max(0.04, curveFactor(x));
            double variation = speedVariation.getValDouble() / 100.0;
            double vary = 1.0 + variation * (Math.sin(tSec * 1.1 + phaseE) * 0.6 + Math.sin(tSec * 2.3 + phaseA) * 0.4);
            double maxH = (60.0 + hSpeed.getValDouble() * 6.0) * vary;
            double maxV = (60.0 + vSpeed.getValDouble() * 6.0) * vary;
            dvYaw = errYaw / mag * f * maxH;
            dvPitch = errPitch / mag * f * maxV;
        }

        double tau = inertia.getValDouble();
        double a = tau <= 0.0 ? 1.0 : 1.0 - Math.exp(-dt * 1000.0 / tau);
        velYaw += (dvYaw - velYaw) * a;
        velPitch += (dvPitch - velPitch) * a;

        moveYaw = velYaw * dt;
        movePitch = velPitch * dt;

        if (Math.abs(moveYaw) > Math.abs(errYaw) && Math.signum(moveYaw) == Math.signum(errYaw)) {
            moveYaw = errYaw;
            velYaw *= 0.3;
        }
        if (Math.abs(movePitch) > Math.abs(errPitch) && Math.signum(movePitch) == Math.signum(errPitch)) {
            movePitch = errPitch;
            velPitch *= 0.3;
        }

        out[0] = moveYaw;
        out[1] = movePitch;
    }

    private double curveFactor(double x) {
        return switch (smoothing.getValString()) {
            case "Linear" -> 1.0;
            case "Exponential" -> x;
            case "Smoothstep" -> x * x * (3.0 - 2.0 * x);
            case "Sigmoid" -> {
                double lo = 1.0 / (1.0 + Math.exp(10.0 * 0.35));
                double hi = 1.0 / (1.0 + Math.exp(-10.0 * 0.65));
                double v = 1.0 / (1.0 + Math.exp(-10.0 * (x - 0.35)));
                yield (v - lo) / (hi - lo);
            }
            case "Custom Curve" -> {
                double[] c = curve.getCurve();
                yield bezierY(x, c[0], c[1], c[2], c[3]);
            }
            default -> Math.sqrt(x);
        };
    }

    private static double bezierY(double x, double x1, double y1, double x2, double y2) {
        double lo = 0.0;
        double hi = 1.0;
        for (int i = 0; i < 24; i++) {
            double t = (lo + hi) * 0.5;
            double u = 1.0 - t;
            double bx = 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t;
            if (bx < x) lo = t;
            else hi = t;
        }
        double t = (lo + hi) * 0.5;
        double u = 1.0 - t;
        double by = 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t;
        return Math.max(0.0, Math.min(1.5, by));
    }

    private static double nowMs() {
        return System.nanoTime() / 1_000_000.0;
    }

    private Vec3 computeBoneOffset(Entity ent) {
        String area = targetArea.getValString();
        double eye = ent.getEyeHeight();
        double bodyY = eye * 0.65;

        float yawRad = (float) Math.toRadians(ent.getYRot());
        double ox = Math.cos(yawRad) * 0.35;
        double oz = Math.sin(yawRad) * 0.35;

        Vec3 head = new Vec3(0, eye, 0);
        Vec3 body = new Vec3(0, bodyY, 0);
        Vec3 legs = new Vec3(0, eye * 0.25, 0);
        Vec3 leftArm = new Vec3(ox, bodyY, oz);
        Vec3 rightArm = new Vec3(-ox, bodyY, -oz);

        return switch (area) {
            case "Head" -> head;
            case "Body" -> body;
            case "Legs" -> legs;
            case "Arms" -> closestOffset(ent, leftArm, rightArm);
            case "Nearest Part" -> closestOffset(ent, head, body, leftArm, rightArm, legs);
            default -> new Vec3(0, eye / 2.0, 0);
        };
    }

    private Vec3 closestOffset(Entity ent, Vec3... offsets) {
        Vec3 base = ent.position();
        Vec3 best = offsets[0];
        double bestDiff = Double.MAX_VALUE;
        for (Vec3 off : offsets) {
            double diff = angleDiff(base.add(off));
            if (diff < bestDiff) {
                bestDiff = diff;
                best = off;
            }
        }
        return best;
    }

    private double angleDiff(Vec3 pos) {
        Vec3 dir = pos.subtract(mc.player.getEyePosition()).normalize();
        double yaw = Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90.0;
        double pitch = -Math.toDegrees(Math.asin(dir.y));
        double yawDiff = Mth.wrapDegrees(yaw - mc.player.getYRot());
        double pitchDiff = Mth.wrapDegrees(pitch - mc.player.getXRot());
        return Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
    }

    private double sortValue(Entity entity, boolean byAngle) {
        if (byAngle) return angleDiff(entity.position().add(0, entity.getEyeHeight() * 0.65, 0));
        return mc.player.distanceTo(entity);
    }

    private void updateTarget() {
        boolean byAngle = "Angle".equals(sortBy.getValString());
        Entity best = null;
        double bestVal = Double.MAX_VALUE;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!isValidTarget(entity)) continue;
            double val = sortValue(entity, byAngle);
            if (val < bestVal) {
                best = entity;
                bestVal = val;
            }
        }

        Entity current = target;
        if (current != null && best != null && current != best && isValidTarget(current)) {
            if (bestVal > sortValue(current, byAngle) * 0.75) best = current;
        }
        target = best;
    }

    private boolean isValidTarget(Entity entity) {
        if (!(entity instanceof LivingEntity) || entity == mc.player || !entity.isAlive()) return false;
        if (TargetFilterManager.isFiltered(entity)) return false;
        if (mc.player.distanceTo(entity) > range.getValDouble()) return false;
        if (!ignoreWalls.getValBoolean() && !mc.player.hasLineOfSight(entity)) return false;

        if (entity instanceof Player p) {
            if (FriendManager.isFriend(p)) return false;
            if (teamCheck.getValBoolean() && Teams.isTeam(p)) return false;
            if (!players.getValBoolean()) return false;
        } else if (isHostileMob(entity)) {
            if (!hostileMobs.getValBoolean()) return false;
        } else if (!passiveMobs.getValBoolean()) {
            return false;
        }

        double maxFov = fov.getValDouble();
        if (maxFov >= 360.0) return true;
        return angleDiff(entity.position().add(0, entity.getEyeHeight() * 0.65, 0)) <= maxFov / 2.0;
    }

    private boolean isHostileMob(Entity entity) {
        if (entity instanceof net.minecraft.world.entity.monster.Monster) return true;
        String name = entity.getType().getDescriptionId().toLowerCase();
        return name.contains("slime") || name.contains("shulker") || name.contains("ghast") || name.contains("dragon");
    }

    private boolean isHoldingWeapon() {
        if (mc.player == null) return false;
        String path = BuiltInRegistries.ITEM.getKey(mc.player.getMainHandItem().getItem()).getPath();
        return path.endsWith("_sword") || path.endsWith("_axe") || path.equals("mace") || path.equals("trident")
                || path.endsWith("_spear") || path.equals("bow") || path.equals("crossbow");
    }
}
