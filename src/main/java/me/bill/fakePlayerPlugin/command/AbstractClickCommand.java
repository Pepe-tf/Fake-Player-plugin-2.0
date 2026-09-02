package me.bill.fakePlayerPlugin.command;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.api.FppClickMode;
import me.bill.fakePlayerPlugin.api.event.FppBotTaskEvent;
import me.bill.fakePlayerPlugin.api.impl.FppApiImpl;
import me.bill.fakePlayerPlugin.fakeplayer.BotNavUtil;
import me.bill.fakePlayerPlugin.fakeplayer.BotPathfinder;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.fakeplayer.NmsPlayerSpawner;
import me.bill.fakePlayerPlugin.fakeplayer.PathfindingService;
import me.bill.fakePlayerPlugin.util.FppScheduler;

/**
 * Shared machinery behind {@code /fpp left-click} and {@code /fpp right-click}: task/state bookkeeping,
 * stand-location search, aim geometry, and the {@code --stop}/save-restore/tab-complete lifecycle. Each
 * subclass supplies its own target resolution and per-tick action (block-destroy-progress vs.
 * use-item packets) - those differ too much between mining/attacking and interacting to usefully share.
 */
public abstract class AbstractClickCommand implements FppCommand {

    protected final FakePlayerPlugin plugin;
    protected final FakePlayerManager manager;
    protected final PathfindingService pathfinding;

    protected final Map<UUID, Integer> clickTasks = new ConcurrentHashMap<>();
    protected final Map<UUID, ClickState> clickStates = new ConcurrentHashMap<>();
    protected final Map<UUID, FppClickMode> clickModes = new ConcurrentHashMap<>();

