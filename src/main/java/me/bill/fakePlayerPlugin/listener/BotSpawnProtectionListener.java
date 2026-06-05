package me.bill.fakePlayerPlugin.listener;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.fakeplayer.NmsPlayerSpawner;
import me.bill.fakePlayerPlugin.util.FppScheduler;

public class BotSpawnProtectionListener implements Listener {

    private final FakePlayerPlugin plugin;
    private final Set<UUID> protectedBots = new HashSet<>();

    public BotSpawnProtectionListener(FakePlayerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBotJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (!isFppBot(player)) return;

        UUID botUuid = player.getUniqueId();
        protectedBots.add(botUuid);

        // Vanilla worlds (NORMAL/NETHER/THE_END) only need 5 ticks to block the
        // synchronous NMS portal-spawn teleports.  Custom worlds (Environment.CUSTOM
        // or any non-vanilla environment) additionally require protection against
        // delayed PLUGIN-cause teleports fired by world-management plugins such as
        // Multiverse-Core, EssentialsX, or CMI that run first-join spawn logic up to
        // ~15 ticks after the PlayerJoinEvent.  Using 20 ticks covers both cases
        // universally without affecting legitimate movement commands (which use
        // NMS-level position updates, not Bukkit teleport events).
        World.Environment env = player.getWorld().getEnvironment();
        boolean isVanillaDimension =
                env == World.Environment.NORMAL || env == World.Environment.NETHER || env == World.Environment.THE_END;
        long protectionTicks = isVanillaDimension ? 5L : 20L;

        Config.debugNms("BotSpawnProtection: protecting "
                + player.getName()
                + " from teleports for "
                + protectionTicks
                + " ticks (world="
                + player.getWorld().getName()
                + ", env="
                + env.name()
                + ")");

        FppScheduler.runSyncLater(
                plugin,
                () -> {
                    protectedBots.remove(botUuid);
                    NmsPlayerSpawner.clearProtectedSpawnTarget(botUuid);
                    Config.debugNms("BotSpawnProtection: removed protection for " + player.getName());
                },
                protectionTicks);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBotTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();

        if (!isFppBot(player)) return;

        if (!protectedBots.contains(player.getUniqueId())) return;

        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        Location target = NmsPlayerSpawner.getProtectedSpawnTarget(player.getUniqueId());
        if (target != null && isSameLocation(event.getTo(), target)) {
            return;
        }

        // Block delayed first-join/world-manager teleports during the grace window. FPP's
        // own spawn correction is allowed above when it moves the bot to the requested target.
        event.setCancelled(true);
        Config.debugNms("BotSpawnProtection: blocked "
                + cause.name()
                + " teleport for "
                + player.getName()
                + " from "
                + formatLoc(event.getFrom())
                + " to "
                + formatLoc(event.getTo()));
    }

    /**
     * Returns true if {@code player} is a managed FPP bot.
     *
     * <p>During the initial spawn, the PDC key is not yet written (it is set after
     * {@link me.bill.fakePlayerPlugin.fakeplayer.NmsPlayerSpawner#spawnFakePlayer} returns), so we
     * fall back to a UUID lookup against the active-player map which is populated before the NMS
     * body is spawned.
     */
    private boolean isFppBot(Player player) {
        // Fast path: PDC key present (works after first-spawn completes and on restores).
        if (FakePlayerManager.FAKE_PLAYER_KEY != null) {
            String marker = player.getPersistentDataContainer()
                    .get(FakePlayerManager.FAKE_PLAYER_KEY, PersistentDataType.STRING);
            if (marker != null && marker.startsWith("fpp-visual:")) return true;
        }
        // Fallback: check active-player registry by UUID.
        // This path fires during the very first PlayerJoinEvent emitted inside
        // placeNewPlayer(), before the PDC key has been written.
        FakePlayerManager manager = plugin.getFakePlayerManager();
        return manager != null && manager.getByUuid(player.getUniqueId()) != null;
    }

    private boolean isSameLocation(Location actual, Location expected) {
        if (actual == null || expected == null) return false;
        if (actual.getWorld() != expected.getWorld()) return false;
        return actual.distanceSquared(expected) <= 0.0001D;
    }

    private String formatLoc(Location loc) {
        if (loc == null) return "null";
        return String.format(
                "%s (%.1f, %.1f, %.1f)",
                loc.getWorld() != null ? loc.getWorld().getName() : "?", loc.getX(), loc.getY(), loc.getZ());
    }
}
