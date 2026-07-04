package me.bill.fakePlayerPlugin.command;

import org.bukkit.command.CommandSender;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;

public final class SaveCommand implements FppCommand {
    private final FakePlayerPlugin plugin;

    public SaveCommand(FakePlayerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "save";
    }

    @Override
    public String getUsage() {
        return "";
    }

    @Override
    public String getDescription() {
        return "Save all active bot data immediately.";
    }

    @Override
    public String getPermission() {
        return Perm.SAVE;
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return Perm.has(sender, Perm.SAVE);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!Config.persistOnRestart()) {
            sender.sendMessage(Lang.get("save-disabled"));
            return true;
        }
        if (plugin.getBotPersistence() == null) {
            sender.sendMessage(Lang.get("save-unavailable"));
            return true;
        }
        plugin.getBotPersistence().saveFullAsync(plugin.getFakePlayerManager().getActivePlayers());
        sender.sendMessage(Lang.get(
                "save-started",
                "count",
                String.valueOf(plugin.getFakePlayerManager().getCount())));
        return true;
    }
}
