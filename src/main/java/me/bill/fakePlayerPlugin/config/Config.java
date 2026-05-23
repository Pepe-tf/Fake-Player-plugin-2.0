package me.bill.fakePlayerPlugin.config;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.util.FppLogger;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

public final class Config {

  private static FakePlayerPlugin plugin;
  private static FileConfiguration cfg;
  private static ChatMessageProvider chatMessageProvider = null;
  private static BooleanSupplier tabListEnabledProvider = null;
  private static final Map<String, FileConfiguration> externalConfigs = new ConcurrentHashMap<>();

  private Config() {
  }

  public interface ChatMessageProvider {
    void reload();

    List<String> getMessages();

    List<String> getReplyMessages();

    List<String> getBurstMessages();

    List<String> getJoinReactionMessages();

    List<String> getDeathReactionMessages();

    List<String> getLeaveReactionMessages();

    List<String> getKeywordReactionMessages(String key);

    List<String> getBotToBotReplyMessages();

    List<String> getAdvancementReactionMessages();

    List<String> getFirstJoinReactionMessages();

    List<String> getKillReactionMessages();

    List<String> getHighLevelReactionMessages();

    List<String> getPlayerChatReactionMessages();
  }

  public static void setChatMessageProvider(ChatMessageProvider provider) {
    chatMessageProvider = provider;
  }

  public static void clearChatMessageProvider(ChatMessageProvider provider) {
    if (chatMessageProvider == provider) chatMessageProvider = null;
  }

  public static void reloadChatMessages() {
    if (chatMessageProvider != null) chatMessageProvider.reload();
  }

  public static void setTabListEnabledProvider(BooleanSupplier provider) {
    tabListEnabledProvider = provider;
  }

  public static void clearTabListEnabledProvider(BooleanSupplier provider) {
    if (tabListEnabledProvider == provider) tabListEnabledProvider = null;
  }

  public static void registerExternalConfig(String rootKey, FileConfiguration config) {
    if (rootKey == null || rootKey.isBlank() || config == null) return;
    externalConfigs.put(rootKey, config);
  }

  public static void unregisterExternalConfig(String rootKey, FileConfiguration config) {
    if (rootKey == null || config == null) return;
    externalConfigs.remove(rootKey, config);
  }

  private static FileConfiguration configFor(String path) {
    int dot = path.indexOf('.');
    String root = dot >= 0 ? path.substring(0, dot) : path;
    FileConfiguration external = externalConfigs.get(root);
    return external != null ? external : cfg;
  }

  private static boolean bool(String path, boolean def) {
    return configFor(path).getBoolean(path, def);
  }

  private static int integer(String path, int def) {
    return configFor(path).getInt(path, def);
  }

  private static double decimal(String path, double def) {
    return configFor(path).getDouble(path, def);
  }

  private static String string(String path, String def) {
    return configFor(path).getString(path, def);
  }

  private static Object value(String path) {
    return configFor(path).get(path);
  }

  private static List<Map<?, ?>> mapList(String path) {
    return configFor(path).getMapList(path);
  }

  private static ConfigurationSection section(String path) {
    return configFor(path).getConfigurationSection(path);
  }

  public static void init(FakePlayerPlugin instance) {
    plugin = instance;
    reload();
  }

  public static void reload() {
    plugin.saveDefaultConfig();
    plugin.reloadConfig();
    cfg = plugin.getConfig();
    cfg.options().copyDefaults(true);

    plugin.saveConfig();
  }

  public static int configVersion() {
    return cfg.getInt("config-version", 0);
  }

  public static String getLanguage() {
    return cfg.getString("language", "en");
  }

  public static boolean isDebug() {
    return cfg != null && cfg.getBoolean("debug", false);
  }

  private static boolean debugFlag(String path) {
    return cfg != null && cfg.getBoolean(path, false);
  }

  public static boolean debugStartup() {
    return isDebug() || debugFlag("logging.debug.startup");
  }

  public static boolean debugNms() {
    return isDebug() || debugFlag("logging.debug.nms");
  }

  public static boolean debugPackets() {
    return isDebug() || debugFlag("logging.debug.packets");
  }

  public static boolean debugNetwork() {
    return isDebug() || debugFlag("logging.debug.network");
  }

  public static boolean debugConfigSync() {
    return isDebug() || debugFlag("logging.debug.config-sync");
  }

  public static boolean debugSkin() {
    return isDebug() || debugFlag("logging.debug.skin");
  }

  public static boolean debugDatabase() {
    return isDebug() || debugFlag("logging.debug.database");
  }

