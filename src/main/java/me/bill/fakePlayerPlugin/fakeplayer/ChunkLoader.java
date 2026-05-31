package me.bill.fakePlayerPlugin.fakeplayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.api.event.FppBotChunkLoadEvent;
import me.bill.fakePlayerPlugin.api.impl.FppBotImpl;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.util.FppScheduler;

public final class ChunkLoader {

    private final FakePlayerPlugin plugin;
    private final FakePlayerManager manager;

    private final Map<UUID, BotChunkState> states = new HashMap<>();

    public ChunkLoader(FakePlayerPlugin plugin, FakePlayerManager manager) {
        this.plugin = plugin;
        this.manager = manager;

        long interval = Math.max(1L, Config.chunkLoadingUpdateInterval());
        FppScheduler.runSyncRepeating(plugin, this::tick, interval, interval);
    }

    private void tick() {
        if (!Config.chunkLoadingEnabled()) {
            if (hasStates()) releaseAll();
            return;
        }

        int globalRadius = Config.chunkLoadingRadius();
        if (globalRadius == 0) {

            if (hasStates()) releaseAll();
            return;
        }

        int activeCount = manager.getActivePlayers().size();
        int massThreshold = Config.chunkLoadingMassDisableThreshold();
        if (massThreshold > 0 && activeCount >= massThreshold) {
            if (hasStates()) releaseAll();
            return;
        }

        Set<UUID> activeUuids = new HashSet<>();

        for (FakePlayer fp : manager.getActivePlayers()) {
            UUID botId = fp.getUuid();
            activeUuids.add(botId);

            if (me.bill.fakePlayerPlugin.fakeplayer.NmsPlayerSpawner.isFoliaServer()) {
                Location loc = fp.getPlayer() != null ? fp.getPlayer().getLocation() : fp.getSpawnLocation();
                if (loc.getWorld() != null) {
                    plugin.getServer().getRegionScheduler().run(plugin, loc, task -> tickBot(fp, globalRadius));
                    continue;
                }
            }

            tickBot(fp, globalRadius);
        }

        synchronized (this) {
            states.entrySet().removeIf(entry -> {
                if (activeUuids.contains(entry.getKey())) return false;
                releaseState(entry.getValue());
                return true;
            });
        }
    }

    private void tickBot(FakePlayer fp, int globalRadius) {
        synchronized (this) {
            UUID botId = fp.getUuid();

            int botR = fp.getChunkLoadRadius();
            int radius = (botR < 0) ? globalRadius : Math.min(botR, globalRadius);

            if (radius == 0) {
                BotChunkState existing = states.remove(botId);
                if (existing != null) releaseState(existing);
                return;
            }

            Location pos = resolvePosition(fp);
            if (pos == null || pos.getWorld() == null) return;

            World world = pos.getWorld();
            String wName = world.getName();
            int cx = pos.getBlockX() >> 4;
            int cz = pos.getBlockZ() >> 4;

            int[] clamped = clampToWorldBorder(world, cx, cz);
            cx = clamped[0];
            cz = clamped[1];

            BotChunkState state = states.get(botId);

            if (state != null && state.worldName.equals(wName) && state.cx == cx && state.cz == cz) {
                return;
            }

            if (state != null && !state.worldName.equals(wName)) {
                releaseState(state);
                state = null;
            }

            List<long[]> spiral = buildSpiral(cx, cz, radius, world);
            Set<Long> desired = new HashSet<>(spiral.size());
            for (long[] coord : spiral) desired.add(packKey((int) coord[0], (int) coord[1]));

            if (state == null) {
                state = new BotChunkState(wName, cx, cz, new HashSet<>());
                states.put(botId, state);
            }

            for (long[] coord : spiral) {
                long key = packKey((int) coord[0], (int) coord[1]);
                if (state.keys.add(key)) {
                    addTicketAndCallEvent(fp, world, (int) coord[0], (int) coord[1]);
                }
            }

            Iterator<Long> it = state.keys.iterator();
            while (it.hasNext()) {
                long key = it.next();
                if (!desired.contains(key)) {
                    removeTicket(world, unpackX(key), unpackZ(key));
                    it.remove();
                }
            }

            state.cx = cx;
            state.cz = cz;
            state.worldName = wName;
        }
    }

    public void releaseForBot(FakePlayer fp) {
        synchronized (this) {
            BotChunkState state = states.remove(fp.getUuid());
            if (state != null) releaseState(state);
        }
    }

    public void releaseAll() {
        synchronized (this) {
            states.values().forEach(this::releaseState);
            states.clear();
        }
    }

