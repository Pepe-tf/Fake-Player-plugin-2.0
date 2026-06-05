package me.bill.fakePlayerPlugin.command;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.FppScheduler;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

@SuppressWarnings("unused")
public class TpCommand implements FppCommand {

    private static final TextColor ACCENT = TextColor.fromHexString("#0079FF");
    private static final TextColor MUTED = NamedTextColor.GRAY;

    private final FakePlayerManager manager;

    public TpCommand(FakePlayerManager manager) {
        this.manager = manager;
    }

    @Override
    public String getName() {
        return "tp";
    }

    @Override
    public String getUsage() {
        return "[botname]";
    }

    @Override
    public String getDescription() {
        return "Teleports you to a bot.";
    }

    @Override
    public String getPermission() {
        return Perm.TP;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Lang.get("player-only"));
            return true;
        }

        List<FakePlayer> all = List.copyOf(manager.getActivePlayers());

        if (all.isEmpty()) {
            sender.sendMessage(Lang.get("tph-no-bots"));
            return true;
        }

        if (!manager.physicalBodiesEnabled()) {
            sender.sendMessage(Component.text("[ꜰᴘᴘ] ")
                    .color(ACCENT)
                    .append(Component.text("No body to tp to or from.").color(MUTED)));
            return true;
        }

        FakePlayer target;

        if (args.length == 0) {

            if (all.size() > 1) {
                sender.sendMessage(Lang.get("tp-specify-name"));
                listBots(sender, all);
                return true;
            }
            target = all.getFirst();
        } else {

            String name = args[0];
            target = all.stream()
                    .filter(fp -> fp.getName().equalsIgnoreCase(name))
                    .findFirst()
                    .orElse(null);

            if (target == null) {
                sender.sendMessage(Lang.get("tph-not-found", "name", name));
                return true;
            }
        }

        Entity body = target.getPhysicsEntity();
        Location dest = (body != null && body.isValid()) ? body.getLocation() : target.getSpawnLocation();

        if (dest == null) {
            sender.sendMessage(Lang.get("tph-failed", "name", target.getDisplayName()));
            return true;
        }

        FppScheduler.teleportAsync(player, dest);
        sender.sendMessage(Component.empty()
                .append(Component.text("[ꜰᴘᴘ] ").color(ACCENT))
                .append(Component.text("Teleported you to ").color(MUTED))
                .append(Component.text(target.getDisplayName()).color(ACCENT))
                .append(Component.text(".").color(MUTED)));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length != 1) return List.of();
        return manager.getActivePlayers().stream()
                .map(FakePlayer::getName)
                .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase()))
                .toList();
    }

    private void listBots(CommandSender sender, List<FakePlayer> bots) {
        sender.sendMessage(Component.empty()
                .append(Component.text("  Active bots: ").color(MUTED))
                .append(Component.text(String.join(
                                ", ",
                                bots.stream().map(FakePlayer::getDisplayName).toList()))
                        .color(ACCENT)));
    }
}
