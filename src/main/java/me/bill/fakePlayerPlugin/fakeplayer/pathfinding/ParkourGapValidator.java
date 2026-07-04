package me.bill.fakePlayerPlugin.fakeplayer.pathfinding;

import org.bukkit.World;

import me.bill.fakePlayerPlugin.fakeplayer.BotNavUtil;
import me.bill.fakePlayerPlugin.fakeplayer.BotPathfinder;

import de.bsommerfeld.pathetic.api.pathing.processing.ValidationProcessor;
import de.bsommerfeld.pathetic.api.pathing.processing.context.EvaluationContext;
import de.bsommerfeld.pathetic.api.wrapper.PathPosition;

/**
 * Gates the "gap jump" offsets added by {@link NavNeighbors#STRATEGY} (horizontal steps longer than
 * one block, used to clear 1-block gaps). These transitions are only legal when the bot's
 * {@link BotPathfinder.PathOptions#parkour()} is enabled for this search, and even then only when
 * there is headroom over the gap being jumped (so a "jump" never clips through a wall).
 */
public final class ParkourGapValidator implements ValidationProcessor {

    /** Horizontal step distances at or below this are normal cardinal/diagonal moves, not jumps. */
    private static final double NORMAL_STEP_THRESHOLD = 1.5;

    @Override
    public boolean isValid(EvaluationContext context) {
        PathPosition current = context.getCurrentPathPosition();
        PathPosition previous = context.getPreviousPathPosition();
        if (previous == null) return true;

        double dx = current.getFlooredX() - previous.getFlooredX();
        double dz = current.getFlooredZ() - previous.getFlooredZ();
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        if (horizontalDist <= NORMAL_STEP_THRESHOLD) return true;

        if (!(context.getEnvironmentContext() instanceof PatheticEnvironment env)
                || !env.options().parkour()) {
            return false;
        }

        World world = env.world();
        int midX = previous.getFlooredX() + (int) Math.round(dx / 2.0);
        int midZ = previous.getFlooredZ() + (int) Math.round(dz / 2.0);
        int y = current.getFlooredY();
        return BotNavUtil.canPassThrough(world, midX, y, midZ) && BotNavUtil.canPassThrough(world, midX, y + 1, midZ);
    }
}
