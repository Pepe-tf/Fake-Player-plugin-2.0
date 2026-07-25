package me.bill.fakePlayerPlugin.economy;

import org.bukkit.entity.Player;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.lang.Lang;

import net.kyori.adventure.text.Component;

/**
 * Shared "charge the player and extend a bot's rental" logic, so the two places that offer it —
 * {@code /fpp rent extend} and the per-bot settings GUI's rental tile — can never drift out of sync
 * on how the charge/refund/expiry-cap math actually works. Money-handling logic like this belongs in
 * exactly one place.
 */
public final class RentalPurchases {

    private RentalPurchases() {}

    public record Result(boolean success, Component message) {}

    /** Charges {@code payer} {@code Config.rentalPricePerHour() * hours} and extends {@code bot}'s rental by that much. */
    public static Result extend(FakePlayerPlugin plugin, Player payer, FakePlayer bot, int hours) {
        EconomyManager economy = plugin.getEconomyManager();
        if (economy == null || !economy.isAvailable()) {
            return new Result(false, Lang.get("rent-economy-unavailable"));
        }

        double cost = Config.rentalPricePerHour() * hours;
        if (!economy.has(payer, cost)) {
            return new Result(
                    false,
                    Lang.get(
                            "rent-insufficient-funds",
                            "cost",
                            economy.format(cost),
                            "balance",
                            economy.format(economy.getBalance(payer))));
        }
        if (!economy.withdraw(payer, cost)) {
            return new Result(false, Lang.get("rent-transaction-failed"));
        }

        long base = Math.max(System.currentTimeMillis(), currentExpiry(bot));
        long cap = System.currentTimeMillis() + Config.rentalMaxBankedHours() * 3_600_000L;
        long finalExpiry = Math.min(base + hours * 3_600_000L, cap);
        setRentalExpiry(plugin, bot, finalExpiry);

        Config.debugRental("Extended rental bot '" + bot.getName() + "' by " + hours + "h for " + payer.getName());
        return new Result(
                true,
                Lang.get(
                        "rent-extended",
                        "name",
                        bot.getDisplayName(),
                        "hours",
                        String.valueOf(hours),
                        "cost",
                        economy.format(cost),
                        "remaining",
                        formatRemaining(finalExpiry - System.currentTimeMillis())));
    }

    public static void setRentalExpiry(FakePlayerPlugin plugin, FakePlayer bot, Long epochMillis) {
        bot.setRentalExpiresAt(epochMillis);
        var db = plugin.getDatabaseManager();
        if (db != null) db.updateBotRentalExpiry(bot.getUuid().toString(), epochMillis);
    }

    public static long currentExpiry(FakePlayer bot) {
        Long existing = bot.getRentalExpiresAt();
        return existing != null ? existing : 0L;
    }

    public static String formatRemaining(long millis) {
        if (millis <= 0) return "0m";
        long totalMinutes = millis / 60_000L;
        long h = totalMinutes / 60;
        long m = totalMinutes % 60;
        return h > 0 ? h + "h " + m + "m" : m + "m";
    }
}
