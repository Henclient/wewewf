package com.henclient.reborn.modules.hud;

import com.henclient.reborn.module.HudTextModule;
import net.minecraft.client.Minecraft;

public final class DirectionModule extends HudTextModule {
    // Minecraft yaw 0 faces south (+Z); yaw increases clockwise seen from above.
    private static final String[] NAMES = {"South", "South-West", "West", "North-West", "North", "North-East", "East", "South-East"};
    private static final String[] AXES = {"+Z", "-X +Z", "-X", "-X -Z", "-Z", "+X -Z", "+X", "+X +Z"};

    public DirectionModule() {
        super("Direction", "Shows the compass direction you are facing.");
        setEnabled(true);
    }

    @Override
    public String hudText(Minecraft mc) {
        if (mc.player == null) {
            return null;
        }
        double yaw = ((mc.player.getYRot() % 360.0) + 360.0) % 360.0;
        int index = (int) Math.floor((yaw + 22.5) / 45.0) % 8;
        return "Facing  " + NAMES[index] + " (" + AXES[index] + ")";
    }
}
