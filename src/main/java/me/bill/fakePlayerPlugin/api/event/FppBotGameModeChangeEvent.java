package me.bill.fakePlayerPlugin.api.event;

import org.bukkit.GameMode;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import me.bill.fakePlayerPlugin.api.FppBot;

public class FppBotGameModeChangeEvent extends FppBotEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final GameMode oldMode;
    private GameMode newMode;
    private boolean cancelled = false;

    public FppBotGameModeChangeEvent(@NotNull FppBot bot, @NotNull GameMode oldMode, @NotNull GameMode newMode) {
        super(bot);
        this.oldMode = oldMode;
        this.newMode = newMode;
    }

    public @NotNull GameMode getOldMode() {
        return oldMode;
    }

    public @NotNull GameMode getNewMode() {
        return newMode;
    }

    public void setNewMode(@NotNull GameMode newMode) {
        this.newMode = newMode;
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
