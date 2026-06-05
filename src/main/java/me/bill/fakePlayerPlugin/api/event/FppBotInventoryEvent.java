package me.bill.fakePlayerPlugin.api.event;

import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import me.bill.fakePlayerPlugin.api.FppBot;

public class FppBotInventoryEvent extends FppBotEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    public enum Action {
        PICKUP,
        DROP,
        EQUIP,
        UNEQUIP
    }

    private final Action action;
    private final ItemStack item;
    private final int slot;
    private boolean cancelled = false;

    public FppBotInventoryEvent(@NotNull FppBot bot, @NotNull Action action, @Nullable ItemStack item, int slot) {
        super(bot);
        this.action = action;
        this.item = item != null ? item.clone() : null;
        this.slot = slot;
    }

    public @NotNull Action getAction() {
        return action;
    }

    public @Nullable ItemStack getItem() {
        return item != null ? item.clone() : null;
    }

    public int getSlot() {
        return slot;
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
