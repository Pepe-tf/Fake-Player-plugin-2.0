package me.bill.fakePlayerPlugin.fakeplayer;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.database.DatabaseManager;
import me.bill.fakePlayerPlugin.util.BotDataYaml;
import me.bill.fakePlayerPlugin.util.FppLogger;
import me.bill.fakePlayerPlugin.util.FppScheduler;

public final class BotIdentityCache {

    private static final String YAML_FILE = "bot-identities.yml";
    private static final String ROOT = "identities.by-name";
    private static final String OFFLINE_UUID_NAMESPACE = "OfflinePlayer:";
    private static final String USER_AGENT = "FakePlayerPlugin/1.6.6.11";
    private static final long MOJANG_RATE_LIMIT_COOLDOWN_MS = TimeUnit.MINUTES.toMillis(5);
    private static final ThreadLocal<MessageDigest> MD5 = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    });

    private final FakePlayerPlugin pluginRef;
    private final DatabaseManager db;
    private final File yamlFile;

    private final Map<String, UUID> cache = new ConcurrentHashMap<>();
    private final Map<String, IdentityResolution> resolvedCache = new ConcurrentHashMap<>();
    private volatile long mojangRateLimitedUntilMs = 0;
    private volatile boolean yamlSaveQueued = false;
    private volatile boolean yamlSaveDirty = false;

    private YamlConfiguration yamlConfig = null;

    public BotIdentityCache(FakePlayerPlugin plugin, DatabaseManager db) {
        this.pluginRef = plugin;
        this.db = db;
        this.yamlFile = new File(new File(plugin.getDataFolder(), "data"), YAML_FILE);

        if (db == null) {
            loadYaml();
        } else {
            loadDbAsync();
        }
    }

    public UUID lookupOrCreate(String botName) {
        String key = normalizeKey(botName);

        UUID cached = cache.get(key);
        if (cached != null) return cached;

        return db != null ? lookupOrCreateDb(botName, key) : lookupOrCreateYaml(botName, key);
    }

    public UUID refresh(String botName) {
        String key = normalizeKey(botName);
        IdentityResolution resolved = resolvePreferredUuid(botName);
        UUID safeUuid = resolved.uuid();

        cache.put(key, safeUuid);
        if (db != null) {
            String serverId = Config.serverId();
            UUID fromDb = db.lookupBotUuid(botName, serverId);
            if (fromDb == null) {
                db.registerBotUuid(botName, safeUuid, serverId);
            } else if (!safeUuid.equals(fromDb)) {
                db.migrateBotUuid(botName, serverId, fromDb, safeUuid);
            }
        } else {
            if (yamlConfig == null) yamlConfig = new YamlConfiguration();
            yamlConfig.set(ROOT + "." + key, safeUuid.toString());
            saveYaml();
        }
        Config.debugDatabase("BotIdentityCache: refreshed identity for '" + botName + "' → " + safeUuid);
        return safeUuid;
    }

    public static UUID deterministicBotUuid(String botName) {
        return offlineModeUuid(botName);
    }

    public static UUID offlineModeUuid(String botName) {
        MessageDigest md5 = MD5.get();
        md5.reset();
        byte[] bytes = md5.digest((OFFLINE_UUID_NAMESPACE + String.valueOf(botName)).getBytes(StandardCharsets.UTF_8));
        bytes[6] &= 0x0f;
        bytes[6] |= 0x30;
        bytes[8] &= 0x3f;
        bytes[8] |= 0x80;
        long most = 0;
        long least = 0;
        for (int i = 0; i < 8; i++) most = (most << 8) | (bytes[i] & 0xffL);
        for (int i = 8; i < 16; i++) least = (least << 8) | (bytes[i] & 0xffL);
        return new UUID(most, least);
    }

    private UUID lookupOrCreateDb(String botName, String cacheKey) {
        UUID safeUuid = offlineModeUuid(botName);
        UUID existing = cache.putIfAbsent(cacheKey, safeUuid);
        if (existing != null) return existing;

        db.registerBotUuid(botName, safeUuid, Config.serverId());
        Config.debugDatabase("BotIdentityCache: new identity for '" + botName + "' → " + safeUuid);
        return safeUuid;
    }

    private void loadDbAsync() {
        FppScheduler.runAsync(pluginRef, () -> {
            String serverId = Config.serverId();
            int loaded = 0;
            for (DatabaseManager.BotIdentityRow row : db.getBotIdentityRows()) {
                if (row == null
                        || row.botName() == null
                        || row.botName().isBlank()
                        || !serverId.equals(row.serverId())) {
                    continue;
                }
                try {
                    cache.putIfAbsent(normalizeKey(row.botName()), UUID.fromString(row.botUuid()));
                    loaded++;
                } catch (Exception e) {
                    FppLogger.warn("BotIdentityCache: skipping malformed DB UUID for '"
                            + row.botName()
                            + "': "
                            + row.botUuid());
                }
            }
            if (loaded > 0) {
                FppLogger.info("BotIdentityCache: asynchronously loaded " + loaded + " DB identity mapping(s).");
            }
        });
    }

    private void loadYaml() {
        yamlConfig = BotDataYaml.load(pluginRef);
        ConfigurationSection root = yamlConfig.getConfigurationSection(ROOT);
        if (root == null && yamlFile.exists()) {
            YamlConfiguration legacy = YamlConfiguration.loadConfiguration(yamlFile);
            if (!legacy.getKeys(false).isEmpty()) {
                root = legacy;
                try {
                    BotDataYaml.replaceSection(pluginRef, ROOT, section -> {
                        for (String key : legacy.getKeys(false)) {
                            section.set(key, legacy.getString(key));
                        }
                    });
                    if (yamlFile.exists()) yamlFile.delete();
                    yamlConfig = BotDataYaml.load(pluginRef);
                    root = yamlConfig.getConfigurationSection(ROOT);
                } catch (IOException e) {
                    FppLogger.warn("BotIdentityCache: failed to migrate " + YAML_FILE + ": " + e.getMessage());
                }
            }
        }
        if (root == null) {
            yamlConfig = new YamlConfiguration();
            Config.debugDatabase("BotIdentityCache: no YAML data yet - will create on first spawn.");
            return;
        }
        int loaded = 0;
        for (String key : root.getKeys(false)) {
            String val = root.getString(key);
            if (val == null || val.isBlank()) continue;
            try {
                cache.put(normalizeKey(key), UUID.fromString(val));
                loaded++;
            } catch (IllegalArgumentException e) {
                FppLogger.warn("BotIdentityCache: skipping malformed entry '" + key + "': " + val);
            }
        }
        if (loaded > 0) {
            FppLogger.info("BotIdentityCache: loaded " + loaded + " name→UUID mapping(s) from " + YAML_FILE + ".");
        }
    }

    private UUID lookupOrCreateYaml(String botName, String cacheKey) {
        synchronized (this) {
            if (yamlConfig == null) yamlConfig = new YamlConfiguration();

            String stored = yamlConfig.getString(ROOT + "." + cacheKey);
            if (stored != null && !stored.isBlank()) {
                try {
                    UUID fromYaml = UUID.fromString(stored);
                    cache.putIfAbsent(cacheKey, fromYaml);
                    Config.debugDatabase("BotIdentityCache: YAML hit for '" + botName + "' → " + fromYaml);
                    return fromYaml;
                } catch (IllegalArgumentException e) {
                    FppLogger.warn("BotIdentityCache: malformed YAML entry for '" + botName + "' - regenerating UUID.");
                }
            }

            UUID safeUuid = offlineModeUuid(botName);
            UUID existing = cache.putIfAbsent(cacheKey, safeUuid);
            if (existing != null) return existing;
            yamlConfig.set(ROOT + "." + cacheKey, safeUuid.toString());
            scheduleYamlSave();
            Config.debugDatabase("BotIdentityCache: new YAML identity for '" + botName + "' → " + safeUuid);
            return safeUuid;
        }
    }

    private void scheduleYamlSave() {
        yamlSaveDirty = true;
        if (yamlSaveQueued) return;
        yamlSaveQueued = true;
        FppScheduler.runSyncLater(
                pluginRef,
                () -> {
                    yamlSaveDirty = false;
                    YamlConfiguration snapshot = snapshotYamlIdentities();
                    FppScheduler.runAsync(pluginRef, () -> {
                        try {
                            BotDataYaml.save(pluginRef, snapshot);
                        } catch (IOException e) {
                            FppLogger.warn("BotIdentityCache: failed to save " + YAML_FILE + ": " + e.getMessage());
                        } finally {
                            yamlSaveQueued = false;
                            if (yamlSaveDirty) scheduleYamlSave();
                        }
                    });
                },
                20L);
    }

    private synchronized YamlConfiguration snapshotYamlIdentities() {
        YamlConfiguration snapshot = new YamlConfiguration();
        if (yamlConfig != null) {
            try {
                snapshot.loadFromString(yamlConfig.saveToString());
            } catch (InvalidConfigurationException e) {
                FppLogger.warn("BotIdentityCache: failed to snapshot identity YAML: " + e.getMessage());
            }
        }
        for (Map.Entry<String, UUID> entry : cache.entrySet()) {
            snapshot.set(ROOT + "." + entry.getKey(), entry.getValue().toString());
        }
        return snapshot;
    }

    private void migrateLegacyDbMappings() {
        if (db == null) return;
        int migrated = 0;
        for (DatabaseManager.BotIdentityRow row : db.getBotIdentityRows()) {
            if (row == null || row.botName() == null || row.botName().isBlank()) continue;
            IdentityResolution resolved = resolvePreferredUuid(row.botName());
            UUID target = resolved.uuid();
            UUID current;
            try {
                current = UUID.fromString(row.botUuid());
            } catch (Exception e) {
                cache.put(normalizeKey(row.botName()), target);
                FppLogger.warn("BotIdentityCache: malformed DB UUID for '"
                        + row.botName()
                        + "' on server '"
                        + row.serverId()
                        + "' - using resolved bot UUID "
                        + target
                        + " for this runtime.");
                continue;
            }
            cache.put(normalizeKey(row.botName()), resolved.authoritative() ? target : current);
            if (resolved.authoritative()
                    && !target.equals(current)
                    && db.migrateBotUuid(row.botName(), row.serverId(), current, target)) {
                migrated++;
            }
        }
        if (migrated > 0) {
            FppLogger.info(
                    "BotIdentityCache: migrated " + migrated + " legacy bot UUID mapping(s) to premium/offline UUIDs.");
        }
    }

    private void migrateLegacyYamlMappings() {
        if (yamlConfig == null) yamlConfig = new YamlConfiguration();
        boolean changed = false;
        int migrated = 0;
        for (Map.Entry<String, UUID> entry : Map.copyOf(cache).entrySet()) {
            IdentityResolution resolved = resolvePreferredUuid(entry.getKey());
            if (!resolved.authoritative()) continue;
            UUID target = resolved.uuid();
            cache.put(entry.getKey(), target);
            if (!target.equals(entry.getValue())) {
                yamlConfig.set(ROOT + "." + entry.getKey(), target.toString());
                changed = true;
                migrated++;
            }
        }
        if (changed) {
            saveYaml();
            FppLogger.info("BotIdentityCache: migrated "
                    + migrated
                    + " legacy YAML bot UUID mapping(s) to premium/offline UUIDs.");
        }
    }

    private void saveYaml() {
        try {
            File parent = yamlFile.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            BotDataYaml.save(pluginRef, yamlConfig);
            if (yamlFile.exists()) yamlFile.delete();
        } catch (IOException e) {
            FppLogger.warn("BotIdentityCache: failed to save " + YAML_FILE + ": " + e.getMessage());
        }
    }

    private static String normalizeKey(String botName) {
        return botName == null ? "" : botName.toLowerCase(Locale.ROOT);
    }

    private IdentityResolution resolvePreferredUuid(String botName) {
        String key = normalizeKey(botName);
        IdentityResolution cached = resolvedCache.get(key);
        if (cached != null) return cached;

        UUID offlineUuid = offlineModeUuid(botName);
        if (!isPotentialPremiumName(botName)) {
            IdentityResolution result = new IdentityResolution(offlineUuid, true);
            resolvedCache.put(key, result);
            return result;
        }

        long now = System.currentTimeMillis();
        if (now < mojangRateLimitedUntilMs) {
            Config.debugDatabase("BotIdentityCache: Mojang UUID lookup skipped for '"
                    + botName
                    + "' during rate-limit cooldown; using offline UUID for new identities.");
            return new IdentityResolution(offlineUuid, false);
        }

        try {
            UUID premiumUuid = fetchMojangUuid(botName);
            IdentityResolution result = premiumUuid != null
                    ? new IdentityResolution(premiumUuid, true)
                    : new IdentityResolution(offlineUuid, true);
            resolvedCache.put(key, result);
            return result;
        } catch (RateLimitException e) {
            mojangRateLimitedUntilMs = System.currentTimeMillis() + MOJANG_RATE_LIMIT_COOLDOWN_MS;
            FppLogger.warn("BotIdentityCache: Mojang UUID lookups are rate-limited; using offline UUID fallback for "
                    + TimeUnit.MILLISECONDS.toMinutes(MOJANG_RATE_LIMIT_COOLDOWN_MS)
                    + " minutes.");
            return new IdentityResolution(offlineUuid, false);
        } catch (Exception e) {
            Config.debugDatabase("BotIdentityCache: Mojang UUID lookup failed for '"
                    + botName
                    + "' ("
                    + e.getMessage()
                    + "); using offline UUID fallback for new identities.");
            return new IdentityResolution(offlineUuid, false);
        }
    }

    private static boolean isPotentialPremiumName(String botName) {
        return botName != null && botName.matches("[A-Za-z0-9_]{3,16}");
    }

    private static UUID fetchMojangUuid(String botName) throws Exception {
        HttpURLConnection conn =
                (HttpURLConnection) URI.create("https://api.mojang.com/users/profiles/minecraft/" + botName)
                        .toURL()
                        .openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(2_500);
        conn.setReadTimeout(2_500);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        int code = conn.getResponseCode();
        if (code == 429) {
            conn.disconnect();
            throw new RateLimitException();
        }
        if (code == 204 || code == 404) {
            conn.disconnect();
            return null;
        }
        try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (element == null || !element.isJsonObject()) return null;
            JsonObject json = element.getAsJsonObject();
            JsonElement id = json.get("id");
            if (id == null || !id.isJsonPrimitive()) return null;
            return parseMojangUuid(id.getAsString());
        } finally {
            conn.disconnect();
        }
    }

    private static UUID parseMojangUuid(String value) {
        if (value == null) return null;
        String compact = value.replace("-", "");
        if (!compact.matches("[0-9a-fA-F]{32}")) return null;
        return UUID.fromString(compact.substring(0, 8)
                + "-"
                + compact.substring(8, 12)
                + "-"
                + compact.substring(12, 16)
                + "-"
                + compact.substring(16, 20)
                + "-"
                + compact.substring(20));
    }

    private record IdentityResolution(UUID uuid, boolean authoritative) {}

    private static final class RateLimitException extends RuntimeException {}
}
