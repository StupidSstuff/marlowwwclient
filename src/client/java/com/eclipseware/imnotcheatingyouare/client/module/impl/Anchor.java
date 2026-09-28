package com.eclipseware.imnotcheatingyouare.client.module.impl;

import com.eclipseware.imnotcheatingyouare.client.ImnotcheatingyouareClient;
import com.eclipseware.imnotcheatingyouare.client.module.Category;
import com.eclipseware.imnotcheatingyouare.client.module.Module;
import com.eclipseware.imnotcheatingyouare.client.setting.Setting;
import com.eclipseware.imnotcheatingyouare.client.utils.InputUtil;
import com.eclipseware.imnotcheatingyouare.client.utils.ModuleUtils;
import com.eclipseware.imnotcheatingyouare.mixin.client.MinecraftAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;

public class Anchor extends Module {

    private final Setting activation;
    private final Setting activateKey;
    private final Setting placeKey;
    private final Setting switchDelay;
    private final Setting glowstoneDelay;
    private final Setting explodeDelay;
    private final Setting totemSlot;

    private final Setting fastMode;
    private final Setting doubleAnchor;
    private final Setting doubleAnchorCondition;
    private final Setting doubleAnchorKey;
    private final Setting doubleAnchorCount;

    private final Setting switchBack;
    private final Setting switchBackDelay;
    private final Setting pauseOnKill;
    private final Setting pauseTime;

    private int switchCounter;
    private int glowstoneCounter;
    private int explodeCounter;
    private int switchBackCounter;
    private int pauseCounter;
    private int chargeTimeout;
    private int currentDoubleAnchorCount;
    private boolean chargedGlowstone;
    private boolean exploded;
    private boolean needsSwitchBack;
    private boolean activateLatched;
    private boolean activateWasDown;
    private int latchWaitTicks;
    private BlockPos placedAnchorPos;
    private BlockPos fireBreakPending;
    private int fireBreakTicks;
    private int placeTimeout;
    private int noAimTicks;
    private final List<Player> deadPlayers = new ArrayList<>();

