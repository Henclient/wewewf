package com.henclient.reborn.module;

import com.henclient.reborn.HenClientReborn;
import com.henclient.reborn.module.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class of every feature. Hooks are only called while the module is enabled
 * (except {@link #onWorldUnload()}, which is always called so caches can be dropped).
 * Tick hooks are only called while both a world and a player exist.
 */
public abstract class Module {
    private final String name;
    private final String description;
    private final Category category;
    private final List<Setting<?>> settings = new ArrayList<>();
    private boolean enabled;
    private int keyCode = -1;

    protected Module(String name, String description, Category category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    public final String name() {
        return name;
    }

    public final String description() {
        return description;
    }

    public final Category category() {
        return category;
    }

    public final boolean isEnabled() {
        return enabled;
    }

    public final int keyCode() {
        return keyCode;
    }

    public final void setKeyCode(int keyCode) {
        this.keyCode = keyCode;
    }

    public final List<Setting<?>> settings() {
        return Collections.unmodifiableList(settings);
    }

    protected final <S extends Setting<?>> S addSetting(S setting) {
        settings.add(setting);
        return setting;
    }

    public final void toggle() {
        setEnabled(!enabled);
    }

    public final void setEnabled(boolean newState) {
        if (newState == enabled) {
            return;
        }
        enabled = newState;
        try {
            if (newState) {
                onEnable();
            } else {
                onDisable();
            }
        } catch (Throwable t) {
            HenClientReborn.LOGGER.error("Module {} failed during {}", name, newState ? "enable" : "disable", t);
        }
    }

    /** Optional note shown in the GUI settings panel (limitations, warnings). */
    public String note() {
        return null;
    }

    /** Short lines shown in the overlay panel while the module is enabled. */
    public List<String> overlayLines() {
        return Collections.emptyList();
    }

    /** True for modules that only show a single line in the info panel. */
    public boolean isInfoLine() {
        return false;
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    public void onTick(Minecraft mc) {
    }

    /** Called every frame while enabled and in a world. */
    public void renderHud(GuiGraphics graphics, Minecraft mc, float partialTick) {
    }

    /** Called when the world unloads, the player disconnects or the dimension changes. */
    public void onWorldUnload() {
    }
}
