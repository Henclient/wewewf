package com.henclient.reborn.util;

import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

/** Storage-like block entity groups counted per chunk by the Stash Finder. */
public enum StashCategory {
    CHEST("Chests"),
    BARREL("Barrels"),
    SHULKER("Shulkers"),
    ENDER_CHEST("Ender chests"),
    FURNACE("Furnaces"),
    HOPPER("Hoppers"),
    DISPENSER("Dispensers/Droppers");

    private final String label;

    StashCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Returns the category of a block entity, or null if it is not storage-like. */
    public static StashCategory of(BlockEntity be) {
        if (be instanceof ChestBlockEntity) {
            return CHEST;
        }
        if (be instanceof BarrelBlockEntity) {
            return BARREL;
        }
        if (be instanceof ShulkerBoxBlockEntity) {
            return SHULKER;
        }
        if (be instanceof EnderChestBlockEntity) {
            return ENDER_CHEST;
        }
        if (be instanceof AbstractFurnaceBlockEntity) {
            return FURNACE;
        }
        if (be instanceof HopperBlockEntity) {
            return HOPPER;
        }
        if (be instanceof DispenserBlockEntity) {
            return DISPENSER;
        }
        return null;
    }
}
