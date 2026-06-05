package me.bill.fakePlayerPlugin.api.event;

import org.bukkit.Chunk;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import me.bill.fakePlayerPlugin.api.FppBot;

public class FppBotChunkLoadEvent extends FppBotEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Chunk chunk;

    public FppBotChunkLoadEvent(@NotNull FppBot bot, @NotNull Chunk chunk) {
        super(bot);
        this.chunk = chunk;
    }

    public @NotNull Chunk getChunk() {
        return chunk;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
