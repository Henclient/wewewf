package com.henclient.reborn.hud;

import com.henclient.reborn.HenClientReborn;
import com.henclient.reborn.gui.Theme;
import com.henclient.reborn.module.HudTextModule;
import com.henclient.reborn.module.Module;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the in-game overlay: the info panel (top left), finder results (below it),
 * the list of active modules (top right) and per-module visuals such as Armor Status and ESP.
 */
public final class HudRenderer {
    private long windowStart = System.nanoTime();
    private int frames;
    private int fps;

    public int fps() {
        return fps;
    }

    public void render(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        updateFps();
        if (mc.level == null || mc.player == null || mc.screen != null && isOurGui(mc)) {
            return;
        }
        float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
        Font font = mc.font;

        List<Line> info = new ArrayList<>();
        List<Line> finders = new ArrayList<>();
        List<String> active = new ArrayList<>();

        for (Module m : HenClientReborn.modules().all()) {
            if (!m.isEnabled()) {
                continue;
            }
            try {
                if (m instanceof HudTextModule t) {
                    String text = t.hudText(mc);
                    if (text != null) {
                        info.add(new Line(text, t.hudColor(mc)));
                    }
                    continue;
                }
                List<String> lines = m.overlayLines();
                for (int i = 0; i < lines.size(); i++) {
                    finders.add(new Line(lines.get(i), i == 0 ? Theme.RED : Theme.TEXT));
                }
                if (m.category() != com.henclient.reborn.module.Category.HUD) {
                    active.add(m.name());
                }
                m.renderHud(g, mc, partial);
            } catch (Throwable t) {
                HenClientReborn.LOGGER.error("HUD error in module {}; disabling it", m.name(), t);
                m.setEnabled(false);
            }
        }

        int y = 4;
        y = drawPanel(g, font, 4, y, info, false);
        if (!finders.isEmpty()) {
            drawPanel(g, font, 4, y + 4, finders, false);
        }
        if (!active.isEmpty()) {
            List<Line> right = new ArrayList<>();
            for (String name : active) {
                right.add(new Line(name, Theme.TEXT));
            }
            drawPanel(g, font, g.guiWidth() - 4, 4, right, true);
        }
    }

    private boolean isOurGui(Minecraft mc) {
        return mc.screen instanceof com.henclient.reborn.gui.ClickGuiScreen;
    }

    private void updateFps() {
        frames++;
        long now = System.nanoTime();
        long elapsed = now - windowStart;
        if (elapsed >= 500_000_000L) {
            fps = (int) Math.round(frames * 1_000_000_000.0 / elapsed);
            frames = 0;
            windowStart = now;
        }
    }

    private record Line(String text, int color) {
    }

    /** Draws a panel and returns the y coordinate just below it. */
    private int drawPanel(GuiGraphics g, Font font, int anchorX, int y, List<Line> lines, boolean rightAligned) {
        if (lines.isEmpty()) {
            return y;
        }
        int width = 0;
        for (Line l : lines) {
            width = Math.max(width, font.width(l.text));
        }
        int pad = 4;
        int lineH = font.lineHeight + 2;
        int boxW = width + pad * 2 + 2;
        int boxH = lines.size() * lineH + pad * 2 - 2;
        int x = rightAligned ? anchorX - boxW : anchorX;

        g.fill(x, y, x + boxW, y + boxH, Theme.HUD_BG);
        if (rightAligned) {
            g.fill(x + boxW - 2, y, x + boxW, y + boxH, Theme.RED);
        } else {
            g.fill(x, y, x + 2, y + boxH, Theme.RED);
        }
        int ty = y + pad;
        for (Line l : lines) {
            int tx = rightAligned ? x + boxW - 2 - pad - font.width(l.text) : x + 2 + pad;
            g.drawString(font, l.text, tx, ty, l.color);
            ty += lineH;
        }
        return y + boxH;
    }
}
