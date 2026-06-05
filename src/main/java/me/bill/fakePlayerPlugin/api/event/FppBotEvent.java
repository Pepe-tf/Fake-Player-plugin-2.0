package me.bill.fakePlayerPlugin.api.event;

import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;

import me.bill.fakePlayerPlugin.api.FppBot;

public abstract class FppBotEvent extends Event {
    private final FppBot bot;

    protected FppBotEvent(@NotNull FppBot bot) {
        this.bot = bot;
    }

    public @NotNull FppBot getBot() {
        return bot;
    }
}
