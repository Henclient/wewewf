package com.henclient.reborn.modules.hud;

import com.henclient.reborn.module.HudTextModule;
import com.henclient.reborn.module.setting.BooleanSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.Locale;

public final class CoordinatesModule extends HudTextModule {
    private final BooleanSetting decimals = addSetting(new BooleanSetting("decimals", "Show decimals", false));

    public CoordinatesModule() {
        super("Coordinates", "Shows your X, Y and Z position.");
        setEnabled(true);
    }

    @Override
    public String hudText(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) {
            return null;
        }
        if (decimals.isOn()) {
            return String.format(Locale.ROOT, "XYZ  %.1f / %.1f / %.1f", p.getX(), p.getY(), p.getZ());
        }
        return "XYZ  " + p.getBlockX() + " / " + p.getBlockY() + " / " + p.getBlockZ();
    }
}
