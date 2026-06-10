package me.bill.fakePlayerPlugin.listener;

import java.util.Locale;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;

import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.util.FppLogger;

public class FakePlayerKickListener implements Listener {

    private final FakePlayerManager manager;

    public FakePlayerKickListener(FakePlayerManager manager) {
        this.manager = manager;
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerKick(PlayerKickEvent event) {
        FakePlayer fp = manager.getByUuid(event.getPlayer().getUniqueId());
        if (fp == null) return;

        String kickReason = event.getReason();
        if (kickReason != null) {
            String lower = kickReason.toLowerCase(Locale.ROOT);
            // PacketEvents / anti-cheat injection failures are harmless for fake players
            // because there is no real network stack to inject into.  Despawning here creates
            // an instant-despawn loop, so we silently swallow the kick instead.
            if (lower.contains("packetevents") && lower.contains("inject")) {
                event.setCancelled(true);
                FppLogger.debug("FakePlayerKickListener: suppressed PacketEvents injection kick for bot '"
                        + fp.getName()
                        + "'");
                return;
            }
        }

        FppLogger.warn("FakePlayerKickListener: bot '"
                + fp.getName()
                + "' was kicked (reason: "
                + kickReason
                + ") — despawning instead.");
        event.setCancelled(true);
        manager.addSyntheticQuit(fp.getUuid());
        manager.delete(fp.getName(), "kicked_by_server|" + (kickReason != null ? kickReason : "unknown"));
    }
}