  public static boolean debugChat() {
    return isDebug() || bool("fake-chat.debug", false);
  }

  public static boolean debugSwap() {
    return isDebug() || bool("swap.debug", false);
  }

  public static boolean debugCommands() {
    return isDebug() || debugFlag("logging.debug.commands");
  }

  public static boolean debugHeadAi() {
    return isDebug() || debugFlag("logging.debug.head-ai");
  }

  public static boolean debugLicense() {
    return isDebug();
  }

  public static boolean updateCheckerEnabled() {
    return cfg.getBoolean("update-checker.enabled", true);
  }

  /**
   * Help display mode. "gui" (default) opens the HelpGui chest for players; "text" always uses
   * the paginated chat renderer. Controlled by the "help.mode" config key.
   */
  public static String helpMode() {
    return cfg.getString("help.mode", "gui").toLowerCase();
  }

  public static boolean metricsEnabled() {
    return cfg.getBoolean("metrics.enabled", true);
  }

  public static boolean metricsDebug() {
    return cfg.getBoolean("metrics.debug", false);
  }

  public static boolean heartbeatEnabled() {
    return cfg.getBoolean("heartbeat.enabled", true);
  }

  public static int spawnCooldown() {
    return Math.max(0, cfg.getInt("spawn-cooldown", 0));
  }

  public static boolean tabListEnabled() {
    if (tabListEnabledProvider != null) return tabListEnabledProvider.getAsBoolean();
    return true;
  }

  public static boolean pingEnabled() {
    return bool("ping.enabled", false);
  }

  public static int pingMin() {
    return integer("ping.min", 20);
  }

  public static int pingMax() {
    return integer("ping.max", 200);
  }

  public static int pingVariability() {
    return integer("ping.variability", 8);
  }

  public static int pingUpdateInterval() {
    return integer("ping.update-interval", 40);
  }

  public static boolean pingLatencyEffect() {
    return bool("ping.latency-effect", true);
  }

  public static boolean pingBehaviorEffect() {
    return bool("ping.behavior-effect", true);
  }

  public static int pingMaxBehaviorSkipTicks() {
    return Math.max(1, integer("ping.max-behavior-skip-ticks", 8));
  }

  public static double pingSpikeChance() {
    return decimal("ping.spike-chance", 0.04);
  }

  public static int pingSpikeMin() {
    return integer("ping.spike-min", 200);
  }

  public static int pingSpikeMax() {
    return integer("ping.spike-max", 600);
  }

  public static int pingJoinRampTicks() {
    return integer("ping.join-ramp-ticks", 60);
  }

  public static int maxBots() {
    return cfg.getInt("limits.max-bots", 1000);
  }

  public static int userBotLimit() {
    return cfg.getInt("limits.user-bot-limit", 1);
  }

  public static List<String> spawnCountPresetsAdmin() {
    List<?> raw = cfg.getList("limits.spawn-presets", List.of(1, 5, 10, 15, 20));
    return raw.stream().map(Object::toString).toList();
  }

  public static String adminBotNameFormat() {
    return cfg.getString("bot-name.admin-format", "<#0079FF>[bot-{bot_name}]</#0079FF>");
  }

  public static String userBotNameFormat() {
    return cfg.getString("bot-name.user-format", "<gray>[bot-{spawner}-{num}]</gray>");
  }

  public static String botNameMode() {
    return cfg.getString("bot-name.mode", "random").toLowerCase();
  }

  public static String skinMode() {
    return cfg.getString("skin.mode", "player").toLowerCase();
  }

  public static boolean skinClearCacheOnReload() {
    return cfg.getBoolean("skin.clear-cache-on-reload", true);
  }

  public static boolean skinGuaranteed() {
    return cfg.getBoolean("skin.guaranteed-skin", false);
  }

  public static List<String> skinCustomPool() {

    Object raw = cfg.get("skin.pool");
    if (raw == null) raw = cfg.get("skin.custom.pool");
    if (raw instanceof List<?> list) {
      return list.stream()
          .filter(o -> o instanceof String)
          .map(o -> (String) o)
          .filter(s -> !s.isBlank())
          .toList();
    }
    return List.of();
  }

  public static Map<String, String> skinCustomByName() {

    Object section = cfg.get("skin.overrides");
    if (section == null) section = cfg.get("skin.custom.by-name");
    if (section instanceof Map<?, ?> raw) {
      Map<String, String> result = new LinkedHashMap<>();
      for (Map.Entry<?, ?> e : raw.entrySet()) {
        if (e.getKey() instanceof String k && e.getValue() instanceof String v) {
          result.put(k.toLowerCase(), v);
        }
      }
      return result;
    }
    return Map.of();
  }

