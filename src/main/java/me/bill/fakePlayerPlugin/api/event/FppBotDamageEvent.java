package me.bill.fakePlayerPlugin.api.event;

import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import me.bill.fakePlayerPlugin.api.FppBot;

public class FppBotDamageEvent extends FppBotEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private double damage;
    private final EntityDamageEvent.DamageCause cause;
    private final Entity damager;
    private boolean cancelled = false;

    public FppBotDamageEvent(
            @NotNull FppBot bot,
            double damage,
            @NotNull EntityDamageEvent.DamageCause cause,
            @Nullable Entity damager) {
        super(bot);
        this.damage = damage;
        this.cause = cause;
        this.damager = damager;
    }

    public double getDamage() {
        return damage;
    }

    public void setDamage(double damage) {
        this.damage = damage;
    }

    public @NotNull EntityDamageEvent.DamageCause getCause() {
        return cause;
    }

    public @Nullable Entity getDamager() {
        return damager;
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
