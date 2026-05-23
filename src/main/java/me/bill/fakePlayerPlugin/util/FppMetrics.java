package me.bill.fakePlayerPlugin.util;

import dev.faststats.bukkit.BukkitMetrics;
import dev.faststats.core.ErrorTracker;
import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;

/**
 * FastStats anonymous usage metrics - developer-only, not user-configurable.
 * <p>
 * No personal data, player names, or server addresses are ever collected.
 */
public final class FppMetrics {
  private static final String TOKEN = "376511af6c97b56954ff2abed24dfaea";

  private final ErrorTracker errorTracker = ErrorTracker.contextAware();
  private BukkitMetrics metrics;

  public void init(final FakePlayerPlugin plugin) {
    metrics = BukkitMetrics.factory()
        .token(TOKEN)
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
    return metrics != null && metrics.getConfig().enabled();
  }
}
