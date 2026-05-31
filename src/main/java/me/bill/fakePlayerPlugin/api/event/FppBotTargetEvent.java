package me.bill.fakePlayerPlugin.api.event;

import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import me.bill.fakePlayerPlugin.api.FppBot;

public class FppBotTargetEvent extends FppBotEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Entity target;
    private boolean cancelled = false;

    public FppBotTargetEvent(@NotNull FppBot bot, @NotNull Entity target) {
        super(bot);
        this.target = target;
    }

    public @NotNull Entity getTarget() {
        return target;
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
