package me.bill.fakePlayerPlugin.api.event;

import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import me.bill.fakePlayerPlugin.api.FppBot;

public class FppBotSpawnEvent extends FppBotEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final boolean restored;

    public FppBotSpawnEvent(@NotNull FppBot bot, boolean restored) {
        super(bot);
        this.restored = restored;
    }

    public boolean isRestored() {
        return restored;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
