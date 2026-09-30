package com.eclipseware.imnotcheatingyouare.mixin.client;

import com.eclipseware.imnotcheatingyouare.client.render.HandShaderRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class HandShaderMixin {

    @Inject(method = "renderHandsWithItems", at = @At("HEAD"))
    private void beginHandCapture(CallbackInfo ci) {
        if (HandShaderRenderer.shouldCapture()) {
            try {
                HandShaderRenderer.beginCapture(Minecraft.getInstance().getMainRenderTarget());
            } catch (Throwable ignored) {
                HandShaderRenderer.abortCapture();
            }
        }
    }

    @Inject(method = "renderHandsWithItems", at = @At("TAIL"))
    private void compositeHands(CallbackInfo ci) {
        HandShaderRenderer.composite(Minecraft.getInstance().getMainRenderTarget());
    }
}
