package com.henclient.reborn.modules.exploration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.henclient.reborn.HenClientReborn;
import com.henclient.reborn.module.Category;
import com.henclient.reborn.module.Module;
import com.henclient.reborn.module.setting.BooleanSetting;
import com.henclient.reborn.module.setting.NumberSetting;
import com.henclient.reborn.render.Compass;
import com.henclient.reborn.util.Notifier;
import com.henclient.reborn.util.StashCategory;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stash Finder: counts storage block entities per chunk as chunks become available and records chunks
 * whose count reaches a threshold. Records are kept per server/dimension, saved to disk, announced in
 * chat, and shown as direction markers. It only reads chunks the client already has loaded.
 */
public final class StashFinderModule extends Module {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final NumberSetting minCount = addSetting(new NumberSetting("minCount", "Min storage per chunk", 4, 1, 50, 1));
    private final NumberSetting minDistance = addSetting(new NumberSetting("minDist", "Min distance from 0,0", 0, 0, 50000, 500));
    private final NumberSetting range = addSetting(new NumberSetting("range", "Scan range (chunks)", 10, 2, 16, 1));
    private final NumberSetting perTick = addSetting(new NumberSetting("perTick", "Chunks per tick", 2, 1, 8, 1));
    private final NumberSetting rescan = addSetting(new NumberSetting("rescan", "Re-scan after (sec)", 20, 5, 300, 5));
    private final NumberSetting markerRange = addSetting(new NumberSetting("markerRange", "Marker range (blocks)", 1000, 100, 10000, 100));

    private final BooleanSetting chests = addSetting(new BooleanSetting("chests", "Chests", true));
    private final BooleanSetting barrels = addSetting(new BooleanSetting("barrels", "Barrels", true));
    private final BooleanSetting shulkers = addSetting(new BooleanSetting("shulkers", "Shulker boxes", true));
    private final BooleanSetting enderChests = addSetting(new BooleanSetting("enderChests", "Ender chests", false));
    private final BooleanSetting furnaces = addSetting(new BooleanSetting("furnaces", "Furnaces", false));
    private final BooleanSetting hoppers = addSetting(new BooleanSetting("hoppers", "Hoppers", false));
    private final BooleanSetting dispensers = addSetting(new BooleanSetting("dispensers", "Dispensers/Droppers", false));

    private final BooleanSetting notify = addSetting(new BooleanSetting("notify", "Chat notifications", true));
    private final BooleanSetting markers = addSetting(new BooleanSetting("markers", "Direction markers", true));

    private record Stash(int cx, int cz, int total, Map<StashCategory, Integer> counts) {
    }

    private final Map<Long, Stash> stashes = new LinkedHashMap<>();
    private final Map<Long, Long> scannedAt = new HashMap<>();
    private long tickCounter;
    private String loadedKey;
    private boolean dirty;

    public StashFinderModule() {
        super("Stash Finder", "Records chunks with many storage blocks, like a stash hunter.", Category.EXPLORATION);
    }

    @Override
    public String note() {
        return "Counts storage blocks per chunk as chunks load and records chunks at or above the threshold. "
                + "Records are saved per server and dimension, and an arrow ring around the crosshair points to the "
                + "nearest ones. Only chunks your client already has are scanned.";
    }

    @Override
    protected void onDisable() {
        saveIfDirty(true);
        stashes.clear();
        scannedAt.clear();
        loadedKey = null;
    }

    @Override
    public void onWorldUnload() {
        saveIfDirty(true);
        stashes.clear();
        scannedAt.clear();
        loadedKey = null;
    }

