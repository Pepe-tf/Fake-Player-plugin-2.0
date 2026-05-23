package me.bill.fakePlayerPlugin;

import me.bill.fakePlayerPlugin.api.FppApi;
import me.bill.fakePlayerPlugin.api.impl.FppApiImpl;
import me.bill.fakePlayerPlugin.command.AttackCommand;
import me.bill.fakePlayerPlugin.command.BadwordCommand;
import me.bill.fakePlayerPlugin.command.CommandManager;
import me.bill.fakePlayerPlugin.command.DeleteCommand;
import me.bill.fakePlayerPlugin.command.ExtensionCommand;
import me.bill.fakePlayerPlugin.command.FindCommand;
import me.bill.fakePlayerPlugin.command.FollowCommand;
import me.bill.fakePlayerPlugin.command.FreezeCommand;
import me.bill.fakePlayerPlugin.command.InfoCommand;
import me.bill.fakePlayerPlugin.command.InventoryCommand;
import me.bill.fakePlayerPlugin.command.ListCommand;
import me.bill.fakePlayerPlugin.command.MigrateCommand;
import me.bill.fakePlayerPlugin.command.MineCommand;
import me.bill.fakePlayerPlugin.command.MoveCommand;
import me.bill.fakePlayerPlugin.command.PlaceCommand;
import me.bill.fakePlayerPlugin.command.ReloadCommand;
import me.bill.fakePlayerPlugin.command.RenameCommand;
import me.bill.fakePlayerPlugin.command.SaveCommand;
import me.bill.fakePlayerPlugin.command.SetOwnerCommand;
import me.bill.fakePlayerPlugin.command.SettingCommand;
import me.bill.fakePlayerPlugin.command.SleepCommand;
import me.bill.fakePlayerPlugin.command.SpawnCommand;
import me.bill.fakePlayerPlugin.command.StatsCommand;
import me.bill.fakePlayerPlugin.command.StopCommand;
import me.bill.fakePlayerPlugin.command.StorageCommand;
import me.bill.fakePlayerPlugin.command.StorageStore;
import me.bill.fakePlayerPlugin.command.TpCommand;
import me.bill.fakePlayerPlugin.command.TphCommand;
import me.bill.fakePlayerPlugin.command.UseCommand;
import me.bill.fakePlayerPlugin.command.XpCommand;
import me.bill.fakePlayerPlugin.config.BotNameConfig;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.database.DatabaseManager;
import me.bill.fakePlayerPlugin.extension.ExtensionLoader;
import me.bill.fakePlayerPlugin.fakeplayer.BotChatController;
import me.bill.fakePlayerPlugin.fakeplayer.BotIdentityCache;
import me.bill.fakePlayerPlugin.fakeplayer.BotPersistence;
import me.bill.fakePlayerPlugin.fakeplayer.ChunkLoader;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.fakeplayer.PathfindingService;
import me.bill.fakePlayerPlugin.fakeplayer.RemoteBotCache;
import me.bill.fakePlayerPlugin.fakeplayer.RemoteBotEntry;
import me.bill.fakePlayerPlugin.fakeplayer.SkinFetchService;
import me.bill.fakePlayerPlugin.fakeplayer.SkinManager;
import me.bill.fakePlayerPlugin.gui.BotSettingGui;
import me.bill.fakePlayerPlugin.gui.HelpGui;
import me.bill.fakePlayerPlugin.gui.SettingGui;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.license.LicenseCredentialsApi;
import me.bill.fakePlayerPlugin.license.LicenseManager;
import me.bill.fakePlayerPlugin.listener.BotCollisionListener;
import me.bill.fakePlayerPlugin.listener.BotLoginOverrideListener;
import me.bill.fakePlayerPlugin.listener.BotSpawnProtectionListener;
import me.bill.fakePlayerPlugin.listener.BotXpPickupListener;
import me.bill.fakePlayerPlugin.listener.FakePlayerEntityListener;
import me.bill.fakePlayerPlugin.listener.FakePlayerKickListener;
import me.bill.fakePlayerPlugin.listener.PlayerJoinListener;
import me.bill.fakePlayerPlugin.listener.PlayerWorldChangeListener;
import me.bill.fakePlayerPlugin.messaging.VelocityChannel;
import me.bill.fakePlayerPlugin.network.NetworkHeartbeatManager;
import me.bill.fakePlayerPlugin.sync.ConfigSyncManager;
import me.bill.fakePlayerPlugin.util.AttributionApiManager;
import me.bill.fakePlayerPlugin.util.AttributionManager;
import me.bill.fakePlayerPlugin.util.BackupManager;
import me.bill.fakePlayerPlugin.util.BadwordFilter;
import me.bill.fakePlayerPlugin.util.CompatibilityChecker;
import me.bill.fakePlayerPlugin.util.ConfigMigrator;
import me.bill.fakePlayerPlugin.util.ConfigValidator;
import me.bill.fakePlayerPlugin.util.FppLogger;
import me.bill.fakePlayerPlugin.util.FppMetrics;
import me.bill.fakePlayerPlugin.util.FppPlaceholderExpansion;
import me.bill.fakePlayerPlugin.util.FppScheduler;
import me.bill.fakePlayerPlugin.util.HeartbeatSender;
import me.bill.fakePlayerPlugin.util.UpdateChecker;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.UUID;

