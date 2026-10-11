package com.henclient.reborn.modules.exploration;

import com.henclient.reborn.module.Category;
import com.henclient.reborn.module.Module;
import com.henclient.reborn.module.setting.BooleanSetting;
import com.henclient.reborn.module.setting.NumberSetting;
import com.henclient.reborn.render.Compass;
import com.henclient.reborn.util.BlockEntityScanner;
import com.henclient.reborn.util.Notifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Lists mob spawners found in already-loaded chunks. */
public final class SpawnerFinderModule extends Module {
    private final NumberSetting range = addSetting(new NumberSetting("range", "Range (chunks)", 8, 1, 16, 1));
    private final NumberSetting interval = addSetting(new NumberSetting("interval", "Scan interval (ticks)", 40, 10, 200, 10));
    private final NumberSetting maxResults = addSetting(new NumberSetting("max", "Max results", 20, 5, 50, 5));

    private final BooleanSetting notify = addSetting(new BooleanSetting("notify", "Chat notifications", true));
    private final BooleanSetting markers = addSetting(new BooleanSetting("markers", "Direction markers", true));

    private List<BlockEntityScanner.Hit> hits = new ArrayList<>();
    private final Set<Long> announced = new HashSet<>();
    private int ticks;

    public SpawnerFinderModule() {
        super("Spawner Finder", "Lists mob spawners in loaded chunks.", Category.EXPLORATION);
    }

    @Override
    public String note() {
        return "Only searches chunks your client has already loaded; it never loads new chunks.";
    }

    @Override
    protected void onEnable() {
        ticks = 0;
    }

    @Override
    protected void onDisable() {
        hits = new ArrayList<>();
        announced.clear();
    }

    @Override
    public void onWorldUnload() {
        hits = new ArrayList<>();
        announced.clear();
    }

    @Override
    public void onTick(Minecraft mc) {
        if (ticks-- > 0) {
            return;
        }
        ticks = interval.getInt();
        hits = BlockEntityScanner.scan(mc, range.getInt(), maxResults.getInt(), BlockEntityScanner::spawnerKind);
        for (BlockEntityScanner.Hit h : hits) {
            if (announced.add(h.pos().asLong()) && notify.isOn()) {
                Notifier.chat(mc, "Spawner at " + h.pos().getX() + " " + h.pos().getY() + " " + h.pos().getZ()
                        + " (" + Math.round(Math.sqrt(h.distSq())) + "m)");
            }
        }
    }

    @Override
    public void renderHud(GuiGraphics g, Minecraft mc, float partialTick) {
        if (!markers.isOn()) {
            return;
        }
        int shown = Math.min(3, hits.size());
        for (int i = 0; i < shown; i++) {
            BlockPos p = hits.get(i).pos();
            Compass.marker(g, mc, partialTick, p.getX() + 0.5, p.getZ() + 0.5,
                    "Spawner y" + p.getY(), BlockEntityScanner.colorOf(BlockEntityScanner.SPAWNER));
        }
    }

    @Override
    public List<String> overlayLines() {
        List<String> lines = new ArrayList<>();
        lines.add("Spawners: " + hits.size() + " found");
        int shown = Math.min(5, hits.size());
        for (int i = 0; i < shown; i++) {
            BlockEntityScanner.Hit h = hits.get(i);
            lines.add(" " + h.pos().getX() + " " + h.pos().getY() + " " + h.pos().getZ()
                    + "  (" + Math.round(Math.sqrt(h.distSq())) + "m)");
        }
        return lines;
    }
}