    public Anchor() {
        super("Anchor", Category.Combat, "Automatically places and blows up respawn anchors");

        activation = new Setting("Activation", this, "HOLD", new ArrayList<>(List.of("HOLD", "CLICK")));
        activateKey = new Setting("Activate Key", this, "MOUSE RIGHT", true);
        placeKey = new Setting("Place Key", this, "NONE", true);
        switchDelay = new Setting("Switch Delay", this, 0.0, 0.0, 20.0, true);
        glowstoneDelay = new Setting("Glowstone Delay", this, 0.0, 0.0, 20.0, true);
        explodeDelay = new Setting("Explode Delay", this, 0.0, 0.0, 20.0, true);
        totemSlot = new Setting("Totem Slot", this, 1.0, 1.0, 9.0, true);

        fastMode = new Setting("Fast Mode", this, false);
        doubleAnchor = new Setting("Double Anchor", this, false);
        doubleAnchorCondition = new Setting("Double Condition", this, "ALWAYS", new ArrayList<>(List.of("ALWAYS", "HOLD_SLOT", "KEY_BIND")));
        doubleAnchorKey = new Setting("Double Anchor Key", this, "NONE", true);
        doubleAnchorCount = new Setting("Double Anchor Count", this, 2.0, 2.0, 5.0, true);

        switchBack = new Setting("Switch Back", this, true);
        switchBackDelay = new Setting("Switch Back Delay", this, 5.0, 0.0, 20.0, true);
        pauseOnKill = new Setting("Pause On Kill", this, true);
        pauseTime = new Setting("Pause Time", this, 2.0, 0.5, 10.0, false);

        var sm = ImnotcheatingyouareClient.INSTANCE.settingsManager;
        sm.rSetting(activation);
        sm.rSetting(activateKey);
        sm.rSetting(placeKey);
        sm.rSetting(switchDelay);
        sm.rSetting(glowstoneDelay);
        sm.rSetting(explodeDelay);
        sm.rSetting(totemSlot);
        sm.rSetting(fastMode);
        sm.rSetting(doubleAnchor);
        sm.rSetting(doubleAnchorCondition);
        sm.rSetting(doubleAnchorKey);
        sm.rSetting(doubleAnchorCount);
        sm.rSetting(switchBack);
        sm.rSetting(switchBackDelay);
        sm.rSetting(pauseOnKill);
        sm.rSetting(pauseTime);
    }

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        resetState();
    }

    private void resetState() {
        switchCounter = 0;
        glowstoneCounter = 0;
        explodeCounter = 0;
        switchBackCounter = 0;
        pauseCounter = 0;
        chargeTimeout = 0;
        currentDoubleAnchorCount = 0;
        chargedGlowstone = false;
        exploded = false;
        needsSwitchBack = false;
        activateLatched = false;
        activateWasDown = false;
        latchWaitTicks = 0;
        placedAnchorPos = null;
        fireBreakPending = null;
        fireBreakTicks = 0;
        placeTimeout = 0;
        noAimTicks = 0;
        deadPlayers.clear();
    }

    private boolean shouldDoubleAnchor() {
        if (!doubleAnchor.getValBoolean()) return false;

        String cond = doubleAnchorCondition.getValString();
        if ("ALWAYS".equals(cond)) {
            return true;
        } else if ("HOLD_SLOT".equals(cond)) {
            int anchorSlot = -1;
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getItem(i).is(Items.RESPAWN_ANCHOR)) {
                    anchorSlot = i;
                    break;
                }
            }
            return anchorSlot != -1 && anchorSlot < mc.options.keyHotbarSlots.length
                    && mc.options.keyHotbarSlots[anchorSlot].isDown();
        } else if ("KEY_BIND".equals(cond)) {
            return InputUtil.isDown(parseKeyCode(doubleAnchorKey.getValText()));
        }
        return false;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.gui.screen() != null || mc.gameMode == null) return;

        if (pauseCounter > 0) {
            pauseCounter--;
            return;
        }

        if (pauseOnKill.getValBoolean() && checkDeadPlayers()) {
            pauseCounter = (int) (pauseTime.getValDouble() * 20);
            return;
        }

        if (isEatingOrBlocking()) return;

        if (needsSwitchBack && switchBack.getValBoolean()) {
            if (switchBackCounter < (int) switchBackDelay.getValDouble()) {
                switchBackCounter++;
                return;
            }
            switchBackCounter = 0;
            swapTo(Items.RESPAWN_ANCHOR);
            needsSwitchBack = false;
            return;
        }

        int activateCode = parseKeyCode(activateKey.getValText());
        int placeCode = parseKeyCode(placeKey.getValText());
        boolean activateKeyDown = InputUtil.isDown(activateCode);
        boolean placeHeld = InputUtil.isDown(placeCode);

        boolean activateHeld;
        if ("CLICK".equals(activation.getValString())) {
            if (activateKeyDown && !activateWasDown) {
                activateLatched = true;
                latchWaitTicks = 0;
            }
            activateHeld = activateLatched;
        } else {
            activateHeld = activateKeyDown;
        }
        activateWasDown = activateKeyDown;

        if (!activateHeld && !placeHeld) {
            activateLatched = false;
            latchWaitTicks = 0;
            chargedGlowstone = false;
            exploded = false;
            needsSwitchBack = false;
            currentDoubleAnchorCount = 0;
            placedAnchorPos = null;
            placeTimeout = 0;
            return;
        }

        BlockPos targetPos = null;

        if (placeHeld && !activateHeld) {
            if (placedAnchorPos != null && exploded && currentDoubleAnchorCount == 0) {
                placedAnchorPos = null;
                placeTimeout = 0;
                chargedGlowstone = false;
                exploded = false;
            }

            if (placedAnchorPos == null) {
                if (!(mc.hitResult instanceof BlockHitResult hit)) return;
                BlockPos hitPos = hit.getBlockPos();

                if (mc.level.getBlockState(hitPos).is(Blocks.RESPAWN_ANCHOR)) {
                    placedAnchorPos = hitPos;
                    placeTimeout = 0;
                } else {
                    mc.options.keyUse.setDown(false);
                    mc.options.keyUse.consumeClick();
                    if (!mc.player.getMainHandItem().is(Items.RESPAWN_ANCHOR)) {
                        if (switchCounter < (int) switchDelay.getValDouble()) {
                            switchCounter++;
                            return;
                        }
                        switchCounter = 0;
                        swapTo(Items.RESPAWN_ANCHOR);
                    }
                    BlockPos expectedPos = mc.level.getBlockState(hitPos).canBeReplaced() ? hitPos : hitPos.relative(hit.getDirection());
                    if (waitForFireClear(expectedPos)) return;
                    startUseItem();
                    placedAnchorPos = expectedPos;
                    placeTimeout = 0;
                    chargedGlowstone = false;
                    exploded = false;
                    return;
                }
            } else {
                if (!mc.level.getBlockState(placedAnchorPos).is(Blocks.RESPAWN_ANCHOR)) {
                    placeTimeout++;
                    if (placeTimeout > 10) {
                        placedAnchorPos = null;
                        placeTimeout = 0;
                    }
                    return;
                }
            }
            targetPos = placedAnchorPos;
        } else {

            BlockPos aimed = mc.hitResult instanceof BlockHitResult h
                    && mc.level.getBlockState(h.getBlockPos()).is(Blocks.RESPAWN_ANCHOR)
                    ? h.getBlockPos() : null;
            if (aimed != null) {
                placedAnchorPos = aimed;
                targetPos = aimed;
                latchWaitTicks = 0;
            } else if (placedAnchorPos != null
                    && mc.level.getBlockState(placedAnchorPos).is(Blocks.RESPAWN_ANCHOR)) {
                targetPos = placedAnchorPos;
            } else if (exploded && placedAnchorPos != null) {
                if ("CLICK".equals(activation.getValString())) {
                    activateLatched = false;
                }
                placedAnchorPos = null;
                placeTimeout = 0;
                chargedGlowstone = false;
                exploded = false;
                return;
            } else {
                if (placedAnchorPos != null) {
                    if (!exploded) {
                        if (++placeTimeout > 10) {
                            placedAnchorPos = null;
                            placeTimeout = 0;
                        }
                    }
                    return;
                }

                if ("CLICK".equals(activation.getValString()) && ++latchWaitTicks > 20) {
                    activateLatched = false;
                    latchWaitTicks = 0;
                }

                if (keyUseIsActivateKey(activateCode)) return;
                if (!(mc.hitResult instanceof BlockHitResult hit)) return;
                BlockPos hitPos = hit.getBlockPos();
                if (mc.level.getBlockState(hitPos).is(Blocks.RESPAWN_ANCHOR)) return;

                mc.options.keyUse.setDown(false);
                mc.options.keyUse.consumeClick();
                if (!mc.player.getMainHandItem().is(Items.RESPAWN_ANCHOR)) {
                    if (switchCounter < (int) switchDelay.getValDouble()) {
                        switchCounter++;
                        return;
                    }
                    switchCounter = 0;
                    swapTo(Items.RESPAWN_ANCHOR);
                }
                BlockPos expectedPos = mc.level.getBlockState(hitPos).canBeReplaced() ? hitPos : hitPos.relative(hit.getDirection());
                if (waitForFireClear(expectedPos)) return;
                startUseItem();
                placedAnchorPos = expectedPos;
                placeTimeout = 0;
                latchWaitTicks = 0;
                chargedGlowstone = false;
                exploded = false;
                return;
            }
        }

        if (targetPos == null || !mc.level.getBlockState(targetPos).is(Blocks.RESPAWN_ANCHOR)) return;

        mc.options.keyUse.setDown(false);
        mc.options.keyUse.consumeClick();

        if ("CLICK".equals(activation.getValString())) {
            if (aimOnTarget(targetPos)) {
                noAimTicks = 0;
            } else if (++noAimTicks > 15) {
                activateLatched = false;
                latchWaitTicks = 0;
                chargedGlowstone = false;
                exploded = false;
                needsSwitchBack = false;
                currentDoubleAnchorCount = 0;
                placedAnchorPos = null;
                placeTimeout = 0;
                noAimTicks = 0;
                if (!mc.player.getMainHandItem().is(Items.RESPAWN_ANCHOR)) {
                    swapTo(Items.RESPAWN_ANCHOR);
                }
                return;
            }
        }

        int charges = mc.level.getBlockState(targetPos).getValue(BlockStateProperties.RESPAWN_ANCHOR_CHARGES);

        if (charges == 0) {
            exploded = false;
            if (!chargedGlowstone) {
                if (!mc.player.getMainHandItem().is(Items.GLOWSTONE)) {
                    if (switchCounter < (int) switchDelay.getValDouble()) {
                        switchCounter++;
                        return;
                    }
                    switchCounter = 0;
                    swapTo(Items.GLOWSTONE);
                }
                if (glowstoneCounter < (int) glowstoneDelay.getValDouble()) {
                    glowstoneCounter++;
                    return;
                }
                glowstoneCounter = 0;

                if (aimOnTarget(targetPos)) {
                    startUseItem();
                    chargedGlowstone = true;
                    chargeTimeout = 0;
                }
            } else if (++chargeTimeout > 10) {
                chargedGlowstone = false;
                chargeTimeout = 0;
            }
        } else if (charges > 0 && !exploded) {
            chargedGlowstone = false;
            chargeTimeout = 0;
            if (shouldDoubleAnchor()) {
                int targetMax = (int) doubleAnchorCount.getValDouble();
                int nextCount = currentDoubleAnchorCount + 1;

                if (nextCount < targetMax) {
                    if (!mc.player.getMainHandItem().is(Items.RESPAWN_ANCHOR)) {
                        if (switchCounter < (int) switchDelay.getValDouble()) {
                            switchCounter++;
                            return;
                        }
                        switchCounter = 0;
                        swapTo(Items.RESPAWN_ANCHOR);
                    }
                    if (explodeCounter < (int) explodeDelay.getValDouble()) {
                        explodeCounter++;
                        return;
                    }
                    explodeCounter = 0;
                    if (aimOnTarget(targetPos)) {
                        startUseItem();
                        startUseItem();
                    }
                    currentDoubleAnchorCount = nextCount;
                    exploded = true;
                } else {
                    int slot = (int) totemSlot.getValDouble() - 1;
                    if (ModuleUtils.getSelectedSlot() != slot) {
                        if (switchCounter < (int) switchDelay.getValDouble()) {
                            switchCounter++;
                            return;
                        }
                        switchCounter = 0;
                        ModuleUtils.switchToSlot(slot);
                    }
                    if (explodeCounter < (int) explodeDelay.getValDouble()) {
                        explodeCounter++;
                        return;
                    }
                    explodeCounter = 0;
                    if (aimOnTarget(targetPos)) {
                        startUseItem();
                    }
                    exploded = true;
                    currentDoubleAnchorCount = 0;
                    if (switchBack.getValBoolean()) {
                        needsSwitchBack = true;
                        switchBackCounter = 0;
                    }
                }
            } else {
                int slot = (int) totemSlot.getValDouble() - 1;
                if (ModuleUtils.getSelectedSlot() != slot) {
                    if (switchCounter < (int) switchDelay.getValDouble()) {
                        switchCounter++;
                        return;
                    }
                    switchCounter = 0;
                    ModuleUtils.switchToSlot(slot);
                }
                if (explodeCounter < (int) explodeDelay.getValDouble()) {
                    explodeCounter++;
                    return;
                }
                explodeCounter = 0;
                if (aimOnTarget(targetPos)) {
                    startUseItem();
                }
                exploded = true;
                if (switchBack.getValBoolean()) {
                    needsSwitchBack = true;
                    switchBackCounter = 0;
                }
            }
        }
    }

    private boolean checkDeadPlayers() {
        for (Player p : mc.level.players()) {
            if (p != mc.player && (p.isDeadOrDying() || p.getHealth() <= 0) && !deadPlayers.contains(p)) {
                deadPlayers.add(p);
                return true;
            }
        }
        deadPlayers.removeIf(p -> !p.isDeadOrDying() && p.getHealth() > 0);
        return false;
    }

    private boolean isEatingOrBlocking() {
        boolean shield = mc.player.getMainHandItem().getItem() instanceof ShieldItem
                || mc.player.getOffhandItem().getItem() instanceof ShieldItem;
        boolean keyHeld = InputUtil.isDown(parseKeyCode(activateKey.getValText()))
                || activateLatched
                || InputUtil.isDown(parseKeyCode(placeKey.getValText()));
        return shield && keyHeld;
    }

    private boolean keyUseIsActivateKey(int activateCode) {
        if (activateCode == 0 || mc.options == null || mc.options.keyUse == null) return false;
        String bindSave;
        if (InputUtil.isMouseBind(activateCode)) {
            int ordinal = -activateCode - 1;
            if (ordinal < 0 || ordinal > 4) return false;
            bindSave = InputConstants.Type.MOUSE.getOrCreate(ordinal).getName();
        } else {
            bindSave = InputConstants.Type.KEYSYM.getOrCreate(activateCode).getName();
        }
        try {
            return mc.options.keyUse.saveString().equals(bindSave);
        } catch (Exception e) {
            return false;
        }
    }

    private void swapTo(Item item) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(item)) {
                ModuleUtils.switchToSlot(i);
                return;
            }
        }
    }

    private void startUseItem() {
        ((MinecraftAccessor) mc).invokeStartUseItem();
    }

    private boolean waitForFireClear(BlockPos pos) {
        if (mc.level == null || pos == null) return false;
        BlockState state = mc.level.getBlockState(pos);
        if (!state.is(Blocks.FIRE) && !state.is(Blocks.SOUL_FIRE)) {
            fireBreakPending = null;
            fireBreakTicks = 0;
            return false;
        }
        if (!pos.equals(fireBreakPending)) {
            fireBreakPending = pos;
            fireBreakTicks = 0;
        }
        if (fireBreakTicks > 8) fireBreakTicks = 0;
        if (fireBreakTicks == 0) {
            mc.gameMode.startDestroyBlock(pos, Direction.UP);
            mc.player.swing(InteractionHand.MAIN_HAND);
        } else {
            mc.gameMode.continueDestroyBlock(pos, Direction.UP);
        }
        fireBreakTicks++;
        return true;
    }

    private boolean aimOnTarget(BlockPos pos) {
        if (mc.level == null || pos == null) return false;
        if (!mc.level.getBlockState(pos).is(Blocks.RESPAWN_ANCHOR)) return false;
        return mc.hitResult instanceof BlockHitResult h && h.getBlockPos().equals(pos);
    }

    private static int parseKeyCode(String keyName) {
        if (keyName == null) return 0;
        String n = keyName.trim().toUpperCase();
        if (n.isEmpty() || n.equals("NONE") || n.equals("UNKNOWN")) return 0;

        switch (n) {
            case "MOUSE LEFT":
            case "MOUSELEFT":
            case "LCLICK":
            case "MOUSE1":
            case "M1":
                return InputUtil.MOUSE_LEFT;
            case "MOUSE RIGHT":
            case "MOUSERIGHT":
            case "RCLICK":
            case "MOUSE2":
            case "M2":
                return InputUtil.MOUSE_RIGHT;
            case "MOUSE MIDDLE":
            case "MOUSEMIDDLE":
            case "MCLICK":
            case "MOUSE3":
            case "M3":
                return InputUtil.MOUSE_MIDDLE;
            case "MOUSE 4":
            case "MOUSE4":
            case "M4":
                return InputUtil.MOUSE_BUTTON_4;
            case "MOUSE 5":
            case "MOUSE5":
            case "M5":
                return InputUtil.MOUSE_BUTTON_5;
            default:
                break;
        }

        if (n.length() == 1) {
            char c = n.charAt(0);
            if (c >= 'A' && c <= 'Z') return InputConstants.KEY_A + (c - 'A');
            switch (c) {
                case '0': return InputConstants.KEY_0;
                case '1': return InputConstants.KEY_1;
                case '2': return InputConstants.KEY_2;
                case '3': return InputConstants.KEY_3;
                case '4': return InputConstants.KEY_4;
                case '5': return InputConstants.KEY_5;
                case '6': return InputConstants.KEY_6;
                case '7': return InputConstants.KEY_7;
                case '8': return InputConstants.KEY_8;
                case '9': return InputConstants.KEY_9;
                default: break;
            }
        }

        switch (n) {
            case "SPACE": return InputConstants.KEY_SPACE;
            case "LSHIFT":
            case "L-SHIFT":
            case "SHIFT": return InputConstants.KEY_LSHIFT;
            case "RSHIFT":
            case "R-SHIFT": return InputConstants.KEY_RSHIFT;
            case "LCTRL":
            case "L-CTRL":
            case "CTRL":
            case "CONTROL": return InputConstants.KEY_LCONTROL;
            case "RCTRL":
            case "R-CTRL": return InputConstants.KEY_RCONTROL;
            case "LALT":
            case "L-ALT":
            case "ALT": return InputConstants.KEY_LALT;
            case "RALT":
            case "R-ALT": return InputConstants.KEY_RALT;
            case "TAB": return InputConstants.KEY_TAB;
            case "ENTER":
            case "RETURN": return InputConstants.KEY_RETURN;
            case "ESC":
            case "ESCAPE": return InputConstants.KEY_ESCAPE;
            case "BACKSPACE": return InputConstants.KEY_BACKSPACE;
            case "DEL":
            case "DELETE": return InputConstants.KEY_DELETE;
            case "INS":
            case "INSERT": return InputConstants.KEY_INSERT;
            case "HOME": return InputConstants.KEY_HOME;
            case "END": return InputConstants.KEY_END;
            case "PGUP":
            case "PAGE_UP": return InputConstants.KEY_PAGEUP;
            case "PGDN":
            case "PAGE_DOWN": return InputConstants.KEY_PAGEDOWN;
            case "UP": return InputConstants.KEY_UP;
            case "DOWN": return InputConstants.KEY_DOWN;
            case "LEFT": return InputConstants.KEY_LEFT;
            case "RIGHT": return InputConstants.KEY_RIGHT;
            case "CAPS":
            case "CAPSLOCK": return InputConstants.KEY_CAPSLOCK;
            case "GRAVE": return InputConstants.KEY_GRAVE;
            case "MINUS": return InputConstants.KEY_MINUS;
            case "EQUALS": return InputConstants.KEY_EQUALS;
            case "LBRACKET": return InputConstants.KEY_LBRACKET;
            case "RBRACKET": return InputConstants.KEY_RBRACKET;
            case "SEMICOLON": return InputConstants.KEY_SEMICOLON;
            case "APOSTROPHE": return InputConstants.KEY_APOSTROPHE;
            case "COMMA": return InputConstants.KEY_COMMA;
            case "PERIOD": return InputConstants.KEY_PERIOD;
            case "SLASH": return InputConstants.KEY_SLASH;
            case "BACKSLASH": return InputConstants.KEY_BACKSLASH;
            case "F1": return InputConstants.KEY_F1;
            case "F2": return InputConstants.KEY_F2;
            case "F3": return InputConstants.KEY_F3;
            case "F4": return InputConstants.KEY_F4;
            case "F5": return InputConstants.KEY_F5;
            case "F6": return InputConstants.KEY_F6;
            case "F7": return InputConstants.KEY_F7;
            case "F8": return InputConstants.KEY_F8;
            case "F9": return InputConstants.KEY_F9;
            case "F10": return InputConstants.KEY_F10;
            case "F11": return InputConstants.KEY_F11;
            case "F12": return InputConstants.KEY_F12;
            default: break;
        }

        if (n.startsWith("KEY-")) {
            try {
                return Integer.parseInt(n.substring(4));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }
}
