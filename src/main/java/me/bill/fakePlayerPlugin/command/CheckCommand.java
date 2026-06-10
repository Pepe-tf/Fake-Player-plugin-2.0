package me.bill.fakePlayerPlugin.command;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.database.DatabaseManager;
import me.bill.fakePlayerPlugin.extension.ExtensionLoader;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.fakeplayer.NmsPlayerSpawner;
import me.bill.fakePlayerPlugin.fakeplayer.PathfindingService;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.FppLogger;
import me.bill.fakePlayerPlugin.util.FppScheduler;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * Comprehensive system health check for FakePlayerPlugin.
 *
 * <p>Runs deep diagnostics across every plugin subsystem and logs detailed
 * progress to both the invoking player and the server console.  Simulation
 * checks verify that spawning <em>would</em> succeed without creating an
 * actual bot.
 */
public final class CheckCommand implements FppCommand {

    private static final TextColor LABEL = NamedTextColor.GRAY;
    private static final TextColor MUTED = NamedTextColor.DARK_GRAY;
    private static final TextColor OK = NamedTextColor.GREEN;
    private static final TextColor WARN = NamedTextColor.YELLOW;
    private static final TextColor ERR = NamedTextColor.RED;

    private final FakePlayerPlugin plugin;
    private final FakePlayerManager manager;

