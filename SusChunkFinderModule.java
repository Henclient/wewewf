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
import com.henclient.reborn.util.WorldKey;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sus Chunk Finder: a heuristic that combines two kinds of evidence. It proves nothing on its own.
 *
 * 1. Block evidence: counts blocks that rarely occur naturally (chests, spawners, torches, rails,
 *    obsidian, redstone parts, crafting stations, ...) in each loaded chunk below a chosen height.
 * 2. Chunk age evidence: a chunk that was just generated still has "unsettled" liquids (water or lava
 *    with air next to or under it that has not flowed yet). Older chunks have settled liquids. Chunks
 *    classified as old while most neighbours are new were probably visited by someone before, which is
 *    what players call a trail. Classification must happen right after a chunk first appears, so new
 *    chunks are queued and checked immediately.
 *
 * Everything is incremental, bounded and based only on chunks the client already has.
 */
public final class SusChunkFinderModule extends Module {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final byte NEW = 1;
    private static final byte OLD = 2;
    private static final int MAX_AGE_ENTRIES = 400_000;
    private static final int MAX_RECORDS = 500;
    private static final int MAX_CLASSIFY_LATENCY_TICKS = 10;
    private static final int MIN_FLUIDS_FOR_OLD = 8;

    // category indexes for block counting
    private static final int CHEST = 0, SPAWNER = 1, OBSIDIAN = 2, COBBLE = 3, MOSSY = 4, TORCH = 5, RAIL = 6, ARTIFICIAL = 7;
    private static final double[] WEIGHTS = {3.0, 25.0, 0.3, 0.08, 1.5, 2.0, 1.2, 1.5};

    private final NumberSetting range = addSetting(new NumberSetting("range", "Range (chunks)", 10, 2, 16, 1));
    private final NumberSetting perTick = addSetting(new NumberSetting("perTick", "Block-scan chunks per tick", 1, 1, 6, 1));
    private final NumberSetting rescan = addSetting(new NumberSetting("rescan", "Re-scan after (sec)", 60, 5, 300, 5));
    private final NumberSetting maxY = addSetting(new NumberSetting("maxY", "Count blocks below Y", 64, -60, 320, 4));
    private final NumberSetting threshold = addSetting(new NumberSetting("threshold", "Score threshold", 30, 5, 200, 5));
    private final BooleanSetting useAge = addSetting(new BooleanSetting("useAge", "Use chunk age (trails)", true));
    private final NumberSetting classifyPerTick = addSetting(new NumberSetting("classify", "Age checks per tick", 12, 4, 32, 2));
    private final NumberSetting minUnsettled = addSetting(new NumberSetting("minUnsettled", "Unsettled liquids = new", 2, 1, 10, 1));
    private final NumberSetting minNewNeighbours = addSetting(new NumberSetting("minNew", "Old chunk needs new neighbours", 5, 3, 8, 1));
    private final NumberSetting showCount = addSetting(new NumberSetting("show", "Rows shown", 5, 1, 10, 1));
    private final BooleanSetting notify = addSetting(new BooleanSetting("notify", "Chat notifications", true));
    private final BooleanSetting markers = addSetting(new BooleanSetting("markers", "Direction markers", true));

    private static Map<Block, Integer> categories;

    private record Entry(int cx, int cz, double blockScore, int[] counts, long tick) {
    }

    private record Flag(int cx, int cz, double score, String reason) {
    }

    private record Pending(int cx, int cz, long firstSeen) {
    }

    private final Map<Long, Entry> cache = new HashMap<>();
    private final Map<Long, Flag> records = new LinkedHashMap<>();
    private final Long2ByteOpenHashMap age = new Long2ByteOpenHashMap();
    private final LongOpenHashSet seen = new LongOpenHashSet();
    private final ArrayDeque<Pending> pending = new ArrayDeque<>();

    private long tickCounter;
    private String loadedKey;
    private boolean ageDirty;
    private boolean recordsDirty;
    private boolean ageBaselineDone;
    private int newCount;
    private int oldCount;

