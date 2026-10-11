package com.henclient.reborn.modules.exploration;

import com.henclient.reborn.module.Category;
import com.henclient.reborn.module.Module;
import com.henclient.reborn.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;

/**
 * Detached camera. A hidden, never-spawned entity becomes the camera entity while the real player
 * keeps standing still (its movement input is swapped for an empty one). Everything is restored
 * when the module is disabled, the world unloads, the player disconnects or the player is replaced.
 */
public final class FreecamModule extends Module {
    private final NumberSetting speed = addSetting(new NumberSetting("speed", "Speed (blocks/tick)", 1.0, 0.1, 5.0, 0.1));

    private ArmorStand camera;
    private LocalPlayer owner;
    private ClientInput savedInput;
    private double eyeX, eyeY, eyeZ;
    private float savedYaw, savedPitch;

    public FreecamModule() {
        super("Freecam", "Move the camera independently from your body.", Category.EXPLORATION);
    }

    @Override
    public String note() {
        return "Your body stays where it is. Interactions (breaking, placing, attacking) still use the camera's "
                + "view, so avoid clicking while in Freecam. The camera and your view are restored when you disable it.";
    }

    @Override
    protected void onDisable() {
        stop(Minecraft.getInstance());
    }

    @Override
    public void onWorldUnload() {
        // The old player/level are gone; just drop our references and restore what still exists.
        stop(Minecraft.getInstance());
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        if (camera != null && owner != player) {
            // The player object was replaced (respawn, dimension change): start over cleanly.
            reset();
        }
        if (camera == null) {
            start(mc, player);
        }
        if (camera == null) {
            return;
        }
        double yaw = Math.toRadians(player.getYRot());
        double pitch = Math.toRadians(player.getXRot());
        double fx = -Math.sin(yaw) * Math.cos(pitch);
        double fy = -Math.sin(pitch);
        double fz = Math.cos(yaw) * Math.cos(pitch);
        double rx = -Math.cos(yaw);
        double rz = -Math.sin(yaw);

        Options o = mc.options;
        double forward = (o.keyUp.isDown() ? 1 : 0) - (o.keyDown.isDown() ? 1 : 0);
        double side = (o.keyRight.isDown() ? 1 : 0) - (o.keyLeft.isDown() ? 1 : 0);
        double vertical = (o.keyJump.isDown() ? 1 : 0) - (o.keyShift.isDown() ? 1 : 0);
        double s = speed.getDouble() * (o.keySprint.isDown() ? 2.0 : 1.0);

        camera.setOldPosAndRot();
        eyeX += (fx * forward + rx * side) * s;
        eyeY += (fy * forward + vertical) * s;
        eyeZ += (fz * forward + rz * side) * s;
        placeCamera(player);
    }

    @Override
    public void renderHud(GuiGraphics graphics, Minecraft mc, float partialTick) {
        // Follow the mouse every frame instead of once per tick.
        if (camera != null && mc.player != null && owner == mc.player) {
            camera.setYRot(mc.player.getYRot());
            camera.setXRot(mc.player.getXRot());
        }
    }

    private void start(Minecraft mc, LocalPlayer player) {
        ArmorStand stand = new ArmorStand(EntityType.ARMOR_STAND, mc.level);
        owner = player;
        savedInput = player.input;
        savedYaw = player.getYRot();
        savedPitch = player.getXRot();
        eyeX = player.getX();
        eyeY = player.getEyeY();
        eyeZ = player.getZ();
        camera = stand;
        player.input = new ClientInput();
        placeCamera(player);
        camera.setOldPosAndRot();
        mc.setCameraEntity(camera);
    }

    private void placeCamera(LocalPlayer player) {
        camera.setPos(eyeX, eyeY - camera.getEyeHeight(), eyeZ);
        camera.setYRot(player.getYRot());
        camera.setXRot(player.getXRot());
    }

    /** Restores camera, input and player rotation (when the original player still exists). */
    private void stop(Minecraft mc) {
        if (camera == null) {
            return;
        }
        LocalPlayer player = mc.player;
        if (player != null && owner == player) {
            if (savedInput != null) {
                player.input = savedInput;
            }
            player.setYRot(savedYaw);
            player.setXRot(savedPitch);
            if (mc.getCameraEntity() == camera) {
                mc.setCameraEntity(player);
            }
        } else if (mc.getCameraEntity() == camera) {
            mc.setCameraEntity(mc.player);
        }
        reset();
    }

    private void reset() {
        camera = null;
        owner = null;
        savedInput = null;
    }
}
