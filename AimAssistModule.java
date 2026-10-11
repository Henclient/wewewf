package com.henclient.reborn.modules.combat;

import com.henclient.reborn.module.Category;
import com.henclient.reborn.module.Module;
import com.henclient.reborn.module.setting.BooleanSetting;
import com.henclient.reborn.module.setting.NumberSetting;
import com.henclient.reborn.modules.exploration.FreecamModule;
import com.henclient.reborn.HenClientReborn;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Gently nudges your aim toward the closest valid target inside a view cone. */
public final class AimAssistModule extends Module {
    private final NumberSetting range = addSetting(new NumberSetting("range", "Range (blocks)", 4.5, 2.0, 8.0, 0.5));
    private final NumberSetting strength = addSetting(new NumberSetting("strength", "Strength", 0.15, 0.05, 1.0, 0.05));
    private final NumberSetting maxStep = addSetting(new NumberSetting("maxStep", "Max turn per tick (deg)", 6, 1, 30, 1));
    private final NumberSetting fov = addSetting(new NumberSetting("fov", "Field of view (deg)", 60, 10, 180, 5));
    private final BooleanSetting playersOnly = addSetting(new BooleanSetting("playersOnly", "Players only", false));
    private final BooleanSetting holdOnly = addSetting(new BooleanSetting("hold", "Only while attack key held", true));

    public AimAssistModule() {
        super("Aim Assist", "Gently rotates your view toward nearby targets.", Category.COMBAT);
    }

    @Override
    public String note() {
        return "Many servers prohibit aim assistance and anticheat can detect or punish it. "
                + "Use only where it is allowed. Keep strength low for subtle assistance.";
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.screen != null) {
            return;
        }
        if (HenClientReborn.modules().isEnabled(FreecamModule.class)) {
            return;
        }
        if (holdOnly.isOn() && !mc.options.keyAttack.isDown()) {
            return;
        }

        double maxRange = range.getDouble();
        double halfFov = fov.getDouble() / 2.0;
        Vec3 eye = player.getEyePosition();

        LivingEntity best = null;
        double bestAngle = Double.MAX_VALUE;
        float bestYaw = 0f, bestPitch = 0f;

        for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(maxRange))) {
            if (e == player || !e.isAlive() || e.isSpectator()) {
                continue;
            }
            if (playersOnly.isOn() && !(e instanceof Player)) {
                continue;
            }
            if (player.distanceTo(e) > maxRange) {
                continue;
            }
            Vec3 target = e.getBoundingBox().getCenter();
            double dx = target.x - eye.x;
            double dy = target.y - eye.y;
            double dz = target.z - eye.z;
            float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            float dYaw = Mth.wrapDegrees(yaw - player.getYRot());
            float dPitch = pitch - player.getXRot();
            double angle = Math.hypot(dYaw, dPitch);
            if (angle <= halfFov && angle < bestAngle) {
                best = e;
                bestAngle = angle;
                bestYaw = dYaw;
                bestPitch = dPitch;
            }
        }
        if (best == null) {
            return;
        }
        float limit = maxStep.getFloat();
        float s = strength.getFloat();
        float stepYaw = Mth.clamp(bestYaw * s, -limit, limit);
        float stepPitch = Mth.clamp(bestPitch * s, -limit, limit);
        player.setYRot(player.getYRot() + stepYaw);
        player.setXRot(Mth.clamp(player.getXRot() + stepPitch, -90.0f, 90.0f));
    }
}
