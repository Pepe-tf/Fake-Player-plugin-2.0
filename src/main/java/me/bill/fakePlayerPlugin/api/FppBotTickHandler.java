package me.bill.fakePlayerPlugin.api;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface FppBotTickHandler {
    void onTick(@NotNull FppBot bot, @NotNull Player entity);
}
