package me.bill.fakePlayerPlugin.command;

import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.database.DatabaseManager;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.AttributionManager;
import me.bill.fakePlayerPlugin.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;

public class StatsCommand implements FppCommand {

  private static final TextColor LABEL = NamedTextColor.GRAY;
  private static final TextColor MUTED = NamedTextColor.DARK_GRAY;
  private static final TextColor OK = NamedTextColor.GREEN;
  private static final TextColor ERR = NamedTextColor.RED;

  private final FakePlayerManager manager;
  private final DatabaseManager db;

  public StatsCommand(FakePlayerManager manager, DatabaseManager db) {
    this.manager = manager;
    this.db = db;
  }

  @Override
  public String getName() {
    return "stats";
  }

  @Override
  public String getUsage() {
    return "";
  }

  @Override
  public String getDescription() {
    return "Display live plugin statistics.";
  }

  @Override
  public String getPermission() {
    return Perm.STATS;
  }

  @Override
  public boolean execute(CommandSender sender, String[] args) {
    Collection<FakePlayer> bots = manager.getActivePlayers();
    int active = bots.size();
    int maxBots = Config.maxBots();
    int frozen = (int) bots.stream().filter(FakePlayer::isFrozen).count();

    sender.sendMessage(
        TextUtil.colorize(
            "<dark_gray><st>━━━━━━━━</st> <#0079FF><bold>ꜰᴘᴘ ꜱᴛᴀᴛꜱ</#0079FF>"
                + " <dark_gray><st>━━━━━━━━</st>"));

    sender.sendMessage(section("ʟɪᴠᴇ ʙᴏᴛꜱ"));
    sender.sendMessage(kvRow("ᴀᴄᴛɪᴠᴇ", active + " / " + (maxBots == 0 ? "∞" : maxBots)));
    sender.sendMessage(kvRow("ꜰʀᴏᴢᴇɴ", frozen > 0 ? frozen + " ❄" : "none"));

    if (!bots.isEmpty()) {
      OptionalDouble avgUptime =
          bots.stream()
              .mapToLong(
                  fp -> {
                    Instant st = fp.getSpawnTime();
                    return st == null ? 0 : Duration.between(st, Instant.now()).getSeconds();
                  })
              .average();
      if (avgUptime.isPresent()) {
        sender.sendMessage(kvRow("ᴀᴠɢ ᴜᴘᴛɪᴍᴇ", formatSeconds((long) avgUptime.getAsDouble())));
      }

      bots.stream()
          .filter(fp -> fp.getSpawnTime() != null)
          .max(
              Comparator.comparing(
                  fp -> Duration.between(fp.getSpawnTime(), Instant.now())))
          .ifPresent(
              fp -> {
                long secs = Duration.between(fp.getSpawnTime(), Instant.now()).getSeconds();
                sender.sendMessage(
                    kvRow(
                        "ʟᴏɴɢᴇꜱᴛ ᴏɴʟɪɴᴇ",
                        fp.getDisplayName() + " <dark_gray>(" + formatSeconds(secs) + ")"));
              });
    }

    sender.sendMessage(section("ꜱʏꜱᴛᴇᴍꜱ"));
    statusBoolRow(sender, "ꜰᴀᴋᴇ ᴄʜᴀᴛ", Config.fakeChatEnabled(), "enabled", "disabled");
    int chunkRadius = Config.chunkLoadingRadius();
    boolean chunkActive = Config.chunkLoadingEnabled() && chunkRadius != 0;
    String chunkLabel =
        !Config.chunkLoadingEnabled()
            ? "disabled"
            : (chunkRadius == 0 ? "disabled (radius 0)" : "enabled (r=" + chunkRadius + ")");
    statusBoolRow(sender, "ᴄʜᴜɴᴋ ʟᴏᴀᴅɪɴɢ", chunkActive, chunkLabel, chunkLabel);
    statusBoolRow(sender, "ᴘᴇʀꜱɪꜱᴛᴇɴᴄᴇ", Config.persistOnRestart(), "enabled", "disabled");
    sender.sendMessage(kvRow("ꜱᴋɪɴ ᴍᴏᴅᴇ", Config.skinMode()));
    int cooldown = Config.spawnCooldown();
    sender.sendMessage(kvRow("ꜱᴘᴀᴡɴ ᴄᴏᴏʟᴅᴏᴡɴ", cooldown > 0 ? cooldown + "s" : "off"));

    if (db != null) {
      sender.sendMessage(section("ᴅᴀᴛᴀʙᴀꜱᴇ"));
      DatabaseManager.DbStats stats = db.getStats();
      sender.sendMessage(kvRow("ʙᴀᴄᴋᴇɴᴅ", stats.backend()));
      sender.sendMessage(kvRow("ᴛᴏᴛᴀʟ ꜱᴇꜱꜱɪᴏɴꜱ", String.valueOf(stats.totalSessions())));
      sender.sendMessage(kvRow("ᴜɴɪQᴜᴇ ʙᴏᴛꜱ", String.valueOf(stats.uniqueBots())));
      sender.sendMessage(kvRow("ᴜɴɪQᴜᴇ ꜱᴘᴀᴡɴᴇʀꜱ", String.valueOf(stats.uniqueSpawners())));
      if (stats.totalUptimeMs() > 0) {
        sender.sendMessage(kvRow("ᴛᴏᴛᴀʟ ᴜᴘᴛɪᴍᴇ", stats.formattedUptime()));
      }
    }

    sender.sendMessage(section("ꜱᴇʀᴠᴇʀ"));
    sender.sendMessage(
        kvRow("ᴏɴʟɪɴᴇ ᴘʟᴀʏᴇʀꜱ", Bukkit.getOnlinePlayers().size() + " real + " + active + " bots"));
    sender.sendMessage(kvRow("ᴛᴘꜱ", getTpsLabel()));
    sender.sendMessage(
        kvRow("ᴜᴘᴛɪᴍᴇ", formatSeconds(ManagementFactory.getRuntimeMXBean().getUptime() / 1000)));

    sender.sendMessage(
        TextUtil.colorize("<dark_gray><st>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</st>"));
    sender.sendMessage(
        Component.empty()
            .append(Component.text("  ").color(MUTED))
            .append(Component.text("Original author: ").color(LABEL))
            .append(
                Component.text(AttributionManager.getOriginalAuthor())
                    .color(NamedTextColor.WHITE))
            .append(Component.text(" · ").color(MUTED))
            .append(Component.text("Free & open-source").color(LABEL)));
    return true;
  }

