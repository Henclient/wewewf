package com.henclient.reborn.modules.hud;

import com.henclient.reborn.module.Category;
import com.henclient.reborn.module.Module;
import com.henclient.reborn.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Shows worn armor with durability and warns when a piece is nearly broken. */
public final class ArmorStatusModule extends Module {
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private final NumberSetting warnPercent = addSetting(
            new NumberSetting("warn", "Warn below (%)", 15, 1, 50, 1));

    public ArmorStatusModule() {
        super("Armor Status", "Shows equipped armor and warns about nearly broken pieces.", Category.HUD);
        setEnabled(true);
    }

    @Override
    public void renderHud(GuiGraphics g, Minecraft mc, float partialTick) {
        if (mc.player == null) {
            return;
        }
        int x = 6;
        int y = g.guiHeight() / 2 - 40;
        boolean low = false;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = mc.player.getItemBySlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            g.fill(x - 2, y - 1, x + 62, y + 17, 0x90101012);
            g.fill(x - 2, y - 1, x - 1, y + 17, 0xFFE11D2E);
            g.renderItem(stack, x, y);
            if (stack.isDamageableItem()) {
                int max = stack.getMaxDamage();
                int left = max - stack.getDamageValue();
                int pct = max <= 0 ? 100 : (int) Math.round(left * 100.0 / max);
                boolean warn = pct <= warnPercent.getInt();
                low |= warn;
                int color = warn ? 0xFFEF5350 : (pct <= 50 ? 0xFFFFCA28 : 0xFF66BB6A);
                g.drawString(mc.font, (warn ? "! " : "") + pct + "%", x + 20, y + 4, color);
            } else {
                g.drawString(mc.font, "-", x + 20, y + 4, 0xFF9A9AA2);
            }
            y += 20;
        }
        if (low && (System.currentTimeMillis() / 500) % 2 == 0) {
            String warning = "ARMOR NEARLY BROKEN";
            g.drawString(mc.font, warning, (g.guiWidth() - mc.font.width(warning)) / 2, g.guiHeight() / 2 + 30, 0xFFEF5350);
        }
    }
}