  public static boolean skinUseSkinFolder() {
    return cfg.getBoolean("skin.use-skin-folder", true);
  }

  public static boolean skinMineSkinUrlUploadEnabled() {
    return cfg.getBoolean("skin.mineskin.url-upload-enabled", true);
  }

  public static String skinMineSkinApiKey() {
    return cfg.getString("skin.mineskin.api-key", "");
  }

  public static String skinMineSkinVisibility() {
    return cfg.getString("skin.mineskin.visibility", "public");
  }

  public static boolean bodyPushable() {
    return cfg.getBoolean("body.pushable", true);
  }

  public static boolean bodyDamageable() {
    return cfg.getBoolean("body.damageable", true);
  }

  public static boolean bodyPickUpItems() {
    return cfg.getBoolean("body.pick-up-items", false);
  }

  public static boolean bodyPickUpXp() {
    return cfg.getBoolean("body.pick-up-xp", true);
  }

  public static boolean autoEatEnabled() {
    return cfg.getBoolean("automation.auto-eat", true);
  }

  public static boolean autoPlaceBedEnabled() {
    return cfg.getBoolean("automation.auto-place-bed", true);
  }

  public static boolean autoMilkEnabled() {
    return cfg.getBoolean("automation.auto-milk", true);
  }

  public static boolean preventBadOmen() {
    return cfg.getBoolean("automation.prevent-bad-omen", true);
  }

  public static boolean dropItemsOnDespawn() {
    return cfg.getBoolean("body.drop-items-on-despawn", false);
  }

  public static boolean persistOnRestart() {
    return cfg.getBoolean("persistence.enabled", true);
  }

  public static List<String> namePool() {
    return BotNameConfig.getNames();
  }

  public static boolean swapEnabled() {
    return bool("swap.enabled", false);
  }

  public static int swapSessionMin() {
    return integer("swap.session.min", 60);
  }

  public static int swapSessionMax() {
    return integer("swap.session.max", 300);
  }

  public static int swapAbsenceMin() {
    return integer("swap.absence.min", 30);
  }

  public static int swapAbsenceMax() {
    return integer("swap.absence.max", 120);
  }

  public static int swapMaxSwappedOut() {
    return integer("swap.max-swapped-out", 0);
  }

  public static boolean swapFarewellChat() {
    return bool("swap.farewell-chat", true);
  }

  public static boolean swapGreetingChat() {
    return bool("swap.greeting-chat", true);
  }

  public static boolean swapSameNameOnRejoin() {
    return bool("swap.same-name-on-rejoin", true);
  }

  public static int swapMinOnline() {
    return integer("swap.min-online", 0);
  }

  public static boolean swapRetryRejoin() {
    return bool("swap.retry-rejoin", true);
  }

  public static int swapRetryDelay() {
    return integer("swap.retry-delay", 60);
  }

  public static boolean peakHoursEnabled() {
    return bool("peak-hours.enabled", false);
  }

  public static String peakHoursTimezone() {
    return string("peak-hours.timezone", "UTC");
  }

  public static int peakHoursStaggerSeconds() {
    return integer("peak-hours.stagger-seconds", 30);
  }

  public static List<Map<?, ?>> peakHoursSchedule() {
    return mapList("peak-hours.schedule");
  }

  public static ConfigurationSection peakHoursDayOverrides() {
    return section("peak-hours.day-overrides");
  }

  public static int peakHoursMinOnline() {
    return Math.max(0, integer("peak-hours.min-online", 0));
  }

  public static boolean peakHoursNotifyTransitions() {
    return bool("peak-hours.notify-transitions", false);
  }

  public static boolean joinMessage() {
    return cfg.getBoolean("messages.join-message", true);
  }

  public static boolean leaveMessage() {
    return cfg.getBoolean("messages.leave-message", true);
  }

  public static boolean deathMessage() {
    return cfg.getBoolean("messages.death-message", true);
  }

  public static boolean killMessage() {
    return cfg.getBoolean("messages.kill-message", false);
  }

  public static boolean warningsNotifyAdmins() {
    return cfg.getBoolean("messages.notify-admins-on-join", true);
  }

  public static double maxHealth() {
    return cfg.getDouble("combat.max-health", 20.0);
  }

  public static boolean hurtSound() {
    return cfg.getBoolean("combat.hurt-sound", true);
  }

  public static boolean fallDamageEnabled() {
    return cfg.getBoolean("combat.fall-damage.enabled", true);
  }

