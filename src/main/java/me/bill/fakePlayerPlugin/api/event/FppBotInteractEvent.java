package me.bill.fakePlayerPlugin.api.event;

import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.NotNull;

import me.bill.fakePlayerPlugin.api.FppBot;

public class FppBotInteractEvent extends FppBotEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Entity target;
    private final EquipmentSlot hand;
    private boolean cancelled = false;

    public FppBotInteractEvent(@NotNull FppBot bot, @NotNull Entity target, @NotNull EquipmentSlot hand) {
        super(bot);
        this.target = target;
        this.hand = hand;
    }

    public @NotNull Entity getTarget() {
        return target;
    }

    public @NotNull EquipmentSlot getHand() {
        return hand;
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