public final class FakePlayerPlugin extends JavaPlugin {

  private static FakePlayerPlugin instance;

  @SuppressWarnings("unused")
  public static FakePlayerPlugin getInstance() {
    return instance;
  }

  private CommandManager commandManager;
  private FakePlayerManager fakePlayerManager;
  private ChunkLoader chunkLoader;
  private DatabaseManager databaseManager;
  private BotPersistence botPersistence;
  private FppMetrics fppMetrics;
  private VelocityChannel velocityChannel;
  private BotChatController botChatAI;
  private RemoteBotCache remoteBotCache;
  private ConfigSyncManager configSyncManager;
  private SettingGui settingGui;
  private BotSettingGui botSettingGui;
  private HelpGui helpGui;
  private BotIdentityCache botIdentityCache;
  private NetworkHeartbeatManager networkHeartbeat;
  private XpCommand xpCommand;
  private MoveCommand moveCommand;
  private MineCommand mineCommand;
  private PlaceCommand placeCommand;
  private UseCommand useCommand;
  private AttackCommand attackCommand;
  private FollowCommand followCommand;
  private SleepCommand sleepCommand;
  private FindCommand findCommand;
  private StopCommand stopCommand;
  private PathfindingService pathfindingService;
  private StorageStore storageStore;
  private InventoryCommand inventoryCommand;
  private SkinManager skinManager;
  private SkinFetchService skinFetchService =
      SkinFetchService.NOOP;
  private HeartbeatSender heartbeatSender;
  private LicenseManager licenseManager;

  private FppApiImpl fppApi;
  private ExtensionLoader extensionLoader;

  private Component updateNotificationMessage = null;

  private boolean worldGuardAvailable = false;

  public boolean isWorldGuardAvailable() {
    return worldGuardAvailable;
  }

  private boolean worldEditAvailable = false;

  public boolean isWorldEditAvailable() {
    return worldEditAvailable;
  }

  private boolean versionUnsupported = false;

  private String detectedMcVersion = "unknown";

  public boolean isVersionUnsupported() {
    return versionUnsupported;
  }

  public String getDetectedMcVersion() {
    return detectedMcVersion;
  }

  private long enabledAt;

