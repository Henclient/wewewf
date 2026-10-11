package com.henclient.reborn;

import com.henclient.reborn.config.ConfigManager;
import com.henclient.reborn.hud.HudRenderer;
import com.henclient.reborn.input.KeybindManager;
import com.henclient.reborn.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client entrypoint: wires the module system, config, keybinds and HUD together. */
public final class HenClientReborn implements ClientModInitializer {
    public static final String MOD_ID = "henclient_reborn";
    public static final Logger LOGGER = LoggerFactory.getLogger("HenClient Reborn");

    private static ModuleManager modules;
    private static ConfigManager config;
    private static KeybindManager keybinds;
    private static HudRenderer hud;

    public static ModuleManager modules() {
        return modules;
    }

    public static ConfigManager config() {
        return config;
    }

    public static KeybindManager keybinds() {
        return keybinds;
    }

    public static HudRenderer hud() {
        return hud;
    }

    @Override
    public void onInitializeClient() {
        modules = new ModuleManager();
        modules.registerDefaults();
        config = new ConfigManager(modules);
        config.load();
        keybinds = new KeybindManager();
        hud = new HudRenderer();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            keybinds.tick(client);
            modules.tick(client);
        });

        // Always restore camera/input state when leaving a server or world.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> modules.onWorldUnload());

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> config.save());

        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(MOD_ID, "hud"),
                hud::render);

        LOGGER.info("HenClient Reborn initialised with {} modules", modules.all().size());
    }
}
