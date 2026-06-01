package me.bill.fakePlayerPlugin.network;

import java.util.UUID;

import org.bukkit.Bukkit;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.database.DatabaseManager;
import me.bill.fakePlayerPlugin.database.NetworkDatabase;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.fakeplayer.RemoteBotEntry;
import me.bill.fakePlayerPlugin.util.FppLogger;
import me.bill.fakePlayerPlugin.util.FppScheduler;

/**
 * Publishes this server's live bot count + real player count into the shared
 * {@code fpp_server_heartbeat} table and periodically refreshes the cross-server
 * bot list from {@code fpp_network_bots}.
 *
 * <p>Runs only in {@code NETWORK} mode with MySQL enabled.
 */
public final class NetworkHeartbeatManager {

    private final FakePlayerPlugin plugin;
    private final FakePlayerManager manager;
    private final NetworkDatabase netDb;

    private static final long HEARTBEAT_INTERVAL_TICKS = 100L; // 5 s
    private static final long PRUNE_INTERVAL_TICKS = 1200L; // 60 s
    private static final long STALE_THRESHOLD_MS = 60000L; // 60 s

    private volatile boolean started = false;
    private volatile int hbTask = -1;
    private volatile int pruneTask = -1;

    public NetworkHeartbeatManager(FakePlayerPlugin plugin, FakePlayerManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        DatabaseManager db = plugin.getDatabaseManager();
        if (db != null && db.isMysql()) {
            var conn = db.getConnection();
            Config.debugDbConn("NetworkHeartbeatManager: creating NetworkDatabase with connection=" + conn + " (valid="
                    + db.isConnectionValid() + ")");
            this.netDb = new NetworkDatabase(conn, true);
        } else {
            Config.debugDbConn("NetworkHeartbeatManager: skipping NetworkDatabase (db=" + db + ", mysql="
                    + (db != null && db.isMysql()) + ")");
            this.netDb = null;
        }
    }

    public void start() {
        if (started) return;
        if (netDb == null) {
            Config.debugNetwork("[NetworkHeartbeat] disabled (no MySQL DB).");
            return;
        }
        if (!Config.isNetworkMode()) {
            Config.debugNetwork("[NetworkHeartbeat] disabled (not in NETWORK mode).");
            return;
        }
        started = true;
        Config.debugNetwork("[NetworkHeartbeat] started — interval=" + HEARTBEAT_INTERVAL_TICKS + "t.");

        hbTask = FppScheduler.runSyncRepeatingWithId(
                plugin, this::tick, HEARTBEAT_INTERVAL_TICKS, HEARTBEAT_INTERVAL_TICKS);

        pruneTask =
                FppScheduler.runSyncRepeatingWithId(plugin, this::prune, PRUNE_INTERVAL_TICKS, PRUNE_INTERVAL_TICKS);

        // Initial refresh from DB
        refreshRemoteCache();
    }

    public void stop() {
        if (!started) return;
        started = false;
        if (hbTask != -1) FppScheduler.cancelTask(hbTask);
        if (pruneTask != -1) FppScheduler.cancelTask(pruneTask);
        if (netDb != null) netDb.shutdown();
        Config.debugNetwork("[NetworkHeartbeat] stopped.");
    }

    private void tick() {
        try {
            DatabaseManager db = plugin.getDatabaseManager();
            if (db == null || !db.isConnectionValid()) {
                FppLogger.error("[NetworkHeartbeat] tick: Database connection is invalid/closed - skipping heartbeat");
                return;
            }

            int botCount = manager.getCount();
            int realPlayers = Math.max(0, Bukkit.getOnlinePlayers().size() - botCount);
            Config.debugDbOps("NetworkHeartbeat tick: server=" + Config.serverId() + " players=" + realPlayers
                    + " bots=" + botCount);
            netDb.heartbeat(Config.serverId(), realPlayers, botCount);

            // Publish local bots into network registry
            int botPublishCount = 0;
            for (var fp : manager.getActivePlayers()) {
                var loc = fp.getLiveLocation();
                netDb.upsertNetworkBot(new NetworkDatabase.NetworkBotRow(
                        fp.getUuid().toString(),
                        fp.getName(),
                        fp.getDisplayName(),
                        Config.serverId(),
                        fp.getSpawnedBy(),
                        loc != null && loc.getWorld() != null ? loc.getWorld().getName() : "unknown",
                        loc != null ? loc.getX() : 0,
                        loc != null ? loc.getY() : 0,
                        loc != null ? loc.getZ() : 0,
                        fp.getEffectivePing(),
                        fp.isFrozen(),
                        System.currentTimeMillis()));
                botPublishCount++;
            }
            Config.debugDbOps("NetworkHeartbeat: published " + botPublishCount + " bot(s)");

            // Refresh from DB into local RemoteBotCache
            refreshRemoteCache();

        } catch (Exception e) {
            FppLogger.error("[NetworkHeartbeat] tick error: " + e.getMessage() + " (connection valid? "
                    + (plugin.getDatabaseManager() != null
                            && plugin.getDatabaseManager().isConnectionValid()) + ")");
            Config.debugDbOps("NetworkHeartbeat tick stack: " + e.getStackTrace()[0]);
        }
    }

    private void refreshRemoteCache() {
        try {
            var cache = plugin.getRemoteBotCache();
            if (cache == null) {
                Config.debugDbOps("refreshRemoteCache: RemoteBotCache is null");
                return;
            }

            // Load all network bots except those on THIS server (local bots are already in FakePlayerManager)
            Config.debugDbOps("refreshRemoteCache: fetching network bots");
            var netBots = netDb.getNetworkBots();
            String myId = Config.serverId();
            int addedCount = 0;
            for (var row : netBots) {
                if (row.serverId().equals(myId)) continue;
                try {
                    UUID uuid = UUID.fromString(row.botUuid());
                    cache.add(new RemoteBotEntry(
                            row.serverId(), uuid, row.botName(), row.botDisplay(), row.botName(), "", "", row.ping()));
                    addedCount++;
                } catch (IllegalArgumentException ignored) {
                    Config.debugDbOps("refreshRemoteCache: invalid UUID format - " + row.botUuid());
                }
            }
            Config.debugDbOps("refreshRemoteCache: added " + addedCount + "/" + netBots.size() + " remote bot(s)");

            // Update stats
            int totalPlayers = netDb.getTotalNetworkPlayers();
            int totalBots = netDb.getTotalNetworkBots();
            Config.debugDbOps("refreshRemoteCache: network stats - players=" + totalPlayers + ", bots=" + totalBots);
            cache.setNetworkTotalPlayers(totalPlayers);
            cache.setNetworkTotalBots(totalBots);

        } catch (Exception e) {
            FppLogger.error("[NetworkHeartbeat] refreshRemoteCache: " + e.getMessage() + " (connection valid? "
                    + (plugin.getDatabaseManager() != null
                            && plugin.getDatabaseManager().isConnectionValid()) + ")");
        }
    }

    private void prune() {
        try {
            netDb.pruneStaleServers(STALE_THRESHOLD_MS);
        } catch (Exception e) {
            FppLogger.warn("[NetworkHeartbeat] prune error: " + e.getMessage());
        }
    }
}
