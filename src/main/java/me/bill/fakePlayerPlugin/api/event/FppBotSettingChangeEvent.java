package me.bill.fakePlayerPlugin.api.event;

import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import me.bill.fakePlayerPlugin.api.FppBot;

public class FppBotSettingChangeEvent extends FppBotEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String settingKey;
    private final Object oldValue;
    private final Object newValue;

    public FppBotSettingChangeEvent(
            @NotNull FppBot bot, @NotNull String settingKey, @Nullable Object oldValue, @Nullable Object newValue) {
        super(bot);
        this.settingKey = settingKey;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public @NotNull String getSettingKey() {
        return settingKey;
    }

    public @Nullable Object getOldValue() {
        return oldValue;
    }

    public @Nullable Object getNewValue() {
        return newValue;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