  public static double fallDamageSafeDistance() {
    return Math.max(0.0, cfg.getDouble("combat.fall-damage.safe-distance", 3.0));
  }

  public static double fallDamageMultiplier() {
    return Math.max(0.0, cfg.getDouble("combat.fall-damage.multiplier", 1.0));
  }

  public static boolean respawnOnDeath() {
    return cfg.getBoolean("death.respawn-on-death", false);
  }

  public static int respawnDelay() {
    return cfg.getInt("death.respawn-delay", 60);
  }

  public static boolean suppressDrops() {
    return cfg.getBoolean("death.suppress-drops", false);
  }

  public static boolean chunkLoadingEnabled() {
    return cfg.getBoolean("chunk-loading.enabled", true);
  }

  public static int chunkLoadingRadius() {
    Object raw = cfg.get("chunk-loading.radius");
    if (raw instanceof Number n) {
      return Math.max(0, n.intValue());
    }

    return Bukkit.getSimulationDistance();
  }

  public static int chunkLoadingUpdateInterval() {
    return cfg.getInt("chunk-loading.update-interval", 20);
  }

  public static int chunkLoadingMassDisableThreshold() {
    return cfg.getInt("chunk-loading.mass-disable-threshold", 100);
  }

  public static boolean headAiEnabled() {
    return cfg.getBoolean("head-ai.enabled", true);
  }

  public static double headAiLookRange() {
    return cfg.getDouble("head-ai.look-range", 8.0);
  }

  public static float headAiTurnSpeed() {
    return (float) cfg.getDouble("head-ai.turn-speed", 0.3);
  }

  public static int headAiTickRate() {
    return Math.max(1, cfg.getInt("head-ai.tick-rate", 3));
  }

  public static boolean swimAiEnabled() {
    return cfg.getBoolean("swim-ai.enabled", true);
  }

  public static boolean pathfindingParkour() {
    return bool("pathfinding.parkour", false);
  }

  public static boolean pathfindingBreakBlocks() {
    return bool("pathfinding.break-blocks", false);
  }

  public static boolean pathfindingPlaceBlocks() {
    return bool("pathfinding.place-blocks", false);
  }

  public static String pathfindingPlaceMaterial() {
    return string("pathfinding.place-material", "DIRT");
  }

  public static double pathfindingArrivalDistance() {
    return decimal("pathfinding.arrival-distance", 1.2);
  }

  public static double pathfindingPatrolArrivalDistance() {
    return decimal("pathfinding.patrol-arrival-distance", 1.5);
  }

  public static double pathfindingWaypointArrivalDistance() {
    return decimal("pathfinding.waypoint-arrival-distance", 0.65);
  }

  public static double pathfindingSprintDistance() {
    return decimal("pathfinding.sprint-distance", 6.0);
  }

  public static double pathfindingFollowRecalcDistance() {
    return decimal("pathfinding.follow-recalc-distance", 3.5);
  }

  public static int pathfindingFollowRecalcInterval() {
    return Math.max(1, integer("pathfinding.follow-recalc-interval", 100));
  }

  public static int pathfindingRecalcInterval() {
    return Math.max(1, integer("pathfinding.recalc-interval", 60));
  }

  public static int pathfindingStuckTicks() {
    return Math.max(1, integer("pathfinding.stuck-ticks", 5));
  }

  public static double pathfindingStuckThreshold() {
    return Math.max(0.001, decimal("pathfinding.stuck-threshold", 0.04));
  }

  public static int pathfindingBreakTicks() {
    return Math.max(1, integer("pathfinding.break-ticks", 15));
  }

  public static int pathfindingPlaceTicks() {
    return Math.max(1, integer("pathfinding.place-ticks", 5));
  }

  public static int pathfindingMaxFall() {
    return Math.max(1, Math.min(integer("pathfinding.max-fall", 3), 16));
  }

  public static int pathfindingMaxRange() {
    return Math.max(8, integer("pathfinding.max-range", 64));
  }

  public static int pathfindingMaxNodes() {
    return Math.max(100, integer("pathfinding.max-nodes", 900));
  }

  public static int pathfindingMaxNodesExtended() {
    return Math.max(pathfindingMaxNodes(), integer("pathfinding.max-nodes-extended", 1800));
  }


  /**
   * Number of lateral sweep steps tried on each side when searching for a detour waypoint.
   */
  public static int pathfindingDetourAttempts() {
    return Math.max(1, Math.min(integer("pathfinding.detour-attempts", 5), 20));
  }

  /**
   * Total lateral radius (in blocks) spread across detour-attempts steps.
   */
  public static double pathfindingDetourRadius() {
    return Math.max(2.0, Math.min(decimal("pathfinding.detour-radius", 16.0), 64.0));
  }

