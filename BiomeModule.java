package com.henclient.reborn.modules.hud;

import com.henclient.reborn.module.HudTextModule;
import net.minecraft.client.Minecraft;

public final class BiomeModule extends HudTextModule {
    public BiomeModule() {
        super("Biome", "Shows the biome you are standing in.");
        setEnabled(true);
    }

    @Override
    public String hudText(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            return null;
        }
        String id = mc.level.getBiome(mc.player.blockPosition()).getRegisteredName();
        int colon = id.indexOf(':');
        String path = colon >= 0 ? id.substring(colon + 1) : id;
        return "Biome  " + pretty(path);
    }

    private static String pretty(String path) {
        StringBuilder sb = new StringBuilder();
        boolean upper = true;
        for (char c : path.toCharArray()) {
            if (c == '_' || c == '/') {
                sb.append(' ');
                upper = true;
            } else {
                sb.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return sb.toString();
    }
}
