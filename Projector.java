package com.henclient.reborn.render;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Projects world positions onto the GUI surface using the current camera entity and FOV setting.
 * This keeps ESP drawing on plain GUI fills (no custom render pipelines), and it follows Freecam
 * automatically because the camera entity is used. FOV effects such as sprinting are ignored.
 */
public final class Projector {
    private double cx, cy, cz;
    private double fx, fy, fz;
    private double rx, ry, rz;
    private double ux, uy, uz;
    private double tanHalfFov;
    private double aspect;
    private int screenW, screenH;

    /** Prepares the projector for this frame. Returns false when no camera is available. */
    public boolean setup(Minecraft mc, float partialTick, int guiWidth, int guiHeight) {
        Entity camera = mc.getCameraEntity();
        if (camera == null || guiWidth <= 0 || guiHeight <= 0) {
            return false;
        }
        Vec3 pos = camera.getEyePosition(partialTick);
        cx = pos.x;
        cy = pos.y;
        cz = pos.z;

        double yaw = Math.toRadians(camera.getViewYRot(partialTick));
        double pitch = Math.toRadians(camera.getViewXRot(partialTick));

        fx = -Math.sin(yaw) * Math.cos(pitch);
        fy = -Math.sin(pitch);
        fz = Math.cos(yaw) * Math.cos(pitch);

        rx = -Math.cos(yaw);
        ry = 0;
        rz = -Math.sin(yaw);

        // up = right x forward
        ux = ry * fz - rz * fy;
        uy = rz * fx - rx * fz;
        uz = rx * fy - ry * fx;

        double fovDegrees = mc.options.fov().get();
        tanHalfFov = Math.tan(Math.toRadians(fovDegrees) / 2.0);
        aspect = (double) guiWidth / (double) guiHeight;
        screenW = guiWidth;
        screenH = guiHeight;
        return true;
    }

    /** Projects a point; writes screen x,y into out and returns false when it is behind the camera. */
    public boolean project(double x, double y, double z, double[] out) {
        double dx = x - cx;
        double dy = y - cy;
        double dz = z - cz;
        double depth = dx * fx + dy * fy + dz * fz;
        if (depth <= 0.05) {
            return false;
        }
        double right = dx * rx + dy * ry + dz * rz;
        double up = dx * ux + dy * uy + dz * uz;
        double ndcX = right / (depth * tanHalfFov * aspect);
        double ndcY = up / (depth * tanHalfFov);
        out[0] = (ndcX + 1.0) * 0.5 * screenW;
        out[1] = (1.0 - ndcY) * 0.5 * screenH;
        return true;
    }

    /**
     * Projects the 8 corners of a box and writes [minX, minY, maxX, maxY] into out.
     * Returns false if any corner is behind the camera or the box is entirely off screen.
     */
    public boolean projectBox(double x1, double y1, double z1, double x2, double y2, double z2, double[] out) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        double[] p = new double[2];
        for (int i = 0; i < 8; i++) {
            double x = (i & 1) == 0 ? x1 : x2;
            double y = (i & 2) == 0 ? y1 : y2;
            double z = (i & 4) == 0 ? z1 : z2;
            if (!project(x, y, z, p)) {
                return false;
            }
            minX = Math.min(minX, p[0]);
            minY = Math.min(minY, p[1]);
            maxX = Math.max(maxX, p[0]);
            maxY = Math.max(maxY, p[1]);
        }
        if (maxX < 0 || maxY < 0 || minX > screenW || minY > screenH) {
            return false;
        }
        out[0] = minX;
        out[1] = minY;
        out[2] = maxX;
        out[3] = maxY;
        return true;
    }
}
