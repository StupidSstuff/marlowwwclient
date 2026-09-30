package com.eclipseware.imnotcheatingyouare.client.module.impl;

import com.eclipseware.imnotcheatingyouare.client.module.Category;
import com.eclipseware.imnotcheatingyouare.client.module.Module;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import com.eclipseware.imnotcheatingyouare.client.utils.InputUtil;

public class GUIMove extends Module {
    public GUIMove() {
        super("GUIMove", Category.Blatant, "Allows you to walk and jump while in menus.");
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (com.eclipseware.imnotcheatingyouare.client.module.impl.AutoTotem.shouldPauseInputs()) return;
        if (mc.screen != null && !(mc.screen instanceof ChatScreen) && !(mc.screen instanceof SignEditScreen) && !(mc.screen instanceof AnvilScreen)) {
            mc.options.keyUp.setDown(InputUtil.isDown(getKeyCode(mc.options.keyUp)));
            mc.options.keyDown.setDown(InputUtil.isDown(getKeyCode(mc.options.keyDown)));
            mc.options.keyLeft.setDown(InputUtil.isDown(getKeyCode(mc.options.keyLeft)));
            mc.options.keyRight.setDown(InputUtil.isDown(getKeyCode(mc.options.keyRight)));
            mc.options.keyJump.setDown(InputUtil.isDown(getKeyCode(mc.options.keyJump)));
            mc.options.keySprint.setDown(InputUtil.isDown(getKeyCode(mc.options.keySprint)));
            if (mc.options.keySprint.isDown()) {
                mc.player.setSprinting(true);
            }
        }
    }

    private int getKeyCode(KeyMapping mapping) {
        return mapping.getDefaultKey().getValue();
    }
}