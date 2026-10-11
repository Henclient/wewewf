package com.henclient.reborn.modules.exploration;

import com.henclient.reborn.module.Category;
import com.henclient.reborn.module.Module;
import com.henclient.reborn.module.setting.BooleanSetting;
import com.henclient.reborn.module.setting.NumberSetting;
import com.henclient.reborn.render.Projector;
import com.henclient.reborn.util.BlockEntityScanner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws colored outlines around nearby storage blocks. The outlines are projected onto the screen
 * (no custom world render pipeline), so they are visible through walls and follow Freecam.
 */
public final class StorageEspModule extends Module {
    private final NumberSetting range = addSetting(new NumberSetting("range", "Range (blocks)", 48, 8, 128, 8));
    private final NumberSetting maxBoxes = addSetting(new NumberSetting("max", "Max highlights", 48, 8, 128, 8));
    private final BooleanSetting chests = addSetting(new BooleanSetting("chests", "Chests", true));
    private final BooleanSetting barrels = addSetting(new BooleanSetting("barrels", "Barrels", true));
    private final BooleanSetting shulkers = addSetting(new BooleanSetting("shulkers", "Shulker boxes", true));
    private final BooleanSetting distance = addSetting(new BooleanSetting("distance", "Show distance", true));

    private final Projector projector = new Projector();
    private List<BlockEntityScanner.Hit> hits = new ArrayList<>();
    private int ticks;

    public StorageEspModule() {
        super("Storage ESP", "Highlights nearby chests, barrels and shulker boxes.", Category.EXPLORATION);
    }

    @Override
    public String note() {
        return "Outlines are drawn as screen-space boxes using the camera and FOV setting, "
                + "so they stay visible through walls.";
    }

    @Override
    protected void onEnable() {
        ticks = 0;
    }

    @Override
    protected void onDisable() {
        hits = new ArrayList<>();
    }

    @Override
    public void onWorldUnload() {
        hits = new ArrayList<>();
    }

    @Override
    public void onTick(Minecraft mc) {
        if (ticks-- > 0) {
            return;
        }
        ticks = 10;
        boolean c = chests.isOn(), b = barrels.isOn(), s = shulkers.isOn();
        int chunkRadius = (int) Math.ceil(range.getDouble() / 16.0) + 1;
        hits = BlockEntityScanner.scan(mc, chunkRadius, maxBoxes.getInt(),
                be -> BlockEntityScanner.storageKind(be, c, b, s));
    }

    @Override
    public void renderHud(GuiGraphics g, Minecraft mc, float partialTick) {
        if (hits.isEmpty() || !projector.setup(mc, partialTick, g.guiWidth(), g.guiHeight())) {
            return;
        }
        double maxDistSq = range.getDouble() * range.getDouble();
        double[] box = new double[4];
        for (BlockEntityScanner.Hit hit : hits) {
            if (hit.distSq() > maxDistSq) {
                continue;
            }
            BlockPos p = hit.pos();
            if (!projector.projectBox(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1, box)) {
                continue;
            }
            int x1 = (int) Math.round(box[0]);
            int y1 = (int) Math.round(box[1]);
            int x2 = Math.max(x1 + 2, (int) Math.round(box[2]));
            int y2 = Math.max(y1 + 2, (int) Math.round(box[3]));
            int color = BlockEntityScanner.colorOf(hit.kind());
            g.fill(x1, y1, x2, y1 + 1, color);
            g.fill(x1, y2 - 1, x2, y2, color);
            g.fill(x1, y1, x1 + 1, y2, color);
            g.fill(x2 - 1, y1, x2, y2, color);
            if (distance.isOn()) {
                String label = hit.kind() + " " + Math.round(Math.sqrt(hit.distSq())) + "m";
                g.drawString(mc.font, label, x1, y1 - 10, color);
            }
        }
    }
}
