package me.bill.fakePlayerPlugin.api.event;

import org.bukkit.entity.Entity;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import me.bill.fakePlayerPlugin.api.FppBot;

public class FppBotFollowEvent extends FppBotEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    public enum Action {
        START,
        STOP,
        TARGET_CHANGE
    }

    private final Action action;
    private final Entity target;

    public FppBotFollowEvent(@NotNull FppBot bot, @NotNull Action action, @Nullable Entity target) {
        super(bot);
        this.action = action;
        this.target = target;
    }

    public @NotNull Action getAction() {
        return action;
    }

    public @Nullable Entity getTarget() {
        return target;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
