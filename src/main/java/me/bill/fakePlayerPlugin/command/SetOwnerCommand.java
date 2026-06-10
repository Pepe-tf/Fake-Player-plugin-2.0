package me.bill.fakePlayerPlugin.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.permission.Perm;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class SetOwnerCommand implements FppCommand {
    private final FakePlayerPlugin plugin;
    private final FakePlayerManager manager;

    public SetOwnerCommand(FakePlayerPlugin plugin, FakePlayerManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public String getName() {
        return "setowner";
    }

    @Override
    public String getUsage() {
        return "<bot> <player>";
    }

    @Override
    public String getDescription() {
        return "Set the owner of a bot.";
    }

    @Override
    public String getPermission() {
        return Perm.SETOWNER;
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return Perm.has(sender, Perm.SETOWNER);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /fpp setowner <bot> <player>", NamedTextColor.RED));
            return true;
        }
        FakePlayer fp = manager.getByName(args[0]);
        if (fp == null) {
            sender.sendMessage(Component.text("Bot not found: " + args[0], NamedTextColor.RED));
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        UUID uuid = target.getUniqueId();
        String name = target.getName() != null ? target.getName() : args[1];
        fp.setSpawnedBy(name, uuid);
        fp.clearSharedControllers();
        if (Config.databaseEnabled() && plugin.getDatabaseManager() != null) {
            plugin.getDatabaseManager().updateBotOwner(fp.getUuid().toString(), name, uuid.toString());
        }
        manager.persistBotSettings(fp);
        sender.sendMessage(Component.text("Set owner of " + fp.getName() + " to " + name + ".", NamedTextColor.YELLOW));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (FakePlayer fp : manager.getActivePlayers())
                if (fp.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(fp.getName());
        } else if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers())
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(p.getName());
        }
        return out;
    }
}
