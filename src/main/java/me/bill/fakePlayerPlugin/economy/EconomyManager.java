package me.bill.fakePlayerPlugin.economy;

import java.util.Locale;

import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

import me.bill.fakePlayerPlugin.config.Config;

/**
 * Picks and delegates to whichever {@link EconomyProvider} is configured/available, so the rest of
 * the plugin (the rental command, the GUI) never has to know or care which of Vault, "Vault2.0", or
 * ExcellentEconomy is actually installed.
 *
 * <p>{@code economy.provider} in config.yml selects the strategy:
 * <ul>
 *   <li>{@code auto} (default) — Vault (which also transparently covers "Vault2.0" and
 *       Vault-bridged ExcellentEconomy) if its Economy service is registered, else native
 *       ExcellentEconomy if present, else unavailable.</li>
 *   <li>{@code vault} / {@code excellenteconomy} — pin to one specific backend.</li>
 *   <li>{@code none} — economy features are fully disabled (rentals become free/unlimited, gated
 *       purely by permissions — see {@code Config.economyEnabled()}).</li>
 * </ul>
 */
public final class EconomyManager {

    private final VaultEconomyProvider vault = new VaultEconomyProvider();
    private final ExcellentEconomyProvider excellentEconomy = new ExcellentEconomyProvider();

    @Nullable
    private EconomyProvider active() {
        if (!Config.economyEnabled()) return null;
        String mode = Config.economyProvider().toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "vault" -> vault.isAvailable() ? vault : null;
            case "excellenteconomy", "excellent-economy" -> excellentEconomy.isAvailable() ? excellentEconomy : null;
            case "none" -> null;
            default -> vault.isAvailable() ? vault : (excellentEconomy.isAvailable() ? excellentEconomy : null);
        };
    }

    /** True when economy is enabled in config AND a working backend is actually available right now. */
    public boolean isAvailable() {
        return active() != null;
    }

    /** Name of the plugin actually backing purchases right now, or null if none is available. */
    @Nullable
    public String activeProviderName() {
        EconomyProvider provider = active();
        return provider != null ? provider.backingPluginName() : null;
    }

    public double getBalance(OfflinePlayer player) {
        EconomyProvider provider = active();
        return provider != null ? provider.getBalance(player) : 0.0;
    }

    public boolean has(OfflinePlayer player, double amount) {
        EconomyProvider provider = active();
        return provider != null && provider.has(player, amount);
    }

    /** Withdraws {@code amount} from the player. Returns false (and touches nothing) if unavailable or insufficient. */
    public boolean withdraw(OfflinePlayer player, double amount) {
        EconomyProvider provider = active();
        if (provider == null || !provider.has(player, amount)) return false;
        return provider.withdraw(player, amount);
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        EconomyProvider provider = active();
        return provider != null && provider.deposit(player, amount);
    }

    public String format(double amount) {
        EconomyProvider provider = active();
        return provider != null ? provider.format(amount) : String.format(Locale.ROOT, "%.2f", amount);
    }
}
