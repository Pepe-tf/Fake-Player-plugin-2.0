package me.bill.fakePlayerPlugin.fakeplayer.pathfinding;

import org.bukkit.World;

import me.bill.fakePlayerPlugin.fakeplayer.BotPathfinder;

import de.bsommerfeld.pathetic.api.pathing.context.EnvironmentContext;

/**
 * Per-search environment handed to Pathetic: which Bukkit {@link World} to read blocks from and the
 * {@link BotPathfinder.PathOptions} (parkour/break/place/avoid-water/avoid-lava) that govern this
 * specific navigation request.
 *
 * <p>One {@link de.bsommerfeld.pathetic.api.pathing.Pathfinder} instance is shared across every bot
 * (see {@link PatheticPathfindingController}); this context is what lets a single pathfinder apply
 * different rules per bot/request without rebuilding the whole engine each time.
 */
public record PatheticEnvironment(World world, BotPathfinder.PathOptions options) implements EnvironmentContext {}
