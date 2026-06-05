package me.bill.fakePlayerPlugin.api;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record FppCommandInfo(
        @NotNull String name,
        @NotNull List<String> aliases,
        @NotNull String usage,
        @NotNull String description,
        @Nullable String permission,
        @NotNull FppCommandSource source,
        boolean modifiesExistingCommand) {}