  @Override
  public void onEnable() {
    instance = this;
    enabledAt = System.currentTimeMillis();
    FppLogger.init(getLogger());

    ConfigMigrator.migrateIfNeeded(this);

    Config.init(this);
    Config.debugStartup("config.yml loaded.");

    // ── License Verification ─────────────────────────────────────────────────
    FppLogger.debug("LICENSE", Config.debugLicense(), "Starting license credential fetch...");
    LicenseCredentialsApi.Credentials credentials = LicenseCredentialsApi.fetch(this);
    if (credentials == null) {
      FppLogger.warn("═══════════════════════════════════════════════════════════════════");
      FppLogger.warn("  ⚠  LICENSE CREDENTIALS NOT AVAILABLE - PLUGIN DISABLED ⚠");
      FppLogger.warn("═══════════════════════════════════════════════════════════════════");
      FppLogger.warn("  Reason: Could not fetch valid license credentials from fpp.wtf");
      FppLogger.warn("  Check your internet connection and try again.");
      FppLogger.warn("═══════════════════════════════════════════════════════════════════");
      getServer().getPluginManager().disablePlugin(this);
      return;
    }

    licenseManager = new LicenseManager(this, credentials);
    FppLogger.debug("LICENSE", Config.debugLicense(), "LicenseManager created with credentials.");
    try {
      FppLogger.info("Verifying license...");
      licenseManager.verify();
      licenseManager.startHeartbeat();
      FppLogger.info("License verification passed.");
      FppLogger.debug("LICENSE", Config.debugLicense(), "License heartbeat scheduler started (15 min interval).");
    } catch (Exception e) {
      FppLogger.warn("═══════════════════════════════════════════════════════════════════");
      FppLogger.warn("  ⚠  LICENSE VERIFICATION FAILED - PLUGIN DISABLED ⚠");
      FppLogger.warn("═══════════════════════════════════════════════════════════════════");
      FppLogger.warn("  Reason: " + e.getMessage());
      FppLogger.debug("LICENSE", Config.debugLicense(), "License verification exception: " + e.getMessage());
      FppLogger.warn("═══════════════════════════════════════════════════════════════════");
      getServer().getPluginManager().disablePlugin(this);
      return;
    }

    AttributionManager.validate(this);
    AttributionApiManager.init(this);

    BadwordFilter.reload(this);
    if (Config.isBadwordFilterEnabled() && BadwordFilter.getBadwordCount() == 0) {
      FppLogger.warn("═══════════════════════════════════════════════════════════════════");
      FppLogger.warn("  ⚠  BADWORD FILTER IS ENABLED BUT NO SOURCES ARE ACTIVE  ⚠");
      FppLogger.warn("  Enable 'badword-filter.use-global-list' or add words to");
      FppLogger.warn("  'badword-filter.words' / 'bad-words.yml', then run /fpp reload");
      FppLogger.warn("═══════════════════════════════════════════════════════════════════");
    }

    Lang.init(this);
    Config.debugStartup("Language file loaded (lang=" + Config.getLanguage() + ").");

    detectedMcVersion = CompatibilityChecker.extractMcVersion();
    if (!CompatibilityChecker.isSupportedVersion(detectedMcVersion)) {
      versionUnsupported = true;
      String pv = getPluginMeta().getVersion();
      FppLogger.warn("═══════════════════════════════════════════════════════════════════");
      FppLogger.warn("  ⚠  FakePlayerPlugin - UNSUPPORTED MINECRAFT VERSION  ⚠");
      FppLogger.warn("═══════════════════════════════════════════════════════════════════");
      FppLogger.warn("  Plugin    : FakePlayerPlugin v" + pv);
      FppLogger.warn("  Server MC : " + detectedMcVersion + "  (NOT supported)");
      FppLogger.warn("  Supported : up to MC 1.21.11, and 26.1.x");
      FppLogger.warn("  Action    : All /fpp commands have been DISABLED.");
      FppLogger.warn("  Support   : If you think this is a bug, contact us:");
      FppLogger.warn("              Discord → https://discord.gg/RfjEJDG2TM");
      FppLogger.warn("═══════════════════════════════════════════════════════════════════");
    }

    BotNameConfig.init(this);
    Config.debugStartup("Bot name pool: " + BotNameConfig.getNames().size() + " names.");

    ensureDataDirectories();

    boolean dbOk = false;
    if (Config.databaseEnabled()) {
      databaseManager = new DatabaseManager();
      dbOk = databaseManager.init(this);
      if (!dbOk) {
        FppLogger.warn("Database could not be initialised - session tracking disabled.");
        databaseManager = null;
      } else {

        String mode = Config.databaseMode();
        String serverId = Config.serverId();
        Config.debugDatabase("Database mode: " + mode + " | server-id=" + serverId);

        databaseManager.cleanExpiredSkinCache();
      }
    } else {
      Config.debugDatabase("Database disabled in config - skipping database initialisation.");
      databaseManager = null;
    }

    botIdentityCache =
        new BotIdentityCache(this, databaseManager);
    Config.debugDatabase(
        "BotIdentityCache initialised (backend="
            + (databaseManager != null ? (Config.mysqlEnabled() ? "MySQL" : "SQLite") : "YAML")
            + ").");

    remoteBotCache = new RemoteBotCache();
    if (Config.isNetworkMode() && databaseManager != null) {
      var remoteRows = databaseManager.getNetworkBotsFromOtherServers();
      for (var row : remoteRows) {
        try {
          UUID uuid = UUID.fromString(row.botUuid());
          String display =
              (row.botDisplay() != null && !row.botDisplay().isBlank())
                  ? row.botDisplay()
                  : row.botName();
          remoteBotCache.add(
              new RemoteBotEntry(
                  row.serverId(), uuid, row.botName(), display, row.botName(), "", "", -1));
        } catch (Exception ignored) {
        }
      }
      Config.debugNetwork(
          "Remote bot cache pre-populated from DB: " + remoteBotCache.count() + " bot(s).");
    }

    if (Config.isNetworkMode() && databaseManager != null) {
      configSyncManager =
          new ConfigSyncManager(this, databaseManager);
      configSyncManager.init();
      Config.debugConfigSync(
          "Config sync manager initialized (mode=" + Config.configSyncMode() + ").");
    } else {
      configSyncManager = null;
    }

    fakePlayerManager = new FakePlayerManager(this);
    if (databaseManager != null) fakePlayerManager.setDatabaseManager(databaseManager);
    fakePlayerManager.setIdentityCache(botIdentityCache);

    fakePlayerManager.refreshCleanNamePool();

    fppApi = new FppApiImpl(this, fakePlayerManager);

    // Load persisted despawn snapshots (DB primary, YAML fallback) so bots that were manually
    // despawned before the restart can have their inventory/XP restored on next spawn.
    fakePlayerManager.initDespawnSnapshots();

    chunkLoader = new ChunkLoader(this, fakePlayerManager);
    fakePlayerManager.setChunkLoader(chunkLoader);

    botPersistence = new BotPersistence(this);
    fakePlayerManager.setBotPersistence(botPersistence);

    networkHeartbeat = new NetworkHeartbeatManager(this, fakePlayerManager);
    if (Config.isNetworkMode() && databaseManager != null) {
      networkHeartbeat.start();
    }

    pathfindingService = new PathfindingService(this, fakePlayerManager);
    commandManager = new CommandManager(this);
    commandManager.register(new SpawnCommand(fakePlayerManager));
    commandManager.register(new DeleteCommand(fakePlayerManager));
    commandManager.register(new ListCommand(this, fakePlayerManager));
    commandManager.register(new TphCommand(fakePlayerManager));
    commandManager.register(new TpCommand(fakePlayerManager));
    xpCommand = new XpCommand(this, fakePlayerManager);
    commandManager.register(xpCommand);
    commandManager.register(new ReloadCommand(this));
    commandManager.register(new InfoCommand(databaseManager, fakePlayerManager));
    commandManager.register(new MigrateCommand(this));
    commandManager.register(
        new BadwordCommand(this, fakePlayerManager));
    commandManager.register(new StatsCommand(fakePlayerManager, databaseManager));
    commandManager.register(new ExtensionCommand(this));
    commandManager.register(new FreezeCommand(fakePlayerManager));
    commandManager.register(
        new RenameCommand(this, fakePlayerManager));
    moveCommand =
        new MoveCommand(
            this, fakePlayerManager, pathfindingService);
    storageStore = new StorageStore(this);
    storageStore.load();
    commandManager.register(moveCommand);
    mineCommand =
        new MineCommand(
            this, fakePlayerManager, storageStore, pathfindingService);
    commandManager.register(mineCommand);
    findCommand =
        new FindCommand(
            this, fakePlayerManager, pathfindingService, mineCommand);
    mineCommand.setFindCommand(findCommand);
    commandManager.register(findCommand);
    commandManager.register(
        new StorageCommand(this, fakePlayerManager, storageStore, pathfindingService));
    placeCommand =
        new PlaceCommand(
            this, fakePlayerManager, storageStore, pathfindingService);
    commandManager.register(placeCommand);
    useCommand =
        new UseCommand(
            this, fakePlayerManager, pathfindingService);
    commandManager.register(useCommand);
    attackCommand =
        new AttackCommand(
            this, fakePlayerManager, pathfindingService);
    commandManager.register(attackCommand);
    followCommand =
        new FollowCommand(
            this, fakePlayerManager, pathfindingService);
    commandManager.register(followCommand);
    sleepCommand =
        new SleepCommand(
            this, fakePlayerManager, pathfindingService);
    commandManager.register(sleepCommand);

    botSettingGui = new BotSettingGui(this, fakePlayerManager);
    inventoryCommand = new InventoryCommand(fakePlayerManager, this, botSettingGui);
    commandManager.register(inventoryCommand);
    commandManager.register(new SetOwnerCommand(this, fakePlayerManager));
    commandManager.register(new SaveCommand(this));

    settingGui = new SettingGui(this);
    commandManager.register(new SettingCommand(settingGui, botSettingGui, fakePlayerManager));
    Config.debugStartup("Commands registered: " + commandManager.getCommands().size() + " total.");

    botPersistence.setMoveCommand(moveCommand);
    botPersistence.setMineCommand(mineCommand);
    botPersistence.setPlaceCommand(placeCommand);
    botPersistence.setUseCommand(useCommand);
    botPersistence.setAttackCommand(attackCommand);
    botPersistence.setFollowCommand(followCommand);
    sleepCommand.setMineCommand(mineCommand);
    sleepCommand.setUseCommand(useCommand);
    sleepCommand.setPlaceCommand(placeCommand);
    sleepCommand.setAttackCommand(attackCommand);
    sleepCommand.setFollowCommand(followCommand);
    sleepCommand.setMoveCommand(moveCommand);
    sleepCommand.setFindCommand(findCommand);

    stopCommand = new StopCommand(fakePlayerManager);
    stopCommand.setMoveCommand(moveCommand);
    stopCommand.setMineCommand(mineCommand);
    stopCommand.setUseCommand(useCommand);
    stopCommand.setPlaceCommand(placeCommand);
    stopCommand.setAttackCommand(attackCommand);
    stopCommand.setFollowCommand(followCommand);
    stopCommand.setFindCommand(findCommand);
    stopCommand.setSleepCommand(sleepCommand);
    commandManager.register(stopCommand);

    var fppCmd = getCommand("fpp");
    if (fppCmd != null) {
      fppCmd.setExecutor(commandManager);
      fppCmd.setTabCompleter(commandManager);
    }

    getServer()
        .getPluginManager()
        .registerEvents(new PlayerJoinListener(this, fakePlayerManager), this);
    getServer()
        .getPluginManager()
        .registerEvents(new PlayerWorldChangeListener(this, fakePlayerManager), this);
    getServer()
        .getPluginManager()
        .registerEvents(new FakePlayerEntityListener(this, fakePlayerManager, chunkLoader), this);
    getServer()
        .getPluginManager()
        .registerEvents(new BotCollisionListener(this, fakePlayerManager), this);

    getServer()
        .getPluginManager()
        .registerEvents(new FakePlayerKickListener(fakePlayerManager), this);

    getServer().getPluginManager().registerEvents(settingGui, this);
    getServer().getPluginManager().registerEvents(botSettingGui, this);
    getServer().getPluginManager().registerEvents(inventoryCommand, this);
    getServer()
        .getPluginManager()
        .registerEvents(
            new BotSpawnProtectionListener(this), this);
    getServer()
        .getPluginManager()
        .registerEvents(new BotLoginOverrideListener(this, fakePlayerManager), this);
    getServer()
        .getPluginManager()
        .registerEvents(
            new BotXpPickupListener(this, fakePlayerManager),
            this);

    helpGui = new HelpGui(this, commandManager);
    getServer().getPluginManager().registerEvents(helpGui, this);
    commandManager.setHelpGui(helpGui);

    extensionLoader = new ExtensionLoader(this);
    extensionLoader.loadExtensions();

    velocityChannel = new VelocityChannel(this, fakePlayerManager);
    getServer().getMessenger().registerOutgoingPluginChannel(this, VelocityChannel.CHANNEL);
    getServer().getMessenger().registerOutgoingPluginChannel(this, VelocityChannel.PROXY_CHANNEL);
    getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
    getServer()
        .getMessenger()
        .registerIncomingPluginChannel(this, VelocityChannel.CHANNEL, velocityChannel);
    getServer()
        .getMessenger()
        .registerIncomingPluginChannel(this, VelocityChannel.PROXY_CHANNEL, velocityChannel);
    Config.debugNetwork(
        "Plugin messaging channels registered: " + VelocityChannel.CHANNEL + " + " + VelocityChannel.PROXY_CHANNEL + " + BungeeCord.");

    FppScheduler.runSyncRepeating(
        this,
        () -> {
          if (fakePlayerManager.getCount() > 0) {
            fakePlayerManager.validateEntities();
          }
        },
        6000L,
        6000L);

    int configIssues = ConfigValidator.validate();
    if (configIssues > 0) {
      FppLogger.warn("Config validation found " + configIssues + " issue(s) - see above.");
    }

    if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
      try {
        new FppPlaceholderExpansion(this, fakePlayerManager)
            .register();
        Config.debugStartup("PlaceholderAPI detected - placeholders registered.");
      } catch (Exception e) {
        FppLogger.warn("PlaceholderAPI: failed to register expansion - " + e.getMessage());
      }
    }

