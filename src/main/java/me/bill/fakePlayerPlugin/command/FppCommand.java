package me.bill.fakePlayerPlugin.command;

import java.util.Collections;
import java.util.List;

import org.bukkit.command.CommandSender;

public interface FppCommand {

    String getName();

    default List<String> getAliases() {
        return Collections.emptyList();
    }

    String getUsage();

    String getDescription();

    default String getPermission() {
        return null;
    }

    default boolean canUse(CommandSender sender) {
        String perm = getPermission();
        return perm == null || sender.hasPermission(perm);
    }

    boolean execute(CommandSender sender, String[] args);

    default List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