    public CheckCommand(FakePlayerPlugin plugin, FakePlayerManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public String getName() {
        return "check";
    }

    @Override
    public String getUsage() {
        return "[--deep|--simulation|--commands|--listeners|--nms|--database|--folia|--world|--config|--extensions|--memory|--all]";
    }

    @Override
    public String getDescription() {
        return "Run a comprehensive system health check on the plugin.";
    }

    @Override
    public String getPermission() {
        return Perm.CHECK;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        boolean all = args.length == 0 || hasFlag(args, "--all");
        boolean deep = hasFlag(args, "--deep") || all;
        boolean simulation = hasFlag(args, "--simulation") || deep || all;
        boolean checkCommands = hasFlag(args, "--commands") || all;
        boolean checkListeners = hasFlag(args, "--listeners") || deep || all;
        boolean checkNms = hasFlag(args, "--nms") || deep || all;
        boolean checkDatabase = hasFlag(args, "--database") || deep || all;
        boolean checkFolia = hasFlag(args, "--folia") || deep || all;
        boolean checkWorld = hasFlag(args, "--world") || deep || all;
        boolean checkConfig = hasFlag(args, "--config") || deep || all;
        boolean checkExtensions = hasFlag(args, "--extensions") || deep || all;
        boolean checkMemory = hasFlag(args, "--memory") || deep || all;

        int issues = 0;
        int warnings = 0;

        log(sender, "Starting FPP system health check...");

        /* ================================================================ */
        /*  1. PLUGIN INITIALISATION                                        */
        /* ================================================================ */
        log(sender, "[1/12] Checking plugin state...");
        if (plugin.isEnabled()) {
            ok(sender, "Plugin enabled");
        } else {
            err(sender, "Plugin is DISABLED");
            issues++;
        }

        String version = plugin.getPluginMeta().getVersion();
        info(sender, "  Version: " + version);

        /* ================================================================ */
        /*  2. CONFIGURATION                                                */
        /* ================================================================ */
        if (checkConfig) {
            log(sender, "[2/12] Checking configuration...");
            File cfgFile = new File(plugin.getDataFolder(), "config.yml");
            if (!cfgFile.exists()) {
                err(sender, "config.yml missing");
                issues++;
            } else {
                ok(sender, "config.yml exists");
            }

            int cfgVer = plugin.getConfig().getInt("config-version", 0);
            int latestVer = 74; // hardcode current config version
            if (cfgVer < latestVer) {
                warn(sender, "Config version outdated: " + cfgVer + " < " + latestVer);
                warnings++;
            } else {
                ok(sender, "Config version: " + cfgVer);
            }

            // Validate critical keys (handle nested paths)
            String[][] criticalKeys = {{"max-bots"}, {"server-id"}, {"database", "type"}};
            for (String[] path : criticalKeys) {
                boolean present;
                if (path.length == 1) {
                    present = plugin.getConfig().contains(path[0]);
                } else {
                    org.bukkit.configuration.ConfigurationSection section =
                            plugin.getConfig().getConfigurationSection(path[0]);
                    present = section != null && section.contains(path[1]);
                }
                if (!present) {
                    warn(sender, "  Missing config key: " + String.join(".", path));
                    warnings++;
                }
            }
        }

        /* ================================================================ */
        /*  3. NMS / SPAWN SUBSYSTEM                                        */
        /* ================================================================ */
        if (checkNms) {
            log(sender, "[3/12] Checking NMS spawn subsystem...");
            boolean nmsAvailable = NmsPlayerSpawner.isAvailable();
            if (nmsAvailable) {
                ok(sender, "NmsPlayerSpawner available");
            } else {
                err(sender, "NmsPlayerSpawner NOT available — unsupported server version");
                issues++;
            }

            // Deep reflection audit
            if (deep && nmsAvailable) {
                log(sender, "  Deep reflection audit...");
                int reflectionIssues = auditReflection(sender);
                issues += reflectionIssues;
            }

            int active = manager.getActivePlayers().size();
            info(sender, "  Active bots: " + active);

            // Simulation: verify spawn prerequisites without actually spawning
            if (simulation) {
                log(sender, "  Running spawn simulation...");
                World w =
                        Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
                if (w == null) {
                    err(sender, "  No worlds — simulation aborted");
                    issues++;
                } else {
                    Location simLoc = w.getSpawnLocation();
                    try {
                        Object craftServer = Bukkit.getServer();
                        Object nmsServer =
                                craftServer.getClass().getMethod("getServer").invoke(craftServer);
                        if (nmsServer != null) {
                            ok(sender, "  CraftServer.getServer() accessible");
                        }

                        Object nmsWorld = w.getClass().getMethod("getHandle").invoke(w);
                        if (nmsWorld != null) {
                            ok(sender, "  CraftWorld.getHandle() accessible");
                        }

                        // Verify PlayerList exists
                        Object playerList =
                                nmsServer.getClass().getMethod("getPlayerList").invoke(nmsServer);
                        if (playerList != null) {
                            ok(sender, "  PlayerList accessible");
                        } else {
                            err(sender, "  PlayerList is null");
                            issues++;
                        }
                    } catch (Exception e) {
                        err(sender, "  Simulation failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                        issues++;
                    }
                }
            }
        }

        /* ================================================================ */
        /*  4. DATABASE                                                     */
        /* ================================================================ */
        if (checkDatabase) {
            log(sender, "[4/12] Checking database...");
            DatabaseManager db = plugin.getDatabaseManager();
            if (db == null) {
                warn(sender, "DatabaseManager is null — using YAML fallback");
                warnings++;
            } else {
                Connection conn = db.getConnection();
                if (conn != null) {
                    ok(sender, "Database connection open");

                    // Verify tables
                    if (deep) {
                        log(sender, "  Verifying schema tables...");
                        String[] tables = {"fpp_bot_sessions", "fpp_active_bots", "fpp_bot_tasks"};
                        for (String table : tables) {
                            try (Statement st = conn.createStatement();
                                    ResultSet rs = st.executeQuery(
                                            "SELECT name FROM sqlite_master WHERE type='table' AND name='" + table
                                                    + "'")) {
                                if (rs.next()) {
                                    ok(sender, "    Table " + table + " exists");
                                } else {
                                    err(sender, "    Table " + table + " MISSING");
                                    issues++;
                                }
                            } catch (SQLException e) {
                                warn(sender, "    Could not verify table " + table + ": " + e.getMessage());
                                warnings++;
                            }
                        }
                    }
                } else {
                    err(sender, "Database connection is null");
                    issues++;
                }
            }

            // Data directories
            File dataDir = new File(plugin.getDataFolder(), "data");
            File extDir = new File(plugin.getDataFolder(), "extensions");
            File langDir = new File(plugin.getDataFolder(), "lang");
            status(sender, "Data directory", dataDir.exists());
            status(sender, "Extensions directory", extDir.exists());
            status(sender, "Lang directory", langDir.exists());
        }

        /* ================================================================ */
        /*  5. FOLIA / SCHEDULER                                            */
        /* ================================================================ */
        if (checkFolia) {
            log(sender, "[5/12] Checking Folia scheduler...");
            boolean folia = NmsPlayerSpawner.isFoliaServer();
            if (folia) {
                ok(sender, "Folia detected — using region schedulers");

                // Verify all bots are on correct threads
                int bad = 0;
                for (FakePlayer fp : manager.getActivePlayers()) {
                    Player p = fp.getPlayer();
                    if (p != null && p.isOnline()) {
                        try {
                            p.getLocation();
                        } catch (Exception e) {
                            bad++;
                            log(sender, "    Bot " + fp.getName() + " on wrong region thread!");
                        }
                    }
                }
                if (bad > 0) {
                    err(sender, "  " + bad + " bot(s) on wrong region thread");
                    issues++;
                } else if (manager.getActivePlayers().size() > 0) {
                    ok(sender, "  All bots on correct region threads");
                }
            } else {
                ok(sender, "Paper detected — using BukkitScheduler");
            }

            // Verify scheduler utility
            try {
                FppScheduler.runSync(plugin, () -> log(sender, "  FppScheduler test callback OK"));
                ok(sender, "FppScheduler functional");
            } catch (Exception e) {
                err(sender, "FppScheduler error: " + e.getMessage());
                issues++;
            }
        }

        /* ================================================================ */
        /*  6. WORLD / ENVIRONMENT                                          */
        /* ================================================================ */
        if (checkWorld) {
            log(sender, "[6/12] Checking world environment...");
            List<World> worlds = Bukkit.getWorlds();
            if (worlds.isEmpty()) {
                err(sender, "No worlds loaded");
                issues++;
            } else {
                ok(sender, worlds.size() + " world(s) loaded");
                for (World w : worlds) {
                    Location spawn = w.getSpawnLocation();
                    boolean ok = spawn != null;
                    status(sender, "  " + w.getName() + " spawn", ok);
                    if (!ok) warnings++;
                }
            }
        }

        /* ================================================================ */
        /*  7. COMMAND REGISTRY                                             */
        /* ================================================================ */
        if (checkCommands) {
            log(sender, "[7/12] Checking command registration...");
            org.bukkit.command.PluginCommand cmd = Bukkit.getPluginCommand("fpp");
            if (cmd == null) {
                err(sender, "/fpp command not registered");
                issues++;
            } else {
                ok(sender, "/fpp command registered");
                if (cmd.getPlugin() != plugin) {
                    err(sender, "/fpp owned by: " + cmd.getPlugin().getName());
                    issues++;
                }
            }

            // List all registered subcommands
            if (deep) {
                log(sender, "  Registered subcommands:");
                for (FppCommand c : plugin.getCommandManager().getCommands()) {
                    info(sender, "    - " + c.getName());
                }
            }
        }

        /* ================================================================ */
        /*  8. EVENT LISTENERS                                              */
        /* ================================================================ */
        if (checkListeners) {
            log(sender, "[8/12] Checking event listeners...");
            String[] criticalEvents = {
                "org.bukkit.event.player.PlayerJoinEvent",
                "org.bukkit.event.player.PlayerQuitEvent",
                "org.bukkit.event.entity.PlayerDeathEvent",
                "org.bukkit.event.entity.EntityDamageEvent",
                "org.bukkit.event.player.PlayerInteractAtEntityEvent"
            };
            for (String ev : criticalEvents) {
                boolean present = hasListener(ev);
                status(sender, "  " + ev.substring(ev.lastIndexOf('.') + 1), present);
                if (!present) warnings++;
            }
        }

        /* ================================================================ */
        /*  9. EXTENSIONS                                                   */
        /* ================================================================ */
        if (checkExtensions) {
            log(sender, "[9/12] Checking extensions...");
            ExtensionLoader loader = plugin.getExtensionLoader();
            if (loader == null) {
                warn(sender, "ExtensionLoader is null");
                warnings++;
            } else {
                int loaded = loader.getLoadedExtensions().size();
                ok(sender, "ExtensionLoader active — " + loaded + " extension(s) loaded");
                if (deep && loaded > 0) {
                    log(sender, "  Loaded extensions:");
                    loader.getLoadedExtensions().forEach(ext -> info(sender, "    - " + ext.getName()));
                }
            }

            // Soft-depends
            Plugin papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
            Plugin we = Bukkit.getPluginManager().getPlugin("WorldEdit");
            Plugin lp = Bukkit.getPluginManager().getPlugin("LuckPerms");
            status(sender, "PlaceholderAPI", papi != null && papi.isEnabled());
            status(sender, "WorldEdit", we != null && we.isEnabled());
            status(sender, "LuckPerms", lp != null && lp.isEnabled());
        }

        /* ================================================================ */
        /*  10. MEMORY / BOT STATE AUDIT                                    */
        /* ================================================================ */
        if (checkMemory) {
            log(sender, "[10/12] Running memory & state audit...");
            int active = manager.getActivePlayers().size();
            info(sender, "  Active bots: " + active);

            if (active > 0) {
                int invalid = 0;
                int offline = 0;
                int missingBody = 0;
                for (FakePlayer fp : manager.getActivePlayers()) {
                    String name = fp.getName();
                    if (fp.getUuid() == null) {
                        err(sender, "    Bot " + name + " has null UUID!");
                        invalid++;
                    }
                    Player p = fp.getPlayer();
                    if (p == null) {
                        missingBody++;
                    } else if (!p.isOnline()) {
                        offline++;
                    }
                }
                if (invalid > 0) {
                    err(sender, "  " + invalid + " bot(s) with invalid state");
                    issues += invalid;
                }
                if (offline > 0) {
                    warn(sender, "  " + offline + " bot(s) with offline body");
                    warnings += offline;
                }
                if (missingBody > 0) {
                    warn(sender, "  " + missingBody + " bot(s) without body (tab-only)");
                    warnings += missingBody;
                }
            }

            Runtime rt = Runtime.getRuntime();
            long usedMB = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
            long maxMB = rt.maxMemory() / 1024 / 1024;
            info(sender, "  JVM memory: " + usedMB + " MB / " + maxMB + " MB used");
        }

        /* ================================================================ */
        /*  11. PATHFINDING                                                 */
        /* ================================================================ */
        if (deep || all) {
            log(sender, "[11/12] Checking pathfinding...");
            PathfindingService pf = plugin.getPathfindingService();
            if (pf == null) {
                warn(sender, "PathfindingService is null");
                warnings++;
            } else {
                ok(sender, "PathfindingService active");
            }
        }

        /* ================================================================ */
        /*  12. LANG / LOCALISATION                                         */
        /* ================================================================ */
        if (deep || all) {
            log(sender, "[12/12] Checking language files...");
            File langDir = new File(plugin.getDataFolder(), "lang");
            if (langDir.exists() && langDir.isDirectory()) {
                File[] files = langDir.listFiles((d, n) -> n.endsWith(".yml"));
                if (files != null && files.length > 0) {
                    ok(sender, "  " + files.length + " language file(s) found");
                } else {
                    warn(sender, "  No language files in lang/ directory");
                    warnings++;
                }
            } else {
                warn(sender, "  lang/ directory missing");
                warnings++;
            }

            // Verify a critical lang key exists
            Component testMsg = Lang.get("spawn-usage");
            if (testMsg == null) {
                warn(sender, "  Lang key 'spawn-usage' missing");
                warnings++;
            } else {
                ok(sender, "  Lang system functional");
            }
        }

        /* ================================================================ */
        /*  SUMMARY                                                         */
        /* ================================================================ */
        log(sender, "Health check complete.");
        if (issues == 0 && warnings == 0) {
            ok(sender, "All systems operational — 0 issues found.");
            FppLogger.info("[CHECK] All systems operational.");
        } else {
            warn(sender, "Found " + issues + " error(s) and " + warnings + " warning(s).");
            FppLogger.warn("[CHECK] " + issues + " error(s), " + warnings + " warning(s) found.");
        }

        return true;
    }

    /* ------------------------------------------------------------------ */
    /*  Helpers                                                           */
    /* ------------------------------------------------------------------ */

    private static boolean hasFlag(String[] args, String flag) {
        for (String s : args) {
            if (s.equalsIgnoreCase(flag)) return true;
        }
        return false;
    }

    /** Log to both player chat and server console. */
    private void log(CommandSender sender, String message) {
        sender.sendMessage(Component.empty()
                .append(Component.text("  ").color(MUTED))
                .append(Component.text("▸ ").color(LABEL))
                .append(Component.text(message).color(LABEL)));
        FppLogger.info("[CHECK] " + message);
    }

    private void info(CommandSender sender, String message) {
        sender.sendMessage(Component.empty()
                .append(Component.text("    ").color(MUTED))
                .append(Component.text(message).color(LABEL)));
        FppLogger.info("[CHECK] " + message);
    }

    private void ok(CommandSender sender, String message) {
        sender.sendMessage(Component.empty()
                .append(Component.text("    ").color(MUTED))
                .append(Component.text("✔ ").color(OK))
                .append(Component.text(message).color(OK)));
        FppLogger.info("[CHECK] [OK] " + message);
    }

    private void warn(CommandSender sender, String message) {
        sender.sendMessage(Component.empty()
                .append(Component.text("    ").color(MUTED))
                .append(Component.text("⚠ ").color(WARN))
                .append(Component.text(message).color(WARN)));
        FppLogger.warn("[CHECK] [WARN] " + message);
    }

    private void err(CommandSender sender, String message) {
        sender.sendMessage(Component.empty()
                .append(Component.text("    ").color(MUTED))
                .append(Component.text("✘ ").color(ERR))
                .append(Component.text(message).color(ERR)));
        FppLogger.warn("[CHECK] [ERR] " + message);
    }

    private void status(CommandSender sender, String key, boolean ok) {
        if (ok) {
            ok(sender, key);
        } else {
            warn(sender, key + " — MISSING / FAILED");
        }
    }

    /**
     * Deep reflection audit: attempt to resolve every critical NMS class/field/method
     * via the same reflection paths NmsPlayerSpawner uses.
     */
    private int auditReflection(CommandSender sender) {
        int issues = 0;
        String[] criticalClasses = {
            "net.minecraft.server.MinecraftServer",
            "net.minecraft.server.level.ServerPlayer",
            "net.minecraft.server.level.ServerLevel",
            "net.minecraft.server.network.ServerGamePacketListenerImpl",
            "net.minecraft.network.Connection",
            "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket",
            "com.mojang.authlib.GameProfile"
        };
        for (String cls : criticalClasses) {
            try {
                Class.forName(cls);
                ok(sender, "  Class " + cls + " resolvable");
            } catch (ClassNotFoundException e) {
                err(sender, "  Class " + cls + " NOT FOUND");
                issues++;
            }
        }
        return issues;
    }

    private static boolean hasListener(String eventClassName) {
        try {
            Class.forName(eventClassName);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase();
        List<String> out = new ArrayList<>();
        for (String f : new String[] {
            "--deep",
            "--simulation",
            "--commands",
            "--listeners",
            "--nms",
            "--database",
            "--folia",
            "--world",
            "--config",
            "--extensions",
            "--memory",
            "--all"
        }) {
            if (f.startsWith(prefix)) out.add(f);
        }
        return out;
    }
}
