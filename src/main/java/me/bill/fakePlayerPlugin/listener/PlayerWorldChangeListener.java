package me.bill.fakePlayerPlugin.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.util.FppScheduler;

public class PlayerWorldChangeListener implements Listener {

    private final FakePlayerPlugin plugin;
    private final FakePlayerManager manager;

    public PlayerWorldChangeListener(FakePlayerPlugin plugin, FakePlayerManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (manager.getCount() == 0) return;

        Player player = event.getPlayer();
        FakePlayer bot = manager.getByUuid(player.getUniqueId());
        if (bot != null) {
            manager.refreshAfterTeleport(bot, player, player.getLocation());
            Config.debugNmsDamage("bot world change refresh queued: bot="
                    + bot.getName()
                    + " entityId="
                    + player.getEntityId()
                    + " from="
                    + event.getFrom().getName()
                    + " to="
                    + player.getWorld().getName());
            return;
        }

        FppScheduler.runSyncLater(
                plugin,
                () -> {
                    if (!player.isOnline()) return;
                    manager.syncToPlayer(player);
                },
                3L);
    }
}
