package me.bill.fakePlayerPlugin.api.event;

import org.bukkit.Location;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import me.bill.fakePlayerPlugin.api.FppBot;

public class FppBotNavigationEvent extends FppBotEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    public enum Action {
        START,
        RECALC,
        ARRIVE,
        FAIL,
        CANCEL
    }

    private final Action action;
    private final Location location;

    public FppBotNavigationEvent(@NotNull FppBot bot, @NotNull Action action, @Nullable Location location) {
        super(bot);
        this.action = action;
        this.location = location != null ? location.clone() : null;
    }

    public @NotNull Action getAction() {
        return action;
    }

    public @Nullable Location getLocation() {
        return location != null ? location.clone() : null;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
