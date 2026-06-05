package me.bill.fakePlayerPlugin.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;

public class BotLoginOverrideListener implements Listener {

    private final FakePlayerPlugin plugin;
    private final FakePlayerManager manager;

    public BotLoginOverrideListener(FakePlayerPlugin plugin, FakePlayerManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        FakePlayer fp = manager.getByUuid(event.getUniqueId());
        if (fp == null) {
            fp = manager.getByName(event.getName());
        }
        if (fp == null) return;

        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            event.allow();
        }
    }
}
