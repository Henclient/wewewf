package com.henclient.reborn.gui;

import com.henclient.reborn.HenClientReborn;
import com.henclient.reborn.input.KeyNames;
import com.henclient.reborn.module.Category;
import com.henclient.reborn.module.Module;
import com.henclient.reborn.module.setting.BooleanSetting;
import com.henclient.reborn.module.setting.NumberSetting;
import com.henclient.reborn.module.setting.Setting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Red-and-black module GUI.
 * Left: module list (left click = toggle and select, right click = select only).
 * Right: settings of the selected module, keybind button and notes.
 * The layout is recomputed every frame, so resizing the window just works.
 */
public final class ClickGuiScreen extends Screen {
    private static final int LIST_W = 190;
    private static final int ROW_H = 18;
    private static final int HEADER_H = 16;
    private static final int TOP_BAR = 28;

    private int wx, wy, ww, wh;
    private int scroll;
    private Module selected;
    private Module rebinding;
    private boolean rebindingGui;
    private NumberSetting dragging;
    private int dragTrackX, dragTrackW;
    private boolean closeKeyArmed;
    private final long openedAt = System.currentTimeMillis();

    private record Row(Module module, Category header, int y) {
    }

    public ClickGuiScreen() {
        super(Component.literal("HenClient Reborn"));
        List<Module> all = HenClientReborn.modules().all();
        if (!all.isEmpty()) {
            selected = all.get(0);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void layout() {
        ww = Math.min(640, width - 20);
        wh = Math.min(380, height - 20);
        wx = (width - ww) / 2;
        wy = (height - wh) / 2;
    }

    private int listTop() {
        return wy + TOP_BAR + 6;
    }

    private int listBottom() {
        return wy + wh - 8;
    }

    private List<Row> buildRows() {
        List<Row> rows = new ArrayList<>();
        int y = listTop() - scroll;
        for (Category c : Category.values()) {
            List<Module> mods = HenClientReborn.modules().in(c);
            if (mods.isEmpty()) {
                continue;
            }
            rows.add(new Row(null, c, y));
            y += HEADER_H;
            for (Module m : mods) {
                rows.add(new Row(m, null, y));
                y += ROW_H;
            }
            y += 4;
        }
        return rows;
    }

    private int contentHeight() {
        int h = 0;
        for (Category c : Category.values()) {
            int n = HenClientReborn.modules().in(c).size();
            if (n > 0) {
                h += HEADER_H + n * ROW_H + 4;
            }
        }
        return h;
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        layout();
        int maxScroll = Math.max(0, contentHeight() - (listBottom() - listTop()));
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        if (!closeKeyArmed && System.currentTimeMillis() - openedAt > 1000) {
            closeKeyArmed = true;
        }

        g.fill(0, 0, width, height, Theme.BACKDROP);
        g.fill(wx - 1, wy - 1, wx + ww + 1, wy + wh + 1, Theme.RED_DARK);
        g.fill(wx, wy, wx + ww, wy + wh, Theme.WINDOW);

        // Title bar
        g.fill(wx, wy, wx + ww, wy + TOP_BAR, Theme.PANEL);
        g.fill(wx, wy + TOP_BAR - 1, wx + ww, wy + TOP_BAR, Theme.RED);
        g.drawString(font, "HEN", wx + 10, wy + 10, Theme.RED);
        g.drawString(font, "CLIENT REBORN", wx + 10 + font.width("HEN "), wy + 10, Theme.TEXT);
        String guiKeyText = rebindingGui ? "Press a key..." : "Menu key: [" + KeyNames.of(HenClientReborn.config().guiKey()) + "]";
        int gkW = font.width(guiKeyText) + 12;
        int gkX = wx + ww - gkW - 8;
        boolean gkHover = inside(mouseX, mouseY, gkX, wy + 6, gkW, 16);
        g.fill(gkX, wy + 6, gkX + gkW, wy + 22, rebindingGui ? Theme.RED_DARK : (gkHover ? Theme.ROW_HOVER : Theme.ROW));
        g.drawString(font, guiKeyText, gkX + 6, wy + 10, Theme.TEXT);

        renderList(g, mouseX, mouseY);
        renderDetails(g, mouseX, mouseY);
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY) {
        int x0 = wx + 8;
        int x1 = x0 + LIST_W;
        g.fill(x0, listTop(), x1, listBottom(), Theme.PANEL);
        g.enableScissor(x0, listTop(), x1, listBottom());
        for (Row row : buildRows()) {
            if (row.y + ROW_H < listTop() || row.y > listBottom()) {
                continue;
            }
            if (row.header != null) {
                g.drawString(font, row.header.label().toUpperCase(java.util.Locale.ROOT), x0 + 6, row.y + 4, Theme.RED);
                continue;
            }
            Module m = row.module;
            boolean hover = inside(mouseX, mouseY, x0, row.y, LIST_W, ROW_H)
                    && mouseY >= listTop() && mouseY < listBottom();
            boolean isSelected = m == selected;
            g.fill(x0 + 2, row.y, x1 - 2, row.y + ROW_H - 1, hover ? Theme.ROW_HOVER : Theme.ROW);
            if (isSelected) {
                g.fill(x0 + 2, row.y, x0 + 4, row.y + ROW_H - 1, Theme.RED);
            }
            g.drawString(font, m.name(), x0 + 10, row.y + 5, m.isEnabled() ? Theme.TEXT : Theme.TEXT_DIM);
            // Enabled / disabled indicator
            int dot = m.isEnabled() ? Theme.ON : Theme.OFF;
            g.fill(x1 - 18, row.y + 5, x1 - 8, row.y + 12, dot);
            if (m.keyCode() >= 0) {
                String k = KeyNames.of(m.keyCode());
                g.drawString(font, k, x1 - 24 - font.width(k), row.y + 5, Theme.TEXT_DIM);
            }
        }
        g.disableScissor();
    }

    private int detailX() {
        return wx + 8 + LIST_W + 8;
    }

    private int detailW() {
        return wx + ww - 8 - detailX();
    }

    private void renderDetails(GuiGraphics g, int mouseX, int mouseY) {
        int x = detailX();
        int w = detailW();
        g.fill(x, listTop(), x + w, listBottom(), Theme.PANEL);
        if (selected == null) {
            return;
        }
        int y = listTop() + 8;
        g.drawString(font, selected.name(), x + 8, y, Theme.TEXT);
        y += 12;
        for (String line : wrap(selected.description(), w - 16)) {
            g.drawString(font, line, x + 8, y, Theme.TEXT_DIM);
            y += 11;
        }
        y += 4;

        // Toggle and keybind buttons
        int bx = x + 8;
        boolean on = selected.isEnabled();
        boolean hoverToggle = inside(mouseX, mouseY, bx, y, 86, 16);
        g.fill(bx, y, bx + 86, y + 16, on ? Theme.RED : (hoverToggle ? Theme.ROW_HOVER : Theme.ROW));
        String toggleText = on ? "ENABLED" : "DISABLED";
        g.drawString(font, toggleText, bx + 43 - font.width(toggleText) / 2, y + 4, Theme.TEXT);

        int kx = bx + 92;
        String keyText = rebinding == selected ? "Press a key..." : "Key: [" + KeyNames.of(selected.keyCode()) + "]";
        int kw = Math.max(86, font.width(keyText) + 12);
        boolean hoverKey = inside(mouseX, mouseY, kx, y, kw, 16);
        g.fill(kx, y, kx + kw, y + 16, rebinding == selected ? Theme.RED_DARK : (hoverKey ? Theme.ROW_HOVER : Theme.ROW));
        g.drawString(font, keyText, kx + 6, y + 4, Theme.TEXT);
        y += 24;

        // Settings
        for (Setting<?> setting : selected.settings()) {
            if (y + 24 > listBottom()) {
                break;
            }
            if (setting instanceof BooleanSetting b) {
                g.drawString(font, b.label(), x + 8, y + 3, Theme.TEXT);
                int pillX = x + w - 8 - 30;
                g.fill(pillX, y, pillX + 30, y + 12, b.isOn() ? Theme.RED : Theme.OFF);
                int knob = b.isOn() ? pillX + 18 : pillX + 2;
                g.fill(knob, y + 2, knob + 10, y + 10, Theme.TEXT);
                y += 18;
            } else if (setting instanceof NumberSetting n) {
                g.drawString(font, n.label(), x + 8, y, Theme.TEXT);
                String value = n.display();
                g.drawString(font, value, x + w - 8 - font.width(value), y, Theme.TEXT_DIM);
                int trackX = x + 8;
                int trackW = w - 16;
                int ty = y + 12;
                g.fill(trackX, ty, trackX + trackW, ty + 5, Theme.ROW);
                int fill = (int) Math.round(trackW * n.ratio());
                g.fill(trackX, ty, trackX + fill, ty + 5, Theme.RED);
                g.fill(trackX + fill - 1, ty - 2, trackX + fill + 2, ty + 7, Theme.TEXT);
                y += 26;
            }
        }

        String note = selected.note();
        if (note != null) {
            List<String> lines = wrap(note, w - 16);
            int noteY = Math.max(y + 4, listBottom() - 8 - lines.size() * 11);
            g.fill(x + 8, noteY - 4, x + w - 8, noteY - 3, Theme.BORDER);
            for (String line : lines) {
                g.drawString(font, line, x + 8, noteY, Theme.TEXT_DIM);
                noteY += 11;
            }
        }
    }

    private List<String> wrap(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            String test = current.length() == 0 ? word : current + " " + word;
            if (font.width(test) > maxWidth && current.length() > 0) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(test);
            }
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        int button = event.button();

        // A click always cancels a pending rebind.
        if (rebinding != null || rebindingGui) {
            rebinding = null;
            rebindingGui = false;
        }

        // Menu key button
        String guiKeyText = "Menu key: [" + KeyNames.of(HenClientReborn.config().guiKey()) + "]";
        int gkW = font.width(guiKeyText) + 12;
        if (inside(mx, my, wx + ww - gkW - 8, wy + 6, gkW, 16) && button == 0) {
            rebindingGui = true;
            return true;
        }

        // Module list
        if (inside(mx, my, wx + 8, listTop(), LIST_W, listBottom() - listTop())) {
            for (Row row : buildRows()) {
                if (row.module != null && my >= row.y && my < row.y + ROW_H) {
                    selected = row.module;
                    if (button == 0) {
                        row.module.toggle();
                        HenClientReborn.config().save();
                    }
                    return true;
                }
            }
            return true;
        }

        // Details panel
        if (selected != null && inside(mx, my, detailX(), listTop(), detailW(), listBottom() - listTop())) {
            return clickDetails(mx, my, button);
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean clickDetails(double mx, double my, int button) {
        int x = detailX();
        int w = detailW();
        int y = listTop() + 8 + 12;
        y += wrap(selected.description(), w - 16).size() * 11 + 4;

        int bx = x + 8;
        if (inside(mx, my, bx, y, 86, 16) && button == 0) {
            selected.toggle();
            HenClientReborn.config().save();
            return true;
        }
        String keyText = "Key: [" + KeyNames.of(selected.keyCode()) + "]";
        int kw = Math.max(86, font.width(keyText) + 12);
        if (inside(mx, my, bx + 92, y, kw, 16) && button == 0) {
            rebinding = selected;
            return true;
        }
        y += 24;

        for (Setting<?> setting : selected.settings()) {
            if (y + 24 > listBottom()) {
                break;
            }
            if (setting instanceof BooleanSetting b) {
                if (my >= y && my < y + 14 && button == 0) {
                    b.toggle();
                    HenClientReborn.config().save();
                    return true;
                }
                y += 18;
            } else if (setting instanceof NumberSetting n) {
                int trackX = x + 8;
                int trackW = w - 16;
                if (my >= y + 8 && my < y + 22 && button == 0) {
                    dragging = n;
                    dragTrackX = trackX;
                    dragTrackW = trackW;
                    applyDrag(mx);
                    return true;
                }
                y += 26;
            }
        }
        return true;
    }

    private void applyDrag(double mx) {
        if (dragging != null && dragTrackW > 0) {
            dragging.setRatio((mx - dragTrackX) / dragTrackW);
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging != null) {
            applyDrag(event.x());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null) {
            dragging = null;
            HenClientReborn.config().save();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inside(mouseX, mouseY, wx + 8, listTop(), LIST_W, listBottom() - listTop())) {
            scroll -= (int) Math.round(scrollY * 14);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (rebindingGui) {
            if (key != GLFW.GLFW_KEY_ESCAPE) {
                HenClientReborn.config().setGuiKey(key);
                HenClientReborn.config().save();
            }
            rebindingGui = false;
            return true;
        }
        if (rebinding != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                // cancel
            } else if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
                rebinding.setKeyCode(-1);
                HenClientReborn.config().save();
            } else {
                rebinding.setKeyCode(key);
                HenClientReborn.config().save();
            }
            rebinding = null;
            return true;
        }
        if (key == HenClientReborn.config().guiKey() && closeKeyArmed) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (event.key() == HenClientReborn.config().guiKey()) {
            closeKeyArmed = true;
        }
        return super.keyReleased(event);
    }

    @Override
    public void removed() {
        // Saves settings and stops the hotkey from immediately re-opening the GUI if it is still held.
        HenClientReborn.config().save();
        HenClientReborn.keybinds().markHeld(HenClientReborn.config().guiKey());
        dragging = null;
        rebinding = null;
        rebindingGui = false;
        super.removed();
    }
}