    private boolean isEnabledCategory(StashCategory c) {
        switch (c) {
            case CHEST: return chests.isOn();
            case BARREL: return barrels.isOn();
            case SHULKER: return shulkers.isOn();
            case ENDER_CHEST: return enderChests.isOn();
            case FURNACE: return furnaces.isOn();
            case HOPPER: return hoppers.isOn();
            case DISPENSER: return dispensers.isOn();
            default: return false;
        }
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        tickCounter++;

        String key = worldKey(mc);
        if (!key.equals(loadedKey)) {
            saveIfDirty(true);
            stashes.clear();
            scannedAt.clear();
            load(key);
            loadedKey = key;
        }

        int pcx = player.getBlockX() >> 4;
        int pcz = player.getBlockZ() >> 4;
        int r = range.getInt();

        // Forget scan timestamps for chunks that are no longer near the player (records are kept).
        if (tickCounter % 100 == 0) {
            Iterator<Long> it = scannedAt.keySet().iterator();
            while (it.hasNext()) {
                long k = it.next();
                int cx = (int) k;
                int cz = (int) (k >> 32);
                if (Math.abs(cx - pcx) > r + 2 || Math.abs(cz - pcz) > r + 2) {
                    it.remove();
                }
            }
        }

        long maxAge = rescan.getInt() * 20L;
        int budget = perTick.getInt();
        outer:
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int cx = pcx + dx;
                int cz = pcz + dz;
                long k = key(cx, cz);
                Long last = scannedAt.get(k);
                if (last != null && tickCounter - last < maxAge) {
                    continue;
                }
                ChunkAccess access = mc.level.getChunk(cx, cz, ChunkStatus.FULL, false);
                if (!(access instanceof LevelChunk chunk)) {
                    continue;
                }
                scannedAt.put(k, tickCounter);
                scanChunk(mc, chunk, cx, cz, k);
                if (--budget <= 0) {
                    break outer;
                }
            }
        }

        if (tickCounter % 100 == 0) {
            saveIfDirty(false);
        }
    }

    private void scanChunk(Minecraft mc, LevelChunk chunk, int cx, int cz, long k) {
        Map<StashCategory, Integer> counts = new EnumMap<>(StashCategory.class);
        int total = 0;
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            StashCategory c = StashCategory.of(be);
            if (c == null || !isEnabledCategory(c)) {
                continue;
            }
            counts.merge(c, 1, Integer::sum);
            total++;
        }

        double centerX = cx * 16 + 8;
        double centerZ = cz * 16 + 8;
        boolean farEnough = Math.hypot(centerX, centerZ) >= minDistance.getDouble();
        Stash old = stashes.get(k);

        if (total >= minCount.getInt() && farEnough) {
            Stash now = new Stash(cx, cz, total, counts);
            stashes.put(k, now);
            if (old == null || old.total != total) {
                dirty = true;
                if (notify.isOn()) {
                    Notifier.chat(mc, "Stash at " + (cx * 16) + ", " + (cz * 16) + ": " + describe(counts));
                }
            }
        } else if (old != null) {
            // The storage there is gone or fell below the threshold.
            stashes.remove(k);
            dirty = true;
        }
    }

    private static String describe(Map<StashCategory, Integer> counts) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<StashCategory, Integer> e : counts.entrySet()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(e.getValue()).append(' ').append(e.getKey().label().toLowerCase(java.util.Locale.ROOT));
        }
        return sb.toString();
    }

    private static long key(int cx, int cz) {
        return ((long) cx & 0xFFFFFFFFL) | (((long) cz & 0xFFFFFFFFL) << 32);
    }

    // ------------------------------------------------------------------ persistence

    private static String worldKey(Minecraft mc) {
        String server = mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "singleplayer";
        String dimension = mc.level != null ? mc.level.dimension().toString() : "unknown";
        return sanitize(server) + "__" + sanitize(dimension);
    }

    private static String sanitize(String s) {
        return s.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static Path fileFor(String key) {
        return FabricLoader.getInstance().getConfigDir()
                .resolve("henclient-reborn").resolve("stashes").resolve(key + ".json");
    }

    private void load(String key) {
        Path file = fileFor(key);
        if (!Files.exists(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonArray array = GSON.fromJson(reader, JsonArray.class);
            if (array == null) {
                return;
            }
            array.forEach(element -> {
                JsonObject o = element.getAsJsonObject();
                int cx = o.get("cx").getAsInt();
                int cz = o.get("cz").getAsInt();
                Map<StashCategory, Integer> counts = new EnumMap<>(StashCategory.class);
                int total = 0;
                JsonObject c = o.getAsJsonObject("counts");
                for (StashCategory cat : StashCategory.values()) {
                    if (c.has(cat.name())) {
                        int n = c.get(cat.name()).getAsInt();
                        counts.put(cat, n);
                        total += n;
                    }
                }
                stashes.put(key(cx, cz), new Stash(cx, cz, total, counts));
            });
        } catch (Exception e) {
            HenClientReborn.LOGGER.error("Could not read stash records {}", file, e);
        }
    }

    private void saveIfDirty(boolean force) {
        if (loadedKey == null || !dirty) {
            return;
        }
        dirty = false;
        JsonArray array = new JsonArray();
        for (Stash s : stashes.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("cx", s.cx);
            o.addProperty("cz", s.cz);
            JsonObject counts = new JsonObject();
            for (Map.Entry<StashCategory, Integer> e : s.counts.entrySet()) {
                counts.addProperty(e.getKey().name(), e.getValue());
            }
            o.add("counts", counts);
            array.add(o);
        }
        Path file = fileFor(loadedKey);
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(array, writer);
            }
        } catch (IOException e) {
            HenClientReborn.LOGGER.error("Could not save stash records {}", file, e);
        }
    }

    // ------------------------------------------------------------------ output

    private List<Stash> sortedByCount() {
        List<Stash> list = new ArrayList<>(stashes.values());
        list.sort(Comparator.comparingInt((Stash s) -> s.total).reversed());
        return list;
    }

    @Override
    public List<String> overlayLines() {
        List<String> lines = new ArrayList<>();
        lines.add("Stashes: " + stashes.size() + " recorded");
        List<Stash> sorted = sortedByCount();
        int shown = Math.min(5, sorted.size());
        for (int i = 0; i < shown; i++) {
            Stash s = sorted.get(i);
            lines.add(" " + (s.cx * 16) + ", " + (s.cz * 16) + "  x" + s.total);
        }
        return lines;
    }

    @Override
    public void renderHud(GuiGraphics g, Minecraft mc, float partialTick) {
        if (!markers.isOn() || mc.player == null || stashes.isEmpty()) {
            return;
        }
        double maxDist = markerRange.getDouble();
        List<Stash> near = new ArrayList<>();
        for (Stash s : stashes.values()) {
            double d = Math.hypot(s.cx * 16 + 8 - mc.player.getX(), s.cz * 16 + 8 - mc.player.getZ());
            if (d <= maxDist) {
                near.add(s);
            }
        }
        near.sort(Comparator.comparingDouble(
                s -> Math.hypot(s.cx * 16 + 8 - mc.player.getX(), s.cz * 16 + 8 - mc.player.getZ())));
        int shown = Math.min(4, near.size());
        for (int i = 0; i < shown; i++) {
            Stash s = near.get(i);
            Compass.marker(g, mc, partialTick, s.cx * 16 + 8, s.cz * 16 + 8, "Stash x" + s.total, 0xFFFFA726);
        }
    }
}
