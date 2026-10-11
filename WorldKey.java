package com.henclient.reborn.util;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;

/** Builds a file-system-safe key for the current server and dimension, and per-feature data paths. */
public final class WorldKey {
    private WorldKey() {
    }

    public static String of(Minecraft mc) {
        String server = mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "singleplayer";
        String dimension = mc.level != null ? mc.level.dimension().toString() : "unknown";
        return sanitize(server) + "__" + sanitize(dimension);
    }

    public static Path file(String folder, String key, String extension) {
        return FabricLoader.getInstance().getConfigDir()
                .resolve("henclient-reborn").resolve(folder).resolve(key + "." + extension);
    }

    private static String sanitize(String s) {
        return s.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
