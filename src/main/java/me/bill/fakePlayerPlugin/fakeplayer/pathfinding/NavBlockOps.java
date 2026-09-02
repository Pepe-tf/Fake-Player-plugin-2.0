package me.bill.fakePlayerPlugin.fakeplayer.pathfinding;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.NmsPlayerSpawner;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Path-clearing block operations: breaking an obstruction or placing a block to bridge a gap, both
 * on a fixed tick budget ({@link Config#pathfindingBreakTicks()} / {@link
 * Config#pathfindingPlaceTicks()}) rather than {@code LeftClickCommand}'s tool-aware mining speed -
 * this is a movement-clearing action, not a precision mining command.
 */
final class NavBlockOps {

    private NavBlockOps() {}

    /**
     * Progresses breaking the block at {@code pos} by one tick. Returns {@code true} once the block
     * is fully broken.
     */
    static boolean tickBreak(Player bot, Block block, int[] progressTicks) {
        if (block.getType().isAir()) return true;
        ServerPlayer nms = ((CraftPlayer) bot).getHandle();
        BlockPos pos = new BlockPos(block.getX(), block.getY(), block.getZ());
        BlockState state = nms.level().getBlockState(pos);
        if (state.isAir()) return true;

        int budget = Math.max(1, Config.pathfindingBreakTicks());
        progressTicks[0]++;
        int stage = Math.min(9, (int) ((progressTicks[0] / (double) budget) * 10));
        NmsPlayerSpawner.destroyBlockProgress(nms, -2, pos, stage);
        NmsPlayerSpawner.handleBlockBreakAction(
                nms,
                pos,
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                Direction.DOWN,
                nms.level().getMaxY(),
                -1);
        nms.swing(InteractionHand.MAIN_HAND);

        if (progressTicks[0] >= budget) {
            NmsPlayerSpawner.handleBlockBreakAction(
                    nms,
                    pos,
                    ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                    Direction.DOWN,
                    nms.level().getMaxY(),
                    -1);
            NmsPlayerSpawner.destroyBlockProgress(nms, -2, pos, -1);
            nms.gameMode.destroyBlock(pos);
            return true;
        }
        return false;
    }

    /**
     * Progresses placing a bridging block at {@code target} by one tick. Returns {@code true} once
     * the block has been placed.
     */
    static boolean tickPlace(Player bot, Location target, int[] progressTicks) {
        Block block = target.getBlock();
        if (!block.getType().isAir()) return true;

        int budget = Math.max(1, Config.pathfindingPlaceTicks());
        progressTicks[0]++;
        if (progressTicks[0] < budget) return false;

        Material material = resolvePlaceMaterial();
        block.setType(material, true);
        try {
            block.getWorld()
                    .playSound(
                            block.getLocation(),
                            block.getBlockData().getSoundGroup().getPlaceSound(),
                            1.0f,
                            1.0f);
        } catch (Throwable ignored) {
        }
        try {
            ServerPlayer nms = ((CraftPlayer) bot).getHandle();
            nms.swing(InteractionHand.MAIN_HAND);
        } catch (Throwable ignored) {
        }
        return true;
    }

    private static Material resolvePlaceMaterial() {
        try {
            Material mat = Material.valueOf(Config.pathfindingPlaceMaterial().toUpperCase(java.util.Locale.ROOT));
            if (mat.isBlock() && mat.isSolid()) return mat;
        } catch (IllegalArgumentException ignored) {
        }
        return Material.DIRT;
    }
}
