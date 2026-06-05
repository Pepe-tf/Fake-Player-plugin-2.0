package me.bill.fakePlayerPlugin.api;

import java.util.List;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public interface FppCommandExtension {
    @NotNull
    String getCommandName();

    default @NotNull List<String> getAliases() {
        return List.of();
    }

    default @NotNull String getUsage() {
        return "";
    }

    default @NotNull String getDescription() {
        return "Extends /fpp " + getCommandName() + ".";
    }

    default @NotNull String getPermission() {
        return "";
    }

    default boolean canUse(@NotNull CommandSender sender) {
        String permission = getPermission();
        return permission == null || permission.isBlank() || sender.hasPermission(permission);
    }

    boolean execute(@NotNull CommandSender sender, @NotNull String[] args);

    default @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return List.of();
    }
}
