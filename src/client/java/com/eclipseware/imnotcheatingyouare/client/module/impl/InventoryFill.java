package com.eclipseware.imnotcheatingyouare.client.module.impl;

import com.eclipseware.imnotcheatingyouare.client.module.Category;
import com.eclipseware.imnotcheatingyouare.client.module.Module;
import com.eclipseware.imnotcheatingyouare.mixin.client.AbstractContainerScreenAccessor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;

public class InventoryFill extends Module {

    public InventoryFill() {
        super("InventoryFill", Category.Farming, "While holding shift, automatically clicks while in inventory to quickly move items.");
        setSubCategory("Inventory");
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.gameMode == null) return;
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) return;
        if (!mc.options.keyShift.isDown()) return;

        Slot hovered = ((AbstractContainerScreenAccessor) screen).getHoveredSlot();
        if (hovered == null || !hovered.hasItem()) return;

        mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, hovered.index, 0, ClickType.QUICK_MOVE, mc.player);
    }
}
