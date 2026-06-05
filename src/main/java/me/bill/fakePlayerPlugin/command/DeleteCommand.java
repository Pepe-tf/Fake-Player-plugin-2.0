package me.bill.fakePlayerPlugin.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.FlagParser;
import me.bill.fakePlayerPlugin.util.TextUtil;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public class DeleteCommand implements FppCommand {

    private final FakePlayerManager manager;

    public DeleteCommand(FakePlayerManager manager) {
        this.manager = manager;
    }

    @Override
    public String getName() {
        return "despawn";
    }

    @Override
    public String getDescription() {
        return "Despawns a fake player bot by name.";
    }

    @Override
    public String getUsage() {
        return "<name> | all | --count <n> | --random [--count <n>]";
    }

    @Override
    public String getPermission() {
        return Perm.DELETE;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Lang.get("unknown-command", "0", "fpp"));
            return true;
        }

        if (args[0].equalsIgnoreCase("--all")) {
            if (Perm.missing(sender, Perm.DELETE_ALL)) {
                sender.sendMessage(Lang.get("no-permission"));
                return true;
            }
            if (manager.isRestorationInProgress()) {
                sender.sendMessage(Lang.get("delete-restore-in-progress"));
                return true;
            }
            int count = manager.getCount();
            if (count == 0) {
                sender.sendMessage(Lang.get("delete-none"));
                return true;
            }
            manager.removeAll("command_despawn_all");
            sender.sendMessage(Lang.get("delete-all", "count", String.valueOf(count)));
            return true;
        }

        if (args[0].equalsIgnoreCase("--own")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Lang.get("player-only"));
                return true;
            }
            if (Perm.missing(sender, Perm.DESPAWN_OWN)) {
                sender.sendMessage(Lang.get("no-permission"));
                return true;
            }
            if (manager.isRestorationInProgress()) {
                sender.sendMessage(Lang.get("delete-restore-in-progress"));
                return true;
            }
            List<FakePlayer> owned = manager.getBotsOwnedBy(player.getUniqueId());
            if (owned.isEmpty()) {
                sender.sendMessage(Lang.get("delete-none"));
                return true;
            }
            int deleted = 0;
            for (FakePlayer fp : owned) {
                manager.delete(fp.getName(), "command_despawn_own");
                deleted++;
            }
            sender.sendMessage(Lang.get("delete-own-success", "count", String.valueOf(deleted)));
            return true;
        }

        boolean firstIsFlag = args[0].startsWith("--");

        if (firstIsFlag) {
            if (Perm.missing(sender, Perm.DELETE)) {
                sender.sendMessage(Lang.get("no-permission"));
                return true;
            }
            if (manager.isRestorationInProgress()) {
                sender.sendMessage(Lang.get("delete-restore-in-progress"));
                return true;
            }

            FlagParser p = new FlagParser(args).deprecate("--num", "--count").parse();

            for (String w : p.warnings()) sender.sendMessage(TextUtil.colorize(w));
            if (p.hasErrors()) {
                sender.sendMessage(TextUtil.colorize(p.errorMessage()));
                return true;
            }

            boolean random = p.hasFlag("--random");
            int count = p.intFlag("--count", 1);
            if (p.hasErrors()) {
                sender.sendMessage(TextUtil.colorize(p.errorMessage()));
                return true;
            }

            List<FakePlayer> active = new ArrayList<>(manager.getActivePlayers());
            if (active.isEmpty()) {
                sender.sendMessage(Lang.get("delete-none"));
                return true;
            }

            if (random) {
                Collections.shuffle(active);
            }
            int toDelete = Math.min(count, active.size());
            for (int i = 0; i < toDelete; i++) {
                manager.delete(active.get(i).getName(), "command_despawn_random");
            }
            sender.sendMessage(Lang.get(
                    random ? "delete-random-success" : "delete-num-success", "count", String.valueOf(toDelete)));
            return true;
        }

        String input = args[0];

        FakePlayer fp = manager.getActivePlayers().stream()
                .filter(p -> p.getName().equalsIgnoreCase(input))
                .findFirst()
                .orElse(null);

        if (fp == null) {
            String inputLower = input.toLowerCase();
            fp = manager.getActivePlayers().stream()
                    .filter(p -> plainOf(p.getDisplayName()).toLowerCase().contains(inputLower))
                    .findFirst()
                    .orElse(null);
        }

        if (fp == null) {
            sender.sendMessage(Lang.get("delete-not-found", "name", input));
            return true;
        }

        String shown = plainOf(fp.getDisplayName());
        manager.delete(fp.getName(), "command_despawn");
        sender.sendMessage(Lang.get("delete-success", "name", shown));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            String typed = args[0].toLowerCase();
            if (Perm.has(sender, Perm.DELETE_ALL) && "--all".startsWith(typed)) suggestions.add("--all");
            if (sender instanceof Player && Perm.has(sender, Perm.DESPAWN_OWN) && "--own".startsWith(typed)) {
                suggestions.add("--own");
            }
            if (Perm.has(sender, Perm.DELETE)) {
                if ("--random".startsWith(typed)) suggestions.add("--random");
                if ("--count".startsWith(typed)) suggestions.add("--count");
            }
            manager.getActivePlayers().stream()
                    .map(FakePlayer::getName)
                    .filter(n -> n.toLowerCase().startsWith(typed))
                    .forEach(suggestions::add);
            return suggestions;
        }

        if (args.length == 2) {
            String prev = args[0].toLowerCase();
            String typed = args[1].toLowerCase();
            if (prev.equals("--count") || prev.equals("--random")) {
                if (prev.equals("--random") && "--count".startsWith(typed)) return List.of("--count");
                if (prev.equals("--count")) {
                    List<String> counts = new ArrayList<>();
                    int max = Math.min(manager.getCount(), 10);
                    for (int i = 1; i <= max; i++) {
                        String s = String.valueOf(i);
                        if (s.startsWith(typed)) counts.add(s);
                    }
                    return counts;
                }
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("--random") && args[1].equalsIgnoreCase("--count")) {
            String typed = args[2];
            List<String> counts = new ArrayList<>();
            int max = Math.min(manager.getCount(), 10);
            for (int i = 1; i <= max; i++) {
                String s = String.valueOf(i);
                if (s.startsWith(typed)) counts.add(s);
            }
            return counts;
        }

        return Collections.emptyList();
    }

    private static String plainOf(String miniMessage) {
        try {
            return PlainTextComponentSerializer.plainText()
                    .serialize(MiniMessage.miniMessage().deserialize(miniMessage));
        } catch (Exception e) {
            return miniMessage;
        }
    }
}
