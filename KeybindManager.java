package com.henclient.reborn.input;

import com.henclient.reborn.HenClientReborn;
import com.henclient.reborn.gui.ClickGuiScreen;
import com.henclient.reborn.module.Module;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/**
 * Polls GLFW key state once per tick and fires module toggles and the GUI hotkey on key-down edges.
 * Polling is used instead of Minecraft key mappings so the keys stay fully configurable in our own GUI.
 * Nothing fires while any screen (chat, inventory, our GUI) is open.
 */
public final class KeybindManager {
    private final Map<Integer, Boolean> previous = new HashMap<>();

    public void tick(Minecraft mc) {
        long window = GLFW.glfwGetCurrentContext();
        if (window == 0L) {
            return;
        }
        boolean screenOpen = mc.screen != null;

        int guiKey = HenClientReborn.config().guiKey();
        boolean guiEdge = edge(window, guiKey);
        if (guiEdge && !screenOpen) {
            mc.setScreen(new ClickGuiScreen());
            return;
        }

        for (Module module : HenClientReborn.modules().all()) {
            int key = module.keyCode();
            if (key < 0) {
                continue;
            }
            boolean pressed = edge(window, key);
            if (pressed && !screenOpen && mc.level != null) {
                module.toggle();
                HenClientReborn.config().save();
            }
        }
    }

    /** Returns true only on the tick a key goes from released to pressed. */
    private boolean edge(long window, int key) {
        if (key < 0) {
            return false;
        }
        boolean down = GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
        boolean was = previous.getOrDefault(key, false);
        previous.put(key, down);
        return down && !was;
    }

    /** Ignore a key until it has been released once (prevents instant re-trigger after closing the GUI). */
    public void markHeld(int key) {
        previous.put(key, true);
    }
}
