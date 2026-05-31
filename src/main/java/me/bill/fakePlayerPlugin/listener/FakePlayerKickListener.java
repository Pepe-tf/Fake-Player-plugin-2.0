package me.bill.fakePlayerPlugin.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;

import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;

public class FakePlayerKickListener implements Listener {

    private final FakePlayerManager manager;

    public FakePlayerKickListener(FakePlayerManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerKick(PlayerKickEvent event) {
        FakePlayer fp = manager.getByUuid(event.getPlayer().getUniqueId());
        if (fp == null) return;

        event.setCancelled(true);
        manager.delete(fp.getName());
    }
}
