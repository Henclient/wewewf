package com.henclient.reborn.input;

import org.lwjgl.glfw.GLFW;

/** Readable names for GLFW key codes (kept local so no Minecraft input API is needed). */
public final class KeyNames {
    private KeyNames() {
    }

    public static String of(int key) {
        if (key < 0) {
            return "NONE";
        }
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) {
            return String.valueOf((char) key);
        }
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) {
            return String.valueOf((char) key);
        }
        if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F25) {
            return "F" + (key - GLFW.GLFW_KEY_F1 + 1);
        }
        if (key >= GLFW.GLFW_KEY_KP_0 && key <= GLFW.GLFW_KEY_KP_9) {
            return "NUM " + (key - GLFW.GLFW_KEY_KP_0);
        }
        switch (key) {
            case GLFW.GLFW_KEY_SPACE: return "SPACE";
            case GLFW.GLFW_KEY_LEFT_SHIFT: return "L-SHIFT";
            case GLFW.GLFW_KEY_RIGHT_SHIFT: return "R-SHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL: return "L-CTRL";
            case GLFW.GLFW_KEY_RIGHT_CONTROL: return "R-CTRL";
            case GLFW.GLFW_KEY_LEFT_ALT: return "L-ALT";
            case GLFW.GLFW_KEY_RIGHT_ALT: return "R-ALT";
            case GLFW.GLFW_KEY_TAB: return "TAB";
            case GLFW.GLFW_KEY_ENTER: return "ENTER";
            case GLFW.GLFW_KEY_UP: return "UP";
            case GLFW.GLFW_KEY_DOWN: return "DOWN";
            case GLFW.GLFW_KEY_LEFT: return "LEFT";
            case GLFW.GLFW_KEY_RIGHT: return "RIGHT";
            case GLFW.GLFW_KEY_INSERT: return "INSERT";
            case GLFW.GLFW_KEY_DELETE: return "DELETE";
            case GLFW.GLFW_KEY_HOME: return "HOME";
            case GLFW.GLFW_KEY_END: return "END";
            case GLFW.GLFW_KEY_PAGE_UP: return "PGUP";
            case GLFW.GLFW_KEY_PAGE_DOWN: return "PGDN";
            case GLFW.GLFW_KEY_GRAVE_ACCENT: return "`";
            case GLFW.GLFW_KEY_MINUS: return "-";
            case GLFW.GLFW_KEY_EQUAL: return "=";
            case GLFW.GLFW_KEY_LEFT_BRACKET: return "[";
            case GLFW.GLFW_KEY_RIGHT_BRACKET: return "]";
            case GLFW.GLFW_KEY_SEMICOLON: return ";";
            case GLFW.GLFW_KEY_APOSTROPHE: return "'";
            case GLFW.GLFW_KEY_COMMA: return ",";
            case GLFW.GLFW_KEY_PERIOD: return ".";
            case GLFW.GLFW_KEY_SLASH: return "/";
            case GLFW.GLFW_KEY_BACKSLASH: return "\\";
            default: return "KEY " + key;
        }
    }
}