    public SusChunkFinderModule() {
        super("Sus Chunk Finder", "Flags chunks that look visited or built in (heuristic).", Category.EXPLORATION);
    }

    @Override
    public String note() {
        return "HEURISTIC ONLY, NOT PROOF. Scores chunks from blocks that rarely generate naturally and, "
                + "optionally, from chunk age: freshly generated chunks still have liquids that have not "
                + "flowed yet, so an old chunk surrounded by new ones suggests someone was there. Chunk age is "
                + "only guessed for chunks where fluids exist, and it can be wrong, so expect false positives.";
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onDisable() {
        flushAndClear();
    }

    @Override
    public void onWorldUnload() {
        flushAndClear();
    }

    private void flushAndClear() {
        saveAge();
        saveRecords();
        cache.clear();
        records.clear();
        age.clear();
        seen.clear();
        pending.clear();
        newCount = 0;
        oldCount = 0;
        ageBaselineDone = false;
        loadedKey = null;
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        tickCounter++;

        String key = WorldKey.of(mc);
        if (!key.equals(loadedKey)) {
            flushAndClear();
            loadedKey = key;
            loadAge();
            loadRecords();
        }

        int pcx = player.getBlockX() >> 4;
        int pcz = player.getBlockZ() >> 4;
        int r = range.getInt();

        if (useAge.isOn()) {
            discoverNewChunks(mc, pcx, pcz, r);
            classifyPending(mc);
        } else {
            ageBaselineDone = false;
            seen.clear();
            pending.clear();
        }
        scanBlocks(mc, pcx, pcz, r);

        if (tickCounter % 20 == 0) {
            refreshRecords(mc);
        }
        if (tickCounter % 100 == 0) {
            prune(mc, pcx, pcz, r);
        }
        if (tickCounter % 600 == 0) {
            saveAge();
            saveRecords();
        }
    }

    /** Queue chunks the first time they show up so liquids can be inspected before they flow. */
    private void discoverNewChunks(Minecraft mc, int pcx, int pcz, int r) {
        // Chunks that were already loaded before we started watching have long since settled, so they
        // would all look "old". The first pass only records them; later arrivals are classified.
        boolean baseline = !ageBaselineDone;
        ageBaselineDone = true;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int cx = pcx + dx;
                int cz = pcz + dz;
                long k = key(cx, cz);
                if (seen.contains(k)) {
                    continue;
                }
                if (!(mc.level.getChunk(cx, cz, ChunkStatus.FULL, false) instanceof LevelChunk)) {
                    continue;
                }
                seen.add(k);
                if (!baseline && age.get(k) == 0) {
                    pending.add(new Pending(cx, cz, tickCounter));
                }
            }
        }
    }

    private void classifyPending(Minecraft mc) {
        int budget = classifyPerTick.getInt();
        while (budget > 0 && !pending.isEmpty()) {
            Pending p = pending.poll();
            if (tickCounter - p.firstSeen > MAX_CLASSIFY_LATENCY_TICKS) {
                // Too late: liquids may already have flowed, so the result would be unreliable.
                continue;
            }
            ChunkAccess access = mc.level.getChunk(p.cx, p.cz, ChunkStatus.FULL, false);
            if (!(access instanceof LevelChunk chunk)) {
                continue;
            }
            budget--;
            int[] stats = fluidStats(chunk);
            byte result = 0;
            if (stats[1] >= minUnsettled.getInt()) {
                result = NEW;
            } else if (stats[0] >= MIN_FLUIDS_FOR_OLD && stats[1] == 0) {
                result = OLD;
            }
            if (result != 0 && age.size() < MAX_AGE_ENTRIES) {
                age.put(key(p.cx, p.cz), result);
                ageDirty = true;
                if (result == NEW) {
                    newCount++;
                } else {
                    oldCount++;
                }
            }
        }
    }

    /** Returns {liquid blocks, unsettled liquid blocks}. Only real liquid blocks count (not waterlogging). */
    private int[] fluidStats(LevelChunk chunk) {
        int fluid = 0;
        int unsettled = 0;
        LevelChunkSection[] sections = chunk.getSections();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection s = sections[i];
            if (s == null || s.hasOnlyAir() || !s.maybeHas(st -> st.getBlock() instanceof LiquidBlock)) {
                continue;
            }
            LevelChunkSection below = i > 0 ? sections[i - 1] : null;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState st = s.getBlockState(x, y, z);
                        if (!(st.getBlock() instanceof LiquidBlock)) {
                            continue;
                        }
                        fluid++;
                        boolean airBelow;
                        if (y > 0) {
                            airBelow = s.getBlockState(x, y - 1, z).isAir();
                        } else {
                            airBelow = below != null && below.getBlockState(x, 15, z).isAir();
                        }
                        if (airBelow) {
                            unsettled++;
                            continue;
                        }
                        if (st.getFluidState().getAmount() >= 3
                                && ((x > 0 && s.getBlockState(x - 1, y, z).isAir())
                                || (x < 15 && s.getBlockState(x + 1, y, z).isAir())
                                || (z > 0 && s.getBlockState(x, y, z - 1).isAir())
                                || (z < 15 && s.getBlockState(x, y, z + 1).isAir()))) {
                            unsettled++;
                        }
                    }
                }
            }
        }
        return new int[]{fluid, unsettled};
    }

    private void scanBlocks(Minecraft mc, int pcx, int pcz, int r) {
        long maxAge = rescan.getInt() * 20L;
        int budget = perTick.getInt();
        outer:
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int cx = pcx + dx;
                int cz = pcz + dz;
                long k = key(cx, cz);
                Entry existing = cache.get(k);
                if (existing != null && tickCounter - existing.tick < maxAge) {
                    continue;
                }
                ChunkAccess access = mc.level.getChunk(cx, cz, ChunkStatus.FULL, false);
                if (!(access instanceof LevelChunk chunk)) {
                    continue;
                }
                cache.put(k, scanChunk(chunk, cx, cz));
                if (--budget <= 0) {
                    break outer;
                }
            }
        }
    }

    private static Map<Block, Integer> categories() {
        if (categories == null) {
            Map<Block, Integer> m = new HashMap<>();
            m.put(Blocks.CHEST, CHEST);
            m.put(Blocks.TRAPPED_CHEST, CHEST);
            m.put(Blocks.SPAWNER, SPAWNER);
            m.put(Blocks.OBSIDIAN, OBSIDIAN);
            m.put(Blocks.COBBLESTONE, COBBLE);
            m.put(Blocks.MOSSY_COBBLESTONE, MOSSY);
            m.put(Blocks.TORCH, TORCH);
            m.put(Blocks.WALL_TORCH, TORCH);
            m.put(Blocks.RAIL, RAIL);
            m.put(Blocks.POWERED_RAIL, RAIL);
            m.put(Blocks.DETECTOR_RAIL, RAIL);
            m.put(Blocks.ACTIVATOR_RAIL, RAIL);
            for (Block b : new Block[]{Blocks.HOPPER, Blocks.DISPENSER, Blocks.DROPPER, Blocks.PISTON,
                    Blocks.STICKY_PISTON, Blocks.OBSERVER, Blocks.REPEATER, Blocks.COMPARATOR,
                    Blocks.REDSTONE_WIRE, Blocks.CRAFTING_TABLE, Blocks.FURNACE, Blocks.BLAST_FURNACE,
                    Blocks.ANVIL, Blocks.ENCHANTING_TABLE, Blocks.GLASS}) {
                m.put(b, ARTIFICIAL);
            }
            categories = m;
        }
        return categories;
    }

    private Entry scanChunk(LevelChunk chunk, int cx, int cz) {
        Map<Block, Integer> cats = categories();
        int limitY = maxY.getInt();
        int[] counts = new int[WEIGHTS.length];
        LevelChunkSection[] sections = chunk.getSections();
        int minSection = chunk.getMinSectionY();
        for (int i = 0; i < sections.length; i++) {
            int baseY = (minSection + i) << 4;
            if (baseY > limitY) {
                continue;
            }
            LevelChunkSection section = sections[i];
            if (section == null || section.hasOnlyAir() || !section.maybeHas(st -> cats.containsKey(st.getBlock()))) {
                continue;
            }
            for (int y = 0; y < 16 && baseY + y <= limitY; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        Integer c = cats.get(section.getBlockState(x, y, z).getBlock());
                        if (c != null) {
                            counts[c]++;
                        }
                    }
                }
            }
        }
        double score = 0;
        for (int i = 0; i < counts.length; i++) {
            score += counts[i] * WEIGHTS[i];
        }
        return new Entry(cx, cz, score, counts, tickCounter);
    }

    // ------------------------------------------------------------------ evaluation

    private int newNeighbours(int cx, int cz) {
        int n = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if ((dx != 0 || dz != 0) && age.get(key(cx + dx, cz + dz)) == NEW) {
                    n++;
                }
            }
        }
        return n;
    }

    private void refreshRecords(Minecraft mc) {
        for (Entry e : cache.values()) {
            long k = key(e.cx, e.cz);
            double score = e.blockScore;
            List<String> reasons = new ArrayList<>();
            if (e.counts[SPAWNER] > 0) {
                reasons.add("spawner");
            }
            if (e.counts[CHEST] > 0) {
                reasons.add(e.counts[CHEST] + " chests");
            }
            int artificial = e.counts[ARTIFICIAL] + e.counts[TORCH] + e.counts[RAIL];
            if (artificial >= 3) {
                reasons.add(artificial + " built blocks");
            }
            if (useAge.isOn() && age.get(k) == OLD) {
                int n = newNeighbours(e.cx, e.cz);
                if (n >= minNewNeighbours.getInt()) {
                    score += Math.min(32.0, n * 4.0);
                    reasons.add("old chunk, " + n + " new neighbours");
                }
            }
            if (score >= threshold.getDouble()) {
                boolean isNew = !records.containsKey(k);
                records.put(k, new Flag(e.cx, e.cz, score, String.join(", ", reasons)));
                recordsDirty = true;
                if (isNew && notify.isOn()) {
                    Notifier.chat(mc, "Suspicious chunk (heuristic) at " + (e.cx << 4) + ", " + (e.cz << 4)
                            + " score " + Math.round(score) + (reasons.isEmpty() ? "" : " - " + String.join(", ", reasons)));
                }
            } else if (records.remove(k) != null) {
                recordsDirty = true;
            }
        }
        if (records.size() > MAX_RECORDS) {
            List<Flag> sorted = new ArrayList<>(records.values());
            sorted.sort(Comparator.comparingDouble(Flag::score).reversed());
            records.clear();
            for (int i = 0; i < MAX_RECORDS; i++) {
                Flag f = sorted.get(i);
                records.put(key(f.cx, f.cz), f);
            }
            recordsDirty = true;
        }
    }

    /** Drops scan data for chunks that are far away or unloaded. Chunk-age data and flagged records stay. */
    private void prune(Minecraft mc, int pcx, int pcz, int r) {
        Iterator<Entry> it = cache.values().iterator();
        while (it.hasNext()) {
            Entry e = it.next();
            boolean far = Math.abs(e.cx - pcx) > r + 1 || Math.abs(e.cz - pcz) > r + 1;
            if (far || !(mc.level.getChunk(e.cx, e.cz, ChunkStatus.FULL, false) instanceof LevelChunk)) {
                it.remove();
            }
        }
        var seenIt = seen.iterator();
        while (seenIt.hasNext()) {
            long k = seenIt.nextLong();
            int cx = (int) k;
            int cz = (int) (k >> 32);
            if (Math.abs(cx - pcx) > r + 3 || Math.abs(cz - pcz) > r + 3) {
                seenIt.remove();
            }
        }
    }

    private static long key(int cx, int cz) {
        return ((long) cx & 0xFFFFFFFFL) | (((long) cz & 0xFFFFFFFFL) << 32);
    }

    // ------------------------------------------------------------------ persistence

    private void loadAge() {
        Path file = WorldKey.file("chunkage", loadedKey, "bin");
        if (!Files.exists(file)) {
            return;
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            int count = Math.min(in.readInt(), MAX_AGE_ENTRIES);
            for (int i = 0; i < count; i++) {
                int cx = in.readInt();
                int cz = in.readInt();
                byte type = in.readByte();
                if (type == NEW || type == OLD) {
                    age.put(key(cx, cz), type);
                    if (type == NEW) {
                        newCount++;
                    } else {
                        oldCount++;
                    }
                }
            }
        } catch (IOException e) {
            HenClientReborn.LOGGER.error("Could not read chunk age data {}", file, e);
        }
    }

    private void saveAge() {
        if (loadedKey == null || !ageDirty) {
            return;
        }
        ageDirty = false;
        Path file = WorldKey.file("chunkage", loadedKey, "bin");
        try {
            Files.createDirectories(file.getParent());
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
                out.writeInt(age.size());
                for (long k : age.keySet()) {
                    out.writeInt((int) k);
                    out.writeInt((int) (k >> 32));
                    out.writeByte(age.get(k));
                }
            }
        } catch (IOException e) {
            HenClientReborn.LOGGER.error("Could not save chunk age data {}", file, e);
        }
    }

    private void loadRecords() {
        Path file = WorldKey.file("suschunks", loadedKey, "json");
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
                records.put(key(cx, cz), new Flag(cx, cz, o.get("score").getAsDouble(), o.get("reason").getAsString()));
            });
        } catch (Exception e) {
            HenClientReborn.LOGGER.error("Could not read sus chunk records {}", file, e);
        }
    }

    private void saveRecords() {
        if (loadedKey == null || !recordsDirty) {
            return;
        }
        recordsDirty = false;
        JsonArray array = new JsonArray();
        for (Flag f : records.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("cx", f.cx);
            o.addProperty("cz", f.cz);
            o.addProperty("score", f.score);
            o.addProperty("reason", f.reason);
            array.add(o);
        }
        Path file = WorldKey.file("suschunks", loadedKey, "json");
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(array, writer);
            }
        } catch (IOException e) {
            HenClientReborn.LOGGER.error("Could not save sus chunk records {}", file, e);
        }
    }

    // ------------------------------------------------------------------ output

    private List<Flag> sortedRecords() {
        List<Flag> list = new ArrayList<>(records.values());
        list.sort(Comparator.comparingDouble(Flag::score).reversed());
        return list;
    }

    @Override
    public List<String> overlayLines() {
        List<String> lines = new ArrayList<>();
        lines.add("Sus chunks: " + records.size() + " (heuristic)");
        List<Flag> sorted = sortedRecords();
        int shown = Math.min(showCount.getInt(), sorted.size());
        for (int i = 0; i < shown; i++) {
            Flag f = sorted.get(i);
            lines.add(" " + (f.cx << 4) + ", " + (f.cz << 4) + "  " + Math.round(f.score)
                    + (f.reason.isEmpty() ? "" : "  " + f.reason));
        }
        if (useAge.isOn()) {
            lines.add(" age map: " + newCount + " new / " + oldCount + " old");
        }
        return lines;
    }

    @Override
    public void renderHud(GuiGraphics g, Minecraft mc, float partialTick) {
        if (!markers.isOn() || mc.player == null || records.isEmpty()) {
            return;
        }
        List<Flag> sorted = sortedRecords();
        int shown = Math.min(3, sorted.size());
        for (int i = 0; i < shown; i++) {
            Flag f = sorted.get(i);
            Compass.marker(g, mc, partialTick, f.cx * 16 + 8, f.cz * 16 + 8, "Sus " + Math.round(f.score), 0xFFEF5350);
        }
    }
}
