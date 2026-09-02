package me.bill.fakePlayerPlugin.fakeplayer.pathfinding;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.BotNavUtil;
import me.bill.fakePlayerPlugin.fakeplayer.BotPathfinder;

import de.bsommerfeld.pathetic.api.pathing.context.EnvironmentContext;
import de.bsommerfeld.pathetic.api.provider.NavigationPoint;
import de.bsommerfeld.pathetic.api.provider.NavigationPointProvider;
import de.bsommerfeld.pathetic.api.wrapper.PathPosition;

/**
 * Bridges Pathetic's grid search to real Bukkit block data.
 *
 * <p>{@code position} is treated as the bot's <b>feet</b> block. A position is traversable when the
 * two body-height blocks (feet, head) are passable and the block below is stand-on-able ground -
 * reusing {@link BotNavUtil}'s existing walkability rules - with per-request relaxations for
 * block-breaking, block-placing (gap bridging), and water/lava avoidance sourced from the
 * {@link BotPathfinder.PathOptions} carried on the {@link PatheticEnvironment}.
 */
public final class BukkitNavigationPointProvider implements NavigationPointProvider {

    /** Blocks never treated as "breakable" for path clearance, even when break-blocks is enabled. */
    private static boolean isUnbreakable(Material mat) {
        return switch (mat) {
            case BEDROCK,
                    BARRIER,
                    OBSIDIAN,
                    CRYING_OBSIDIAN,
                    ANCIENT_DEBRIS,
                    END_PORTAL_FRAME,
                    END_PORTAL,
                    NETHER_PORTAL,
                    CHEST,
                    TRAPPED_CHEST,
                    ENDER_CHEST,
                    BARREL,
                    FURNACE,
                    BLAST_FURNACE,
                    SMOKER,
                    SHULKER_BOX,
                    SPAWNER,
                    BEACON,
                    ENCHANTING_TABLE -> true;
            default -> mat.name().endsWith("SHULKER_BOX");
        };
    }

    @Override
    public NavigationPoint getNavigationPoint(PathPosition position, EnvironmentContext environmentContext) {
        if (!(environmentContext instanceof PatheticEnvironment env) || env.world() == null) {
            return () -> false;
        }
        boolean traversable = isTraversable(env.world(), position, env.options());
        return () -> traversable;
    }

    /**
     * The single source of truth for "can a bot's feet legally occupy this block" - shared by the
     * live search ({@link #getNavigationPoint}) and {@link PatheticPathfindingController}'s
     * post-search re-verification pass, so both agree on exactly the same rules.
     */
    static boolean isTraversable(World world, PathPosition pos, BotPathfinder.PathOptions options) {
        int x = pos.getFlooredX();
        int y = pos.getFlooredY();
        int z = pos.getFlooredZ();

        if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 1) return false;
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return false;

        Material feet = world.getBlockAt(x, y, z).getType();
        Material head = world.getBlockAt(x, y + 1, z).getType();
        Material below = world.getBlockAt(x, y - 1, z).getType();

        if (options.avoidWater() && (feet == Material.WATER || head == Material.WATER)) return false;
        // Lava is never passable in BotNavUtil.canPassThrough, so no separate avoidLava check is
        // needed here - it is already always avoided.

        if (!passable(world, x, y, z, options.breakBlocks())) return false;
        if (!passable(world, x, y + 1, z, options.breakBlocks())) return false;

        if (BotNavUtil.canStandOn(world, x, y - 1, z)) return true;

        if (options.placeBlocks() && below.isAir()) {
            return hasFloorWithinFallLimit(world, x, y - 1, z);
        }
        return false;
    }

    private static boolean passable(World world, int x, int y, int z, boolean breakBlocks) {
        if (BotNavUtil.canPassThrough(world, x, y, z)) return true;
        if (!breakBlocks) return false;
        Block block = world.getBlockAt(x, y, z);
        Material mat = block.getType();
        return mat.isSolid() && !isUnbreakable(mat);
    }

    /** Guards block-placing so bots never "bridge" across a bottomless gap. */
    private static boolean hasFloorWithinFallLimit(World world, int x, int y, int z) {
        int maxFall = Config.pathfindingMaxFall();
        for (int dy = 0; dy <= maxFall; dy++) {
            if (BotNavUtil.canStandOn(world, x, y - dy, z)) return true;
        }
        return false;
    }
}