  public static double collisionWalkRadius() {
    return cfg.getDouble("collision.walk-radius", 0.85);
  }

  public static double collisionWalkStrength() {
    return cfg.getDouble("collision.walk-strength", 0.22);
  }

  public static double collisionMaxHoriz() {
    return cfg.getDouble("collision.max-horizontal-speed", 0.30);
  }

  public static double collisionHitStrength() {
    return cfg.getDouble("collision.hit-strength", 0.45);
  }

  public static double collisionHitMaxHoriz() {
    return cfg.getDouble("collision.hit-max-horizontal-speed", 0.80);
  }

  public static double collisionBotRadius() {
    return cfg.getDouble("collision.bot-radius", 0.90);
  }

  public static double collisionBotStrength() {
    return cfg.getDouble("collision.bot-strength", 0.14);
  }

  public static boolean fakeChatEnabled() {
    return bool("fake-chat.enabled", false);
  }

  public static boolean fakeChatRequirePlayer() {
    return bool("fake-chat.require-player-online", true);
  }

  public static double fakeChatChance() {
    return decimal("fake-chat.chance", 0.75);
  }

  public static int fakeChatIntervalMin() {
    return integer("fake-chat.interval.min", 5);
  }

  public static int fakeChatIntervalMax() {
    return integer("fake-chat.interval.max", 10);
  }

  public static boolean fakeChatTypingDelay() {
    return bool("fake-chat.typing-delay", true);
  }

  public static double fakeChatBurstChance() {
    return decimal("fake-chat.burst-chance", 0.12);
  }

  public static int fakeChatBurstDelayMin() {
    return integer("fake-chat.burst-delay.min", 2);
  }

  public static int fakeChatBurstDelayMax() {
    return integer("fake-chat.burst-delay.max", 5);
  }

  public static boolean fakeChatReplyToMentions() {
    return bool("fake-chat.reply-to-mentions", true);
  }

  public static double fakeChatMentionReplyChance() {
    return decimal("fake-chat.mention-reply-chance", 0.65);
  }

  public static int fakeChatReplyDelayMin() {
    return integer("fake-chat.reply-delay.min", 2);
  }

  public static int fakeChatReplyDelayMax() {
    return integer("fake-chat.reply-delay.max", 8);
  }

  public static int fakeChatStaggerInterval() {
    return integer("fake-chat.stagger-interval", 3);
  }

  public static boolean fakeChatActivityVariation() {
    return bool("fake-chat.activity-variation", true);
  }

  public static int fakeChatHistorySize() {
    return integer("fake-chat.history-size", 5);
  }

  public static List<String> fakeChatMessages() {
    return chatMessageProvider != null
        ? chatMessageProvider.getMessages()
        : fallbackChatMessages();
  }

  public static List<String> chatReplyMessages() {
    return chatMessageProvider != null
        ? chatMessageProvider.getReplyMessages()
        : fallbackChatReplies();
  }

  public static List<String> chatBurstMessages() {
    return chatMessageProvider != null
        ? chatMessageProvider.getBurstMessages()
        : fallbackChatBursts();
  }

  public static List<String> chatJoinReactionMessages() {
    return chatMessageProvider != null ? chatMessageProvider.getJoinReactionMessages() : List.of();
  }

  public static List<String> chatDeathReactionMessages() {
    return chatMessageProvider != null ? chatMessageProvider.getDeathReactionMessages() : List.of();
  }

  public static List<String> chatLeaveReactionMessages() {
    return chatMessageProvider != null ? chatMessageProvider.getLeaveReactionMessages() : List.of();
  }

  public static List<String> chatKeywordReactionMessages(String key) {
    return chatMessageProvider != null
        ? chatMessageProvider.getKeywordReactionMessages(key)
        : List.of();
  }

  public static String fakeChatRemoteFormat() {
    return string("fake-chat.remote-format", "<yellow>{name}<dark_gray>: <white>{message}");
  }

  public static boolean fakeChatEventTriggersEnabled() {
    return bool("fake-chat.event-triggers.enabled", true);
  }

  public static boolean fakeChatOnJoinEnabled() {
    return bool("fake-chat.event-triggers.on-player-join.enabled", true);
  }

  public static double fakeChatOnJoinChance() {
    return decimal("fake-chat.event-triggers.on-player-join.chance", 0.40);
  }

  public static int fakeChatOnJoinDelayMin() {
    return integer("fake-chat.event-triggers.on-player-join.delay.min", 2);
  }

