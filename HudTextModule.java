package com.henclient.reborn.module;

import net.minecraft.client.Minecraft;

/** A module that contributes a single colored line to the information panel. */
public abstract class HudTextModule extends Module {
    protected HudTextModule(String name, String description) {
        super(name, description, Category.HUD);
    }

    /** Text to show, or null to show nothing this frame. */
    public abstract String hudText(Minecraft mc);

    public int hudColor(Minecraft mc) {
        return 0xFFF2F2F2;
    }

    @Override
    public final boolean isInfoLine() {
        return true;
    }
}
