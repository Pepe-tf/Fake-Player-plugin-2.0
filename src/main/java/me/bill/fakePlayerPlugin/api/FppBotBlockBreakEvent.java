package me.bill.fakePlayerPlugin.api;

import java.util.Objects;

import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import me.bill.fakePlayerPlugin.api.event.FppBotEvent;

public class FppBotBlockBreakEvent extends FppBotEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Block block;
    private boolean cancelled;

    public FppBotBlockBreakEvent(@NotNull FppBot bot, @NotNull Block block) {
        super(bot);
        this.block = Objects.requireNonNull(block, "block");
    }

    public @NotNull Block getBlock() {
        return block;
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
