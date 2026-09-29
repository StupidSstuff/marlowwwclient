package com.eclipseware.imnotcheatingyouare.client.module.impl;

import com.eclipseware.imnotcheatingyouare.client.ImnotcheatingyouareClient;
import com.eclipseware.imnotcheatingyouare.client.module.Category;
import com.eclipseware.imnotcheatingyouare.client.module.Module;
import com.eclipseware.imnotcheatingyouare.client.setting.Setting;
import com.eclipseware.imnotcheatingyouare.client.setting.SettingsManager;
import com.eclipseware.imnotcheatingyouare.client.utils.FriendManager;
import com.eclipseware.imnotcheatingyouare.client.utils.TargetFilterManager;
import com.eclipseware.imnotcheatingyouare.mixin.client.MinecraftAccessor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public class Triggerbot extends Module {
    public static boolean isTriggerbotAttacking = false;

    private static long lastTimePacketMs = 0;
    private static float serverTps = 20.0f;

    private enum Plan { NONE, EARLY, LATE, RANGE }

    private final Setting stickyTarget;
    private final Setting weaponOnly;
    private final Setting requireLeftClick;
    private final Setting ignoreFirstClick;
    private final Setting pauseOnMining;
    private final Setting pauseInGui;
    private final Setting delay;
    private final Setting cooldown;
    private final Setting antiLag;
    private final Setting missChance;

    private Entity lastTarget;
    private long lastAttackMs = 0L;
    private long acquiredAt = 0L;
    private long readyAt = 0L;
    private Plan plan = Plan.NONE;
    private float earlyNeed = 0.4f;
    private long lateExtraMs = 0L;

    public Triggerbot() {
        super("Triggerbot", Category.Combat);
        SettingsManager sm = ImnotcheatingyouareClient.INSTANCE.settingsManager;

        stickyTarget = new Setting("Sticky Target", this, false);
        weaponOnly = new Setting("Weapon Only", this, true);
        requireLeftClick = new Setting("Require Left Click", this, false);
        ignoreFirstClick = new Setting("Ignore First Click", this, true).visibleWhen(() -> requireLeftClick.getValBoolean());
        pauseOnMining = new Setting("Pause On Mining", this, false);
        pauseInGui = new Setting("Pause in GUI", this, true);
        delay = new Setting("Delay (ms)", this, 25.0, 75.0, 0.0, 300.0, true);
        cooldown = new Setting("Cooldown %", this, 78.0, 30.0, 100.0, true);
        antiLag = new Setting("Anti-Lag", this, false);
        missChance = new Setting("Miss Chance %", this, 0.0, 0.0, 100.0, true);

        sm.rSetting(stickyTarget);
        sm.rSetting(weaponOnly);
        sm.rSetting(requireLeftClick);
        sm.rSetting(ignoreFirstClick);
        sm.rSetting(pauseOnMining);
        sm.rSetting(pauseInGui);
        sm.rSetting(delay);
        sm.rSetting(cooldown);
        sm.rSetting(antiLag);
        sm.rSetting(missChance);
    }

    public static void onUpdateTimePacket() {
        long now = System.currentTimeMillis();
        if (lastTimePacketMs != 0) {
            long diff = now - lastTimePacketMs;
            if (diff > 0) {
                float tps = 20000.0f / (float) diff;
                if (tps > 20.0f) tps = 20.0f;
                if (tps < 1.0f) tps = 1.0f;
                serverTps = (serverTps * 0.9f) + (tps * 0.1f);
            }
        }
        lastTimePacketMs = now;
    }

    @Override
    public void onEnable() {
        resetEngagement();
        lastTarget = null;
        lastAttackMs = 0L;
    }

    @Override
    public void onDisable() {
        resetEngagement();
        lastTarget = null;
    }

    private void resetEngagement() {
        acquiredAt = 0L;
        readyAt = 0L;
        plan = Plan.NONE;
        lateExtraMs = 0L;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;
        run();
    }

    private void run() {
        long now = System.currentTimeMillis();
        boolean down = mc.options.keyAttack.isDown();

        if (pauseInGui.getValBoolean() && mc.gui.screen() != null) {
            resetEngagement();
            return;
        }
        if (!weaponReady()) {
            resetEngagement();
            return;
        }
        if (requireLeftClick.getValBoolean() && !down) {
            resetEngagement();
            return;
        }

        HitResult hit = mc.hitResult;
        if (pauseOnMining.getValBoolean() && down && hit != null && hit.getType() == HitResult.Type.BLOCK) {
            resetEngagement();
            return;
        }

        if (lastTarget != null && (!lastTarget.isAlive() || lastTarget.isRemoved() || now - lastAttackMs > 4000L
                || mc.player.distanceToSqr(lastTarget) > 64.0)) {
            lastTarget = null;
        }

        double chance = missChance.getValDouble();
        Entity target = null;
        boolean beyondReach = false;

        if (hit != null && hit.getType() == HitResult.Type.ENTITY) {
            Entity e = ((EntityHitResult) hit).getEntity();
            if (isValidTarget(e)) target = e;
        }
        if (target == null && chance > 0.0 && acquiredAt != 0L && plan == Plan.RANGE) {
            target = extendedCandidate();
            beyondReach = target != null;
        }

        if (target != null && stickyTarget.getValBoolean() && lastTarget != null && target != lastTarget) {
            target = null;
        }

        if (target == null) {
            resetEngagement();
            return;
        }

        if (acquiredAt == 0L) {
            acquiredAt = now;
            rollPlan(chance);
        }

        double reach = mc.player.entityInteractionRange();
        if (plan == Plan.RANGE) {
            double dist = Math.sqrt(mc.player.distanceToSqr(target));
            boolean edge = beyondReach || dist >= reach - 0.35;
            if (!edge && now - acquiredAt < 450L) return;
        } else if (beyondReach) {
            resetEngagement();
            return;
        }

        float need = plan == Plan.EARLY ? earlyNeed : requiredCooldown();
        if (mc.player.getAttackStrengthScale(0.5f) < need) {
            readyAt = 0L;
            return;
        }

        if (readyAt == 0L) readyAt = now + randomDelay() + (plan == Plan.LATE ? lateExtraMs : 0L);
        if (now < readyAt) return;

        Module hitSelectMod = ImnotcheatingyouareClient.INSTANCE.moduleManager.getModule("HitSelect");
        if (hitSelectMod != null && hitSelectMod.isToggled() && hitSelectMod instanceof HitSelect hs && !hs.canAttack(target)) return;

        isTriggerbotAttacking = true;
        try {
            ((MinecraftAccessor) mc).invokeStartAttack();
        } finally {
            isTriggerbotAttacking = false;
        }
        mc.player.resetAttackStrengthTicker();

        lastAttackMs = now;
        lastTarget = target;
        resetEngagement();
    }

    private void rollPlan(double chance) {
        plan = Plan.NONE;
        if (chance <= 0.0) return;
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        if (rng.nextDouble() * 100.0 >= chance) return;
        switch (rng.nextInt(3)) {
            case 0 -> {
                plan = Plan.EARLY;
                earlyNeed = 0.15f + rng.nextFloat() * 0.4f;
            }
            case 1 -> {
                plan = Plan.LATE;
                lateExtraMs = 150L + rng.nextLong(200L);
            }
            default -> plan = Plan.RANGE;
        }
    }

    private long randomDelay() {
        int lo = (int) delay.getRangeLow();
        int hi = (int) delay.getRangeHigh();
        if (hi <= lo) return Math.max(0, lo);
        return lo + ThreadLocalRandom.current().nextInt(hi - lo + 1);
    }

    private float requiredCooldown() {
        float need = (float) cooldown.getValDouble() / 100.0f;
        if (!antiLag.getValBoolean()) return need;

        float pingMs = 0.0f;
        if (mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (info != null) pingMs = info.getLatency();
        }

        float fullMs = Math.max(200.0f, mc.player.getCurrentItemAttackStrengthDelay() * 50.0f);
        float pingCredit = (pingMs * 0.5f) / fullMs;
        float tpsPenalty = serverTps < 19.5f ? (20.0f - serverTps) / 20.0f * 0.5f : 0.0f;

        float adjusted = need - pingCredit + tpsPenalty;
        return Math.max(0.3f, Math.min(1.0f, adjusted));
    }

    private Entity extendedCandidate() {
        double reach = mc.player.entityInteractionRange();
        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(reach + 1.2));

        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!isValidTarget(e)) continue;
            AABB box = e.getBoundingBox().inflate(e.getPickRadius());
            Optional<Vec3> clip = box.clip(eye, end);
            if (clip.isEmpty()) continue;
            double d = eye.distanceTo(clip.get());
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    private boolean weaponReady() {
        if (!weaponOnly.getValBoolean()) return true;
        String path = BuiltInRegistries.ITEM.getKey(mc.player.getMainHandItem().getItem()).getPath();
        return path.endsWith("_sword") || path.endsWith("_axe") || path.equals("mace")
                || path.equals("trident") || path.endsWith("_spear");
    }

    public static boolean shouldCancelManualAttack() {
        if (isTriggerbotAttacking) return false;
        Module mod = ImnotcheatingyouareClient.INSTANCE.moduleManager.getModule("Triggerbot");
        if (mod == null || !mod.isToggled() || !(mod instanceof Triggerbot tb)) return false;
        if (!tb.requireLeftClick.getValBoolean() || !tb.ignoreFirstClick.getValBoolean()) return false;

        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gui.screen() != null) return false;
        if (!tb.weaponReady()) return false;

        HitResult hit = mc.hitResult;
        return hit == null || hit.getType() != HitResult.Type.BLOCK;
    }

    public boolean shouldBlock(Entity target) {
        if (!this.isToggled() || mc.player == null || mc.level == null) return false;
        if (mc.gui.screen() != null) return false;
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.ENTITY) return false;
        if (target != ((EntityHitResult) mc.hitResult).getEntity()) return false;
        if (!isValidTarget(target)) return false;
        if (mc.player.getAttackStrengthScale(0.0f) < 1.0f) return false;
        Module hitSelectMod = ImnotcheatingyouareClient.INSTANCE.moduleManager.getModule("HitSelect");
        if (hitSelectMod != null && hitSelectMod.isToggled() && hitSelectMod instanceof HitSelect hs) {
            return !hs.canAttack(target);
        }
        return false;
    }

    private boolean isValidTarget(Entity entity) {
        if (!(entity instanceof LivingEntity) || entity instanceof ArmorStand) return false;
        if (entity == mc.player || !entity.isAlive() || entity.isSpectator()) return false;
        if (TargetFilterManager.isFiltered(entity)) return false;
        if (entity instanceof Player p) return !FriendManager.isFriend(p);
        return entity instanceof Enemy;
    }
}
