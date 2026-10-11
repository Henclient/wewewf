package com.henclient.reborn.modules.combat;

import com.henclient.reborn.module.Category;
import com.henclient.reborn.module.Module;
import com.henclient.reborn.module.setting.BooleanSetting;
import com.henclient.reborn.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Attacks the entity under the crosshair at a fixed interval while the attack key is held.
 * Does nothing when disabled, when a screen is open, or when no entity is targeted.
 */
public final class AutoClickerModule extends Module {
    private final NumberSetting intervalMs = addSetting(new NumberSetting("interval", "Interval (ms)", 150, 50, 1000, 10));
    private final BooleanSetting holdOnly = addSetting(new BooleanSetting("hold", "Only while attack key held", true));
    private final BooleanSetting respectCooldown = addSetting(new BooleanSetting("cooldown", "Wait for attack cooldown", true));

    private long lastClick;

    public AutoClickerModule() {
        super("Auto Clicker", "Attacks the targeted entity at a configurable interval.", Category.COMBAT);
    }

    @Override
    public String note() {
        return "Many servers prohibit automated clicking and anticheat can detect or punish it. "
                + "Use only where it is allowed. The interval is limited to 50-1000 ms.";
    }

    @Override
    protected void onEnable() {
        lastClick = 0L;
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null || mc.screen != null) {
            return;
        }
        if (holdOnly.isOn() && !mc.options.keyAttack.isDown()) {
            return;
        }
        HitResult hit = mc.hitResult;
        if (!(hit instanceof EntityHitResult entityHit)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastClick < intervalMs.getInt()) {
            return;
        }
        if (respectCooldown.isOn() && player.getAttackStrengthScale(0.0f) < 0.9f) {
            return;
        }
        Entity target = entityHit.getEntity();
        mc.gameMode.attack(player, target);
        player.swing(InteractionHand.MAIN_HAND);
        lastClick = now;
    }
}
