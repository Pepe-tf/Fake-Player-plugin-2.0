package me.bill.fakePlayerPlugin.fakeplayer.pathfinding;

import org.bukkit.Material;
import org.bukkit.World;

import me.bill.fakePlayerPlugin.fakeplayer.BotNavUtil;

import de.bsommerfeld.pathetic.api.pathing.processing.Cost;
import de.bsommerfeld.pathetic.api.pathing.processing.CostProcessor;
import de.bsommerfeld.pathetic.api.pathing.processing.context.EvaluationContext;
import de.bsommerfeld.pathetic.api.wrapper.PathPosition;

/**
 * Soft preferences layered on top of {@link ParkourGapValidator}'s hard gate: wading through water,
 * breaking a block, or bridging a gap are all allowed when the corresponding
 * {@link me.bill.fakePlayerPlugin.fakeplayer.BotPathfinder.PathOptions} is enabled, but the search
 * should still prefer a normal dry walking route when one exists at similar length.
 */
public final class TerrainCostProcessor implements CostProcessor {

    @Override
    public Cost calculateCostContribution(EvaluationContext context) {
        if (!(context.getEnvironmentContext() instanceof PatheticEnvironment env)) return Cost.ZERO;
        World world = env.world();
        PathPosition pos = context.getCurrentPathPosition();
        int x = pos.getFlooredX();
        int y = pos.getFlooredY();
        int z = pos.getFlooredZ();
        if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 1) return Cost.ZERO;
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return Cost.ZERO;

        Material feet = world.getBlockAt(x, y, z).getType();
        if (feet == Material.WATER) return Cost.of(1.5);

        // Vines/scaffolding/ladders are climbable so the hard-gate in
        // BukkitNavigationPointProvider allows them, but real climbing is far more failure-prone
        // to actually execute (autojump/step-up logic isn't built for it) than walking — only take
        // this route when nothing better exists.
        if (BotNavUtil.isClimbable(feet)) return Cost.of(3.0);

        Material below = world.getBlockAt(x, y - 1, z).getType();
        if (below.isAir()) return Cost.of(3.0); // gap that needs bridging

        // Standing on leaves is legal (they have real collision), but routes through a tree canopy
        // are frequently a dead end wedged between more leaves/vines on every side — nudge the
        // search toward solid ground when a comparable route exists instead of tunneling through.
        if (below.name().contains("LEAVES")) return Cost.of(2.0);

        if (feet.isSolid()) return Cost.of(4.0); // needs breaking to pass

        return Cost.ZERO;
    }
}
