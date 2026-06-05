package me.bill.fakePlayerPlugin.command;

import java.io.File;
import java.util.List;

import org.bukkit.command.CommandSender;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.BotNameConfig;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.database.DatabaseManager;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.BackupManager;
import me.bill.fakePlayerPlugin.util.BadwordFilter;
import me.bill.fakePlayerPlugin.util.ConfigMigrator;
import me.bill.fakePlayerPlugin.util.DataMigrator;
import me.bill.fakePlayerPlugin.util.FppScheduler;
import me.bill.fakePlayerPlugin.util.TextUtil;
import me.bill.fakePlayerPlugin.util.YamlFileSyncer;

public class MigrateCommand implements FppCommand {

    private static final String COLOR = "<#0079FF>";
    private static final String C_CLOSE = "</#0079FF>";
    private static final String GRAY = "<gray>";
    private static final String GREEN = "<green>";
    private static final String RED = "<red>";
    private static final String YELLOW = "<yellow>";

    private final FakePlayerPlugin plugin;

    public MigrateCommand(FakePlayerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "migrate";
    }

    @Override
    public String getUsage() {
        return "<backup|status|config|lang|names|db>";
    }

    @Override
    public String getDescription() {
        return "Manages config/data migration and backups.";
    }

    @Override
    public String getPermission() {
        return Perm.MIGRATE;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Perm.MIGRATE)) {
            sender.sendMessage(Lang.get("no-permission"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "backup" -> doBackup(sender);
            case "backups" -> doBackupsList(sender);
            case "status" -> doStatus(sender);
            case "config" -> doConfig(sender);
            case "lang" -> doFileSync(sender, "language/en.yml", "language/en.yml", "Language file (en.yml)");
            case "names" -> doFileSync(sender, "bot-names.yml", "bot-names.yml", "Bot names (bot-names.yml)");
            case "db" -> doDb(sender, args);
            case "apply" -> doApply(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void doBackup(CommandSender sender) {
        msg(sender, GRAY + "Creating backup of all plugin files…");
        FppScheduler.runAsync(plugin, () -> {
            File dir = BackupManager.createFullBackup(plugin, "manual");
            long bytes = BackupManager.totalBackupSizeBytes(plugin);
            sync(sender, GREEN + "✔ Backup created: " + GRAY + "backups/" + dir.getName());
            sync(sender, GRAY + "  Total backup storage: " + GRAY + formatBytes(bytes));
        });
    }

    private void doBackupsList(CommandSender sender) {
        List<String> backups = BackupManager.listBackups(plugin);
        if (backups.isEmpty()) {
            msg(sender, GRAY + "No backups found.");
            return;
        }
        msg(sender, COLOR + "ꜱᴛᴏʀᴇᴅ ʙᴀᴄᴋᴜᴘꜱ" + C_CLOSE + GRAY + " (" + backups.size() + " total):");
        backups.stream().limit(15).forEach(b -> msg(sender, GRAY + "  • " + b));
        if (backups.size() > 15) {
            msg(sender, GRAY + "  … and " + (backups.size() - 15) + " more.");
        }
        msg(sender, GRAY + "  Total size: " + formatBytes(BackupManager.totalBackupSizeBytes(plugin)));
    }

    private void doStatus(CommandSender sender) {
        int configVer = plugin.getConfig().getInt("config-version", 0);
        boolean configCurrent = configVer >= ConfigMigrator.CURRENT_VERSION;

        msg(sender, COLOR + "ᴍɪɢʀᴀᴛɪᴏɴ ꜱᴛᴀᴛᴜꜱ" + C_CLOSE);

        msg(
                sender,
                GRAY
                        + "  Config version : "
                        + configVer
                        + " / "
                        + ConfigMigrator.CURRENT_VERSION
                        + (configCurrent
                                ? "  " + GREEN + "✔ current"
                                : "  " + RED + "✘ outdated - run /fpp migrate config"));

        msg(sender, COLOR + "  ꜰɪʟᴇ ꜱʏɴᴄ ꜱᴛᴀᴛᴜꜱ" + C_CLOSE);

        String[][] syncFiles = {
            {"language/en.yml", "language/en.yml", "  en.yml       ", "lang"},
            {"bot-names.yml", "bot-names.yml", "  bot-names    ", "names"},
        };
        for (String[] f : syncFiles) {
            int missing = YamlFileSyncer.countMissingKeys(plugin, f[0], f[1]);
            int total = YamlFileSyncer.countJarKeys(plugin, f[1]);
            if (missing < 0) {
                msg(sender, GRAY + f[2] + ": " + RED + "✘ file missing (run /fpp reload to extract)");
            } else if (missing == 0) {
                msg(sender, GRAY + f[2] + ": " + GREEN + "✔ up to date" + GRAY + " (" + total + " keys)");
            } else {
                msg(
                        sender,
                        GRAY
                                + f[2]
                                + ": "
                                + "<yellow>⚠ "
                                + missing
                                + " / "
                                + total
                                + " key(s) missing - run /fpp migrate "
                                + f[3]);
            }
        }

        DatabaseManager db = plugin.getDatabaseManager();
        if (db != null) {
            var stats = db.getStats();
            msg(sender, GRAY + "  DB backend     : " + stats.backend());
            msg(
                    sender,
                    GRAY
                            + "  Sessions       : "
                            + stats.totalSessions()
                            + " total, "
                            + stats.activeSessions()
                            + " active");
            msg(sender, GRAY + "  Unique bots    : " + stats.uniqueBots());
            msg(sender, GRAY + "  Unique spawners: " + stats.uniqueSpawners());
            if (stats.totalUptimeMs() > 0) {
                msg(sender, GRAY + "  Combined uptime: " + stats.formattedUptime());
            }
        } else {
            msg(sender, RED + "  Database       : offline");
        }

        List<String> backups = BackupManager.listBackups(plugin);
        msg(sender, GRAY + "  Backups stored : " + backups.size() + " (max 10 kept)");
        if (!backups.isEmpty()) {
            msg(sender, GRAY + "  Latest backup  : " + backups.get(0));
        }
        msg(sender, GRAY + "  Backup storage : " + formatBytes(BackupManager.totalBackupSizeBytes(plugin)));
    }

    private void doConfig(CommandSender sender) {
        int current = plugin.getConfig().getInt("config-version", 0);
        if (current >= ConfigMigrator.CURRENT_VERSION) {
            msg(
                    sender,
                    GREEN
                            + "✔ Config is already at the latest version (v"
                            + ConfigMigrator.CURRENT_VERSION
                            + "). Nothing to do.");
            return;
        }
        msg(sender, GRAY + "Running config migration from v" + current + " → v" + ConfigMigrator.CURRENT_VERSION + "…");

        plugin.getConfig().set("config-version", 0);
        plugin.saveConfig();

        boolean migrated = ConfigMigrator.migrateIfNeeded(plugin);

        Config.reload();

        if (migrated) {
            msg(
                    sender,
                    GREEN
                            + "✔ Config migrated to v"
                            + ConfigMigrator.CURRENT_VERSION
                            + " and reloaded. Check console for details.");
        } else {
            msg(sender, GREEN + "✔ Config stamped as v" + ConfigMigrator.CURRENT_VERSION + " (defaults filled).");
        }
    }

    private void doFileSync(CommandSender sender, String diskPath, String jarPath, String label) {
        int missing = YamlFileSyncer.countMissingKeys(plugin, diskPath, jarPath);

        if (missing == 0) {
            msg(sender, GREEN + "✔ " + label + " is already up to date - no missing keys.");
            return;
        }
        if (missing < 0) {
            msg(sender, GRAY + label + " does not exist yet - extracting from JAR…");
        } else {
            msg(sender, GRAY + "Backing up config files before sync…");
            FppScheduler.runAsync(plugin, () -> {
                BackupManager.createConfigFilesBackup(plugin, "pre-sync");
                sync(sender, GRAY + "Backup created. Syncing " + label + "…");
                YamlFileSyncer.SyncResult result = YamlFileSyncer.syncMissingKeys(plugin, diskPath, jarPath);
                if (result.hasChanges()) {
                    sync(sender, GREEN + "✔ Added " + result.count() + " key(s) to " + label + ":");
                    result.keysAdded().forEach(k -> sync(sender, GRAY + "    + " + k));
                    sync(
                            sender,
                            GRAY + "  Run " + COLOR + "/fpp reload" + C_CLOSE + GRAY + " to apply the updated file.");
                } else {
                    sync(sender, GREEN + "✔ " + label + " is up to date - no changes made.");
                }
            });
            return;
        }

        YamlFileSyncer.SyncResult result = YamlFileSyncer.syncMissingKeys(plugin, diskPath, jarPath);
        msg(
                sender,
                GREEN
                        + "✔ "
                        + label
                        + " extracted from JAR ("
                        + result.count()
                        + " keys). Run "
                        + COLOR
                        + "/fpp reload"
                        + C_CLOSE
                        + GRAY
                        + " to load it.");
    }

    private void doDb(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendHelp(sender);
            return;
        }
        DatabaseManager db = plugin.getDatabaseManager();

        switch (args[1].toLowerCase()) {
            case "merge" -> {
                String filename = args.length > 2 ? args[2] : "fpp.db";
                File file = resolveDbFile(filename);

                if (!file.exists()) {
                    msg(sender, RED + "File not found: " + filename);
                    msg(sender, GRAY + "Tip: place the old fpp.db inside " + "plugins/FakePlayerPlugin/data/ and run:");
                    msg(sender, GRAY + "  /fpp migrate db merge " + filename);
                    return;
                }
                if (db == null) {
                    msg(sender, RED + "Database is offline.");
                    return;
                }

                msg(sender, GRAY + "Merging " + file.getName() + " into current database… (this may take a moment)");

                FppScheduler.runAsync(plugin, () -> {
                    int merged = DataMigrator.mergeFromSQLite(plugin, db, file);
                    if (merged >= 0) {
                        sync(sender, GREEN + "✔ Merge complete - " + merged + " row(s) inserted.");
                        sync(sender, GRAY + "  Total sessions now: " + db.countSessions());
                    } else {
                        sync(sender, RED + "✘ Merge failed. Check console for details.");
                    }
                });
            }

            case "export" -> {
                if (db == null) {
                    msg(sender, RED + "Database is offline.");
                    return;
                }
                msg(sender, GRAY + "Exporting session history to CSV…");
                FppScheduler.runAsync(plugin, () -> {
                    File exported = DataMigrator.exportSessionsCsv(plugin, db);
                    if (exported != null) {
                        sync(sender, GREEN + "✔ Exported → " + GRAY + "exports/" + exported.getName());
                    } else {
                        sync(sender, RED + "✘ Export failed. Check console for" + " details.");
                    }
                });
            }

            case "tomysql" -> {
                if (!plugin.getConfig().getBoolean("database.mysql-enabled", false)) {
                    msg(sender, RED + "MySQL is not enabled in config.yml.");
                    msg(
                            sender,
                            GRAY
                                    + "Set database.mysql-enabled: true and fill in your"
                                    + " credentials, then run /fpp reload before using this"
                                    + " command.");
                    return;
                }
                if (db == null) {
                    msg(sender, RED + "Database is offline.");
                    return;
                }
                if (!db.isMysql()) {
                    msg(
                            sender,
                            RED
                                    + "The active database is still SQLite - run /fpp reload first"
                                    + " so the plugin connects to MySQL, then retry.");
                    return;
                }
                msg(sender, GRAY + "Migrating SQLite → MySQL… (this may take a moment)");
                FppScheduler.runAsync(plugin, () -> {
                    int count = DataMigrator.migrateToMysql(plugin, db);
                    if (count >= 0) {
                        sync(sender, GREEN + "✔ Migration complete - " + count + " row(s) transferred.");
                    } else {
                        sync(sender, RED + "✘ Migration failed. Check console for" + " details.");
                    }
                });
            }

            case "schema" -> {
                if (db == null) {
                    msg(sender, RED + "Database is offline.");
                    return;
                }
                int stored = DataMigrator.getStoredSchemaVersion(db);
                int current = DatabaseManager.getCurrentSchemaVersion();
                boolean ok = stored >= current;
                msg(sender, COLOR + "ᴅʙ ꜱᴄʜᴇᴍᴀ ɪɴꜰᴏ" + C_CLOSE);
                msg(
                        sender,
                        GRAY
                                + "  Schema version : "
                                + stored
                                + " / "
                                + current
                                + (ok
                                        ? "  " + GREEN + "✔ current"
                                        : "  " + RED + "✘ outdated - restart the server to apply DB" + " migrations"));
                msg(sender, GRAY + "  Backend        : " + db.getStats().backend());
                msg(sender, GRAY + "  Sessions table : " + db.countSessions() + " row(s)");
                msg(sender, GRAY + "  Active bots    : " + db.countActiveBotRows() + " row(s)");
            }

            case "cleanup" -> {
                if (db == null) {
                    msg(sender, RED + "Database is offline.");
                    return;
                }
                msg(sender, GRAY + "Scanning for stale fpp_active_bots rows…");
                FppScheduler.runAsync(plugin, () -> {
                    int removed = DataMigrator.cleanupStaleActiveBots(plugin, db);
                    if (removed > 0) {
                        sync(sender, GREEN + "✔ Cleanup complete - " + removed + " stale row(s) removed.");
                    } else if (removed == 0) {
                        sync(sender, GREEN + "✔ No stale rows found - fpp_active_bots" + " is clean.");
                    } else {
                        sync(sender, RED + "✘ Cleanup failed. Check console for" + " details.");
                    }
                });
            }

            case "repair" -> {
                if (db == null) {
                    msg(sender, RED + "Database is offline.");
                    return;
                }
                msg(sender, GRAY + "Scanning for orphaned open sessions…");
                FppScheduler.runAsync(plugin, () -> {
                    int repaired = DataMigrator.repairOrphanedSessions(plugin, db);
                    if (repaired > 0) {
                        sync(
                                sender,
                                GREEN
                                        + "✔ Repair complete - "
                                        + repaired
                                        + " orphaned session(s) closed as"
                                        + " ORPHAN_REPAIR.");
                    } else if (repaired == 0) {
                        sync(sender, GREEN + "✔ No orphaned sessions found - database" + " is consistent.");
                    } else {
                        sync(sender, RED + "✘ Repair failed. Check console for" + " details.");
                    }
                });
            }

            default -> sendHelp(sender);
        }
    }

    private void doApply(CommandSender sender) {
        msg(sender, COLOR + "ᴍɪɢʀᴀᴛᴇ ᴀᴘᴘʟʏ" + C_CLOSE + GRAY + " — starting full migration sequence…");

        msg(sender, GRAY + "  [1/2] Reloading config, language and badword filter…");
        try {
            plugin.reloadConfig();
            Config.reload();
            Lang.reload();
            BotNameConfig.reload();
            BadwordFilter.reload(plugin);
            msg(sender, GREEN + "  ✔ " + GRAY + "Config, lang and badword filter reloaded.");
        } catch (Exception e) {
            msg(sender, RED + "  ✘ " + GRAY + "Reload failed: " + e.getMessage());
            return;
        }

        msg(sender, GRAY + "  [2/2] Scanning for bots with badword names…");
        FakePlayerManager manager = plugin.getFakePlayerManager();
        if (manager == null) {
            msg(sender, RED + "  ✘ " + GRAY + "FakePlayerManager not available — skipping badword step.");
        } else if (!Config.isBadwordFilterEnabled()) {
            msg(sender, YELLOW + "  ⚠ " + GRAY + "Badword filter is disabled — skipping rename step.");
        } else if (BadwordFilter.getBadwordCount() == 0) {
            msg(sender, YELLOW + "  ⚠ " + GRAY + "No badword sources are active — skipping rename step.");
        } else {

            long flaggedCount = manager.getActivePlayers().stream()
                    .filter(fp -> !BadwordFilter.isAllowed(fp.getName()))
                    .count();
            if (flaggedCount == 0) {
                msg(
                        sender,
                        GREEN
                                + "  ✔ "
                                + GRAY
                                + "All "
                                + manager.getActivePlayers().size()
                                + " active bot(s) have clean names.");
            } else {
                msg(sender, GRAY + "  Found " + flaggedCount + " bot(s) with flagged names — renaming…");

                BadwordCommand badwordCmd = null;
                try {

                    for (FppCommand cmd : plugin.getCommandManager().getCommands()) {
                        if (cmd instanceof BadwordCommand bc) {
                            badwordCmd = bc;
                            break;
                        }
                    }
                } catch (Exception ignored) {
                }
                if (badwordCmd != null) {
                    badwordCmd.doUpdate(sender);
                } else {

                    new BadwordCommand(plugin, manager).doUpdate(sender);
                }
            }
        }

        msg(
                sender,
                COLOR
                        + "ᴍɪɢʀᴀᴛᴇ ᴀᴘᴘʟʏ"
                        + C_CLOSE
                        + GREEN
                        + " complete."
                        + GRAY
                        + " Run "
                        + COLOR
                        + "/fpp reload"
                        + C_CLOSE
                        + GRAY
                        + " to finalize.");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Perm.MIGRATE)) return List.of();
        if (args.length == 1)
            return filter(List.of("backup", "backups", "status", "config", "lang", "names", "db", "apply"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("db"))
            return filter(List.of("schema", "merge", "export", "tomysql", "cleanup", "repair"), args[1]);
        if (args.length == 3 && args[0].equalsIgnoreCase("db") && args[1].equalsIgnoreCase("merge"))
            return filter(List.of("fpp.db", "fpp_old.db", "fpp_backup.db"), args[2]);
        return List.of();
    }

    private void sendHelp(CommandSender sender) {
        msg(sender, COLOR + "ᴍɪɢʀᴀᴛɪᴏɴ & ʙᴀᴄᴋᴜᴘ ꜱʏꜱᴛᴇᴍ" + C_CLOSE);
        row(sender, "/fpp migrate apply", "Reload → fix badword names → refresh bot data");
        row(sender, "/fpp migrate backup", "Create a manual backup now");
        row(sender, "/fpp migrate backups", "List stored backups");
        row(sender, "/fpp migrate status", "Show migration & file sync status");
        row(sender, "/fpp migrate config", "Re-run config.yml migration chain");
        row(sender, "/fpp migrate lang", "Sync missing keys in language/en.yml");
        row(sender, "/fpp migrate names", "Sync missing keys in bot-names.yml");
        row(sender, "/fpp migrate db schema", "Show DB schema version and table stats");
        row(sender, "/fpp migrate db merge [file]", "Merge old fpp.db into current DB");
        row(sender, "/fpp migrate db export", "Export sessions to CSV");
        row(sender, "/fpp migrate db tomysql", "Migrate SQLite → MySQL");
        row(sender, "/fpp migrate db cleanup", "Remove stale fpp_active_bots rows");
        row(sender, "/fpp migrate db repair", "Close orphaned open sessions");
    }

    private void row(CommandSender sender, String cmd, String desc) {
        msg(sender, GRAY + "  " + COLOR + cmd + C_CLOSE + " " + GRAY + "- " + desc);
    }

    private void msg(CommandSender sender, String mm) {
        sender.sendMessage(TextUtil.colorize(mm));
    }

    private void sync(CommandSender sender, String mm) {
        FppScheduler.runSync(plugin, () -> msg(sender, mm));
    }

    private File resolveDbFile(String filename) {

        File inData = new File(plugin.getDataFolder(), "data/" + filename);
        if (inData.exists()) return inData;

        File inPlugins = new File(plugin.getDataFolder().getParentFile(), filename);
        if (inPlugins.exists()) return inPlugins;

        File abs = new File(filename);
        if (abs.isAbsolute()) return abs;

        return inData;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    private static List<String> filter(List<String> options, String partial) {
        String p = partial.toLowerCase();
        return options.stream().filter(o -> o.toLowerCase().startsWith(p)).toList();
    }
}
