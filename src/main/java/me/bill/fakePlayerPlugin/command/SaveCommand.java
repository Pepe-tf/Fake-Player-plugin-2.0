package me.bill.fakePlayerPlugin.command;

import org.bukkit.command.CommandSender;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.permission.Perm;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

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
            sender.sendMessage(Component.text("Persistence is disabled in config.yml.", NamedTextColor.RED));
            return true;
        }
        if (plugin.getBotPersistence() == null) {
            sender.sendMessage(Component.text("Bot persistence is not available.", NamedTextColor.RED));
            return true;
        }
        plugin.getBotPersistence().saveFullAsync(plugin.getFakePlayerManager().getActivePlayers());
        sender.sendMessage(Component.text(
                "Saving " + plugin.getFakePlayerManager().getCount() + " active bot(s).", NamedTextColor.YELLOW));
        return true;
    }
}
