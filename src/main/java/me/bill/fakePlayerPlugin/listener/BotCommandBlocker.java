package me.bill.fakePlayerPlugin.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.persistence.PersistentDataType;

import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;

public class BotCommandBlocker implements Listener {
    private final FakePlayerManager manager;

    public BotCommandBlocker(FakePlayerManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBotCommandEarly(PlayerCommandPreprocessEvent event) {
        if (isFppBot(event.getPlayer(), manager)) {
            event.setCancelled(true);
            Config.debugNms("BotCommandBlocker: blocked command (LOWEST) for "
                    + event.getPlayer().getName()
                    + ": "
                    + event.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBotCommandLate(PlayerCommandPreprocessEvent event) {
        if (isFppBot(event.getPlayer(), manager)) {
            event.setCancelled(true);
            Config.debugNms("BotCommandBlocker: blocked command (HIGHEST) for "
                    + event.getPlayer().getName()
                    + ": "
                    + event.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBotCommandMonitor(PlayerCommandPreprocessEvent event) {
        if (isFppBot(event.getPlayer(), manager)) {
            if (!event.isCancelled()) {
                event.setCancelled(true);
                Config.debugNms("BotCommandBlocker: blocked command (MONITOR) for "
                        + event.getPlayer().getName()
                        + ": "
                        + event.getMessage());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBotCommandSend(PlayerCommandSendEvent event) {
        if (isFppBot(event.getPlayer(), manager)) {
            event.getCommands().clear();
            Config.debugNms("BotCommandBlocker: cleared command suggestions for "
                    + event.getPlayer().getName());
        }
    }

    private static boolean isFppBot(Player player, FakePlayerManager manager) {
        if (FakePlayerManager.FAKE_PLAYER_KEY == null) return false;
        String marker =
                player.getPersistentDataContainer().get(FakePlayerManager.FAKE_PLAYER_KEY, PersistentDataType.STRING);
        if (manager == null) return false;
        FakePlayer fp = manager.getByUuid(player.getUniqueId());
        if (fp == null) return false;
        if (marker != null && marker.startsWith("fpp-visual:")) return true;
        return fp.getName().equalsIgnoreCase(player.getName());
    }
}
