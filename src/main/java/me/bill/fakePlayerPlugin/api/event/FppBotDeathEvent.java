package me.bill.fakePlayerPlugin.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import me.bill.fakePlayerPlugin.api.FppBot;

public class FppBotDeathEvent extends FppBotEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player killer;

    public FppBotDeathEvent(@NotNull FppBot bot, @Nullable Player killer) {
        super(bot);
        this.killer = killer;
    }

    public @Nullable Player getKiller() {
        return killer;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
