package com.henclient.reborn.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Small helper for client-side chat messages that are never sent to the server. */
public final class Notifier {
    private Notifier() {
    }

    public static void chat(Minecraft mc, String message) {
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("[HenClient] " + message), false);
        }
    }
}
