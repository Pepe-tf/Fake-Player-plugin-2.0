package me.bill.fakePlayerPlugin.fakeplayer;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import me.bill.fakePlayerPlugin.config.Config;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class BotNavUtil {

    private BotNavUtil() {}

    @FunctionalInterface
    public interface SelectionBounds {
        boolean contains(int x, int y, int z);
    }

    @Nullable
    public static Location findStandLocation(World world, @Nullable SelectionBounds sel, int tx, int ty, int tz) {
        int[][] candidates = {
            {tx + 1, ty, tz}, {tx - 1, ty, tz}, {tx, ty, tz + 1}, {tx, ty, tz - 1},
            {tx + 2, ty, tz}, {tx - 2, ty, tz}, {tx, ty, tz + 2}, {tx, ty, tz - 2},
            {tx + 1, ty - 1, tz}, {tx - 1, ty - 1, tz}, {tx, ty - 1, tz + 1}, {tx, ty - 1, tz - 1},
            {tx + 1, ty + 1, tz}, {tx - 1, ty + 1, tz}, {tx, ty + 1, tz + 1}, {tx, ty + 1, tz - 1}
        };
        Location targetCenter = new Location(world, tx + 0.5, ty + 0.5, tz + 0.5);

        if (sel != null) {
            for (int[] c : candidates) {
                if (sel.contains(c[0], c[1], c[2])) continue;
                if (BotPathfinder.walkable(world, c[0], c[1], c[2])) {
                    Location loc = new Location(world, c[0] + 0.5, c[1], c[2] + 0.5);
                    if (loc.distanceSquared(targetCenter) <= 36.0) return loc;
                }
            }
        }

        for (int[] c : candidates) {
            if (BotPathfinder.walkable(world, c[0], c[1], c[2])) {
                Location loc = new Location(world, c[0] + 0.5, c[1], c[2] + 0.5);
                if (loc.distanceSquared(targetCenter) <= 36.0) return loc;
            }
        }
        return null;
    }

    public static Location faceToward(Location from, Location target) {
        Location loc = from.clone();
        double dx = target.getX() - loc.getX();
        double dy = target.getY() - (loc.getY() + 1.62);
        double dz = target.getZ() - loc.getZ();
        double xz = Math.sqrt(dx * dx + dz * dz);
        loc.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
        loc.setPitch((float) -Math.toDegrees(Math.atan2(dy, xz)));
        return loc;
    }

    public static boolean isAtActionLocation(@Nullable Player bot, @Nullable Location loc) {
        if (bot == null || loc == null || bot.getWorld() != loc.getWorld()) return false;
        double xz = PathfindingService.xzDist(bot.getLocation(), loc);
        double dy = Math.abs(bot.getLocation().getY() - loc.getY());
        return xz <= Config.pathfindingArrivalDistance() && dy < 1.25;
    }

    public static void useStorageBlock(Player bot, Block block) {
        try {
            ServerPlayer nms = ((CraftPlayer) bot).getHandle();
            BlockPos pos = new BlockPos(block.getX(), block.getY(), block.getZ());
            Vec3 hitVec = new Vec3(block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);
            BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, pos, false);
            nms.resetLastActionTime();
            var result = NmsPlayerSpawner.useItemOn(nms, InteractionHand.MAIN_HAND, hit);
            if (NmsPlayerSpawner.consumesAction(result)) {
                nms.swing(InteractionHand.MAIN_HAND);
            }
        } catch (Throwable ignored) {
        }
    }
}
