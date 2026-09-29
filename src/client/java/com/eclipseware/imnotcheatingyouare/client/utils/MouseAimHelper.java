package com.eclipseware.imnotcheatingyouare.client.utils;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class MouseAimHelper {
    private static double manualDeltaX;
    private static double manualDeltaY;
    
    private static double targetAimRateX;
    private static double targetAimRateY;
    
    private static long lastAimTime;
    private static long lastTickTime;
    private static double deltaTime;
    
    private static double accumulatorX;
    private static double accumulatorY;

    public interface FrameSource {
        boolean active();
        void step(double dtSeconds, double[] outDegrees);
    }

    private static volatile FrameSource frameSource;
    private static long lastFrameNanos;
    private static double framePitchPending;
    private static double frameAccX;
    private static double frameAccY;
    private static final double[] frameOut = new double[2];

    private MouseAimHelper() {}

    public static void setFrameSource(FrameSource source) {
        frameSource = source;
        lastFrameNanos = 0L;
        frameAccX = 0.0D;
        frameAccY = 0.0D;
        framePitchPending = 0.0D;
    }

    private static double stepFrameSource() {
        FrameSource src = frameSource;
        long now = System.nanoTime();
        double dt = lastFrameNanos == 0L ? 0.0D : Math.min((now - lastFrameNanos) / 1_000_000_000.0D, 0.1D);
        lastFrameNanos = now;

        framePitchPending = 0.0D;
        if (src == null || !src.active()) {
            frameAccX = 0.0D;
            frameAccY = 0.0D;
            return 0.0D;
        }

        frameOut[0] = 0.0D;
        frameOut[1] = 0.0D;
        src.step(dt, frameOut);

        float sens = getSensitivityMultiplier();
        frameAccX += frameOut[0];
        frameAccY += frameOut[1];

        long px = Math.round(frameAccX / sens);
        frameAccX -= px * (double) sens;
        long py = Math.round(frameAccY / sens);
        frameAccY -= py * (double) sens;

        framePitchPending = py;
        return px;
    }

    public static void addDelta(double dx, double dy) {
        manualDeltaX += dx;
        manualDeltaY += dy;
    }

    public static void setAimRate(double rateX, double rateY) {
        targetAimRateX = rateX;
        targetAimRateY = rateY;
        
        long currentTime = System.currentTimeMillis();
        lastAimTime = currentTime;
        
        if (lastTickTime == 0L) {
            lastTickTime = currentTime;
        }
    }

    public static void clearAimRate() {
        targetAimRateX = 0.0D;
        targetAimRateY = 0.0D;
        accumulatorX = 0.0D;
        accumulatorY = 0.0D;
        lastTickTime = 0L;
        deltaTime = 0.0D;
    }

    public static double pollDX() {
        double currentDelta = manualDeltaX + stepFrameSource();
        manualDeltaX = 0.0D;
        
        long currentTime = System.currentTimeMillis();
        long timeSinceLastTick = (lastTickTime > 0L) ? Math.min(currentTime - lastTickTime, 100L) : 0L;
        lastTickTime = currentTime;
        
        deltaTime = timeSinceLastTick / 50.0D;
        
        if (targetAimRateX != 0.0D && currentTime - lastAimTime <= 100L) {
            float sensitivityMultiplier = getSensitivityMultiplier();
            accumulatorX += targetAimRateX * deltaTime;
            
            long discretePixels = Math.round(accumulatorX / sensitivityMultiplier);
            accumulatorX -= ((float)discretePixels * sensitivityMultiplier);
            currentDelta += discretePixels;
        }
        
        return currentDelta;
    }

    public static double pollDY() {
        double currentDelta = manualDeltaY + framePitchPending;
        framePitchPending = 0.0D;
        manualDeltaY = 0.0D;
        
        long currentTime = System.currentTimeMillis();
        
        if (targetAimRateY != 0.0D && currentTime - lastAimTime <= 100L) {
            float sensitivityMultiplier = getSensitivityMultiplier();
            accumulatorY += targetAimRateY * deltaTime;
            
            long discretePixels = Math.round(accumulatorY / sensitivityMultiplier);
            accumulatorY -= ((float)discretePixels * sensitivityMultiplier);
            currentDelta += discretePixels;
        }
        
        return currentDelta;
    }

    private static float getSensitivityMultiplier() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return 0.15F;
        
        float rawSensitivity = mc.options.sensitivity().get().floatValue();
        float f2 = rawSensitivity * 0.6F + 0.2F;
        return f2 * f2 * f2 * 1.2F;
    }

    static {
        manualDeltaX = 0.0D;
        manualDeltaY = 0.0D;
        targetAimRateX = 0.0D;
        targetAimRateY = 0.0D;
        lastAimTime = 0L;
        lastTickTime = 0L;
        accumulatorX = 0.0D;
        accumulatorY = 0.0D;
        deltaTime = 0.0D;
    }
}
