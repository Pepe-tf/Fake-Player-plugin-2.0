package me.bill.fakePlayerPlugin.util;

import dev.faststats.bukkit.BukkitMetrics;
import dev.faststats.core.ErrorTracker;
import dev.faststats.core.data.Metric;
import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;

/**
 * FastStats anonymous usage metrics - developer-only, not user-configurable.
 * <p>
 * No personal data, player names, or server addresses are ever collected.
 */
public final class FppMetrics {
  private static final String TOKEN = "376511af6c97b56954ff2abed24dfaea";

  private final ErrorTracker errorTracker = ErrorTracker.contextAware();
  private BukkitMetrics metrics;

  public void init(final FakePlayerPlugin plugin, final FakePlayerManager botManager) {
    metrics = BukkitMetrics.factory()
        .token(TOKEN)
        .addMetric(Metric.number("active_bots", () -> botManager == null ? 0 : botManager.getCount()))
        .addMetric(Metric.number("online_players", () -> Bukkit.getOnlinePlayers().size() - botManager.getCount()))
        .addMetric(Metric.number("max_bots_config", Config::maxBots))
        .addMetric(Metric.number("user_bot_limit", Config::userBotLimit))
        .addMetric(Metric.number("persistence_enabled", () -> bool(Config.persistOnRestart())))
        .addMetric(Metric.number("body_damageable", () -> bool(Config.bodyDamageable())))
        .addMetric(Metric.number("body_pushable", () -> bool(Config.bodyPushable())))
        .addMetric(Metric.number("fake_chat_enabled", () -> bool(Config.fakeChatEnabled())))
        .addMetric(Metric.number("chunk_loading_enabled", () -> bool(Config.chunkLoadingEnabled())))
        .addMetric(Metric.number("swap_enabled", () -> bool(Config.swapEnabled())))
        .addMetric(Metric.number("peak_hours_enabled", () -> bool(Config.peakHoursEnabled())))
        .addMetric(Metric.number("head_ai_enabled", () -> bool(Config.headAiEnabled())))
        .addMetric(Metric.number("swim_ai_enabled", () -> bool(Config.swimAiEnabled())))
        .addMetric(Metric.number("fall_damage_enabled", () -> bool(Config.fallDamageEnabled())))
        .addMetric(Metric.number("respawn_on_death", () -> bool(Config.respawnOnDeath())))
        .addMetric(Metric.number("tab_list_enabled", () -> bool(Config.tabListEnabled())))
        .addMetric(Metric.number("ping_enabled", () -> bool(Config.pingEnabled())))
        .addMetric(Metric.number("luckperms_installed", () -> bool(isPluginInstalled("LuckPerms"))))
        .addMetric(Metric.number("placeholderapi_installed", () -> bool(isPluginInstalled("PlaceholderAPI"))))
        .addMetric(Metric.number("worldguard_installed", () -> bool(isPluginInstalled("WorldGuard"))))
        .addMetric(Metric.number("worldedit_installed", () -> bool(isPluginInstalled("WorldEdit"))))
        .addMetric(Metric.number("nametag_installed", () -> bool(isPluginInstalled("NameTag"))))
        .addMetric(Metric.string("skin_mode", Config::skinMode))
        .addMetric(Metric.string("database_type", () -> Config.mysqlEnabled() ? "mysql" : "sqlite"))
        .addMetric(Metric.string("bot_name_mode", Config::botNameMode))
        .addMetric(Metric.string("mc_version", plugin::getDetectedMcVersion))
        .addMetric(Metric.string("plugin_version", () -> plugin.getPluginMeta().getVersion()))
        .addMetric(Metric.stringArray("active_features", FppMetrics::collectActiveFeatures))
        .errorTracker(errorTracker)
        .debug(Config.metricsDebug())
        .create(plugin);

    metrics.ready();
    FppLogger.debug("Metrics: FastStats connected and reporting.");
  }

  public void shutdown() {
    if (metrics == null) return;
    metrics.shutdown();
    metrics = null;
  }

  public boolean isActive() {
    return metrics != null;
  }

  private static int bool(final boolean value) {
    return value ? 1 : 0;
  }

  private static boolean isPluginInstalled(final String name) {
    return Bukkit.getPluginManager().getPlugin(name) != null;
  }

  private static String[] collectActiveFeatures() {
    final List<String> features = new ArrayList<>();
    if (Config.bodyDamageable()) features.add("body_damageable");
    if (Config.bodyPushable()) features.add("body_pushable");
    if (Config.persistOnRestart()) features.add("persistence");
    if (Config.fakeChatEnabled()) features.add("fake_chat");
    if (Config.chunkLoadingEnabled()) features.add("chunk_loading");
    if (Config.swapEnabled()) features.add("swap");
    if (Config.peakHoursEnabled()) features.add("peak_hours");
    if (Config.headAiEnabled()) features.add("head_ai");
    if (Config.swimAiEnabled()) features.add("swim_ai");
    if (Config.fallDamageEnabled()) features.add("fall_damage");
    if (Config.respawnOnDeath()) features.add("respawn");
    if (Config.tabListEnabled()) features.add("tab_list");
    if (Config.pingEnabled()) features.add("ping");
    if (isPluginInstalled("LuckPerms")) features.add("luckperms");
    if (isPluginInstalled("PlaceholderAPI")) features.add("placeholderapi");
    if (isPluginInstalled("WorldGuard")) features.add("worldguard");
    if (isPluginInstalled("WorldEdit")) features.add("worldedit");
    if (isPluginInstalled("NameTag")) features.add("nametag");
    return features.toArray(String[]::new);
  }
}