    worldGuardAvailable = Bukkit.getPluginManager().getPlugin("WorldGuard") != null;
    if (worldGuardAvailable) {
      Config.debugStartup("WorldGuard detected - bot PvP region protection enabled.");
    }

    worldEditAvailable = Bukkit.getPluginManager().getPlugin("WorldEdit") != null;
    if (worldEditAvailable) {
      Config.debugStartup("WorldEdit detected - --wesel flag enabled for /fpp mine and /fpp place.");
    }

    UpdateChecker.check(this);

    heartbeatSender = new HeartbeatSender(this, fakePlayerManager);
    heartbeatSender.start();

    fppMetrics = new FppMetrics();
    if (Config.metricsEnabled()) {
      fppMetrics.init(this);
    } else {
      Config.debugStartup("Metrics disabled in config.yml - skipping FastStats init.");
    }

    String dbLabel =
        databaseManager == null ? "none" : Config.mysqlEnabled() ? "MySQL" : "SQLite (local)";
    String dbState =
        !Config.databaseEnabled() ? "disabled" : (dbOk ? dbLabel : dbLabel + " (failed)");
    int dbSchemaVersion = databaseManager != null ? DatabaseManager.getCurrentSchemaVersion() : 0;

    boolean effectiveChunkLoading =
        Config.chunkLoadingEnabled() && Config.chunkLoadingRadius() != 0;
    boolean effectiveTaskPersist = Config.persistOnRestart() && databaseManager != null;

