package com.henclient.reborn.modules.hud;

import com.henclient.reborn.module.HudTextModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.Locale;

public final class HealthModule extends HudTextModule {
    public HealthModule() {
        super("Health HUD", "Shows your health and absorption.");
        setEnabled(true);
    }

    @Override
    public String hudText(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) {
            return null;
        }
        String text = String.format(Locale.ROOT, "HP  %.1f / %.1f", p.getHealth(), p.getMaxHealth());
        float absorption = p.getAbsorptionAmount();
        if (absorption > 0) {
            text += String.format(Locale.ROOT, "  (+%.1f)", absorption);
        }
        return text;
    }

    @Override
    public int hudColor(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || p.getMaxHealth() <= 0) {
            return 0xFFF2F2F2;
        }
        float ratio = p.getHealth() / p.getMaxHealth();
        if (ratio > 0.6f) {
            return 0xFF66BB6A;
        }
        return ratio > 0.3f ? 0xFFFFCA28 : 0xFFEF5350;
    }
}