  private Component section(String label) {
    return TextUtil.colorize("<dark_gray> ── <#0079FF>" + label + "</#0079FF> <dark_gray>──");
  }

  private Component kvRow(String key, String value) {
    return Component.empty()
        .append(Component.text("  ").color(MUTED))
        .append(Component.text(key + " ").color(LABEL))
        .append(Component.text("· ").color(MUTED))
        .append(TextUtil.colorize("<white>" + value));
  }

  private void statusBoolRow(
      CommandSender sender, String key, boolean on, String labelOn, String labelOff) {
    Component badge = on ? Component.text("✔ ").color(OK) : Component.text("✘ ").color(ERR);
    Component row =
        Component.empty()
            .append(Component.text("  ").color(MUTED))
            .append(badge)
            .append(Component.text(key).color(LABEL))
            .append(Component.text(" · ").color(MUTED))
            .append(Component.text(on ? labelOn : labelOff).color(on ? OK : MUTED));
    sender.sendMessage(row);
  }

  private static String formatSeconds(long secs) {
    if (secs < 60) return secs + "s";
    if (secs < 3600) return (secs / 60) + "m " + (secs % 60) + "s";
    long h = secs / 3600, m = (secs % 3600) / 60;
    return h + "h " + m + "m";
  }

  private static String getTpsLabel() {
    try {
      double[] tps = Bukkit.getServer().getTPS();
      double t = tps.length > 0 ? tps[0] : 20.0;
      String color = t >= 19.5 ? "<green>" : t >= 18.0 ? "<yellow>" : "<red>";
      return color + String.format("%.1f", t) + "<gray> (1m avg)";
    } catch (Exception e) {
      return "N/A";
    }
  }

  @Override
  public List<String> tabComplete(CommandSender sender, String[] args) {
    return List.of();
  }
}
