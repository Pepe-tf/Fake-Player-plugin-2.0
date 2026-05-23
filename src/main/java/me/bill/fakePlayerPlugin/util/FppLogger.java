package me.bill.fakePlayerPlugin.util;

import me.bill.fakePlayerPlugin.config.Config;

import java.util.logging.Logger;

public final class FppLogger {

  private static final String RESET = "\u001B[0m";
  private static final String BOLD = "\u001B[1m";

  private static final String BLUE = "\u001B[38;2;0;121;255m";

  private static final String WHITE = "\u001B[97m";

  private static final String YELLOW = "\u001B[93m";

  private static final String GREEN = "\u001B[92m";

  private static final String GOLD = "\u001B[33m";

  private static final String RED = "\u001B[91m";

  private static final String GRAY = "\u001B[90m";

  private static final String CYAN = "\u001B[96m";

  private static final String DARK = "\u001B[38;5;240m";

  private static final String TAG = BOLD + BLUE + "[ꜰᴘᴘ]" + RESET;

  private static final int RULE_WIDTH = 50;

  private static final int KEY_WIDTH = 18;

  private static Logger logger;

  private FppLogger() {
  }

  public static void init(Logger javaLogger) {
    logger = javaLogger;
  }

  public static void info(String message) {
    logger.info(TAG + " " + WHITE + message + RESET);
  }

  public static void success(String message) {
    logger.info(TAG + " " + GREEN + message + RESET);
  }

  public static void warn(String message) {
    logger.warning(TAG + " " + YELLOW + message + RESET);
  }

  public static void error(String message) {
    logger.severe(TAG + " " + RED + message + RESET);
  }

  public static void debug(String message) {
    debug("GENERAL", Config.isDebug(), message);
  }

  public static void debug(String topic, boolean enabled, String message) {
    if (!enabled) return;
    String label = (topic == null || topic.isBlank()) ? "DEBUG" : topic.trim().toUpperCase();
    logger.info(
        TAG + " " + GRAY + "[" + YELLOW + "DEBUG" + GRAY + "/" + CYAN + label + GRAY + "] " + YELLOW
            + message + RESET);
  }

  public static void highlight(String message) {
    logger.info(TAG + " " + BOLD + CYAN + message + RESET);
  }

  public static void rule() {
    logger.info(TAG + " " + DARK + "─".repeat(RULE_WIDTH) + RESET);
  }

  public static void boldRule() {
    logger.info(TAG + " " + GRAY + BOLD + "═".repeat(RULE_WIDTH) + RESET);
  }

  public static void section(String label) {
    String dashes = "─".repeat(Math.max(0, RULE_WIDTH - label.length() - 4));
    logger.info(
        TAG + " " + DARK + "── " + RESET + BOLD + WHITE + label + " " + DARK + dashes + RESET);
  }

  public static void kv(String key, Object value) {
    int dots = Math.max(1, KEY_WIDTH - key.length());
    String dotStr = DARK + ".".repeat(dots) + RESET;
    logger.info(TAG + " " + GRAY + "  " + WHITE + key + " " + dotStr + " " + BLUE + value + RESET);
  }

  public static void statusRow(boolean ok, String label, String detail) {
    String badge = ok ? GREEN + "[+]" + RESET : RED + "[✘]" + RESET;
    int dots = Math.max(1, KEY_WIDTH - label.length());
    String dotStr = DARK + ".".repeat(dots) + RESET;
    String valueColor = ok ? GREEN : GRAY;
    logger.info(
        TAG
            + " "
            + GRAY
            + "  "
            + badge
            + " "
            + WHITE
            + label
            + " "
            + dotStr
            + " "
            + valueColor
            + detail
            + RESET);
  }

  private static void stateRow(RowState state, String label, String detail) {
    String badge;
    String valueColor;
    switch (state) {
      case OK -> {
        badge = GREEN + "[+]" + RESET;
        valueColor = GREEN;
      }
      case WARN -> {
        badge = YELLOW + "[!]" + RESET;
        valueColor = YELLOW;
      }
      default -> {
        badge = GRAY + "[-]" + RESET;
        valueColor = GRAY;
      }
    }

    int dots = Math.max(1, KEY_WIDTH - label.length());
    String dotStr = DARK + ".".repeat(dots) + RESET;
    logger.info(
        TAG
            + " "
            + GRAY
            + "  "
            + badge
            + " "
            + WHITE
            + label
            + " "
            + dotStr
            + " "
            + valueColor
            + detail
            + RESET);
  }

