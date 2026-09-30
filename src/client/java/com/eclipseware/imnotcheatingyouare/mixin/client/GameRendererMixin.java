package com.eclipseware.imnotcheatingyouare.mixin.client;

import com.eclipseware.imnotcheatingyouare.client.ImnotcheatingyouareClient;
import com.eclipseware.imnotcheatingyouare.client.module.impl.RenderOptimizer;
import com.eclipseware.imnotcheatingyouare.client.setting.Setting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    private int itemActivationTicks;

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        if (RenderOptimizer.INSTANCE != null && RenderOptimizer.INSTANCE.isToggled()) {
            Setting fastTotem = ImnotcheatingyouareClient.INSTANCE.settingsManager.getSettingByName(RenderOptimizer.INSTANCE, "Fast Totem Anim");
            if (fastTotem != null && fastTotem.getValBoolean()) {
                if (this.itemActivationTicks > 0) {
                    this.itemActivationTicks -= 2;
                    if (this.itemActivationTicks < 0) this.itemActivationTicks = 0;
                }
            }
        }
    }

    @Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true)
    private void onDisplayItemActivation(net.minecraft.world.item.ItemStack stack, CallbackInfo ci) {
        if (stack == null || !stack.is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING)) return;
        if (ImnotcheatingyouareClient.INSTANCE == null || ImnotcheatingyouareClient.INSTANCE.moduleManager == null) return;

        com.eclipseware.imnotcheatingyouare.client.module.Module noTotemPop = ImnotcheatingyouareClient.INSTANCE.moduleManager.getModule("NoTotemPop");
        if (noTotemPop != null && noTotemPop.isToggled()) {
            ci.cancel();
        }
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void onBobHurt(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        if (RenderOptimizer.INSTANCE != null && RenderOptimizer.INSTANCE.isToggled()) {
            Setting noHurtCam = ImnotcheatingyouareClient.INSTANCE.settingsManager.getSettingByName(RenderOptimizer.INSTANCE, "No Hurt Cam");
            if (noHurtCam != null && noHurtCam.getValBoolean()) {
                ci.cancel();
            }
        }
    }

}