    private boolean hasStates() {
        synchronized (this) {
            return !states.isEmpty();
        }
    }

    @SuppressWarnings("unused")
    public int totalTickets() {
        synchronized (this) {
            return states.values().stream().mapToInt(s -> s.keys.size()).sum();
        }
    }

    private static Location resolvePosition(FakePlayer fp) {
        Location loc = fp.getLiveLocation();
        if (loc != null && loc.getWorld() != null) return loc;

        Player player = fp.getPlayer();
        if (player != null && player.getWorld() != null) return player.getLocation();

        loc = fp.getSpawnLocation();
        if (loc != null && loc.getWorld() != null) return loc;
        return null;
    }

    private static List<long[]> buildSpiral(int cx, int cz, int radius, World world) {
        int diameter = radius * 2 + 1;
        List<long[]> result = new ArrayList<>(diameter * diameter);

        result.add(new long[] {cx, cz});
        for (int r = 1; r <= radius; r++) {

            for (int dx = -r; dx <= r; dx++) addIfInBorder(result, cx + dx, cz - r, world);

            for (int dz = -r + 1; dz <= r; dz++) addIfInBorder(result, cx + r, cz + dz, world);

            for (int dx = r - 1; dx >= -r; dx--) addIfInBorder(result, cx + dx, cz + r, world);

            for (int dz = r - 1; dz >= -r + 1; dz--) addIfInBorder(result, cx - r, cz + dz, world);
        }
        return result;
    }

    private static void addIfInBorder(List<long[]> list, int x, int z, World world) {
        double borderRadius = world.getWorldBorder().getSize() / 2.0;
        double bx = world.getWorldBorder().getCenter().getX();
        double bz = world.getWorldBorder().getCenter().getZ();
        double chunkCenterX = x * 16.0 + 8;
        double chunkCenterZ = z * 16.0 + 8;
        if (Math.abs(chunkCenterX - bx) <= borderRadius && Math.abs(chunkCenterZ - bz) <= borderRadius) {
            list.add(new long[] {x, z});
        }
    }

    private static int[] clampToWorldBorder(World world, int cx, int cz) {
        double borderHalf = world.getWorldBorder().getSize() / 2.0;
        double bx = world.getWorldBorder().getCenter().getX();
        double bz = world.getWorldBorder().getCenter().getZ();
        double minChunkX = Math.floor((bx - borderHalf) / 16.0);
        double maxChunkX = Math.floor((bx + borderHalf) / 16.0);
        double minChunkZ = Math.floor((bz - borderHalf) / 16.0);
        double maxChunkZ = Math.floor((bz + borderHalf) / 16.0);
        return new int[] {
            (int) Math.max(minChunkX, Math.min(maxChunkX, cx)), (int) Math.max(minChunkZ, Math.min(maxChunkZ, cz))
        };
    }

    private void releaseState(BotChunkState state) {
        World world = Bukkit.getWorld(state.worldName);
        if (world == null || state.keys.isEmpty()) {
            state.keys.clear();
            return;
        }
        for (long key : state.keys) {
            removeTicket(world, unpackX(key), unpackZ(key));
        }
        state.keys.clear();
    }

    private void addTicketAndCallEvent(FakePlayer fp, World world, int chunkX, int chunkZ) {
        Runnable runnable = () -> {
            world.addPluginChunkTicket(chunkX, chunkZ, plugin);
            if (world.isChunkLoaded(chunkX, chunkZ)) {
                var chunkEvt = new FppBotChunkLoadEvent(new FppBotImpl(fp), world.getChunkAt(chunkX, chunkZ));
                Bukkit.getPluginManager().callEvent(chunkEvt);
            }
        };
        runnable.run();
    }

    private void removeTicket(World world, int chunkX, int chunkZ) {
        Runnable runnable = () -> world.removePluginChunkTicket(chunkX, chunkZ, plugin);
        runnable.run();
    }

    private static long packKey(int x, int z) {
        return ((long) x & 0xFFFFFFFFL) | (((long) z & 0xFFFFFFFFL) << 32);
    }

    private static int unpackX(long key) {
        return (int) (key & 0xFFFFFFFFL);
    }

    private static int unpackZ(long key) {
        return (int) ((key >>> 32) & 0xFFFFFFFFL);
    }

    private static final class BotChunkState {
        String worldName;
        int cx, cz;
        Set<Long> keys;

        BotChunkState(String worldName, int cx, int cz, Set<Long> keys) {
            this.worldName = worldName;
            this.cx = cx;
            this.cz = cz;
            this.keys = keys;
        }
    }
}
