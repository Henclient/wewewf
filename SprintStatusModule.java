package com.henclient.reborn.modules.hud;

import com.henclient.reborn.module.HudTextModule;
import net.minecraft.client.Minecraft;

public final class SprintStatusModule extends HudTextModule {
    public SprintStatusModule() {
        super("Sprint Status", "Shows whether you are currently sprinting.");
    }

    @Override
    public String hudText(Minecraft mc) {
        if (mc.player == null) {
            return null;
        }
        return "Sprint  " + (mc.player.isSprinting() ? "ON" : "OFF");
    }

    @Override
    public int hudColor(Minecraft mc) {
        return mc.player != null && mc.player.isSprinting() ? 0xFF66BB6A : 0xFF9A9AA2;
    }
}
