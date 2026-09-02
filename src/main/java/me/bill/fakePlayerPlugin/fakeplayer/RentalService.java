package me.bill.fakePlayerPlugin.fakeplayer;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.FppScheduler;

/**
 * Periodically checks every active bot's {@link FakePlayer#getRentalExpiresAt()} and:
 * <ul>
 *   <li>warns the owner once, {@code economy.rental.warn-minutes-before-expiry} minutes out, and</li>
 *   <li>despawns the bot (with a notice) the moment its paid time actually runs out.</li>
 * </ul>
 *
 * <p>This runs independently of whether economy purchasing itself is enabled/available -
 * {@code /fpp rent give} can hand out rental time with no economy plugin involved at all (that's
 * the point of it - a custom shop plugin's own reward command), so a bot can be "rented" (i.e. carry
 * an expiry) regardless of {@link Config#economyEnabled()}. The sweep only cares whether any bot
 * actually has an expiry set.
 */
public final class RentalService {

    private final FakePlayerPlugin plugin;
    private final FakePlayerManager manager;
    private int taskId = -1;

    public RentalService(FakePlayerPlugin plugin, FakePlayerManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void start() {
        if (taskId >= 0) return;
        long periodTicks = Math.max(100L, Config.rentalSweepIntervalSeconds() * 20L);
        taskId = FppScheduler.runSyncRepeatingWithId(plugin, this::sweep, periodTicks, periodTicks);
    }

    public void shutdown() {
        if (taskId >= 0) {
            FppScheduler.cancelTask(taskId);
            taskId = -1;
        }
    }

    private void sweep() {
        long now = System.currentTimeMillis();
        long warnThresholdMs = Config.rentalWarnMinutesBeforeExpiry() * 60_000L;

        // Snapshot first: despawning inside the loop would mutate manager.getActivePlayers() while
        // iterating it.
        List<FakePlayer> expired = new ArrayList<>();
        for (FakePlayer fp : manager.getActivePlayers()) {
            Long expiresAt = fp.getRentalExpiresAt();
            if (expiresAt == null) continue;
            if (hasUnlimitedRental(fp)) continue;

            long remaining = expiresAt - now;
            if (remaining <= 0) {
                expired.add(fp);
                continue;
            }
            if (warnThresholdMs > 0 && !fp.isRentalWarningSent() && remaining <= warnThresholdMs) {
                warnOwner(fp, remaining);
                fp.setRentalWarningSent(true);
            }
        }

        for (FakePlayer fp : expired) {
            expire(fp);
        }
    }

    /** True if the bot's owner currently holds the bypass permission - checked live, not cached. */
    private boolean hasUnlimitedRental(FakePlayer fp) {
        Player owner = Bukkit.getPlayer(fp.getSpawnedByUuid());
        return owner != null && owner.isOnline() && owner.hasPermission(Perm.RENT_UNLIMITED);
    }

    private void warnOwner(FakePlayer fp, long remainingMs) {
        Player owner = Bukkit.getPlayer(fp.getSpawnedByUuid());
        if (owner == null || !owner.isOnline()) return;
        long minutes = Math.max(1, remainingMs / 60_000L);
        owner.sendMessage(Lang.get("rental-warning", "name", fp.getDisplayName(), "minutes", String.valueOf(minutes)));
    }

    private void expire(FakePlayer fp) {
        Player owner = Bukkit.getPlayer(fp.getSpawnedByUuid());
        if (owner != null && owner.isOnline()) {
            owner.sendMessage(Lang.get("rental-expired", "name", fp.getDisplayName()));
        }
        Config.debugRental(
                "Rental time expired for bot '" + fp.getName() + "' (owner=" + fp.getSpawnedBy() + ") - despawning.");
        manager.delete(fp.getName(), "rental_expired");
    }
}