    long startupMs = System.currentTimeMillis() - enabledAt;
    int cfgVer = Config.configVersion();
    String configVersion =
        "v" + cfgVer + (cfgVer >= ConfigMigrator.CURRENT_VERSION ? " ✔" : " (migrated)");
    int backupCount = BackupManager.listBackups(this).size();

    FppLogger.printStartupBanner(
        getPluginMeta().getVersion(),
        String.join(", ", getPluginMeta().getAuthors()),
        BotNameConfig.getNames().size(),
        dbState,
        dbSchemaVersion,
        Config.persistOnRestart(),
        effectiveTaskPersist,
        Bukkit.getPluginManager().getPlugin("LuckPerms") != null,
        worldGuardAvailable,
        effectiveChunkLoading,
        Config.maxBots(),
        fppMetrics.isActive(),
        configVersion,
        backupCount,
        startupMs);

    botPersistence.purgeOrphanedBodiesAndRestore(fakePlayerManager);

    if (velocityChannel != null) {
      FppScheduler.runSyncLater(this, () -> velocityChannel.broadcastResyncRequest(), 10L);
    }

    Config.debugStartup("onEnable complete.");
  }

  @Override
  public void onDisable() {
    Config.debugStartup("onDisable called.");

    int botsRemoved = fakePlayerManager != null ? fakePlayerManager.getCount() : 0;

    if (chunkLoader != null) chunkLoader.releaseAll();

    if (botChatAI != null) botChatAI.cancelAll();

    if (sleepCommand != null) sleepCommand.stopAll();

    if (botPersistence != null && fakePlayerManager != null) {
      if (Config.persistOnRestart()) {
        Config.debugStartup(
            "Saving " + fakePlayerManager.getCount() + " bot(s) for persistence...");
        botPersistence.save(fakePlayerManager.getActivePlayers());
      }
    }

    if (fppApi != null) fppApi.disableAllAddons();
    if (extensionLoader != null) extensionLoader.closeClassLoaders();

    if (velocityChannel != null) {
      velocityChannel.broadcastServerOffline();
    }

    if (fakePlayerManager != null) fakePlayerManager.removeAllSyncFast();

    boolean dbFlushed = false;
    if (databaseManager != null) {
      databaseManager.recordAllShutdown();
      databaseManager.close();
      dbFlushed = true;
    }

    if (heartbeatSender != null) heartbeatSender.stop();
    if (networkHeartbeat != null) networkHeartbeat.stop();
    if (licenseManager != null) licenseManager.shutdown();

    if (fppMetrics != null) fppMetrics.shutdown();

    getServer().getMessenger().unregisterIncomingPluginChannel(this, VelocityChannel.CHANNEL);
    getServer().getMessenger().unregisterIncomingPluginChannel(this, VelocityChannel.PROXY_CHANNEL);
    getServer().getMessenger().unregisterOutgoingPluginChannel(this, VelocityChannel.CHANNEL);
    getServer().getMessenger().unregisterOutgoingPluginChannel(this, VelocityChannel.PROXY_CHANNEL);
    getServer().getMessenger().unregisterOutgoingPluginChannel(this, "BungeeCord");

    long uptimeMs = System.currentTimeMillis() - enabledAt;
    boolean tasksPersisted = Config.persistOnRestart() && databaseManager != null;
    FppLogger.printShutdownBanner(botsRemoved, dbFlushed, tasksPersisted, botsRemoved, uptimeMs);
  }

  @SuppressWarnings("unused")
  public CommandManager getCommandManager() {
    return commandManager;
  }

  /**
   * Returns the public addon API entry point. Available after {@code onEnable} completes.
   */
  @SuppressWarnings("unused")
  public FppApi getFppApi() {
    return fppApi;
  }

  /**
   * Internal accessor for subsystems that need the concrete impl (e.g. fireTickHandlers).
   */
  public FppApiImpl getFppApiImpl() {
    return fppApi;
  }

  @SuppressWarnings("unused")
  public FakePlayerManager getFakePlayerManager() {
    return fakePlayerManager;
  }

  public BotPersistence getBotPersistence() {
    return botPersistence;
  }

  public SettingGui getSettingGui() {
    return settingGui;
  }

  public BotSettingGui getBotSettingGui() {
    return botSettingGui;
  }

  public DatabaseManager getDatabaseManager() {
    return databaseManager;
  }

  public VelocityChannel getVelocityChannel() {
    return velocityChannel;
  }

  public BotChatController getBotChatAI() {
    return botChatAI;
  }

  public void setBotChatAI(BotChatController botChatAI) {
    this.botChatAI = botChatAI;
  }

  public RemoteBotCache getRemoteBotCache() {
    return remoteBotCache;
  }

  public ConfigSyncManager getConfigSyncManager() {
    return configSyncManager;
  }

  public BotIdentityCache getBotIdentityCache() {
    return botIdentityCache;
  }

  public XpCommand getXpCommand() {
    return xpCommand;
  }

  public MoveCommand getMoveCommand() {
    return moveCommand;
  }

  public MineCommand getMineCommand() {
    return mineCommand;
  }

  public PlaceCommand getPlaceCommand() {
    return placeCommand;
  }

  public UseCommand getUseCommand() {
    return useCommand;
  }

  public AttackCommand getAttackCommand() {
    return attackCommand;
  }

  public FollowCommand getFollowCommand() {
    return followCommand;
  }

  public SleepCommand getSleepCommand() {
    return sleepCommand;
  }

  public PathfindingService getPathfindingService() {
    return pathfindingService;
  }

  public StorageStore getStorageStore() {
    return storageStore;
  }

  public InventoryCommand getInventoryCommand() {
    return inventoryCommand;
  }

  public SkinManager getSkinManager() {
    return skinManager;
  }

  public void setSkinManager(SkinManager skinManager) {
    this.skinManager = skinManager;
  }

  public SkinFetchService getSkinFetchService() {
    return skinFetchService != null
        ? skinFetchService
        : SkinFetchService.NOOP;
  }

  public void setSkinFetchService(
      SkinFetchService skinFetchService) {
    this.skinFetchService =
        skinFetchService != null
            ? skinFetchService
            : SkinFetchService.NOOP;
  }

  public ExtensionLoader getExtensionLoader() {
    return extensionLoader;
  }

  public FppMetrics getFppMetrics() {
    return fppMetrics;
  }

  public Component getUpdateNotification() {
    return updateNotificationMessage;
  }

  public void setUpdateNotification(Component c) {
    this.updateNotificationMessage = c;
  }

  private volatile String latestKnownVersion = null;

  private volatile boolean runningBeta = false;

  public String getLatestKnownVersion() {
    return latestKnownVersion;
  }

  public void setLatestKnownVersion(String v) {
    this.latestKnownVersion = v;
  }

  public boolean isRunningBeta() {
    return runningBeta;
  }

  public void setRunningBeta(boolean b) {
    this.runningBeta = b;
  }

  private void ensureDataDirectories() {
    File root = getDataFolder();
    String[] dirs = {"data", "language", "extensions"};
    for (String dir : dirs) {
      File d = new File(root, dir);
      if (!d.exists()) {
        boolean ok = d.mkdirs();
        Config.debugStartup(
            "Created directory: " + d.getPath() + (ok ? " ✔" : " (already exists or failed)"));
      }
    }

    File extReadme = new File(root, "extensions/README.txt");
    if (!extReadme.exists()) {
      try (PrintWriter w = new PrintWriter(extReadme)) {
        w.println("# FakePlayerPlugin - Extensions Folder");
        w.println("#");
        w.println("# Drop extension JAR files here to load them automatically.");
        w.println("#");
        w.println("# Requirements:");
        w.println(
            "#   - JAR must contain a class implementing me.bill.fakePlayerPlugin.api.FppExtension");
        w.println("#   - That class must have a public no-arg constructor");
        w.println("#");
        w.println("# Run /fpp reload or restart the server after adding or removing extensions.");
      } catch (IOException e) {
        Config.debugStartup("Could not write extensions/README.txt: " + e.getMessage());
      }
    }
  }
}
