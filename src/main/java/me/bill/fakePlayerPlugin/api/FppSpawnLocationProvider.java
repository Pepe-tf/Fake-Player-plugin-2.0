package me.bill.fakePlayerPlugin.api;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows extensions to provide a last-known spawn location for core /fpp spawn --notp.
 */
public interface FppSpawnLocationProvider {
    @Nullable
    Location getSpawnLocation(@NotNull String botName, @Nullable CommandSender sender);
}
