package me.bill.fakePlayerPlugin.fakeplayer.pathfinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.BotNavUtil;
import me.bill.fakePlayerPlugin.fakeplayer.BotPathfinder;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.fakeplayer.NmsPlayerSpawner;
import me.bill.fakePlayerPlugin.fakeplayer.PathfindingService;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.util.FppLogger;
import me.bill.fakePlayerPlugin.util.FppScheduler;

import de.bsommerfeld.pathetic.api.factory.PathfinderFactory;
import de.bsommerfeld.pathetic.api.pathing.Pathfinder;
import de.bsommerfeld.pathetic.api.pathing.PathfindingSearch;
import de.bsommerfeld.pathetic.api.pathing.configuration.PathfinderConfiguration;
import de.bsommerfeld.pathetic.api.pathing.result.PathfinderResult;
import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import de.bsommerfeld.pathetic.engine.factory.AStarPathfinderFactory;

/**
 * Core-owned {@link PathfindingService.Controller} backed by the
 * <a href="https://github.com/bsommerfeld/pathetic">Pathetic</a> A* engine.
 *
 * <p>One shared {@link Pathfinder} instance serves every bot; per-request behavior (parkour,
 * block-breaking, block-placing, water/lava avoidance) is carried per-search via
 * {@link PatheticEnvironment} rather than by rebuilding the engine. Pathetic only produces the
 * waypoint list — actually walking the bot along it (rotation, sprint/jump, obstacle clearing,
 * recalculation, arrival/stuck detection) is driven here every tick via a per-bot repeating task.
 */
public final class PatheticPathfindingController implements PathfindingService.Controller {

    private final FakePlayerPlugin plugin;
    private final FakePlayerManager manager;
    private final Pathfinder pathfinder;
    private final Map<UUID, NavState> states = new ConcurrentHashMap<>();

    public PatheticPathfindingController(FakePlayerPlugin plugin, FakePlayerManager manager) {
        this.plugin = plugin;
        this.manager = manager;

        PathfinderConfiguration config = PathfinderConfiguration.builder()
                .provider(new BukkitNavigationPointProvider())
                .neighborStrategy(NavNeighbors.STRATEGY)
                .validationProcessors(List.of(new ParkourGapValidator()))
                .costProcessor(List.of(new TerrainCostProcessor()))
                .async(true)
                .fallback(true)
                .maxIterations(Math.max(500, Config.pathfindingMaxNodesExtended()))
                .maxLength(Math.max(64, Config.pathfindingMaxRange() * 3))
                .build();

        PathfinderFactory factory = new AStarPathfinderFactory();
        this.pathfinder = factory.createPathfinder(config);
    }

    // ── Controller: bookkeeping ─────────────────────────────────────────────

    @Override
    public boolean isNavigating(@NotNull UUID botUuid) {
        return states.containsKey(botUuid);
    }

    @Override
    public boolean isNavigating(@NotNull UUID botUuid, @NotNull PathfindingService.Owner owner) {
        NavState state = states.get(botUuid);
        return state != null && state.owner == owner;
    }

    @Override
    public PathfindingService.Owner getOwner(@NotNull UUID botUuid) {
        NavState state = states.get(botUuid);
        return state != null ? state.owner : null;
    }

    @Override
    public void cancel(@NotNull UUID botUuid) {
        NavState state = states.remove(botUuid);
        if (state == null) return;
        stop(botUuid, state);
        if (state.request.onCancel() != null) state.request.onCancel().run();
    }

    @Override
    public void cancelAll() {
        for (UUID uuid : new ArrayList<>(states.keySet())) cancel(uuid);
    }

    @Override
    public void cancelAll(@NotNull PathfindingService.Owner owner) {
        for (Map.Entry<UUID, NavState> entry : new ArrayList<>(states.entrySet())) {
            if (entry.getValue().owner == owner) cancel(entry.getKey());
        }
    }

    // ── Controller: starting a navigation ───────────────────────────────────

    @Override
    public void navigate(@NotNull FakePlayer fp, @NotNull PathfindingService.NavigationRequest request) {
        UUID uuid = fp.getUuid();
        NavState previous = states.remove(uuid);
        if (previous != null) stop(uuid, previous); // superseded, not an external cancel — no onCancel

        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline()) {
            failImmediately(request);
            return;
        }
        Location target = request.destinationSupplier().get();
        if (target == null || target.getWorld() != bot.getWorld()) {
            failImmediately(request);
            return;
        }

