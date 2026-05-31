package me.bill.fakePlayerPlugin.api.event;

import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import me.bill.fakePlayerPlugin.api.FppBot;

public class FppBotRenameEvent extends FppBotEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String oldName;
    private final String newName;

    public FppBotRenameEvent(@NotNull FppBot bot, @NotNull String oldName, @NotNull String newName) {
        super(bot);
        this.oldName = oldName;
        this.newName = newName;
    }

    public @NotNull String getOldName() {
        return oldName;
    }

    public @NotNull String getNewName() {
        return newName;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
