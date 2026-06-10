package me.bill.fakePlayerPlugin.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.BotAccess;

public final class SneakCommand implements FppCommand {

    private final FakePlayerManager manager;

    public SneakCommand(FakePlayerManager manager) {
        this.manager = manager;
    }

    @Override
    public String getName() {
        return "sneak";
    }

    @Override
    public String getUsage() {
        return "<bot> [on|off|toggle]";
    }

    @Override
    public String getDescription() {
        return "Set or toggle a bot's sneaking state.";
    }

    @Override
    public String getPermission() {
        return Perm.SNEAK;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1 || args.length > 2) {
            sender.sendMessage(Lang.get("sneak-usage"));
            return true;
        }

        String botName = args[0];
        FakePlayer fp = manager.getByName(botName);
        if (fp == null) {
            sender.sendMessage(Lang.get("sneak-not-found", "name", botName));
            return true;
        }
        if (sender instanceof Player player
                && !Perm.hasOrOp(sender, Perm.ADMIN)
                && !BotAccess.canAdminister(player, fp)) {
            sender.sendMessage(Lang.get("no-permission"));
            return true;
        }

        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline()) {
            sender.sendMessage(Lang.get("sneak-bot-offline", "name", fp.getDisplayName()));
            return true;
        }

        SneakState state = args.length == 2 ? SneakState.parse(args[1]) : SneakState.TOGGLE;
        if (state == null) {
            sender.sendMessage(Lang.get("sneak-usage"));
            return true;
        }

        boolean sneaking =
                switch (state) {
                    case ON -> true;
                    case OFF -> false;
                    case TOGGLE -> !bot.isSneaking();
                };
        bot.setSneaking(sneaking);

        sender.sendMessage(Lang.get(sneaking ? "sneak-enabled" : "sneak-disabled", "name", fp.getDisplayName()));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!canUse(sender)) return List.of();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return manager.getActiveNames().stream()
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return List.of("on", "off", "toggle").stream()
                    .filter(option -> option.startsWith(prefix))
                    .toList();
        }
        return List.of();
    }

    private enum SneakState {
        ON,
        OFF,
        TOGGLE;

        private static SneakState parse(String raw) {
            for (SneakState state : values()) {
                if (state.name().equalsIgnoreCase(raw)) return state;
            }
            return null;
        }
    }
}