  public static int fakeChatOnJoinDelayMax() {
    return integer("fake-chat.event-triggers.on-player-join.delay.max", 6);
  }

  public static boolean fakeChatOnDeathEnabled() {
    return bool("fake-chat.event-triggers.on-death.enabled", true);
  }

  public static boolean fakeChatOnDeathPlayersOnly() {
    return bool("fake-chat.event-triggers.on-death.players-only", false);
  }

  public static double fakeChatOnDeathChance() {
    return decimal("fake-chat.event-triggers.on-death.chance", 0.30);
  }

  public static int fakeChatOnDeathDelayMin() {
    return integer("fake-chat.event-triggers.on-death.delay.min", 1);
  }

  public static int fakeChatOnDeathDelayMax() {
    return integer("fake-chat.event-triggers.on-death.delay.max", 4);
  }

  public static boolean fakeChatOnLeaveEnabled() {
    return bool("fake-chat.event-triggers.on-player-leave.enabled", true);
  }

  public static double fakeChatOnLeaveChance() {
    return decimal("fake-chat.event-triggers.on-player-leave.chance", 0.30);
  }

  public static int fakeChatOnLeaveDelayMin() {
    return integer("fake-chat.event-triggers.on-player-leave.delay.min", 1);
  }

  public static int fakeChatOnLeaveDelayMax() {
    return integer("fake-chat.event-triggers.on-player-leave.delay.max", 4);
  }

  public static boolean fakeChatOnPlayerChatEnabled() {
    return bool("fake-chat.event-triggers.on-player-chat.enabled", false);
  }

  public static double fakeChatOnPlayerChatChance() {
    return decimal("fake-chat.event-triggers.on-player-chat.chance", 0.25);
  }

  public static int fakeChatOnPlayerChatMaxBots() {
    return integer("fake-chat.event-triggers.on-player-chat.max-bots", 1);
  }

  public static boolean fakeChatOnPlayerChatIgnoreShort() {
    return bool("fake-chat.event-triggers.on-player-chat.ignore-short", true);
  }

  public static boolean fakeChatOnPlayerChatIgnoreCommands() {
    return bool("fake-chat.event-triggers.on-player-chat.ignore-commands", true);
  }

  public static double fakeChatOnPlayerChatMentionChance() {
    return decimal("fake-chat.event-triggers.on-player-chat.mention-player", 0.50);
  }

  public static int fakeChatOnPlayerChatDelayMin() {
    return integer("fake-chat.event-triggers.on-player-chat.delay.min", 2);
  }

  public static int fakeChatOnPlayerChatDelayMax() {
    return integer("fake-chat.event-triggers.on-player-chat.delay.max", 8);
  }

  public static boolean fakeChatBotToBotEnabled() {
    return bool("fake-chat.bot-to-bot.enabled", true);
  }

  public static double fakeChatBotToBotReplyChance() {
    return decimal("fake-chat.bot-to-bot.reply-chance", 0.35);
  }

  public static double fakeChatBotToBotChainChance() {
    return decimal("fake-chat.bot-to-bot.chain-chance", 0.40);
  }

  public static int fakeChatBotToBotMaxChain() {
    return integer("fake-chat.bot-to-bot.max-chain", 3);
  }

  public static int fakeChatBotToBotDelayMin() {
    return integer("fake-chat.bot-to-bot.delay.min", 4);
  }

  public static int fakeChatBotToBotDelayMax() {
    return integer("fake-chat.bot-to-bot.delay.max", 14);
  }

  public static int fakeChatBotToBotCooldown() {
    return integer("fake-chat.bot-to-bot.cooldown", 8);
  }

  public static boolean fakeChatOnAdvancementEnabled() {
    return bool("fake-chat.event-triggers.on-advancement.enabled", true);
  }

  public static double fakeChatOnAdvancementChance() {
    return decimal("fake-chat.event-triggers.on-advancement.chance", 0.45);
  }

  public static int fakeChatOnAdvancementDelayMin() {
    return integer("fake-chat.event-triggers.on-advancement.delay.min", 1);
  }

  public static int fakeChatOnAdvancementDelayMax() {
    return integer("fake-chat.event-triggers.on-advancement.delay.max", 5);
  }

  public static boolean fakeChatOnFirstJoinEnabled() {
    return bool("fake-chat.event-triggers.on-first-join.enabled", true);
  }

  public static double fakeChatOnFirstJoinChance() {
    return decimal("fake-chat.event-triggers.on-first-join.chance", 0.70);
  }

  public static boolean fakeChatOnKillEnabled() {
    return bool("fake-chat.event-triggers.on-kill.enabled", true);
  }

