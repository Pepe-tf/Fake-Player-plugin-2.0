package me.bill.fakePlayerPlugin.api.event;

import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import me.bill.fakePlayerPlugin.api.FppBot;

public class FppBotTaskEvent extends FppBotEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String taskType;
    private final Action action;

    public enum Action {
        START,
        STOP
    }

    public FppBotTaskEvent(@NotNull FppBot bot, @NotNull String taskType, @NotNull Action action) {
        super(bot);
        this.taskType = taskType;
        this.action = action;
    }

    public @NotNull String getTaskType() {
        return taskType;
    }

    public @NotNull Action getAction() {
        return action;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
