package me.bill.fakePlayerPlugin.economy;

import org.bukkit.OfflinePlayer;

/**
 * A single economy backend FPP can charge/refund against. Every method is safe to call whether or
 * not the backing plugin is actually installed - {@link #isAvailable()} reports that, and every
 * other method is a no-op/failure response (never a thrown exception) when it isn't.
 */
public interface EconomyProvider {

    /** Short identifier used in config (`economy.provider`) and log/debug output. */
    String id();

    /** Human-readable name of the plugin actually backing this provider right now, or null if unavailable. */
    String backingPluginName();

    /** True once the backing plugin is detected, loaded, and ready to service calls. */
    boolean isAvailable();

    double getBalance(OfflinePlayer player);

    boolean has(OfflinePlayer player, double amount);

    /** Attempts to withdraw {@code amount} from the player. Returns true only on confirmed success. */
    boolean withdraw(OfflinePlayer player, double amount);

    /** Deposits {@code amount} into the player's account. Returns true only on confirmed success. */
    boolean deposit(OfflinePlayer player, double amount);

    /** Formats a raw amount using the backend's own currency formatting (symbol, decimals, name). */
    String format(double amount);
}