  public static double fakeChatOnKillChance() {
    return decimal("fake-chat.event-triggers.on-kill.chance", 0.35);
  }

  public static int fakeChatOnKillDelayMin() {
    return integer("fake-chat.event-triggers.on-kill.delay.min", 1);
  }

  public static int fakeChatOnKillDelayMax() {
    return integer("fake-chat.event-triggers.on-kill.delay.max", 4);
  }

  public static boolean fakeChatOnHighLevelEnabled() {
    return bool("fake-chat.event-triggers.on-high-level.enabled", true);
  }

  public static int fakeChatOnHighLevelMinLevel() {
    return integer("fake-chat.event-triggers.on-high-level.min-level", 30);
  }

  public static double fakeChatOnHighLevelChance() {
    return decimal("fake-chat.event-triggers.on-high-level.chance", 0.35);
  }

  public static int fakeChatOnHighLevelDelayMin() {
    return integer("fake-chat.event-triggers.on-high-level.delay.min", 1);
  }

  public static int fakeChatOnHighLevelDelayMax() {
    return integer("fake-chat.event-triggers.on-high-level.delay.max", 5);
  }

  public static List<String> chatBotToBotReplyMessages() {
    return chatMessageProvider != null
        ? chatMessageProvider.getBotToBotReplyMessages()
        : chatReplyMessages();
  }

  public static List<String> chatAdvancementReactionMessages() {
    return chatMessageProvider != null ? chatMessageProvider.getAdvancementReactionMessages() : List.of();
  }

  public static List<String> chatFirstJoinReactionMessages() {
    return chatMessageProvider != null
        ? chatMessageProvider.getFirstJoinReactionMessages()
        : chatJoinReactionMessages();
  }

  public static List<String> chatKillReactionMessages() {
    return chatMessageProvider != null ? chatMessageProvider.getKillReactionMessages() : List.of();
  }

  public static List<String> chatHighLevelReactionMessages() {
    return chatMessageProvider != null ? chatMessageProvider.getHighLevelReactionMessages() : List.of();
  }

  public static List<String> chatPlayerChatReactionMessages() {
    return chatMessageProvider != null
        ? chatMessageProvider.getPlayerChatReactionMessages()
        : chatReplyMessages();
  }

  private static List<String> fallbackChatMessages() {
    return List.of("gg", "let's go!", "hey everyone", "what's up", "nice server");
  }

  private static List<String> fallbackChatReplies() {
    return List.of("yeah?", "sup", "what?", "hm?", "here!");
  }

  private static List<String> fallbackChatBursts() {
    return List.of("lol", "fr", "ngl", "no cap", "lmao");
  }

  public static boolean fakeChatKeywordReactionsEnabled() {
    return bool("fake-chat.keyword-reactions.enabled", false);
  }

  @SuppressWarnings("unchecked")
  public static Map<String, String> fakeChatKeywordMap() {
    Object raw = value("fake-chat.keyword-reactions.keywords");
    if (raw instanceof Map<?, ?> m) {
      Map<String, String> result = new LinkedHashMap<>();
      for (Map.Entry<?, ?> e : m.entrySet()) {
        if (e.getKey() instanceof String k && e.getValue() instanceof String v) {
          result.put(k.toLowerCase(), v);
        }
      }
      return result;
    }
    return Map.of();
  }

  public static boolean mysqlEnabled() {
    return cfg.getBoolean("database.mysql-enabled", false);
  }

  public static boolean databaseEnabled() {
    return cfg.getBoolean("database.enabled", true);
  }

  public static String databaseMode() {
    String raw = cfg.getString("database.mode", "LOCAL");
    return raw.trim().equalsIgnoreCase("NETWORK") ? "NETWORK" : "LOCAL";
  }

  public static boolean isNetworkMode() {
    return databaseEnabled() && databaseMode().equalsIgnoreCase("NETWORK");
  }

  public static String configSyncMode() {
    String raw = cfg.getString("config-sync.mode", "DISABLED");
    return raw.trim().toUpperCase();
  }

  public static String mysqlHost() {
    return cfg.getString("database.mysql.host", "localhost");
  }

  public static int mysqlPort() {
    return cfg.getInt("database.mysql.port", 3306);
  }

  public static String mysqlDatabase() {
    return cfg.getString("database.mysql.database", "fpp");
  }

  public static String mysqlUsername() {
    return cfg.getString("database.mysql.username", "root");
  }

  public static String mysqlPassword() {
    return cfg.getString("database.mysql.password", "");
  }

  public static boolean mysqlUseSSL() {
    return cfg.getBoolean("database.mysql.use-ssl", false);
  }

