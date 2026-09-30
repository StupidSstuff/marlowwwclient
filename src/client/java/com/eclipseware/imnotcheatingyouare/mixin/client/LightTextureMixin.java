package com.eclipseware.imnotcheatingyouare.mixin.client;

import com.eclipseware.imnotcheatingyouare.client.ImnotcheatingyouareClient;
import com.eclipseware.imnotcheatingyouare.client.module.Module;
import com.eclipseware.imnotcheatingyouare.client.setting.Setting;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LightTexture.class)
public class LightTextureMixin {

    private static boolean imnotcheatingyouare$gammaFullbright() {
        Module fbMod = ImnotcheatingyouareClient.INSTANCE.moduleManager.getModule("Fullbright");
        if (fbMod != null && fbMod.isToggled()) {
            Setting mode = ImnotcheatingyouareClient.INSTANCE.settingsManager.getSettingByName(fbMod, "Mode");
            return mode != null && mode.getValString().equals("Gamma");
        }
        return false;
    }

    @Inject(method = "getBrightness(Lnet/minecraft/world/level/dimension/DimensionType;I)F", at = @At("HEAD"), cancellable = true)
    private static void onGetBrightness(net.minecraft.world.level.dimension.DimensionType dimensionType, int lightLevel, CallbackInfoReturnable<Float> cir) {
        if (imnotcheatingyouare$gammaFullbright()) {
            cir.setReturnValue(15.0F);
        }
    }

    @ModifyExpressionValue(
            method = "updateLightTexture",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 2)
    )
    private Object imnotcheatingyouare$fullbrightGamma(Object original) {
        return imnotcheatingyouare$gammaFullbright() ? (Object) Double.valueOf(15.0) : original;
    }
}
