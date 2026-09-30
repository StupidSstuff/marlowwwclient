package com.eclipseware.imnotcheatingyouare.client.utils;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Keyboard/mouse polling for Minecraft 1.21.11 (GLFW window). Keyboard codes are GLFW key codes
 * ({@link InputConstants} values). Mouse buttons are encoded as negative ints, one more than
 * the GLFW button index: -1 = left, -2 = right, -3 = middle, -4/-5 = side buttons.
 */
public final class InputUtil {
    private InputUtil() {}

    public static final int MOUSE_LEFT = -1;
    public static final int MOUSE_RIGHT = -2;
    public static final int MOUSE_MIDDLE = -3;
    public static final int MOUSE_BUTTON_4 = -4;
    public static final int MOUSE_BUTTON_5 = -5;

    /** Convert Minecraft's click-event button ordinal (0=left,1=right,2=middle,3+=extra) to our stored code. */
    public static int fromClickOrdinal(int ordinal) {
        return -(ordinal + 1);
    }

    public static int toLegacyOrdinal(int glfwButton) {
        return glfwButton;
    }

    public static boolean isMouseBind(int code) {
        return code < 0;
    }

    public static boolean isDown(int code) {
        if (code == 0) return false;
        if (isMouseBind(code)) {
            return isMouseButtonDown(code);
        }
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), code);
    }

    public static boolean isMouseButtonDown(int mouseCode) {
        int ordinal = -mouseCode - 1;
        if (ordinal < 0 || ordinal > 4) return false;
        return GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().handle(), ordinal) == GLFW.GLFW_PRESS;
    }

    /** Human-readable name for a keybind code, mouse or keyboard. */
    public static String getName(int code) {
        if (code == 0) return "NONE";
        if (isMouseBind(code)) {
            return switch (code) {
                case MOUSE_LEFT -> "MOUSE LEFT";
                case MOUSE_MIDDLE -> "MOUSE MIDDLE";
                case MOUSE_RIGHT -> "MOUSE RIGHT";
                case MOUSE_BUTTON_4 -> "MOUSE 4";
                case MOUSE_BUTTON_5 -> "MOUSE 5";
                default -> "MOUSE ?";
            };
        }

        switch (code) {
            case InputConstants.KEY_RSHIFT: return "RSHIFT";
            case InputConstants.KEY_LSHIFT: return "LSHIFT";
            case InputConstants.KEY_RCONTROL: return "RCTRL";
            case InputConstants.KEY_LCONTROL: return "LCTRL";
            case InputConstants.KEY_RALT: return "RALT";
            case InputConstants.KEY_LALT: return "LALT";
            case InputConstants.KEY_TAB: return "TAB";
            case InputConstants.KEY_SPACE: return "SPACE";
            case InputConstants.KEY_RETURN: return "ENTER";
            case InputConstants.KEY_ESCAPE: return "NONE";
            default: break;
        }

        try {
            String name = InputConstants.Type.KEYSYM.getOrCreate(code).getDisplayName().getString();
            if (name != null && !name.isEmpty()) return name.toUpperCase();
        } catch (Throwable ignored) {}
        return "KEY " + code;
    }
}
