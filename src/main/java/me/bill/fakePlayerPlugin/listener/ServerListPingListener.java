package me.bill.fakePlayerPlugin.listener;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;

import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;

/**
 * Keeps bots out of the multiplayer server-list ping entirely: subtracted from the shown player
 * count and removed from the hover player sample, so a bot never shows up there as "Anonymous
 * Player" (the client's fallback for a sample entry it can't resolve a real profile for).
 *
 * <p>The sample is <b>rebuilt from real online players</b> rather than filtered by bot UUID: the
 * profile a bot contributes to the sample isn't guaranteed to carry the bot's logical UUID (skin
 * injection can swap the profile identity, and an anonymized sample carries no identity at all), so
 * subtractive filtering can miss — an entry the client then renders as "Anonymous Player".
 */
public final class ServerListPingListener implements Listener {

    private final FakePlayerManager manager;

    public ServerListPingListener(FakePlayerManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onServerListPing(PaperServerListPingEvent event) {
        Set<UUID> botUuids = new HashSet<>();
        Set<String> botNames = new HashSet<>();
        for (FakePlayer fp : manager.getActivePlayers()) {
            botUuids.add(fp.getUuid());
            botNames.add(fp.getName().toLowerCase(Locale.ROOT));
            Player body = fp.getPlayer();
            // The live entity's UUID can differ from the bot's logical UUID — cover both.
            if (body != null) botUuids.add(body.getUniqueId());
        }
        if (botUuids.isEmpty()) return;

        List<PaperServerListPingEvent.ListedPlayerInfo> listed = event.getListedPlayers();
        listed.clear();
        int realPlayers = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (botUuids.contains(online.getUniqueId())
                    || botNames.contains(online.getName().toLowerCase(Locale.ROOT))) {
                continue;
            }
            realPlayers++;
            listed.add(new PaperServerListPingEvent.ListedPlayerInfo(online.getName(), online.getUniqueId()));
        }

        event.setNumPlayers(realPlayers);
    }
}
