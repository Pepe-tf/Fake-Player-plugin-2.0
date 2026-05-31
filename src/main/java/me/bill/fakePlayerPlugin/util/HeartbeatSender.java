package me.bill.fakePlayerPlugin.util;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import com.google.gson.JsonObject;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;

public final class HeartbeatSender {

    private static final String ENDPOINT = "https://fpp.wtf/api/heartbeat";
    private static final long INTERVAL_TICKS = 20L * 30L; // 30 seconds

    private final Plugin plugin;
    private final FakePlayerManager fakePlayerManager;
    private int taskId = -1;

    public HeartbeatSender(FakePlayerPlugin plugin, FakePlayerManager fakePlayerManager) {
        this.plugin = plugin;
        this.fakePlayerManager = fakePlayerManager;
    }

    public void start() {
        if (!Config.heartbeatEnabled()) {
            Config.debug("Heartbeat: disabled in config.");
            return;
        }
        if (taskId != -1) {
            return;
        }
        taskId = FppScheduler.runSyncRepeatingWithId(plugin, this::send, INTERVAL_TICKS, INTERVAL_TICKS);
        Config.debug("Heartbeat: started (every " + (INTERVAL_TICKS / 20) + "s).");
    }

    public void stop() {
        if (taskId != -1) {
            FppScheduler.cancelTask(taskId);
            taskId = -1;
            Config.debug("Heartbeat: stopped.");
        }
    }

    private void send() {
        if (!Config.heartbeatEnabled()) return;
        FppScheduler.runAsync(plugin, this::sendBlocking);
    }

    private void sendBlocking() {
        try {
            String configuredId = Config.serverId();
            int totalOnline = Bukkit.getOnlinePlayers().size();
            int botCount = fakePlayerManager == null ? 0 : fakePlayerManager.getCount();
            // Bots are real ServerPlayer entities and appear in getOnlinePlayers(),
            // so subtract them so the web dashboard shows *real* human players.
            int playerCount = Math.max(0, totalOnline - botCount);
            String version = plugin.getPluginMeta().getVersion();

            // If server_id is the generic "default", make it unique per JVM
            // by appending IP:port. This prevents multiple backend servers from
            // overwriting each other in the web dashboard.
            String serverId = configuredId;
            if ("default".equalsIgnoreCase(configuredId) || configuredId.isBlank()) {
                String ip = Bukkit.getServer().getIp();
                int port = Bukkit.getServer().getPort();
                serverId = "default@" + (ip.isEmpty() ? "0.0.0.0" : ip) + ":" + port;
            }

            JsonObject payload = new JsonObject();
            payload.addProperty("server_id", serverId);
            payload.addProperty("player_count", playerCount);
            payload.addProperty("bot_count", botCount);
            payload.addProperty("version", version);

            byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);

            HttpURLConnection conn =
                    (HttpURLConnection) URI.create(ENDPOINT).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(5_000);
            conn.setReadTimeout(5_000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Content-Length", String.valueOf(body.length));
            conn.setRequestProperty("User-Agent", "FakePlayerPlugin-Heartbeat/" + version + " (https://fpp.wtf)");

            try (OutputStream out = conn.getOutputStream()) {
                out.write(body);
            }

            int code = conn.getResponseCode();
            Config.debug("Heartbeat: sent (players="
                    + playerCount
                    + ", bots="
                    + botCount
                    + ", server="
                    + serverId
                    + ") → HTTP "
                    + code);
        } catch (Exception e) {
            Config.debug("Heartbeat: failed - " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
