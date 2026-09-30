package com.eclipseware.imnotcheatingyouare.client.ui;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

public abstract class CompatAbstractWidget extends AbstractWidget {
    protected CompatAbstractWidget(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }
}
