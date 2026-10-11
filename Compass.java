package com.henclient.reborn.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a direction marker on a ring around the crosshair that points toward a world position
 * (a tracer-style indicator that works even when the target is behind you or very far away).
 */
public final class Compass {
    private Compass() {
    }

    public static void marker(GuiGraphics g, Minecraft mc, float partialTick, double targetX, double targetZ,
                              String label, int color) {
        Entity camera = mc.getCameraEntity();
        if (camera == null) {
            return;
        }
        Vec3 pos = camera.getEyePosition(partialTick);
        double dx = targetX - pos.x;
        double dz = targetZ - pos.z;
        double dist = Math.hypot(dx, dz);

        // Minecraft yaw 0 faces +Z and grows clockwise, so convert the bearing the same way.
        double targetYaw = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        double delta = Mth.wrapDegrees((float) (targetYaw - camera.getViewYRot(partialTick)));
        double rad = Math.toRadians(delta);

        int cx = g.guiWidth() / 2;
        int cy = g.guiHeight() / 2;
        int radius = Math.max(30, Math.min(80, g.guiHeight() / 2 - 24));
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);

        // Short tracer line from near the crosshair out to the marker.
        for (int d = radius / 2; d < radius; d += 4) {
            int px = cx + (int) Math.round(sin * d);
            int py = cy - (int) Math.round(cos * d);
            g.fill(px, py, px + 2, py + 2, color);
        }
        int mx = cx + (int) Math.round(sin * radius);
        int my = cy - (int) Math.round(cos * radius);
        g.fill(mx - 3, my - 3, mx + 4, my + 4, color);

        String text = label + " " + Math.round(dist) + "m";
        int textW = mc.font.width(text);
        int tx = mx >= cx ? mx + 8 : mx - 8 - textW;
        g.drawString(mc.font, text, tx, my - 4, color);
    }
}
