package me.bill.fakePlayerPlugin.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

public final class FppScheduler {

    private static final AtomicInteger NEXT_TASK_ID = new AtomicInteger(1);
    private static final Map<Integer, ScheduledTask> TASKS = new ConcurrentHashMap<>();

    private FppScheduler() {}

    public static void runAtEntity(Plugin plugin, Entity entity, Runnable runnable) {
        if (entity == null) return;
        entity.getScheduler().run(plugin, ignored -> runnable.run(), null);
    }

    public static int runAtEntityRepeatingWithId(
            Plugin plugin, Entity entity, Runnable runnable, long delayTicks, long periodTicks) {
        if (entity == null) return -1;
        ScheduledTask task = entity.getScheduler()
                .runAtFixedRate(
                        plugin,
                        ignored -> runnable.run(),
                        null,
                        normalizeDelay(delayTicks),
                        normalizePeriod(periodTicks));
        return register(task);
    }

    public static int runAtEntityLaterWithId(Plugin plugin, Entity entity, Runnable runnable, long delayTicks) {
        if (entity == null) return -1;
        ScheduledTask task =
                entity.getScheduler().runDelayed(plugin, ignored -> runnable.run(), null, normalizeDelay(delayTicks));
        return register(task);
    }

    public static void runAtLocation(Plugin plugin, Location location, Runnable runnable) {
        if (location == null || location.getWorld() == null) {
            runSync(plugin, runnable);
            return;
        }
        Bukkit.getRegionScheduler().run(plugin, location, ignored -> runnable.run());
    }

    public static void runAtChunk(Plugin plugin, World world, int chunkX, int chunkZ, Runnable runnable) {
        if (world == null) {
            runSync(plugin, runnable);
            return;
        }
        Bukkit.getRegionScheduler().run(plugin, world, chunkX, chunkZ, ignored -> runnable.run());
    }

    public static void runSync(Plugin plugin, Runnable runnable) {
        Bukkit.getGlobalRegionScheduler().run(plugin, ignored -> runnable.run());
    }

    public static void runSyncRepeating(Plugin plugin, Runnable runnable, long delayTicks, long periodTicks) {
        Bukkit.getGlobalRegionScheduler()
                .runAtFixedRate(
                        plugin, ignored -> runnable.run(), normalizeDelay(delayTicks), normalizePeriod(periodTicks));
    }

    public static int runSyncLaterWithId(Plugin plugin, Runnable runnable, long delayTicks) {
        ScheduledTask task = Bukkit.getGlobalRegionScheduler()
                .runDelayed(plugin, ignored -> runnable.run(), normalizeDelay(delayTicks));
        return register(task);
    }

    public static int runSyncRepeatingWithId(Plugin plugin, Runnable runnable, long delayTicks, long periodTicks) {
        return runSyncRepeatingWithId(plugin, null, runnable, delayTicks, periodTicks);
    }

    public static int runSyncRepeatingWithId(
            Plugin plugin, org.bukkit.entity.Entity entity, Runnable runnable, long delayTicks, long periodTicks) {
        if (isFolia() && entity != null) {
            ScheduledTask task = entity.getScheduler()
                    .runAtFixedRate(
                            plugin,
                            ignored -> runnable.run(),
                            null,
                            normalizeDelay(delayTicks),
                            normalizePeriod(periodTicks));
            return register(task);
        }
        ScheduledTask task = Bukkit.getGlobalRegionScheduler()
                .runAtFixedRate(
                        plugin, ignored -> runnable.run(), normalizeDelay(delayTicks), normalizePeriod(periodTicks));
        return register(task);
    }

    public static int runSyncRepeatingWithIdAtEntity(
            Plugin plugin, Entity entity, Runnable runnable, long delayTicks, long periodTicks) {
        if (entity == null) return -1;
        ScheduledTask task = entity.getScheduler()
                .runAtFixedRate(
                        plugin,
                        ignored -> runnable.run(),
                        null,
                        normalizeDelay(delayTicks),
                        normalizePeriod(periodTicks));
        return register(task);
    }

    private static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.ThreadedRegionizer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static void runSyncLater(Plugin plugin, Runnable runnable, long delayTicks) {
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, ignored -> runnable.run(), normalizeDelay(delayTicks));
    }

    public static void runAsync(Plugin plugin, Runnable runnable) {
        Bukkit.getAsyncScheduler().runNow(plugin, ignored -> runnable.run());
    }

    public static void teleportAsync(Entity entity, Location dest) {
        if (entity == null || dest == null) return;
        entity.teleportAsync(dest, PlayerTeleportEvent.TeleportCause.PLUGIN);
    }

    public static void cancelTask(int taskId) {
        ScheduledTask task = TASKS.remove(taskId);
        if (task != null) {
            task.cancel();
        }
    }

    private static int register(ScheduledTask task) {
        if (task == null) return -1;
        int id = NEXT_TASK_ID.getAndIncrement();
        TASKS.put(id, task);
        return id;
    }

    private static long normalizeDelay(long delayTicks) {
        return Math.max(1L, delayTicks);
    }

    private static long normalizePeriod(long periodTicks) {
        return Math.max(1L, periodTicks);
    }
}
