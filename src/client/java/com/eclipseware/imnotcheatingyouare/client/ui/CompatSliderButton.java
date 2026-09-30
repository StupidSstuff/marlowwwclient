package com.eclipseware.imnotcheatingyouare.client.ui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

public abstract class CompatSliderButton extends AbstractSliderButton {
    protected CompatSliderButton(int x, int y, int width, int height, Component message, double value) {
        super(x, y, width, height, message, value);
    }
}