  @SuppressWarnings("unused")
  public static void blank() {
    logger.info("");
  }

  public static void printStartupBanner(
      String version,
      String authors,
      int namePoolSize,
      String dbState,
      int dbSchemaVersion,
      boolean persistEnabled,
      boolean taskPersistEnabled,
      boolean luckPermsFound,
      boolean worldGuardFound,
      boolean chunkLoading,
      int maxBots,
      boolean metricsActive,
      String configVersion,
      int backupCount,
      long startupMs) {
    boldRule();
    info("  " + BOLD + BLUE + "FakePlayerPlugin" + RESET + WHITE + " v" + version + RESET);
    rule();

    section("Runtime");
    String dbDisplay =
        dbSchemaVersion > 0 ? dbState + "  (schema v" + dbSchemaVersion + ")" : dbState;
    stateRow(resolveDbState(dbState), "Database", dbDisplay);
    kv("Config version", configVersion);
    kv("Backups", backupCount);
    kv("Startup time", startupMs + "ms");

    section("Features");
    stateRow(persistEnabled ? RowState.OK : RowState.OFF, "Persistence", onOff(persistEnabled));
    stateRow(
        taskPersistEnabled ? RowState.OK : RowState.OFF,
        "Task persistence",
        taskPersistEnabled ? "db + yaml" : onOff(false));
    stateRow(chunkLoading ? RowState.OK : RowState.OFF, "Chunk loading", onOff(chunkLoading));

    section("Integrations");
    stateRow(luckPermsFound ? RowState.OK : RowState.OFF, "LuckPerms", onOff(luckPermsFound));
    stateRow(worldGuardFound ? RowState.OK : RowState.OFF, "WorldGuard", onOff(worldGuardFound));
    stateRow(metricsActive ? RowState.OK : RowState.OFF, "Metrics", onOff(metricsActive));

    section("Pools & Limits");
    kv("Name pool", namePoolSize);
    kv("Max bots", maxBots == 0 ? "unlimited" : maxBots);

    if (Config.isDebug()) {
      section("Debug");
      kv("Authors", authors);
    }

    rule();
    success("  Ready: /fpp help");
    rule();
    info(
        "  " + GRAY + "Original author: " + WHITE + AttributionManager.getOriginalAuthor() + RESET);
    info("  " + GRAY + AttributionManager.getAttributionMessage() + RESET);
    info("  " + GRAY + "Source: " + CYAN + AttributionManager.getModrinthLink() + RESET);
    boldRule();
  }

  public static void printShutdownBanner(
      int botsRemoved, boolean dbFlushed, boolean tasksPersisted, int botsSaved, long uptimeMs) {
    boldRule();
    highlight("  ꜰᴀᴋᴇ ᴘʟᴀʏᴇʀ ᴘʟᴜɢɪɴ  -  shutting down");
    rule();
    kv("Bots removed", botsRemoved);
    kv("Bots saved", botsSaved > 0 ? botsSaved + " ✔" : "none");
    kv("Tasks persisted", tasksPersisted ? "db + yaml ✔" : "yaml only");
    kv("DB sessions", dbFlushed ? "flushed ✔" : "skipped (no DB)");
    kv("Session uptime", formatUptime(uptimeMs));
    boldRule();
    info("  Goodbye! ꜰᴀᴋᴇ ᴘʟᴀʏᴇʀ ᴘʟᴜɢɪɴ has been disabled.");
    boldRule();
  }

  private static String formatUptime(long ms) {
    long totalSec = ms / 1_000;
    long hours = totalSec / 3600;
    long minutes = (totalSec % 3600) / 60;
    long seconds = totalSec % 60;
    if (hours > 0) return hours + "h " + minutes + "m " + seconds + "s";
    if (minutes > 0) return minutes + "m " + seconds + "s";
    return seconds + "s";
  }

  private static String onOff(boolean enabled) {
    return enabled ? "enabled" : "disabled";
  }

  private static RowState resolveDbState(String dbState) {
    if (dbState == null) return RowState.WARN;
    String s = dbState.toLowerCase();
    if (s.contains("failed")) return RowState.WARN;
    if (s.contains("disabled") || s.contains("none")) return RowState.OFF;
    return RowState.OK;
  }

  private enum RowState {
    OK,
    WARN,
    OFF
  }
}
