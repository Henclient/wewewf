package com.henclient.reborn.modules.hud;

import com.henclient.reborn.HenClientReborn;
import com.henclient.reborn.module.HudTextModule;
import net.minecraft.client.Minecraft;

public final class FpsModule extends HudTextModule {
    public FpsModule() {
        super("FPS Counter", "Shows the current frames per second.");
        setEnabled(true);
    }

    @Override
    public String hudText(Minecraft mc) {
        return "FPS  " + HenClientReborn.hud().fps();
    }

    @Override
    public int hudColor(Minecraft mc) {
        int fps = HenClientReborn.hud().fps();
        if (fps >= 60) {
            return 0xFF66BB6A;
        }
        return fps >= 30 ? 0xFFFFCA28 : 0xFFEF5350;
    }
}
