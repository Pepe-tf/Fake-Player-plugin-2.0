package me.bill.fakePlayerPlugin.listener;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.persistence.PersistentDataType;

import com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.command.XpCommand;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerBody;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;

public class BotXpPickupListener implements Listener {

    private final FakePlayerPlugin plugin;
    private final FakePlayerManager manager;

    public BotXpPickupListener(FakePlayerPlugin plugin, FakePlayerManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBotXpOrbPickup(PlayerPickupExperienceEvent event) {
        if (!isFakeBotBody(event.getPlayer())) return;

        FakePlayer fp = manager.getByUuid(event.getPlayer().getUniqueId());
        if (fp == null) return;

        if (!Config.bodyPickUpXp() || !fp.isPickUpXpEnabled() || isOnXpCooldown(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBotXpChange(PlayerExpChangeEvent event) {
        if (!isFakeBotBody(event.getPlayer())) return;

        FakePlayer fp = manager.getByUuid(event.getPlayer().getUniqueId());
        if (fp == null) return;

        if (!Config.bodyPickUpXp() || !fp.isPickUpXpEnabled() || isOnXpCooldown(event.getPlayer())) {
            event.setAmount(0);
        }
    }

    private boolean isFakeBotBody(Entity entity) {
        if (!(entity instanceof Player)) return false;
        if (FakePlayerManager.FAKE_PLAYER_KEY == null) return false;
        String val =
                entity.getPersistentDataContainer().get(FakePlayerManager.FAKE_PLAYER_KEY, PersistentDataType.STRING);
        return val != null && val.startsWith(FakePlayerBody.VISUAL_PDC_VALUE);
    }

    private boolean isOnXpCooldown(Player player) {
        XpCommand xpCmd = plugin.getXpCommand();
        if (xpCmd == null) return false;
        FakePlayer fp = manager.getByUuid(player.getUniqueId());
        if (fp == null) return false;
        return xpCmd.isOnXpCooldown(fp.getUuid());
    }
}
