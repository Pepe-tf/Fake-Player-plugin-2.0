package me.bill.fakePlayerPlugin.api.event;

import org.bukkit.World;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import me.bill.fakePlayerPlugin.api.FppBot;

public class FppBotWorldChangeEvent extends FppBotEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final World from;
    private final World to;

    public FppBotWorldChangeEvent(@NotNull FppBot bot, @Nullable World from, @Nullable World to) {
        super(bot);
        this.from = from;
        this.to = to;
    }

    public @Nullable World getFrom() {
        return from;
    }

    public @Nullable World getTo() {
        return to;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
