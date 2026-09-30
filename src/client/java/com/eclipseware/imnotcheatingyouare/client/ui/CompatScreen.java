package com.eclipseware.imnotcheatingyouare.client.ui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class CompatScreen extends Screen {
    protected CompatScreen(Component title) {
        super(title);
    }
}