        NavState state = new NavState(request);
        states.put(uuid, state);
        computePath(fp, state, bot.getLocation(), target);
    }

    private void failImmediately(PathfindingService.NavigationRequest request) {
        if (request.onPathFailure() != null) request.onPathFailure().run();
        else if (request.onCancel() != null) request.onCancel().run();
    }

    private void computePath(FakePlayer fp, NavState state, Location from, Location target) {
        BotPathfinder.PathOptions options = PathfindingService.resolvePathOptions(fp, state.request.overrideOpts());
        PatheticEnvironment env = new PatheticEnvironment(from.getWorld(), options);
        PathPosition start = new PathPosition(from.getX(), from.getY(), from.getZ());
        PathPosition goal = new PathPosition(target.getX(), target.getY(), target.getZ());

        state.lastTarget = target.clone();
        PathfindingSearch search = pathfinder.findPath(start, goal, env);
        search.ifPresent(result -> FppScheduler.runSync(plugin, () -> onPathFound(fp, state, result)))
                .orElse(result -> FppScheduler.runSync(plugin, () -> onPathFailed(fp, state)))
                .exceptionally(t -> {
                    FppLogger.debug("PatheticPathfindingController: search failed for '" + fp.getName() + "': "
                            + t.getMessage());
                    FppScheduler.runSync(plugin, () -> onPathFailed(fp, state));
                });
    }

    private void onPathFound(FakePlayer fp, NavState state, PathfinderResult result) {
        UUID uuid = fp.getUuid();
        if (states.get(uuid) != state) return; // cancelled/superseded while the search was running

        List<PathPosition> waypoints = new ArrayList<>(result.getPath().collect());
        if (waypoints.isEmpty()) {
            onPathFailed(fp, state);
            return;
        }

        Player bot = fp.getPlayer();
        if (bot == null) {
            onPathFailed(fp, state);
            return;
        }

        // Pathetic's search runs async against a snapshot of the world and only checks each node in
        // isolation. Re-walk the finished route synchronously, on the main thread, against the real
        // current block state before committing the bot to it — this catches routes that are legal
        // node-by-node but not actually walkable end to end (chunks changed mid-search, or a sequence
        // the node-level model can't see, like tunneling through several stacked leaf/vine blocks),
        // so the bot never sets off toward something it's about to fail to reach.
        String failureReason = verifyPath(fp, state, bot.getWorld(), waypoints);
        if (failureReason != null) {
            Config.debugPathfinding("event=PATH_REJECTED bot='" + fp.getName() + "' reason=" + failureReason);
            onPathFailed(fp, state);
            return;
        }

        state.waypoints = waypoints;
        state.waypointIndex = 0;
        state.stuckTicks = 0;
        state.failedRecalculations = 0;
        state.lastPos = bot.getLocation();

        if (state.taskId < 0) {
            manager.lockForNavigation(uuid);
            state.taskId = FppScheduler.runSyncRepeatingWithId(plugin, bot, () -> tickMovement(fp, state), 0L, 1L);
        }
    }

    /**
     * Simulates the whole waypoint list before any movement starts: every node must still be
     * traversable right now (not just when the async search visited it), and every consecutive step
     * must be a move the tick-driven walker can actually execute (bounded rise, bounded horizontal
     * jump, only climbing where a climbable block is actually present). Returns {@code null} if the
     * path checks out, or a short machine-readable reason string identifying the first failure.
     */
    private String verifyPath(FakePlayer fp, NavState state, World world, List<PathPosition> waypoints) {
        BotPathfinder.PathOptions options = PathfindingService.resolvePathOptions(fp, state.request.overrideOpts());

        // Waypoint 0 is always the bot's real, current physical position (the search starts from
        // wherever the bot is standing right now) — not a synthesized grid node. It's valid by
        // definition; re-running the grid model's standability heuristic against it is redundant at
        // best and, at worst, a false rejection (e.g. the bot is legitimately standing on something
        // the block-name heuristics don't recognize) that would reject *every single path* the bot
        // ever tries to walk. Only nodes the search actually generated need re-verifying.
        PathPosition previous = waypoints.get(0);
        for (int i = 1; i < waypoints.size(); i++) {
            PathPosition wp = waypoints.get(i);
            if (!BukkitNavigationPointProvider.isTraversable(world, wp, options)) {
                return "NOT_TRAVERSABLE@" + i + "(" + wp.getFlooredX() + "," + wp.getFlooredY() + "," + wp.getFlooredZ()
                        + ")";
            }

            double horiz = horizontalDistance(previous, wp);
            double rise = wp.getY() - previous.getY();

            if (rise > 1.05) {
                int x = wp.getFlooredX(), y = wp.getFlooredY(), z = wp.getFlooredZ();
                Material below = world.getBlockAt(x, y - 1, z).getType();
                Material feet = world.getBlockAt(x, y, z).getType();
                if (!BotNavUtil.isClimbable(below) && !BotNavUtil.isClimbable(feet)) {
                    return "IMPASSABLE_RISE@" + i + "(+" + String.format("%.2f", rise) + ")";
                }
            }

            if (horiz > 1.5 && !options.parkour()) {
                // A gap-jump offset slipped through without parkour enabled for this search —
                // ParkourGapValidator should already prevent this, but verify defensively.
                return "UNEXPECTED_GAP@" + i + "(" + String.format("%.2f", horiz) + ")";
            }
            if (horiz > 2.9) {
                return "GAP_TOO_WIDE@" + i + "(" + String.format("%.2f", horiz) + ")";
            }
            previous = wp;
        }
        return null;
    }

    private void onPathFailed(FakePlayer fp, NavState state) {
        UUID uuid = fp.getUuid();
        if (states.get(uuid) != state) return;

        // If the bot was already mid-walk on a previous path, a single failed recalculation just
        // keeps it going on the remaining waypoints instead of stopping dead in its tracks.
        state.failedRecalculations++;
        if (!state.waypoints.isEmpty()
                && state.waypointIndex < state.waypoints.size()
                && state.failedRecalculations <= maxRecalcFailures(state)) {
            return;
        }

        states.remove(uuid);
        stop(uuid, state);
        Config.debugPathfinding(diagnosticSummary(fp, state, "NO_PATH"));
        sendDebugChat(uuid, fp.getName(), "pathdebug-no-path");
        if (state.request.onPathFailure() != null) state.request.onPathFailure().run();
        else if (state.request.onCancel() != null) state.request.onCancel().run();
    }

    /**
     * Unconditionally gives up on the current navigation — used when the stuck→recalculate budget
     * ({@link Config#pathfindingMaxStuckCycles()}) is exhausted with zero real progress in between,
     * meaning the target is reachable-on-paper but not physically, and recalculating again would just
     * loop forever. Unlike {@link #onPathFailed}, this never "continues on the remaining waypoints" —
     * those waypoints are exactly what the bot has been failing to walk.
     */
    private void abandonNavigation(FakePlayer fp, NavState state) {
        UUID uuid = fp.getUuid();
        if (states.get(uuid) != state) return;

        states.remove(uuid);
        stop(uuid, state);
        Config.debugPathfinding(diagnosticSummary(fp, state, "ABANDONED"));
        sendDebugChat(uuid, fp.getName(), "pathdebug-unreachable");
        if (state.request.onPathFailure() != null) state.request.onPathFailure().run();
        else if (state.request.onCancel() != null) state.request.onCancel().run();
    }

    /**
     * Builds a single-line, grep-friendly diagnostic string for the server log — everything needed to
     * understand why a navigation stalled/failed after the fact: bot, owner, where it was stuck,
     * where it was headed, how far into the path it got, and which path options were active.
     */
    private String diagnosticSummary(FakePlayer fp, NavState state, String event) {
        Player bot = fp.getPlayer();
        Location current = bot != null ? bot.getLocation() : null;
        Location target = state.lastTarget;
        BotPathfinder.PathOptions options = PathfindingService.resolvePathOptions(fp, state.request.overrideOpts());

        double distRemaining = (current != null && target != null && current.getWorld() == target.getWorld())
                ? current.distance(target)
                : -1;

        return "event=" + event
                + " bot='" + fp.getName() + "'"
                + " owner=" + state.owner
                + " stuckCycle=" + state.totalStuckCycles + "/" + Config.pathfindingMaxStuckCycles()
                + " failedRecalcs=" + state.failedRecalculations
                + " waypoint=" + state.waypointIndex + "/" + state.waypoints.size()
                + " pos=" + fmt(current)
                + " target=" + fmt(target)
                + " distRemaining=" + (distRemaining < 0 ? "n/a" : String.format("%.2f", distRemaining))
                + " blocks{feet=" + blockAt(current, 0, 0, 0)
                + ",below=" + blockAt(current, 0, -1, 0)
                + ",head=" + blockAt(current, 0, 1, 0) + "}"
                + " opts{parkour=" + options.parkour()
                + ",break=" + options.breakBlocks()
                + ",place=" + options.placeBlocks()
                + ",avoidWater=" + options.avoidWater()
                + ",avoidLava=" + options.avoidLava() + "}";
    }

    private static String fmt(Location loc) {
        if (loc == null || loc.getWorld() == null) return "null";
        return loc.getWorld().getName() + String.format(":(%.1f,%.1f,%.1f)", loc.getX(), loc.getY(), loc.getZ());
    }

    private static String blockAt(Location origin, int dx, int dy, int dz) {
        if (origin == null || origin.getWorld() == null) return "?";
        return origin.clone().add(dx, dy, dz).getBlock().getType().name();
    }

    private int maxRecalcFailures(NavState state) {
        int max = state.request.maxNullPathRecalculations();
        return max <= 0 ? Integer.MAX_VALUE : max;
    }

    // ── Movement execution ───────────────────────────────────────────────────

    private void tickMovement(FakePlayer fp, NavState state) {
        UUID uuid = fp.getUuid();
        if (states.get(uuid) != state) return; // superseded/cancelled — task will be cancelled shortly

        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline() || fp.isFrozen() || fp.isActionsPaused()) return;

        Location current = bot.getLocation();
        Location finalTarget = state.lastTarget;
        if (finalTarget == null || finalTarget.getWorld() != current.getWorld()) {
            onPathFailed(fp, state);
            return;
        }

        if (hasArrived(current, finalTarget, state.request.arrivalDistance())) {
            arrive(fp, state, finalTarget);
            return;
        }

        // Recalculate if the (potentially moving) destination drifted too far from what we planned for.
        if (state.request.recalcDistance() > 0) {
            Location freshTarget = state.request.destinationSupplier().get();
            if (freshTarget != null
                    && freshTarget.getWorld() == finalTarget.getWorld()
                    && freshTarget.distance(finalTarget) > state.request.recalcDistance()) {
                computePath(fp, state, current, freshTarget);
                return;
            }
        }

        List<PathPosition> waypoints = state.waypoints;
        if (waypoints.isEmpty()) {
            onPathFailed(fp, state);
            return;
        }

        int index = advanceWaypointIndex(current, waypoints, state.waypointIndex);
        state.waypointIndex = index;
        renderDebugPath(uuid, state, current);
        PathPosition waypoint = waypoints.get(index);
        Location moveTarget =
                new Location(current.getWorld(), waypoint.getCenteredX(), waypoint.getY(), waypoint.getCenteredZ());

        if (clearingObstruction(fp, bot, state, moveTarget)) {
            NmsPlayerSpawner.setMovementForward(bot, 0f);
            return;
        }

        boolean gapJump = index > 0
                && horizontalDistance(waypoints.get(index - 1), waypoint) > 1.5
                && !hasArrivedHorizontally(current, moveTarget, 0.6);
        boolean stepUp = moveTarget.getY() - current.getY() >= 0.45;
        walkToward(fp, bot, current, moveTarget, finalTarget, gapJump || stepUp);

        trackStuck(fp, state, current);
    }

    private boolean hasArrived(Location current, Location target, double arrivalDistance) {
        double xz = PathfindingService.xzDist(current, target);
        double dy = Math.abs(current.getY() - target.getY());
        return xz <= arrivalDistance && dy < 1.25;
    }

    private boolean hasArrivedHorizontally(Location current, Location target, double distance) {
        return PathfindingService.xzDist(current, target) <= distance;
    }

    private int advanceWaypointIndex(Location current, List<PathPosition> waypoints, int index) {
        double waypointArrival = Config.pathfindingWaypointArrivalDistance();
        while (index < waypoints.size() - 1) {
            PathPosition wp = waypoints.get(index);
            double xz =
                    PathfindingService.xzDistRaw(current.getX(), current.getZ(), wp.getCenteredX(), wp.getCenteredZ());
            double dy = Math.abs(current.getY() - wp.getY());
            if (xz <= waypointArrival && dy < 1.25) {
                index++;
            } else {
                break;
            }
        }
        return index;
    }

    // ── Baritone-style path debug rendering ──────────────────────────────────

    private static final int DEBUG_RENDER_INTERVAL_TICKS = 4;
    private static final int DEBUG_MAX_WAYPOINTS_SHOWN = 40;

    private void renderDebugPath(UUID botUuid, NavState state, Location current) {
        Set<UUID> viewers = PathfindingDebugManager.getViewers(botUuid);
        if (viewers.isEmpty()) return;
        if (++state.debugRenderTick % DEBUG_RENDER_INTERVAL_TICKS != 0) return;

        List<PathPosition> waypoints = state.waypoints;
        int startIndex = state.waypointIndex;
        int endIndex = Math.min(waypoints.size(), startIndex + DEBUG_MAX_WAYPOINTS_SHOWN);
        int lastIndex = waypoints.size() - 1;

        for (UUID viewerUuid : viewers) {
            Player viewer = Bukkit.getPlayer(viewerUuid);
            if (viewer == null || !viewer.isOnline() || viewer.getWorld() != current.getWorld()) continue;

            for (int i = startIndex; i < endIndex; i++) {
                PathPosition wp = waypoints.get(i);
                Location loc = new Location(current.getWorld(), wp.getCenteredX(), wp.getY() + 0.15, wp.getCenteredZ());
                boolean isNext = i == startIndex;
                boolean isFinal = i == lastIndex;
                Color color = isFinal ? Color.RED : (isNext ? Color.ORANGE : Color.LIME);
                float size = isNext || isFinal ? 1.3f : 0.9f;
                viewer.spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0, new Particle.DustOptions(color, size));

                if (isFinal) {
                    for (int h = 1; h <= 3; h++) {
                        viewer.spawnParticle(
                                Particle.DUST,
                                loc.clone().add(0, h * 0.4, 0),
                                1,
                                0,
                                0,
                                0,
                                0,
                                new Particle.DustOptions(Color.RED, 1.0f));
                    }
                }
            }
        }
    }

    /**
     * Chat-based counterpart to the particle path debug: sent to every viewer subscribed to this
     * bot ({@link PathfindingDebugManager}) whenever something goes wrong (stuck, no path, watchdog,
     * mining stall), so the cause isn't just visible — it's explained.
     */
    public static void sendDebugChat(UUID botUuid, String botName, String langKey, String... args) {
        Set<UUID> viewers = PathfindingDebugManager.getViewers(botUuid);
        if (viewers.isEmpty()) return;

        String[] merged = new String[args.length + 2];
        merged[0] = "name";
        merged[1] = botName;
        System.arraycopy(args, 0, merged, 2, args.length);

        var message = Lang.get(langKey, merged);
        for (UUID viewerUuid : viewers) {
            Player viewer = Bukkit.getPlayer(viewerUuid);
            if (viewer != null && viewer.isOnline()) viewer.sendMessage(message);
        }
    }

    private double horizontalDistance(PathPosition a, PathPosition b) {
        double dx = a.getFlooredX() - b.getFlooredX();
        double dz = a.getFlooredZ() - b.getFlooredZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * If the next waypoint is currently blocked by a solid block (break-blocks path) or missing its
     * floor (place-blocks bridging), progresses clearing it. Returns {@code true} while clearing is
     * still in progress (movement should hold).
     */
    private boolean clearingObstruction(FakePlayer fp, Player bot, NavState state, Location moveTarget) {
        BotPathfinder.PathOptions options = PathfindingService.resolvePathOptions(fp, state.request.overrideOpts());
        double xz = PathfindingService.xzDist(bot.getLocation(), moveTarget);
        if (xz > 1.6) return false; // not adjacent yet — nothing to clear right now

        if (options.breakBlocks()) {
            Block feet = moveTarget.getBlock();
            Block head = moveTarget.clone().add(0, 1, 0).getBlock();
            if (feet.getType().isSolid()) {
                return !NavBlockOps.tickBreak(bot, feet, state.breakProgress);
            }
            if (head.getType().isSolid()) {
                return !NavBlockOps.tickBreak(bot, head, state.breakProgress);
            }
        }
        if (options.placeBlocks()) {
            Block below = moveTarget.clone().add(0, -1, 0).getBlock();
            if (below.getType().isAir()) {
                return !NavBlockOps.tickPlace(bot, moveTarget.clone().add(0, -1, 0), state.placeProgress);
            }
        }
        state.breakProgress[0] = 0;
        state.placeProgress[0] = 0;
        return false;
    }

    private void walkToward(
            FakePlayer fp, Player bot, Location current, Location moveTarget, Location finalTarget, boolean jump) {
        UUID uuid = fp.getUuid();
        Location face = current.clone();
        double dx = moveTarget.getX() - current.getX();
        double dz = moveTarget.getZ() - current.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        face.setYaw(yaw);
        bot.setRotation(yaw, current.getPitch());
        NmsPlayerSpawner.setHeadYaw(bot, yaw);

        boolean sprint = PathfindingService.xzDist(current, finalTarget) > Config.pathfindingSprintDistance();
        bot.setSprinting(sprint);
        NmsPlayerSpawner.setMovementForward(bot, 1.0f);
        NmsPlayerSpawner.setJumping(bot, jump);
        if (jump) manager.requestNavJump(uuid);
    }

    private void trackStuck(FakePlayer fp, NavState state, Location current) {
        int stuckLimit = Config.pathfindingStuckTicks();
        if (state.lastPos != null && state.lastPos.getWorld() == current.getWorld()) {
            double moved = PathfindingService.xzDist(current, state.lastPos);
            if (moved < Config.pathfindingStuckThreshold()) {
                state.stuckTicks++;
            } else {
                state.stuckTicks = 0;
                // Genuine movement happened — the obstruction that caused earlier stuck cycles (if
                // any) is no longer in the way, so the give-up budget can reset too.
                state.totalStuckCycles = 0;
            }
        }
        state.lastPos = current.clone();

        // Nudge with a jump partway through the window before committing to a full recalculation —
        // covers the common case of being wedged against a lip/leaf/slab that just needs a hop, not
        // an actually-bad route. Recalculating against unchanged terrain tends to just return the
        // same path and get stuck again immediately.
        int nudgeAt = Math.max(1, stuckLimit / 2);
        if (state.stuckTicks == nudgeAt) {
            manager.requestNavJump(fp.getUuid());
        }

        if (state.stuckTicks >= stuckLimit) {
            state.stuckTicks = 0;
            state.totalStuckCycles++;

            if (state.totalStuckCycles >= Config.pathfindingMaxStuckCycles()) {
                abandonNavigation(fp, state);
                return;
            }

            Config.debugPathfinding(diagnosticSummary(fp, state, "STUCK"));
            sendDebugChat(
                    fp.getUuid(), fp.getName(), "pathdebug-stuck", "cycle", String.valueOf(state.totalStuckCycles));
            computePath(fp, state, current, state.lastTarget);
        }
    }

    private void arrive(FakePlayer fp, NavState state, Location finalTarget) {
        UUID uuid = fp.getUuid();
        states.remove(uuid);
        stop(uuid, state);

        Player bot = fp.getPlayer();
        Location lock = state.request.lockOnArrival();
        if (bot != null && bot.isOnline() && lock != null) {
            bot.setRotation(lock.getYaw(), lock.getPitch());
            NmsPlayerSpawner.setHeadYaw(bot, lock.getYaw());
        }
        if (state.request.onArrive() != null) state.request.onArrive().run();
    }

    private void stop(UUID uuid, NavState state) {
        if (state.taskId >= 0) {
            FppScheduler.cancelTask(state.taskId);
            state.taskId = -1;
        }
        manager.unlockNavigation(uuid);
        manager.clearNavJump(uuid);
        FakePlayer fp = manager.getByUuid(uuid);
        Player bot = fp != null ? fp.getPlayer() : null;
        if (bot != null && bot.isOnline()) {
            NmsPlayerSpawner.setMovementForward(bot, 0f);
            NmsPlayerSpawner.setJumping(bot, false);
            bot.setSprinting(false);
        }
    }

    private static final class NavState {
        final PathfindingService.NavigationRequest request;
        final PathfindingService.Owner owner;
        volatile List<PathPosition> waypoints = List.of();
        volatile int waypointIndex;
        volatile int taskId = -1;
        volatile Location lastTarget;
        volatile Location lastPos;
        volatile int stuckTicks;
        volatile int totalStuckCycles;
        volatile int failedRecalculations;
        volatile int debugRenderTick;
        final int[] breakProgress = {0};
        final int[] placeProgress = {0};

        NavState(PathfindingService.NavigationRequest request) {
            this.request = request;
            this.owner = request.owner();
        }
    }
}
