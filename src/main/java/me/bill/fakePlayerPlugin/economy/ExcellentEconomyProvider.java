package me.bill.fakePlayerPlugin.economy;

import java.lang.reflect.Method;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import me.bill.fakePlayerPlugin.config.Config;

/**
 * Native integration with ExcellentEconomy (nightexpressdev.com/excellenteconomy,
 * github.com/nulli0n/ExcellentEconomy) for servers that run it <em>without</em> Vault - when Vault
 * is present, {@link VaultEconomyProvider} already covers ExcellentEconomy transparently (its own
 * docs confirm it hooks into Vault automatically), so this class only matters as the standalone path.
 *
 * <p>ExcellentEconomy is a multi-currency plugin (unlimited admin-defined currencies, no single
 * fixed "default"), so the currency id to charge against is configurable
 * ({@code economy.excellent-economy-currency-id}, default {@code "money"}) and must match a
 * currency the server admin has actually created.
 *
 * <p>Its convenience API ({@code ExcellentEconomyAPI#getBalance/deposit/withdraw(Player, String,
 * double)}) only accepts an <em>online</em> {@link Player} - fine for FPP's use, since rentals are
 * only ever bought/extended while the paying player is online running a command. An offline target
 * simply reports unavailable rather than silently no-op'ing incorrectly.
 *
 * <p>Reflective (no compile-time dependency), same defensive pattern as
 * {@link me.bill.fakePlayerPlugin.fakeplayer.ViaVersionCompat}.
 */
public final class ExcellentEconomyProvider implements EconomyProvider {

    private volatile boolean ready = false;
    private volatile boolean broken = false;

    private Class<?> apiClass;
    private Method getBalanceMethod;
    private Method depositMethod;
    private Method withdrawMethod;
    private Method hasCurrencyMethod;

    private synchronized void init() {
        if (ready || broken) return;
        try {
            apiClass = Class.forName("su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI");
            getBalanceMethod = apiClass.getMethod("getBalance", Player.class, String.class);
            depositMethod = apiClass.getMethod("deposit", Player.class, String.class, double.class);
            withdrawMethod = apiClass.getMethod("withdraw", Player.class, String.class, double.class);
            hasCurrencyMethod = apiClass.getMethod("hasCurrency", String.class);

            ready = true;
            Config.debugStartup("ExcellentEconomyProvider: API classes resolved.");
        } catch (Throwable t) {
            broken = true;
            Config.debugStartup("ExcellentEconomyProvider: unavailable - " + t);
        }
    }

    private boolean ensureReady() {
        if (ready) return true;
        if (broken) return false;
        init();
        return ready;
    }

    @Override
    public String id() {
        return "excellenteconomy";
    }

    @Override
    public String backingPluginName() {
        return isAvailable() ? "ExcellentEconomy" : null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object api() {
        if (!ensureReady()) return null;
        RegisteredServiceProvider<?> reg = Bukkit.getServicesManager().getRegistration((Class) apiClass);
        return reg != null ? reg.getProvider() : null;
    }

    private String currencyId() {
        String id = Config.excellentEconomyCurrencyId();
        return id == null || id.isBlank() ? "money" : id;
    }

    @Override
    public boolean isAvailable() {
        Object api = api();
        if (api == null) return false;
        try {
            return (boolean) hasCurrencyMethod.invoke(api, currencyId());
        } catch (Throwable t) {
            Config.debugStartup("ExcellentEconomyProvider.isAvailable failed: " + t);
            return false;
        }
    }

    @Override
    public double getBalance(OfflinePlayer offlinePlayer) {
        Object api = api();
        Player player = onlineOrNull(offlinePlayer);
        if (api == null || player == null) return 0.0;
        try {
            return (double) getBalanceMethod.invoke(api, player, currencyId());
        } catch (Throwable t) {
            Config.debugStartup("ExcellentEconomyProvider.getBalance failed: " + t);
            return 0.0;
        }
    }

    @Override
    public boolean has(OfflinePlayer offlinePlayer, double amount) {
        return getBalance(offlinePlayer) >= amount;
    }

    @Override
    public boolean withdraw(OfflinePlayer offlinePlayer, double amount) {
        Object api = api();
        Player player = onlineOrNull(offlinePlayer);
        if (api == null || player == null) return false;
        try {
            return (boolean) withdrawMethod.invoke(api, player, currencyId(), amount);
        } catch (Throwable t) {
            Config.debugStartup("ExcellentEconomyProvider.withdraw failed: " + t);
            return false;
        }
    }

    @Override
    public boolean deposit(OfflinePlayer offlinePlayer, double amount) {
        Object api = api();
        Player player = onlineOrNull(offlinePlayer);
        if (api == null || player == null) return false;
        try {
            return (boolean) depositMethod.invoke(api, player, currencyId(), amount);
        } catch (Throwable t) {
            Config.debugStartup("ExcellentEconomyProvider.deposit failed: " + t);
            return false;
        }
    }

    @Override
    public String format(double amount) {
        return String.format("%.2f", amount);
    }

    private static Player onlineOrNull(OfflinePlayer player) {
        return player != null && player.isOnline() ? player.getPlayer() : null;
    }
}
