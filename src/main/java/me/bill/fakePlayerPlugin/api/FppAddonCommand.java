package me.bill.fakePlayerPlugin.api;

import java.util.Collections;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public interface FppAddonCommand {
    @NotNull
    String getName();

    @NotNull
    String getDescription();

    @NotNull
    String getUsage();

    @NotNull
    String getPermission();

    default @NotNull Material getIcon() {
        return Material.COMMAND_BLOCK;
    }

    default @NotNull List<String> getAliases() {
        return Collections.emptyList();
    }

    default boolean canUse(@NotNull CommandSender sender) {
        String permission = getPermission();
        return permission == null || permission.isBlank() || sender.hasPermission(permission);
    }

    boolean execute(@NotNull CommandSender sender, @NotNull String[] args);

    default @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return Collections.emptyList();
    }
}
