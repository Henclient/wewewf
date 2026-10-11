package com.henclient.reborn.module;

import com.henclient.reborn.HenClientReborn;
import com.henclient.reborn.modules.combat.AimAssistModule;
import com.henclient.reborn.modules.combat.AutoClickerModule;
import com.henclient.reborn.modules.exploration.FreecamModule;
import com.henclient.reborn.modules.exploration.SpawnerFinderModule;
import com.henclient.reborn.modules.exploration.StorageEspModule;
import com.henclient.reborn.modules.exploration.StashFinderModule;
import com.henclient.reborn.modules.exploration.SusChunkFinderModule;
import com.henclient.reborn.modules.hud.ArmorStatusModule;
import com.henclient.reborn.modules.hud.BiomeModule;
import com.henclient.reborn.modules.hud.CoordinatesModule;
import com.henclient.reborn.modules.hud.DirectionModule;
import com.henclient.reborn.modules.hud.FpsModule;
import com.henclient.reborn.modules.hud.HealthModule;
import com.henclient.reborn.modules.hud.SprintStatusModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ModuleManager {
    private final List<Module> modules = new ArrayList<>();
    private ClientLevel lastLevel;

    public void registerDefaults() {
        register(new CoordinatesModule());
        register(new FpsModule());
        register(new HealthModule());
        register(new SprintStatusModule());
        register(new ArmorStatusModule());
        register(new DirectionModule());
        register(new BiomeModule());

        register(new StashFinderModule());
        register(new SpawnerFinderModule());
        register(new SusChunkFinderModule());
        register(new StorageEspModule());
        register(new FreecamModule());

        register(new AutoClickerModule());
        register(new AimAssistModule());
    }

    private void register(Module module) {
        modules.add(module);
    }

    public List<Module> all() {
        return Collections.unmodifiableList(modules);
    }

    public List<Module> in(Category category) {
        List<Module> result = new ArrayList<>();
        for (Module m : modules) {
            if (m.category() == category) {
                result.add(m);
            }
        }
        return result;
    }

    public Module byName(String name) {
        for (Module m : modules) {
            if (m.name().equalsIgnoreCase(name)) {
                return m;
            }
        }
        return null;
    }

    public <T extends Module> T get(Class<T> type) {
        for (Module m : modules) {
            if (type.isInstance(m)) {
                return type.cast(m);
            }
        }
        return null;
    }

    public boolean isEnabled(Class<? extends Module> type) {
        Module m = get(type);
        return m != null && m.isEnabled();
    }

    /** Runs once per client tick. Safe to call without a world or player. */
    public void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level != lastLevel) {
            if (lastLevel != null) {
                onWorldUnload();
            }
            lastLevel = level;
        }
        if (level == null || mc.player == null) {
            return;
        }
        for (Module m : modules) {
            if (!m.isEnabled()) {
                continue;
            }
            try {
                m.onTick(mc);
            } catch (Throwable t) {
                HenClientReborn.LOGGER.error("Module {} crashed in tick and was disabled", m.name(), t);
                m.setEnabled(false);
            }
        }
    }

    public void onWorldUnload() {
        for (Module m : modules) {
            try {
                m.onWorldUnload();
            } catch (Throwable t) {
                HenClientReborn.LOGGER.error("Module {} failed to unload world state", m.name(), t);
            }
        }
    }
}