  public static int mysqlPoolSize() {
    return cfg.getInt("database.mysql.pool-size", 5);
  }

  public static int mysqlConnTimeout() {
    return cfg.getInt("database.mysql.connection-timeout", 30000);
  }

  public static int dbLocationFlushInterval() {
    return cfg.getInt("database.location-flush-interval", 30);
  }

  public static int dbMaxHistoryRows() {
    return cfg.getInt("database.session-history.max-rows", 20);
  }

  public static String serverId() {

    String id = cfg.getString("database.server-id", null);

    if (id == null || id.isBlank()) {
      id = cfg.getString("server.id", "default");
    }
    return (id == null || id.isBlank()) ? "default" : id.trim();
  }

  public static double positionSyncDistance() {
    return cfg.getDouble("performance.position-sync-distance", 128.0);
  }

  public static boolean isBadwordFilterEnabled() {
    return cfg.getBoolean("badword-filter.enabled", true);
  }

  public static List<String> getBadwords() {
    Object raw = cfg.get("badword-filter.words");
    if (raw instanceof List<?> list) {
      return list.stream()
          .filter(o -> o instanceof String)
          .map(o -> (String) o)
          .filter(s -> !s.isBlank())
          .toList();
    }
    return List.of();
  }

  public static boolean isBadwordGlobalListEnabled() {
    return cfg.getBoolean("badword-filter.use-global-list", true);
  }

  public static String badwordGlobalListUrl() {
    return cfg.getString(
        "badword-filter.global-list-url", "https://www.cs.cmu.edu/~biglou/resources/bad-words.txt");
  }

  public static int badwordGlobalListTimeoutMs() {
    return Math.max(1000, cfg.getInt("badword-filter.global-list-timeout-ms", 5000));
  }

  public static List<String> getBadwordWhitelist() {
    Object raw = cfg.get("badword-filter.whitelist");
    if (raw instanceof List<?> list) {
      return list.stream()
          .filter(o -> o instanceof String)
          .map(o -> (String) o)
          .filter(s -> !s.isBlank())
          .toList();
    }
    return List.of();
  }

  public static boolean isBadwordAutoRenameEnabled() {
    return cfg.getBoolean("badword-filter.auto-rename", true);
  }

  public static boolean isBadwordAutoDetectionEnabled() {
    return cfg.getBoolean("badword-filter.auto-detection.enabled", true);
  }

  public static String getBadwordAutoDetectionMode() {
    return cfg.getString("badword-filter.auto-detection.mode", "normal").toLowerCase();
  }

  public static boolean isBotRightClickEnabled() {
    return cfg.getBoolean("bot-interaction.right-click-enabled", true);
  }

  public static boolean isBotShiftRightClickSettingsEnabled() {
    return cfg.getBoolean("bot-interaction.shift-right-click-settings", true);
  }

  public static void debug(String message) {
    FppLogger.debug(message);
  }

  public static void debugStartup(String message) {
    FppLogger.debug("STARTUP", debugStartup(), message);
  }

  public static void debugNms(String message) {
    FppLogger.debug("NMS", debugNms(), message);
  }

  public static void debugPackets(String message) {
    FppLogger.debug("PACKETS", debugPackets(), message);
  }

  public static void debugNetwork(String message) {
    FppLogger.debug("NETWORK", debugNetwork(), message);
  }

  public static void debugConfigSync(String message) {
    FppLogger.debug("CONFIG_SYNC", debugConfigSync(), message);
  }

  public static void debugSkin(String message) {
    FppLogger.debug("SKIN", debugSkin(), message);
  }

  public static void debugDatabase(String message) {
    FppLogger.debug("DATABASE", debugDatabase(), message);
  }

  public static void debugChat(String message) {
    FppLogger.debug("CHAT", debugChat(), message);
  }

  public static void debugSwap(String message) {
    FppLogger.debug("SWAP", debugSwap(), message);
  }

  public static double attackMobDefaultRange() {
    return cfg.getDouble("attack-mob.default-range", 8.0);
  }

  public static String attackMobDefaultPriority() {
    return cfg.getString("attack-mob.default-priority", "nearest");
  }

  public static double attackMobSmoothRotationSpeed() {
    return cfg.getDouble("attack-mob.smooth-rotation-speed", 12.0);
  }

  public static int attackMobRetargetInterval() {
    return cfg.getInt("attack-mob.retarget-interval", 10);
  }

  public static boolean attackMobLineOfSight() {
    return cfg.getBoolean("attack-mob.line-of-sight", true);
  }
}
