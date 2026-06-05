package me.bill.fakePlayerPlugin.api.event;

import org.bukkit.Location;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import me.bill.fakePlayerPlugin.api.FppBot;

public class FppBotTeleportEvent extends FppBotEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Location from;
    private Location to;
    private boolean cancelled = false;

    public FppBotTeleportEvent(@NotNull FppBot bot, @NotNull Location from, @NotNull Location to) {
        super(bot);
        this.from = from.clone();
        this.to = to.clone();
    }

    public @NotNull Location getFrom() {
        return from.clone();
    }

    public @NotNull Location getTo() {
        return to.clone();
    }

    public void setTo(@NotNull Location to) {
        this.to = to.clone();
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
