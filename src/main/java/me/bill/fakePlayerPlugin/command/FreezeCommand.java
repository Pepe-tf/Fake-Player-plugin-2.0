package me.bill.fakePlayerPlugin.command;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.api.event.FppBotFreezeEvent;
import me.bill.fakePlayerPlugin.api.impl.FppBotImpl;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;

public class FreezeCommand implements FppCommand {

    private final FakePlayerManager manager;

    public FreezeCommand(FakePlayerManager manager) {
        this.manager = manager;
    }

    @Override
    public String getName() {
        return "freeze";
    }

    @Override
    public String getUsage() {
        return "<bot|all> [on|off]";
    }

    @Override
    public String getDescription() {
        return "Freeze or unfreeze a bot in place.";
    }

    @Override
    public String getPermission() {
        return Perm.FREEZE;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Lang.get("freeze-usage"));
            return true;
        }

        String target = args[0];
        String statArg = args.length >= 2 ? args[1].toLowerCase() : null;

        if (target.equalsIgnoreCase("--all")) {
            return handleFreezeAll(sender, statArg);
        }

        FakePlayer fp = manager.getByName(target);
        if (fp == null) {
            sender.sendMessage(Lang.get("freeze-not-found", "name", target));
            return true;
        }

        boolean nowFrozen;
        if ("on".equals(statArg)) {
            nowFrozen = true;
        } else if ("off".equals(statArg)) {
            nowFrozen = false;
        } else {
            nowFrozen = !fp.isFrozen();
        }

        applyFreeze(fp, nowFrozen);
        manager.persistBotSettings(fp);
        if (nowFrozen) {
            sender.sendMessage(Lang.get("freeze-frozen", "name", fp.getDisplayName()));
        } else {
            sender.sendMessage(Lang.get("freeze-unfrozen", "name", fp.getDisplayName()));
        }
        return true;
    }

    private boolean handleFreezeAll(CommandSender sender, String statArg) {
        Collection<FakePlayer> bots = manager.getActivePlayers();
        if (bots.isEmpty()) {
            sender.sendMessage(Lang.get("delete-none"));
            return true;
        }

        boolean nowFrozen;
        if ("on".equals(statArg)) {
            nowFrozen = true;
        } else if ("off".equals(statArg)) {
            nowFrozen = false;
        } else {
            long frozenCount = bots.stream().filter(FakePlayer::isFrozen).count();
            nowFrozen = frozenCount < bots.size() / 2.0 + 0.5;
        }

        for (FakePlayer fp : bots) applyFreeze(fp, nowFrozen);

        bots.forEach(fp -> manager.persistBotSettings(fp));
        if (nowFrozen) {
            sender.sendMessage(Lang.get("freeze-all-frozen", "count", String.valueOf(bots.size())));
        } else {
            sender.sendMessage(Lang.get("freeze-all-unfrozen", "count", String.valueOf(bots.size())));
        }
        return true;
    }

    private static void applyFreeze(FakePlayer fp, boolean freeze) {
        var api = FakePlayerPlugin.getInstance();
        if (api != null) {
            var freezeEvt = new FppBotFreezeEvent(new FppBotImpl(fp), freeze);
            Bukkit.getPluginManager().callEvent(freezeEvt);
            if (freezeEvt.isCancelled()) return;
        }
        fp.setFrozen(freeze);
        Player player = fp.getPlayer();
        if (player != null && player.isValid()) {

            if (freeze) {
                player.setVelocity(new Vector(0, 0, 0));
            }
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> names = manager.getActiveNames().stream()
                    .filter(n -> n.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
            if ("--all".startsWith(prefix)) names.add(0, "--all");
            return names;
        }
        if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            return List.of("on", "off").stream()
                    .filter(s -> s.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