    protected AbstractClickCommand(FakePlayerPlugin plugin, FakePlayerManager manager, PathfindingService pathfinding) {
        this.plugin = plugin;
        this.manager = manager;
        this.pathfinding = pathfinding;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  Hooks subclasses must (or may) supply
    // ─────────────────────────────────────────────────────────────────────────────

    /** Reach distance (blocks) used for target resolution and stand-location search. */
    protected abstract double clickReach();

    /** The pathfinding "owner" tag used while walking to a vantage point for this action. */
    protected abstract PathfindingService.Owner navOwner();

    /** Arrival distance passed to the navigation request when walking to a target. */
    protected abstract double navArrivalDistance();

    /** Task-event/debug-log name for this action, e.g. {@code "left-click"} / {@code "right-click"}. */
    protected abstract String taskName();

    protected abstract void dbg(String msg);

    /** Starts (or restarts) the click loop for an already-resolved bot/mode. Used by the public API and save/restore. */
    public abstract boolean click(FakePlayer fp, FppClickMode mode);

    /** Hook: extra cleanup run before the task/state is torn down, while the bot is still known-online. */
    protected void onBeforeStop(FakePlayer fp, Player bot, @Nullable ClickState state) {}

    /** Hook: extra cleanup run after the task/state is torn down, while the bot is still known-online. */
    protected void onAfterStop(Player bot) {}

    // ─────────────────────────────────────────────────────────────────────────────
    //  Aim geometry
    // ─────────────────────────────────────────────────────────────────────────────

    protected static boolean isSelfTarget(Player bot, Object target) {
        return bot != null
                && target instanceof Entity entity
                && entity.getUniqueId().equals(bot.getUniqueId());
    }

    @Nullable
    protected static Location getTargetLocation(Player bot, Object target) {
        if (target instanceof Block b) {
            return new Location(bot.getWorld(), b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5);
        } else if (target instanceof Entity e) {
            return e.getLocation().clone();
        }
        return null;
    }

    /** Computes the geometric center of a specific block face. */
    @Nullable
    protected static org.bukkit.util.Vector computeFaceCenter(Object target, BlockFace face) {
        if (!(target instanceof Block b) || face == null) return null;
        double cx = b.getX() + 0.5;
        double cy = b.getY() + 0.5;
        double cz = b.getZ() + 0.5;
        return switch (face) {
            case UP -> new org.bukkit.util.Vector(cx, b.getY() + 1.0, cz);
            case DOWN -> new org.bukkit.util.Vector(cx, b.getY(), cz);
            case NORTH -> new org.bukkit.util.Vector(cx, cy, b.getZ());
            case SOUTH -> new org.bukkit.util.Vector(cx, cy, b.getZ() + 1.0);
            case WEST -> new org.bukkit.util.Vector(b.getX(), cy, cz);
            case EAST -> new org.bukkit.util.Vector(b.getX() + 1.0, cy, cz);
            default -> new org.bukkit.util.Vector(cx, cy, cz);
        };
    }

    protected static Location faceTowardTarget(Location botLoc, Object target) {
        return faceTowardTarget(botLoc, target, null);
    }

    protected static Location faceTowardTarget(
            Location botLoc, Object target, @Nullable org.bukkit.util.Vector faceCenter) {
        double tx, ty, tz;
        if (faceCenter != null) {
            tx = faceCenter.getX();
            ty = faceCenter.getY();
            tz = faceCenter.getZ();
        } else if (target instanceof Block b) {
            tx = b.getX() + 0.5;
            ty = b.getY() + 0.5;
            tz = b.getZ() + 0.5;
        } else if (target instanceof Entity e) {
            Location eLoc = e.getLocation();
            tx = eLoc.getX() + 0.5;
            ty = eLoc.getY() + 1.0;
            tz = eLoc.getZ() + 0.5;
        } else {
            return botLoc.clone();
        }
        double dx = tx - botLoc.getX();
        double dy = ty - (botLoc.getY() + 1.62);
        double dz = tz - botLoc.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        Location result = botLoc.clone();
        result.setYaw(yaw);
        result.setPitch(pitch);
        return result;
    }

    /**
     * Re-faces the bot toward its commanded target (exact aim point, else target centre). Rotates via
     * NMS {@code absSnapRotationTo} - NOT {@code CraftPlayer#setRotation}, which does a connection
     * teleport that arms {@code awaitingPositionFromClient} and blocks all block interactions until
     * confirmed.
     */
    protected final void refreshAim(Player bot, ClickState state) {
        if (state.aimTarget == null && state.aimPoint == null) return;
        Location faceLoc = faceTowardTarget(bot.getLocation(), state.aimTarget, state.aimPoint);
        ((CraftPlayer) bot).getHandle().absSnapRotationTo(faceLoc.getYaw(), faceLoc.getPitch());
        NmsPlayerSpawner.setHeadYaw(bot, faceLoc.getYaw());
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  Stand-location search / navigation
    // ─────────────────────────────────────────────────────────────────────────────

    @Nullable
    protected final Location findStandLocationNearTarget(World world, Location targetLoc) {
        int tx = targetLoc.getBlockX(), ty = targetLoc.getBlockY(), tz = targetLoc.getBlockZ();
        for (int r = 1; r <= 4; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) < r && Math.abs(dz) < r) continue;
                    int cx = tx + dx, cz = tz + dz;
                    for (int dy : new int[] {0, -1, 1}) {
                        int cy = ty + dy;
                        if (BotNavUtil.walkable(world, cx, cy, cz)) {
                            Location loc = new Location(world, cx + 0.5, cy, cz + 0.5);
                            double dist = loc.distance(targetLoc);
                            if (dist <= clickReach() - 1.5) {
                                return faceTowardTarget(loc, targetLoc, null);
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Finds a walkable stand spot around {@code center} (e.g. the command sender's own location) from
     * which the target is within reach. Includes the centre block itself (r=0) so the bot can stand
     * exactly where the player is standing.
     */
    @Nullable
    protected final Location findStandLocationNear(World world, Location center, Location targetLoc) {
        int ox = center.getBlockX(), oy = center.getBlockY(), oz = center.getBlockZ();
        for (int r = 0; r <= 4; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (r > 0 && Math.abs(dx) < r && Math.abs(dz) < r) continue;
                    int cx = ox + dx, cz = oz + dz;
                    for (int dy : new int[] {0, -1, 1}) {
                        int cy = oy + dy;
                        if (BotNavUtil.walkable(world, cx, cy, cz)) {
                            Location loc = new Location(world, cx + 0.5, cy, cz + 0.5);
                            if (loc.distance(targetLoc) <= clickReach() - 0.5) {
                                return faceTowardTarget(loc, targetLoc, null);
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Resolves where the bot should stand to reach an out-of-reach target. Prefers the sender's own
     * standing location - a vantage the target is provably aim-able from, since the player just aimed
     * at it from there - and falls back to searching around the target itself.
     */
    @Nullable
    protected final Location resolveStandLocation(World world, CommandSender sender, Location targetLoc) {
        if (sender instanceof Player player && player.getWorld() == world) {
            Location atPlayer = findStandLocationNear(world, player.getLocation(), targetLoc);
            if (atPlayer != null) return atPlayer;
        }
        return findStandLocationNearTarget(world, targetLoc);
    }

    protected final void startNavigation(FakePlayer fp, Location dest, Runnable onArrive) {
        BotPathfinder.PathOptions baseOpts = PathfindingService.resolvePathOptions(fp);
        BotPathfinder.PathOptions opts = new BotPathfinder.PathOptions(
                fp.isNavParkour(), true, fp.isNavPlaceBlocks(), baseOpts.avoidWater(), baseOpts.avoidLava());
        pathfinding.navigate(
                fp,
                new PathfindingService.NavigationRequest(
                        navOwner(),
                        () -> dest,
                        navArrivalDistance(),
                        0.0,
                        Integer.MAX_VALUE,
                        onArrive,
                        null,
                        null,
                        null,
                        opts));
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  Lifecycle: stop / cleanup / save-restore
    // ─────────────────────────────────────────────────────────────────────────────

    protected final void cancelAll(UUID botUuid) {
        // Only release the nav slot if this action's own walk-to-vantage currently owns it - another
        // concurrently running task (move, find, PVE) may hold it instead, and resetting *this* bot's
        // click task must never cancel someone else's navigation.
        if (pathfinding.isNavigating(botUuid, navOwner())) {
            pathfinding.cancel(botUuid);
        }
        stopClicking(botUuid);
        FakePlayer fp = manager.getByUuid(botUuid);
        if (fp != null) {
            Player bot = fp.getPlayer();
            if (bot != null && bot.isOnline()) {
                NmsPlayerSpawner.setMovementForward(bot, 0f);
                NmsPlayerSpawner.setJumping(bot, false);
                bot.setSprinting(false);
            }
        }
    }

    public final void stopClicking(UUID botUuid) {
        stopClicking(botUuid, true);
    }

    public final void stopClicking(UUID botUuid, boolean clearState) {
        FakePlayer fp = manager.getByUuid(botUuid);
        if (fp != null) {
            dbg("stop: bot=" + fp.getDisplayName() + " clearState=" + clearState);
            FppApiImpl.fireTaskEvent(fp, taskName(), FppBotTaskEvent.Action.STOP);
            Player bot = fp.getPlayer();
            if (bot != null && bot.isOnline()) {
                onBeforeStop(fp, bot, clickStates.get(botUuid));
            }
        }
        Integer taskId = clickTasks.remove(botUuid);
        if (taskId != null) FppScheduler.cancelTask(taskId);
        manager.unlockAction(botUuid);
        if (clearState) {
            clickStates.remove(botUuid);
            clickModes.remove(botUuid);
        }
        if (fp != null) {
            Player bot = fp.getPlayer();
            if (bot != null && bot.isOnline()) {
                onAfterStop(bot);
            }
        }
    }

    public final void stopAll() {
        pathfinding.cancelAll(navOwner());
        new HashSet<>(clickTasks.keySet()).forEach(this::cleanupBot);
    }

    public final void cleanupBot(UUID botUuid) {
        cancelAll(botUuid);
    }

    public final boolean isClicking(UUID botUuid) {
        return clickTasks.containsKey(botUuid);
    }

    /** Snapshot of the bot's active click task for persistence, or null when it isn't clicking. */
    @Nullable
    public final SavedClickTask getSavedTask(UUID botUuid) {
        FppClickMode mode = clickModes.get(botUuid);
        if (mode == null || mode == FppClickMode.STOP || !clickTasks.containsKey(botUuid)) return null;
        FakePlayer fp = manager.getByUuid(botUuid);
        Player bot = fp != null ? fp.getPlayer() : null;
        if (bot == null) return null;
        ClickState state = clickStates.get(botUuid);
        return new SavedClickTask(mode.name(), bot.getWorld().getName(), state != null ? state.aimPoint : null);
    }

    /**
     * Resumes a persisted click task after a restart: re-aims the bot at the saved point (when one was
     * locked) and restarts the click loop via the normal self-view resolution.
     */
    public final void resumeSavedTask(FakePlayer fp, String modeName, @Nullable org.bukkit.util.Vector aimPoint) {
        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline()) return;
        FppClickMode mode;
        try {
            mode = FppClickMode.valueOf(modeName);
        } catch (IllegalArgumentException | NullPointerException e) {
            mode = FppClickMode.HOLD;
        }
        if (mode == FppClickMode.STOP) return;
        if (aimPoint != null) {
            Location faceLoc = faceTowardTarget(bot.getLocation(), null, aimPoint);
            ((CraftPlayer) bot).getHandle().absSnapRotationTo(faceLoc.getYaw(), faceLoc.getPitch());
            NmsPlayerSpawner.setHeadYaw(bot, faceLoc.getYaw());
        }
        dbg("resume: bot=" + fp.getDisplayName() + " mode=" + mode + " aim=" + aimPoint);
        click(fp, mode);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  Tab completion - identical shape for both commands: <bot> [--once|--repeat|--hold|--stop] | --stop
    // ─────────────────────────────────────────────────────────────────────────────

    @Override
    public final List<String> tabComplete(CommandSender sender, String[] args) {
        if (!canUse(sender)) return List.of();

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            if ("--stop".startsWith(prefix)) out.add("--stop");
            for (FakePlayer fp : manager.getActivePlayers()) {
                if (fp.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(fp.getName());
            }
            return out;
        }

        if (args.length == 2 && !args[0].equalsIgnoreCase("--stop")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            if ("--once".startsWith(prefix)) out.add("--once");
            if ("--repeat".startsWith(prefix)) out.add("--repeat");
            if ("--hold".startsWith(prefix)) out.add("--hold");
            if ("--stop".startsWith(prefix)) out.add("--stop");
            return out;
        }

        return List.of();
    }

    /** Base per-bot click-loop state. Subclasses extend this with their own action-specific fields. */
    protected static class ClickState {
        Object target;
        // The commanded target + exact aim point, set once at start and never overwritten by per-tick
        // picks. Used to re-aim the head every tick/pulse.
        Object aimTarget;
        org.bukkit.util.Vector aimPoint;
    }
}
