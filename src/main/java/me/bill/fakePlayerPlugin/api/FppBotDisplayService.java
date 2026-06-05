package me.bill.fakePlayerPlugin.api;

import org.jetbrains.annotations.NotNull;

public interface FppBotDisplayService {
    @NotNull
    String decorateDisplayName(@NotNull FppBot bot, @NotNull String displayName);
}
