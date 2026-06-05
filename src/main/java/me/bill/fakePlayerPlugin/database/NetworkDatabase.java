package me.bill.fakePlayerPlugin.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.util.FppLogger;

/**
 * Network-layer database operations for proxy-merged multi-server setups.
 * All backends share the same MySQL database in NETWORK mode.
 *
 * <p>Tables managed:
 * <ul>
 *   <li>{@code fpp_network_bots} — live bot registry shared across all servers</li>
 *   <li>{@code fpp_server_heartbeat} — per-server liveness + player counts</li>
 *   <li>{@code fpp_network_tasks} — cross-server command queue (task router)</li>
 * </ul>
 */
public final class NetworkDatabase {

    private final Connection connection;
    private final boolean isMysql;
    private final ExecutorService async = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "FPP-NetworkDB");
        t.setDaemon(true);
        return t;
    });

    public NetworkDatabase(Connection connection, boolean isMysql) {
        this.connection = connection;
        this.isMysql = isMysql;
        Config.debugDbConn("NetworkDatabase initialized - connection=" + connection + ", isMysql=" + isMysql);
        Config.debugDbConn("Connection valid? " + isValidConnection());
    }

    private boolean isValidConnection() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    //  fpp_network_bots  —  shared live bot registry
    // ═════════════════════════════════════════════════════════════════════════════

    public void upsertNetworkBot(NetworkBotRow row) {
        if (!isValidConnection()) {
            FppLogger.error(
                    "[NetworkDB] upsertNetworkBot: Connection is closed before operation - bot=" + row.botName());
            return;
        }
        Config.debugDbOps("upsertNetworkBot: bot=" + row.botName() + " server=" + row.serverId());
        String sql = isMysql
                ? "INSERT INTO fpp_network_bots (bot_uuid, bot_name, bot_display, server_id, spawned_by, world_name, pos_x, pos_y, pos_z, ping, frozen, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                        + " ON DUPLICATE KEY UPDATE bot_name=?, bot_display=?, server_id=?, spawned_by=?, world_name=?, pos_x=?, pos_y=?, pos_z=?, ping=?, frozen=?, updated_at=?"
                : "INSERT INTO fpp_network_bots (bot_uuid, bot_name, bot_display, server_id, spawned_by, world_name, pos_x, pos_y, pos_z, ping, frozen, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                        + " ON CONFLICT(bot_uuid) DO UPDATE SET bot_name=excluded.bot_name, bot_display=excluded.bot_display, server_id=excluded.server_id, spawned_by=excluded.spawned_by, world_name=excluded.world_name, pos_x=excluded.pos_x, pos_y=excluded.pos_y, pos_z=excluded.pos_z, ping=excluded.ping, frozen=excluded.frozen, updated_at=excluded.updated_at";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            long now = System.currentTimeMillis();
            ps.setString(1, row.botUuid());
            ps.setString(2, row.botName());
            ps.setString(3, row.botDisplay());
            ps.setString(4, row.serverId());
            ps.setString(5, row.spawnedBy());
            ps.setString(6, row.worldName());
            ps.setDouble(7, row.posX());
            ps.setDouble(8, row.posY());
            ps.setDouble(9, row.posZ());
            ps.setInt(10, row.ping());
            ps.setBoolean(11, row.frozen());
            ps.setLong(12, now);
            if (isMysql) {
                ps.setString(13, row.botName());
                ps.setString(14, row.botDisplay());
                ps.setString(15, row.serverId());
                ps.setString(16, row.spawnedBy());
                ps.setString(17, row.worldName());
                ps.setDouble(18, row.posX());
                ps.setDouble(19, row.posY());
                ps.setDouble(20, row.posZ());
                ps.setInt(21, row.ping());
                ps.setBoolean(22, row.frozen());
                ps.setLong(23, now);
            }
            ps.executeUpdate();
            Config.debugDbOps("upsertNetworkBot: SUCCESS - bot=" + row.botName());
        } catch (SQLException e) {
            FppLogger.error("[NetworkDB] upsertNetworkBot: " + e.getMessage() + " (SQLState=" + e.getSQLState()
                    + ", closed=" + !isValidConnection() + ")");
            Config.debugDbOps("upsertNetworkBot: Stack trace - " + e.getStackTrace()[0]);
        }
    }

    public void removeNetworkBot(String botUuid) {
        if (!isValidConnection()) {
            FppLogger.error("[NetworkDB] removeNetworkBot: Connection is closed before operation - uuid=" + botUuid);
            return;
        }
        Config.debugDbOps("removeNetworkBot: uuid=" + botUuid);
        String sql = "DELETE FROM fpp_network_bots WHERE bot_uuid=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, botUuid);
            int rows = ps.executeUpdate();
            FppLogger.debug("DB-OPS", true, "removeNetworkBot: SUCCESS - rows=" + rows);
        } catch (SQLException e) {
            FppLogger.error("[NetworkDB] removeNetworkBot: " + e.getMessage() + " (SQLState=" + e.getSQLState()
                    + ", closed=" + !isValidConnection() + ")");
        }
    }

    public List<NetworkBotRow> getNetworkBots() {
        Config.debugDbOps("getNetworkBots: Starting query (connection valid? " + isValidConnection() + ")");
        List<NetworkBotRow> list = new ArrayList<>();
        String sql = "SELECT * FROM fpp_network_bots ORDER BY updated_at DESC";
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapNetworkBotRow(rs));
            }
            Config.debugDbOps("getNetworkBots: SUCCESS - found " + list.size() + " bot(s)");
        } catch (SQLException e) {
            FppLogger.error("[NetworkDB] getNetworkBots: " + e.getMessage() + " (SQLState=" + e.getSQLState()
                    + ", closed=" + !isValidConnection() + ")");
            Config.debugDbOps("getNetworkBots: Connection state - valid=" + isValidConnection());
        }
        return list;
    }

    public List<NetworkBotRow> getNetworkBotsOnServer(String serverId) {
        List<NetworkBotRow> list = new ArrayList<>();
        String sql = "SELECT * FROM fpp_network_bots WHERE server_id=? ORDER BY updated_at DESC";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, serverId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapNetworkBotRow(rs));
                }
            }
        } catch (SQLException e) {
            FppLogger.error("[NetworkDB] getNetworkBotsOnServer: " + e.getMessage());
        }
        return list;
    }

    public NetworkBotRow getNetworkBotByName(String name) {
        String sql = "SELECT * FROM fpp_network_bots WHERE bot_name=? COLLATE NOCASE";
        if (isMysql) {
            sql = "SELECT * FROM fpp_network_bots WHERE bot_name=?";
        }
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapNetworkBotRow(rs);
            }
        } catch (SQLException e) {
            FppLogger.error("[NetworkDB] getNetworkBotByName: " + e.getMessage());
        }
        return null;
    }

    public int getNetworkBotCount() {
        String sql = "SELECT COUNT(*) FROM fpp_network_bots";
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            FppLogger.error("[NetworkDB] getNetworkBotCount: " + e.getMessage());
        }
        return 0;
    }

    public void clearNetworkBotsForServer(String serverId) {
        String sql = "DELETE FROM fpp_network_bots WHERE server_id=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, serverId);
            ps.executeUpdate();
        } catch (SQLException e) {
            FppLogger.error("[NetworkDB] clearNetworkBotsForServer: " + e.getMessage());
        }
    }

    private NetworkBotRow mapNetworkBotRow(ResultSet rs) throws SQLException {
        return new NetworkBotRow(
                rs.getString("bot_uuid"),
                rs.getString("bot_name"),
                rs.getString("bot_display"),
                rs.getString("server_id"),
                rs.getString("spawned_by"),
                rs.getString("world_name"),
                rs.getDouble("pos_x"),
                rs.getDouble("pos_y"),
                rs.getDouble("pos_z"),
                rs.getInt("ping"),
                rs.getBoolean("frozen"),
                rs.getLong("updated_at"));
    }

    // ═════════════════════════════════════════════════════════════════════════════
    //  fpp_server_heartbeat  —  per-server liveness
    // ═════════════════════════════════════════════════════════════════════════════

    public void heartbeat(String serverId, int realPlayers, int botCount) {
        if (!isValidConnection()) {
            FppLogger.error("[NetworkDB] heartbeat: Connection is closed before operation - server=" + serverId
                    + " players=" + realPlayers + " bots=" + botCount);
            return;
        }
        Config.debugDbOps("heartbeat: server=" + serverId + " players=" + realPlayers + " bots=" + botCount);
        String sql = isMysql
                ? "INSERT INTO fpp_server_heartbeat (server_id, real_players, bot_count, last_seen) VALUES (?, ?, ?, ?)"
                        + " ON DUPLICATE KEY UPDATE real_players=?, bot_count=?, last_seen=?"
                : "INSERT INTO fpp_server_heartbeat (server_id, real_players, bot_count, last_seen) VALUES (?, ?, ?, ?)"
                        + " ON CONFLICT(server_id) DO UPDATE SET real_players=excluded.real_players, bot_count=excluded.bot_count, last_seen=excluded.last_seen";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            long now = System.currentTimeMillis();
            ps.setString(1, serverId);
            ps.setInt(2, realPlayers);
            ps.setInt(3, botCount);
            ps.setLong(4, now);
            if (isMysql) {
                ps.setInt(5, realPlayers);
                ps.setInt(6, botCount);
                ps.setLong(7, now);
            }
            ps.executeUpdate();
            Config.debugDbOps("heartbeat: SUCCESS - server=" + serverId);
        } catch (SQLException e) {
            FppLogger.error("[NetworkDB] heartbeat: " + e.getMessage() + " (SQLState=" + e.getSQLState() + ", closed="
                    + !isValidConnection() + ", server=" + serverId + ")");
            Config.debugDbOps("heartbeat: Connection closed during operation - checking state");
        }
    }

    public List<ServerHeartbeatRow> getAllServerHeartbeats() {
        List<ServerHeartbeatRow> list = new ArrayList<>();
        String sql = "SELECT * FROM fpp_server_heartbeat ORDER BY last_seen DESC";
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new ServerHeartbeatRow(
                        rs.getString("server_id"),
                        rs.getInt("real_players"),
                        rs.getInt("bot_count"),
                        rs.getLong("last_seen")));
            }
        } catch (SQLException e) {
            FppLogger.error("[NetworkDB] getAllServerHeartbeats: " + e.getMessage());
        }
        return list;
    }

    public int getTotalNetworkPlayers() {
        Config.debugDbOps("getTotalNetworkPlayers: Starting query (connection valid? " + isValidConnection() + ")");
        String sql = "SELECT SUM(real_players + bot_count) FROM fpp_server_heartbeat";
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                int total = rs.getInt(1);
                Config.debugDbOps("getTotalNetworkPlayers: SUCCESS - total=" + total);
                return total;
            }
        } catch (SQLException e) {
            FppLogger.error("[NetworkDB] getTotalNetworkPlayers: " + e.getMessage() + " (SQLState=" + e.getSQLState()
                    + ", closed=" + !isValidConnection() + ")");
        }
        Config.debugDbOps("getTotalNetworkPlayers: Returning 0 (no data or error)");
        return 0;
    }

    public int getTotalNetworkBots() {
        Config.debugDbOps("getTotalNetworkBots: Starting query (connection valid? " + isValidConnection() + ")");
        String sql = "SELECT SUM(bot_count) FROM fpp_server_heartbeat";
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                int total = rs.getInt(1);
                Config.debugDbOps("getTotalNetworkBots: SUCCESS - total=" + total);
                return total;
            }
        } catch (SQLException e) {
            FppLogger.error("[NetworkDB] getTotalNetworkBots: " + e.getMessage() + " (SQLState=" + e.getSQLState()
                    + ", closed=" + !isValidConnection() + ")");
        }
        Config.debugDbOps("getTotalNetworkBots: Returning 0 (no data or error)");
        return 0;
    }

    public void pruneStaleServers(long maxAgeMs) {
        String sql = "DELETE FROM fpp_server_heartbeat WHERE last_seen < ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis() - maxAgeMs);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                FppLogger.info("[NetworkDB] Pruned " + rows + " stale server heartbeat(s).");
            }
        } catch (SQLException e) {
            FppLogger.error("[NetworkDB] pruneStaleServers: " + e.getMessage());
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    //  fpp_network_tasks  —  cross-server command queue
    // ═════════════════════════════════════════════════════════════════════════════

    public long createNetworkTask(
            String targetBot, String sourceServer, String targetServer, String taskType, String taskData) {
        String sql = isMysql
                ? "INSERT INTO fpp_network_tasks (target_bot, source_server, target_server, task_type, task_data, created_at, status) VALUES (?, ?, ?, ?, ?, ?, 'PENDING')"
                : "INSERT INTO fpp_network_tasks (target_bot, source_server, target_server, task_type, task_data, created_at, status) VALUES (?, ?, ?, ?, ?, ?, 'PENDING')";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, targetBot);
            ps.setString(2, sourceServer);
            ps.setString(3, targetServer);
            ps.setString(4, taskType);
            ps.setString(5, taskData);
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) {
            FppLogger.error("[NetworkDB] createNetworkTask: " + e.getMessage());
        }
        return -1;
    }

    public boolean claimNetworkTask(long taskId, String serverId) {
        String sql =
                "UPDATE fpp_network_tasks SET status='CLAIMED', claimed_at=?, claimed_by=? WHERE id=? AND status='PENDING'";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, serverId);
            ps.setLong(3, taskId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            FppLogger.error("[NetworkDB] claimNetworkTask: " + e.getMessage());
        }
        return false;
    }

    public List<NetworkTaskRow> getPendingTasksForServer(String serverId) {
        List<NetworkTaskRow> list = new ArrayList<>();
        String sql =
                "SELECT * FROM fpp_network_tasks WHERE target_server=? AND status='PENDING' ORDER BY created_at ASC";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, serverId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapNetworkTaskRow(rs));
                }
            }
        } catch (SQLException e) {
            FppLogger.error("[NetworkDB] getPendingTasksForServer: " + e.getMessage());
        }
        return list;
    }

    public void completeNetworkTask(long taskId, String result) {
        String sql = "UPDATE fpp_network_tasks SET status='COMPLETED', result=? WHERE id=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, result);
            ps.setLong(2, taskId);
            ps.executeUpdate();
        } catch (SQLException e) {
            FppLogger.error("[NetworkDB] completeNetworkTask: " + e.getMessage());
        }
    }

    public void failNetworkTask(long taskId, String reason) {
        String sql = "UPDATE fpp_network_tasks SET status='FAILED', result=? WHERE id=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, reason);
            ps.setLong(2, taskId);
            ps.executeUpdate();
        } catch (SQLException e) {
            FppLogger.error("[NetworkDB] failNetworkTask: " + e.getMessage());
        }
    }

    private NetworkTaskRow mapNetworkTaskRow(ResultSet rs) throws SQLException {
        return new NetworkTaskRow(
                rs.getLong("id"),
                rs.getString("target_bot"),
                rs.getString("source_server"),
                rs.getString("target_server"),
                rs.getString("task_type"),
                rs.getString("task_data"),
                rs.getLong("created_at"),
                rs.getLong("claimed_at"),
                rs.getString("claimed_by"),
                rs.getString("status"),
                rs.getString("result"));
    }

    public void shutdown() {
        async.shutdown();
    }

    // ═════════════════════════════════════════════════════════════════════════════
    //  Records
    // ═════════════════════════════════════════════════════════════════════════════

    public record NetworkBotRow(
            String botUuid,
            String botName,
            String botDisplay,
            String serverId,
            String spawnedBy,
            String worldName,
            double posX,
            double posY,
            double posZ,
            int ping,
            boolean frozen,
            long updatedAt) {}

    public record ServerHeartbeatRow(String serverId, int realPlayers, int botCount, long lastSeen) {}

    public record NetworkTaskRow(
            long id,
            String targetBot,
            String sourceServer,
            String targetServer,
            String taskType,
            String taskData,
            long createdAt,
            long claimedAt,
            String claimedBy,
            String status,
            String result) {}
}
