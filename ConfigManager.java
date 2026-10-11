package com.henclient.reborn.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.henclient.reborn.HenClientReborn;
import com.henclient.reborn.module.Module;
import com.henclient.reborn.module.ModuleManager;
import com.henclient.reborn.module.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Loads and saves module states, keybinds and settings as JSON in the Fabric config directory. */
public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file = FabricLoader.getInstance().getConfigDir().resolve("henclient-reborn.json");
    private final ModuleManager modules;
    private int guiKey = GLFW.GLFW_KEY_RIGHT_SHIFT;

    public ConfigManager(ModuleManager modules) {
        this.modules = modules;
    }

    public int guiKey() {
        return guiKey;
    }

    public void setGuiKey(int key) {
        this.guiKey = key;
    }

    public void load() {
        if (!Files.exists(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) {
                return;
            }
            if (root.has("guiKey") && root.get("guiKey").isJsonPrimitive()) {
                guiKey = root.get("guiKey").getAsInt();
            }
            JsonObject mods = root.has("modules") && root.get("modules").isJsonObject()
                    ? root.getAsJsonObject("modules") : new JsonObject();
            for (Module module : modules.all()) {
                if (!mods.has(module.name()) || !mods.get(module.name()).isJsonObject()) {
                    continue;
                }
                JsonObject obj = mods.getAsJsonObject(module.name());
                if (obj.has("key") && obj.get("key").isJsonPrimitive()) {
                    module.setKeyCode(obj.get("key").getAsInt());
                }
                if (obj.has("settings") && obj.get("settings").isJsonObject()) {
                    JsonObject settings = obj.getAsJsonObject("settings");
                    for (Setting<?> setting : module.settings()) {
                        JsonElement element = settings.get(setting.key());
                        if (element != null) {
                            setting.fromJson(element);
                        }
                    }
                }
                // Enable last so onEnable sees the loaded settings.
                if (obj.has("enabled") && obj.get("enabled").isJsonPrimitive()) {
                    module.setEnabled(obj.get("enabled").getAsBoolean());
                }
            }
        } catch (Exception e) {
            HenClientReborn.LOGGER.error("Could not read {}; using defaults", file, e);
        }
    }

    public void save() {
        JsonObject root = new JsonObject();
        root.addProperty("guiKey", guiKey);
        JsonObject mods = new JsonObject();
        for (Module module : modules.all()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("enabled", module.isEnabled());
            obj.addProperty("key", module.keyCode());
            JsonObject settings = new JsonObject();
            for (Setting<?> setting : module.settings()) {
                settings.add(setting.key(), setting.toJson());
            }
            obj.add("settings", settings);
            mods.add(module.name(), obj);
        }
        root.add("modules", mods);

        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            HenClientReborn.LOGGER.error("Could not save {}", file, e);
        }
    }
}
