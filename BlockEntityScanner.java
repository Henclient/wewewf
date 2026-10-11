package com.henclient.reborn.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * Finds block entities in chunks that are already loaded on the client.
 * It never requests new chunks and only walks the (small) block entity maps, not every block.
 */
public final class BlockEntityScanner {
    public static final String CHEST = "Chest";
    public static final String BARREL = "Barrel";
    public static final String SHULKER = "Shulker";
    public static final String SPAWNER = "Spawner";

    public record Hit(BlockPos pos, String kind, double distSq) {
    }

    private BlockEntityScanner() {
    }

    /** Classifies a block entity; returns null to ignore it. */
    public static String storageKind(BlockEntity be, boolean chests, boolean barrels, boolean shulkers) {
        if (chests && be instanceof ChestBlockEntity) {
            return CHEST;
        }
        if (barrels && be instanceof BarrelBlockEntity) {
            return BARREL;
        }
        if (shulkers && be instanceof ShulkerBoxBlockEntity) {
            return SHULKER;
        }
        return null;
    }

    public static String spawnerKind(BlockEntity be) {
        return be instanceof SpawnerBlockEntity ? SPAWNER : null;
    }

    /** Distinct color per kind (ARGB). */
    public static int colorOf(String kind) {
        switch (kind) {
            case CHEST: return 0xFFFFA726;
            case BARREL: return 0xFFA1887F;
            case SHULKER: return 0xFFBA68C8;
            case SPAWNER: return 0xFF29B6F6;
            default: return 0xFFFFFFFF;
        }
    }

    /**
     * @param radiusChunks horizontal search radius in chunks around the player
     * @param max          maximum number of results kept (nearest first)
     */
    public static List<Hit> scan(Minecraft mc, int radiusChunks, int max, Function<BlockEntity, String> classifier) {
        List<Hit> hits = new ArrayList<>();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) {
            return hits;
        }
        int pcx = player.getBlockX() >> 4;
        int pcz = player.getBlockZ() >> 4;
        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();

        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                ChunkAccess access = level.getChunk(pcx + dx, pcz + dz, ChunkStatus.FULL, false);
                if (!(access instanceof LevelChunk chunk)) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    String kind = classifier.apply(be);
                    if (kind == null) {
                        continue;
                    }
                    BlockPos pos = be.getBlockPos();
                    double ddx = pos.getX() + 0.5 - px;
                    double ddy = pos.getY() + 0.5 - py;
                    double ddz = pos.getZ() + 0.5 - pz;
                    hits.add(new Hit(pos, kind, ddx * ddx + ddy * ddy + ddz * ddz));
                }
            }
        }
        hits.sort(Comparator.comparingDouble(Hit::distSq));
        if (hits.size() > max) {
            return new ArrayList<>(hits.subList(0, max));
        }
        return hits;
    }
}
