package me.bill.fakePlayerPlugin.command;

import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.gui.BotSettingGui;
import me.bill.fakePlayerPlugin.gui.SettingGui;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;

public class SettingCommand implements FppCommand {

    private final SettingGui gui;
    private final BotSettingGui botSettingGui;
    private final FakePlayerManager manager;

    public SettingCommand(SettingGui gui, BotSettingGui botSettingGui, FakePlayerManager manager) {
        this.gui = gui;
        this.botSettingGui = botSettingGui;
        this.manager = manager;
    }

    @Override
    public String getName() {
        return "settings";
    }

    @Override
    public String getUsage() {
        return "[bot]";
    }

    @Override
    public String getDescription() {
        return "Open the interactive settings GUI. Provide a bot name to open per-bot settings.";
    }

    @Override
    public String getPermission() {
        return Perm.SETTINGS;
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return Perm.has(sender, Perm.SETTINGS);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Lang.get("player-only"));
            return true;
        }
        if (!Perm.has(sender, Perm.SETTINGS)) {
            sender.sendMessage(Lang.get("no-permission"));
            return true;
        }

        if (args.length >= 1) {
            // Open per-bot settings GUI for the named bot
            FakePlayer fp = manager.getByName(args[0]);
            if (fp == null) {
                player.sendMessage(Lang.get("settings-bot-not-found", "name", args[0]));
                return true;
            }
            botSettingGui.open(player, fp);
            return true;
        }

        // No argument — open global settings GUI
        gui.open(player);
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return manager.getActivePlayers().stream()
                    .map(FakePlayer::getName)
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
