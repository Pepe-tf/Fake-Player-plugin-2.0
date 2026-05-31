package me.bill.fakePlayerPlugin.api.event;

import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import me.bill.fakePlayerPlugin.api.FppBot;

public class FppBotAttackEvent extends FppBotEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Entity target;
    private double damage;
    private boolean cancelled = false;

    public FppBotAttackEvent(@NotNull FppBot bot, @NotNull Entity target, double damage) {
        super(bot);
        this.target = target;
        this.damage = damage;
    }

    public @NotNull Entity getTarget() {
        return target;
    }

    public double getDamage() {
        return damage;
    }

    public void setDamage(double damage) {
        this.damage = damage;
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
